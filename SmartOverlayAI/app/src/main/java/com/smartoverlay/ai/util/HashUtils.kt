package com.smartoverlay.ai.util

import java.security.MessageDigest

object HashUtils {

    fun generateQuestionHash(questionText: String): String {
        val normalizedText = questionText.trim().lowercase()
        val bytes = MessageDigest.getInstance("SHA-256").digest(normalizedText.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateActivationCode(deviceId: String, secretKey: String, expiryDays: Int = 30): String {
        val timestamp = System.currentTimeMillis()
        val expiryMs = timestamp + (expiryDays.toLong() * 24 * 60 * 60 * 1000)
        
        val dataToHash = "$deviceId:$timestamp:$expiryMs:$secretKey"
        val hash = MessageDigest.getInstance("SHA-256").digest(dataToHash.toByteArray())
        val hashStr = hash.joinToString("") { "%02x".format(it) }.take(16)
        
        // Format: TIMESTAMP-EXPIRY-HASH (base64 encoded for shorter length)
        return "${toBase64(timestamp)}-${toBase64(expiryMs)}-$hashStr".uppercase()
    }

    fun validateActivationCode(
        deviceId: String,
        activationCode: String,
        secretKey: String
    ): ActivationResult {
        try {
            val parts = activationCode.split("-")
            if (parts.size != 3) {
                return ActivationResult(false, "Invalid code format")
            }

            val timestamp = fromBase64(parts[0])
            val expiryMs = fromBase64(parts[1])
            val providedHash = parts[2]

            // Verify hash
            val dataToHash = "$deviceId:$timestamp:$expiryMs:$secretKey"
            val expectedHash = MessageDigest.getInstance("SHA-256").digest(dataToHash.toByteArray())
            val expectedHashStr = expectedHash.joinToString("") { "%02x".format(it) }.take(16)

            if (providedHash.lowercase() != expectedHashStr.lowercase()) {
                return ActivationResult(false, "Invalid code")
            }

            // Check expiry
            if (System.currentTimeMillis() > expiryMs) {
                return ActivationResult(false, "Code expired")
            }

            return ActivationResult(true, "Valid", expiryMs)
        } catch (e: Exception) {
            return ActivationResult(false, "Validation error: ${e.message}")
        }
    }

    private fun toBase64(value: Long): String {
        return android.util.Base64.encodeToString(
            java.nio.ByteBuffer.allocate(8).putLong(value).array(),
            android.util.Base64.NO_WRAP
        ).replace("/", "_").replace("+", "-")
    }

    private fun fromBase64(encoded: String): Long {
        val normalized = encoded.replace("_", "/").replace("-", "+")
        val bytes = android.util.Base64.decode(normalized, android.util.Base64.NO_WRAP)
        return java.nio.ByteBuffer.wrap(bytes).long
    }
}

data class ActivationResult(
    val isValid: Boolean,
    val message: String,
    val expiryDate: Long? = null
)
