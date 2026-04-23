package com.smartoverlay.ai.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import com.smartoverlay.ai.R
import com.smartoverlay.ai.SmartOverlayApplication
import kotlinx.coroutines.*

class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        
        var instance: OverlayService? = null
            private set
        
        private var currentView: View? = null
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    private var isShowing = false
    private var autoHideDelay = 5000L // 5 seconds
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Listen for screen analysis results
        ScreenCaptureService.onScreenAnalyzed = { question, answer, confidence ->
            showOverlay(question, answer, confidence)
        }
        
        Log.d(TAG, "OverlayService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createOverlay()
        return START_STICKY
    }

    private fun createOverlay() {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_window, null)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 50
            y = 200
        }
        
        // Setup close button
        overlayView.findViewById<ImageButton>(R.id.close_button).setOnClickListener {
            hideOverlay()
        }
        
        // Make overlay draggable
        setupDraggableOverlay(overlayView, params)
        
        Log.d(TAG, "Overlay view created")
    }

    private fun setupDraggableOverlay(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        
        view.setOnTouchListener(object : View.OnTouchListener {
            private var clickStartTime = 0L
            
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                event ?: return false
                
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        clickStartTime = System.currentTimeMillis()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(view, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val clickDuration = System.currentTimeMillis() - clickStartTime
                        val dx = kotlin.math.abs(event.rawX - initialTouchX)
                        val dy = kotlin.math.abs(event.rawY - initialTouchY)
                        
                        // If it was a tap (not drag)
                        if (dx < 10 && dy < 10 && clickDuration < 300) {
                            // Double tap detection could be added here
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    fun showOverlay(question: String, answer: String, confidence: Float) {
        handler.post {
            try {
                if (currentView != null && isShowing) {
                    windowManager.removeView(currentView)
                }
                
                val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                currentView = inflater.inflate(R.layout.overlay_window, null)
                
                // Set content
                currentView?.findViewById<TextView>(R.id.question_text)?.text = 
                    question.take(100) + if (question.length > 100) "..." else ""
                currentView?.findViewById<TextView>(R.id.answer_text)?.text = answer
                currentView?.findViewById<TextView>(R.id.confidence_score)?.text = 
                    getString(R.string.confidence_score, (confidence * 100).toInt())
                
                // Setup close button
                currentView?.findViewById<ImageButton>(R.id.close_button)?.setOnClickListener {
                    hideOverlay()
                }
                
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = 300
                }
                
                // Make draggable
                setupDraggableOverlay(currentView!!, params)
                
                windowManager.addView(currentView, params)
                isShowing = true
                
                Log.d(TAG, "Overlay shown: $answer")
                
                // Auto-hide after delay
                handler.postDelayed({
                    hideOverlay()
                }, autoHideDelay)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error showing overlay", e)
            }
        }
    }

    fun hideOverlay() {
        handler.post {
            try {
                currentView?.let { view ->
                    windowManager.removeView(view)
                    isShowing = false
                    Log.d(TAG, "Overlay hidden")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error hiding overlay", e)
            }
        }
    }

    fun setAutoHideDelay(delayMs: Long) {
        autoHideDelay = delayMs
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        handler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        
        try {
            currentView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing view", e)
        }
        
        Log.d(TAG, "OverlayService destroyed")
    }

    override fun onBind(intent: Intent?) = null
}
