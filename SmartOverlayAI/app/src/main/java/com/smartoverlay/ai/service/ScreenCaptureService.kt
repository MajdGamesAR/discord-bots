package com.smartoverlay.ai.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.smartoverlay.ai.MainActivity
import com.smartoverlay.ai.R
import com.smartoverlay.ai.cache.CachedQuestion
import com.smartoverlay.ai.ocr.OcrEngine
import com.smartoverlay.ai.rag.RagEngine
import com.smartoverlay.ai.solver.MathSolver
import com.smartoverlay.ai.util.HashUtils
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "screen_capture_service"
        
        var instance: ScreenCaptureService? = null
            private set
        
        // Callback for screen capture results
        var onScreenAnalyzed: ((String, String, Float) -> Unit)? = null
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    
    private lateinit var ocrEngine: OcrEngine
    private lateinit var ragEngine: RagEngine
    private lateinit var mathSolver: MathSolver
    private lateinit var windowManager: WindowManager
    
    private val isCapturing = AtomicBoolean(false)
    private var lastCaptureTime = 0L
    private val CAPTURE_COOLDOWN = 2000L // 2 seconds between captures
    
    // Shake detection
    private var lastUpdate = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private val SHAKE_THRESHOLD = 12f

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        ocrEngine = OcrEngine(this)
        ragEngine = RagEngine(this)
        mathSolver = MathSolver()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Initialize sensors for shake detection
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        // Initialize RAG engine
        serviceScope.launch {
            ragEngine.initialize()
        }
        
        Log.d(TAG, "ScreenCaptureService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundService()
            ACTION_CAPTURE -> captureAndAnalyze()
            ACTION_STOP -> stopService()
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_running))
            .setContentText(getString(R.string.tap_to_configure))
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(createPendingIntent())
            .setOngoing(true)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
        
        // Register sensor listener
        accelerometer?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        
        Log.d(TAG, "Service started in foreground")
    }

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun setMediaProjection(projection: MediaProjection) {
        mediaProjection = projection
        setupVirtualDisplay()
    }

    private fun setupVirtualDisplay() {
        if (mediaProjection == null) return
        
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        
        imageReader = ImageReader.newInstance(
            metrics.widthPixels,
            metrics.heightPixels,
            PixelFormat.RGBA_8888,
            2
        )
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "SmartOverlayCapture",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
        
        Log.d(TAG, "Virtual display setup complete")
    }

    fun captureAndAnalyze() {
        if (!isCapturing.compareAndSet(false, true)) {
            Log.d(TAG, "Capture already in progress")
            return
        }
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCaptureTime < CAPTURE_COOLDOWN) {
            isCapturing.set(false)
            return
        }
        lastCaptureTime = currentTime
        
        serviceScope.launch {
            try {
                val bitmap = captureScreen()
                if (bitmap != null) {
                    analyzeBitmap(bitmap)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during capture and analyze", e)
            } finally {
                isCapturing.set(false)
            }
        }
    }

    private suspend fun captureScreen(): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Try ImageReader first (for Android 10+)
            imageReader?.acquireLatestImage()?.use { image ->
                val width = image.width
                val height = image.height
                
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width
                
                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                
                // Crop to actual size
                Bitmap.createBitmap(bitmap, 0, 0, width, height)
            } ?: run {
                // Fallback: use screenshot from accessibility if available
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture screen", e)
            null
        }
    }

    private suspend fun analyzeBitmap(bitmap: Bitmap) {
        // Step 1: Extract text using OCR
        val ocrResult = ocrEngine.extractText(bitmap)
        
        if (!ocrResult.success || ocrResult.text.isEmpty()) {
            Log.w(TAG, "OCR failed or no text found")
            return
        }
        
        // Step 2: Detect question areas
        val questionLines = ocrEngine.detectQuestionAreas(ocrResult.text)
        if (questionLines.isEmpty()) {
            Log.d(TAG, "No question areas detected")
            return
        }
        
        val questionText = questionLines.joinToString(" ")
        val questionHash = HashUtils.generateQuestionHash(questionText)
        
        Log.d(TAG, "Detected question: $questionText")
        
        // Step 3: Check cache first (anti-repetition system)
        val cachedAnswer = SmartOverlayApplication.database.questionCacheDao()
            .getCachedQuestion(questionHash)
        
        if (cachedAnswer != null) {
            Log.d(TAG, "Found cached answer")
            onScreenAnalyzed?.invoke(
                questionText,
                cachedAnswer.answer,
                cachedAnswer.confidenceScore
            )
            return
        }
        
        // Step 4: Check if it's a math problem
        if (mathSolver.isMathProblem(questionText)) {
            handleMathProblem(questionText, questionHash)
            return
        }
        
        // Step 5: Use RAG to find answer
        handleRagQuery(questionText, questionHash)
    }

    private suspend fun handleMathProblem(questionText: String, questionHash: String) {
        val expression = mathSolver.extractExpression(questionText)
        if (expression == null) {
            showNotFound(questionText, questionHash)
            return
        }
        
        val solution = mathSolver.solve(expression)
        
        if (solution.success) {
            val confidence = 0.9f // High confidence for math solutions
            
            // Cache the result
            val cachedQuestion = CachedQuestion(
                questionHash = questionHash,
                questionText = questionText,
                answer = solution.solution,
                confidenceScore = confidence,
                questionType = "math"
            )
            SmartOverlayApplication.database.questionCacheDao().insertCachedQuestion(cachedQuestion)
            
            onScreenAnalyzed?.invoke(questionText, solution.solution, confidence)
        } else {
            showNotFound(questionText, questionHash)
        }
    }

    private suspend fun handleRagQuery(questionText: String, questionHash: String) {
        // Note: In production, generate embedding using TensorFlow Lite model
        // For now, use a placeholder embedding
        val dummyEmbedding = FloatArray(384) { 0.1f }
        
        val response = ragEngine.findAnswer(questionText, dummyEmbedding)
        
        if (response.found && response.confidence > 0.65f) {
            // Cache the result
            val cachedQuestion = CachedQuestion(
                questionHash = questionHash,
                questionText = questionText,
                answer = response.answer,
                confidenceScore = response.confidence,
                questionType = "text"
            )
            SmartOverlayApplication.database.questionCacheDao().insertCachedQuestion(cachedQuestion)
            
            onScreenAnalyzed?.invoke(questionText, response.answer, response.confidence)
        } else {
            showNotFound(questionText, questionHash)
        }
    }

    private suspend fun showNotFound(questionText: String, questionHash: String) {
        val notFoundMsg = getString(R.string.not_found_in_source)
        
        // Still cache that we've seen this question
        val cachedQuestion = CachedQuestion(
            questionHash = questionHash,
            questionText = questionText,
            answer = notFoundMsg,
            confidenceScore = 0f,
            questionType = "text"
        )
        SmartOverlayApplication.database.questionCacheDao().insertCachedQuestion(cachedQuestion)
        
        onScreenAnalyzed?.invoke(questionText, notFoundMsg, 0f)
    }

    // SensorEventListener for shake detection
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val currentTime = System.currentTimeMillis()
            
            if (currentTime - lastUpdate > 100) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                val speed = kotlin.math.abs(x + y + z - lastX - lastY - lastZ) / 
                    (currentTime - lastUpdate) * 10000
                
                if (speed > SHAKE_THRESHOLD) {
                    Log.d(TAG, "Shake detected!")
                    captureAndAnalyze()
                }
                
                lastX = x
                lastY = y
                lastZ = z
                lastUpdate = currentTime
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun stopService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        
        sensorManager?.unregisterListener(this)
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        ocrEngine.close()
        
        Log.d(TAG, "ScreenCaptureService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.smartoverlay.ai.START_CAPTURE"
        const val ACTION_CAPTURE = "com.smartoverlay.ai.CAPTURE"
        const val ACTION_STOP = "com.smartoverlay.ai.STOP_CAPTURE"
    }
}
