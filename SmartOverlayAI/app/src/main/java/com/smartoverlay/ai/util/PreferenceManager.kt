package com.smartoverlay.ai.util

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings.Secure
import java.security.MessageDigest

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val deviceId: String = generateDeviceId(context)

    companion object {
        private const val PREFS_NAME = "smart_overlay_prefs"
        
        // Keys
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ACTIVATION_CODE = "activation_code"
        private const val KEY_IS_ACTIVATED = "is_activated"
        private const val KEY_ACTIVATION_DATE = "activation_date"
        private const val KEY_EXPIRY_DATE = "expiry_date"
        private const val KEY_VOICE_EMBEDDING = "voice_embedding"
        private const val KEY_IS_VOICE_REGISTERED = "is_voice_registered"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_STEALTH_MODE = "stealth_mode"
    }

    fun getDeviceId(): String = deviceId

    fun isActivated(): Boolean = prefs.getBoolean(KEY_IS_ACTIVATED, false)

    fun setActivated(activationCode: String, expiryDate: Long) {
        prefs.edit().apply {
            putString(KEY_ACTIVATION_CODE, activationCode)
            putBoolean(KEY_IS_ACTIVATED, true)
            putLong(KEY_ACTIVATION_DATE, System.currentTimeMillis())
            putLong(KEY_EXPIRY_DATE, expiryDate)
        }.apply()
    }

    fun getActivationCode(): String? = prefs.getString(KEY_ACTIVATION_CODE, null)

    fun getExpiryDate(): Long = prefs.getLong(KEY_EXPIRY_DATE, 0L)

    fun isExpired(): Boolean {
        val expiryDate = getExpiryDate()
        return expiryDate > 0 && System.currentTimeMillis() > expiryDate
    }

    fun saveVoiceEmbedding(embedding: FloatArray) {
        val embeddingStr = embedding.joinToString(",") { it.toString() }
        prefs.edit().putString(KEY_VOICE_EMBEDDING, embeddingStr).apply()
        prefs.edit().putBoolean(KEY_IS_VOICE_REGISTERED, true).apply()
    }

    fun getVoiceEmbedding(): FloatArray? {
        val embeddingStr = prefs.getString(KEY_VOICE_EMBEDDING, null) ?: return null
        return embeddingStr.split(",").map { it.toFloat() }.toFloatArray()
    }

    fun isVoiceRegistered(): Boolean = prefs.getBoolean(KEY_IS_VOICE_REGISTERED, false)

    fun isServiceEnabled(): Boolean = prefs.getBoolean(KEY_SERVICE_ENABLED, true)

    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    fun isStealthMode(): Boolean = prefs.getBoolean(KEY_STEALTH_MODE, false)

    fun setStealthMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STEALTH_MODE, enabled).apply()
    }

    fun clearActivation() {
        prefs.edit().apply {
            remove(KEY_ACTIVATION_CODE)
            putBoolean(KEY_IS_ACTIVATED, false)
            remove(KEY_ACTIVATION_DATE)
            remove(KEY_EXPIRY_DATE)
        }.apply()
    }

    private fun generateDeviceId(context: Context): String {
        val androidId = Secure.getString(
            context.contentResolver,
            Secure.ANDROID_ID
        ) ?: "unknown"
        
        // Create SHA-256 hash of Android ID
        val bytes = MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }
}
