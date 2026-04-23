package com.smartoverlay.ai.gesture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class GestureController(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "GestureController"
        
        // Shake detection threshold
        private const val SHAKE_THRESHOLD = 15f
        
        // Flip detection (using proximity + orientation)
        private const val FLIP_THRESHOLD = -0.8f
        
        // Lift detection
        private const val LIFT_ACCELERATION = 2.5f
    }

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private val vibrator: Vibrator
    
    // Callbacks
    var onShakeDetected: (() -> Unit)? = null
    var onFlipDetected: (() -> Unit)? = null
    var onLiftDetected: (() -> Unit)? = null
    
    // State tracking
    private var lastUpdate = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastGyroX = 0f
    private var lastGyroY = 0f
    private var lastGyroZ = 0f
    
    private val isProcessing = AtomicBoolean(false)
    
    // For flip detection
    private var lastOrientation = 0f
    private var faceDownStartTime = 0L
    private val FACE_DOWN_DURATION = 500L // 0.5 seconds

    init {
        @Suppress("DEPRECATION")
        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        initializeSensors()
    }

    private fun initializeSensors() {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    fun startListening() {
        accelerometer?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        Log.d(TAG, "Gesture listening started")
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
        Log.d(TAG, "Gesture listening stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                handleAccelerometer(event.values)
            }
            Sensor.TYPE_GYROSCOPE -> {
                handleGyroscope(event.values)
            }
        }
    }

    private fun handleAccelerometer(values: FloatArray) {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastUpdate > 100) { // 100ms debounce
            val x = values[0]
            val y = values[1]
            val z = values[2]
            
            // Detect shake
            detectShake(x, y, z, currentTime)
            
            // Detect flip (face down)
            detectFlip(z, currentTime)
            
            // Detect lift
            detectLift(x, y, z, currentTime)
            
            lastX = x
            lastY = y
            lastZ = z
            lastUpdate = currentTime
        }
    }

    private fun handleGyroscope(values: FloatArray) {
        lastGyroX = values[0]
        lastGyroY = values[1]
        lastGyroZ = values[2]
    }

    private fun detectShake(x: Float, y: Float, z: Float, currentTime: Long) {
        if (isProcessing.get()) return
        
        val speed = kotlin.math.abs(x + y + z - lastX - lastY - lastZ) / 
            (currentTime - lastUpdate) * 10000
        
        if (speed > SHAKE_THRESHOLD) {
            Log.d(TAG, "Shake detected! Speed: $speed")
            isProcessing.set(true)
            
            vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            onShakeDetected?.invoke()
            
            // Cooldown
            handler.postDelayed({
                isProcessing.set(false)
            }, 2000)
        }
    }

    private fun detectFlip(z: Float, currentTime: Long) {
        // When device is face down, Z acceleration is negative
        if (z < FLIP_THRESHOLD) {
            if (faceDownStartTime == 0L) {
                faceDownStartTime = currentTime
            } else if (currentTime - faceDownStartTime > FACE_DOWN_DURATION) {
                Log.d(TAG, "Flip detected!")
                faceDownStartTime = 0L
                onFlipDetected?.invoke()
            }
        } else {
            faceDownStartTime = 0L
        }
    }

    private fun detectLift(x: Float, y: Float, z: Float, currentTime: Long) {
        // Detect sudden upward movement
        val totalAccel = kotlin.math.sqrt(x*x + y*y + z*z)
        
        if (totalAccel > LIFT_ACCELERATION * 9.8f) { // Compare to gravity
            Log.d(TAG, "Lift detected! Acceleration: $totalAccel")
            onLiftDetected?.invoke()
        }
    }

    private fun vibrate(effect: VibrationEffect) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Sensor accuracy changed: $accuracy")
    }

    fun release() {
        stopListening()
        sensorManager = null
    }
}

/**
 * Touch gesture detector for overlay interactions
 */
class TouchGestureDetector(private val context: Context) {

    companion object {
        private const val TAG = "TouchGestureDetector"
        private const val DOUBLE_TAP_TIMEOUT = 300L
        private const val LONG_PRESS_TIMEOUT = 500L
        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }

    var onDoubleTap: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null
    var onSwipeUp: (() -> Unit)? = null
    var onSwipeDown: (() -> Unit)? = null

    private var lastTapTime = 0L
    private var longPressHandler = Handler(Looper.getMainLooper())
    private var startX = 0f
    private var startY = 0f
    private val longPressRunnable = Runnable {
        onLongPress?.invoke()
    }

    fun onTouchEvent(action: Int, x: Float, y: Float): Boolean {
        when (action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                startX = x
                startY = y
                longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT)
            }
            android.view.MotionEvent.ACTION_UP -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                
                val deltaTime = System.currentTimeMillis() - lastTapTime
                if (deltaTime < DOUBLE_TAP_TIMEOUT) {
                    onDoubleTap?.invoke()
                    lastTapTime = 0
                } else {
                    lastTapTime = System.currentTimeMillis()
                    
                    // Check for swipe
                    val deltaX = x - startX
                    val deltaY = y - startY
                    
                    if (kotlin.math.abs(deltaY) > SWIPE_THRESHOLD) {
                        if (deltaY < 0) {
                            onSwipeUp?.invoke()
                        } else {
                            onSwipeDown?.invoke()
                        }
                    }
                }
            }
            android.view.MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(longPressRunnable)
            }
        }
        return true
    }

    fun cancelLongPress() {
        longPressHandler.removeCallbacks(longPressRunnable)
    }
}
