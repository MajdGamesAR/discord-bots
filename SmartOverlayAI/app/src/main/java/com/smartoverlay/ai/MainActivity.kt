package com.smartoverlay.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.smartoverlay.ai.service.ScreenCaptureService
import com.smartoverlay.ai.service.OverlayService
import com.smartoverlay.ai.util.HashUtils
import com.smartoverlay.ai.util.PreferenceManager
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var preferences: PreferenceManager
    private lateinit var deviceIdText: TextView
    private lateinit var activationCard: MaterialCardView
    private lateinit var activationCodeInput: TextInputEditText
    private lateinit var activateButton: Button
    private lateinit var startServiceButton: Button
    private lateinit var stopServiceButton: Button
    private lateinit var registerVoiceButton: Button
    private lateinit var voiceStatusText: TextView

    private val mediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            startServices()
        } else {
            Toast.makeText(this, "Permissions required for app to function", Toast.LENGTH_LONG).show()
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val mediaProjection = mediaProjectionManager.getMediaProjection(result.resultCode, data!!)
            
            // Start screen capture service with projection
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
            }
            startForegroundService(serviceIntent)
            
            // Pass media projection to service
            ScreenCaptureService.instance?.setMediaProjection(mediaProjection)
            
            Toast.makeText(this, "Screen capture started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val voiceRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            matches?.firstOrNull()?.let { command ->
                handleVoiceCommand(command)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferences = SmartOverlayApplication.preferences
        
        initViews()
        setupClickListeners()
        updateUI()
    }

    private fun initViews() {
        deviceIdText = findViewById(R.id.device_id_text)
        activationCard = findViewById(R.id.activation_card)
        activationCodeInput = findViewById(R.id.activation_code_input)
        activateButton = findViewById(R.id.activate_button)
        startServiceButton = findViewById(R.id.start_service_button)
        stopServiceButton = findViewById(R.id.stop_service_button)
        registerVoiceButton = findViewById(R.id.register_voice_button)
        voiceStatusText = findViewById(R.id.voice_status)
    }

    private fun setupClickListeners() {
        activateButton.setOnClickListener {
            val code = activationCodeInput.text.toString().trim()
            if (code.isNotEmpty()) {
                validateAndActivate(code)
            }
        }

        startServiceButton.setOnClickListener {
            checkPermissionsAndStart()
        }

        stopServiceButton.setOnClickListener {
            stopServices()
        }

        registerVoiceButton.setOnClickListener {
            startVoiceRegistration()
        }
    }

    private fun updateUI() {
        // Show device ID
        deviceIdText.text = getString(R.string.device_id_format, preferences.getDeviceId())
        
        // Check activation status
        if (preferences.isActivated() && !preferences.isExpired()) {
            activationCard.visibility = View.GONE
            startServiceButton.isEnabled = true
        } else {
            activationCard.visibility = View.VISIBLE
            startServiceButton.isEnabled = false
        }
        
        // Update voice status
        if (preferences.isVoiceRegistered()) {
            voiceStatusText.text = "Voice registered ✓"
        } else {
            voiceStatusText.text = "Voice not registered"
        }
    }

    private fun validateAndActivate(code: String) {
        val deviceId = preferences.getDeviceId()
        // Use a secret key - in production, this should be securely stored
        val secretKey = "SMART_OVERLAY_SECRET_KEY_2024"
        
        val result = HashUtils.validateActivationCode(deviceId, code, secretKey)
        
        if (result.isValid) {
            result.expiryDate?.let { expiry ->
                preferences.setActivated(code, expiry)
                Toast.makeText(this, R.string.activation_success, Toast.LENGTH_SHORT).show()
                updateUI()
            }
        } else {
            Toast.makeText(this, "${R.string.activation_failed}: ${result.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkPermissionsAndStart() {
        if (!preferences.isActivated() || preferences.isExpired()) {
            Toast.makeText(this, R.string.activation_required, Toast.LENGTH_SHORT).show()
            return
        }

        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.grant_overlay_permission, Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        // Request runtime permissions
        val requiredPermissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.VIBRATE
        )

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            // All permissions granted, request screen capture
            requestScreenCapture()
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun requestScreenCapture() {
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }

    private fun startServices() {
        // Start overlay service
        val overlayIntent = Intent(this, OverlayService::class.java)
        startForegroundService(overlayIntent)
        
        // Screen capture will start after media projection is granted
        requestScreenCapture()
    }

    private fun stopServices() {
        // Stop screen capture service
        val captureIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        }
        startService(captureIntent)
        
        // Stop overlay service
        val overlayIntent = Intent(this, OverlayService::class.java)
        startService(overlayIntent)
        
        Toast.makeText(this, R.string.stop_service, Toast.LENGTH_SHORT).show()
    }

    private fun startVoiceRegistration() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say 'Activate' to register your voice")
        }
        
        try {
            voiceRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice recognition not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleVoiceCommand(command: String) {
        val lowerCommand = command.lowercase().trim()
        
        when {
            lowerCommand.contains("solve") -> {
                ScreenCaptureService.instance?.captureAndAnalyze()
                Toast.makeText(this, "Solving...", Toast.LENGTH_SHORT).show()
            }
            lowerCommand.contains("hide") -> {
                OverlayService.instance?.hideOverlay()
                Toast.makeText(this, "Overlay hidden", Toast.LENGTH_SHORT).show()
            }
            lowerCommand.contains("stop") -> {
                stopServices()
            }
            else -> {
                Toast.makeText(this, "Unknown command: $command", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
