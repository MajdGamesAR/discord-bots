package com.smartoverlay.ai.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class VoiceAuthenticator(private val context: Context) {

    companion object {
        private const val TAG = "VoiceAuthenticator"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val SIMILARITY_THRESHOLD = 0.8f
    }

    private var registeredEmbedding: FloatArray? = null

    /**
     * Register user's voice profile
     */
    suspend fun registerVoice(): VoiceAuthResult = withContext(Dispatchers.IO) {
        try {
            val audioData = recordAudio(durationSeconds = 3)
            val embedding = extractVoiceFeatures(audioData)
            
            registeredEmbedding = embedding
            
            // Save to preferences
            val prefs = context.getSharedPreferences("smart_overlay_prefs", Context.MODE_PRIVATE)
            val embeddingStr = embedding.joinToString(",") { it.toString() }
            prefs.edit().putString("voice_embedding", embeddingStr).apply()
            
            VoiceAuthResult(
                success = true,
                message = "Voice registered successfully",
                similarity = 1.0f
            )
        } catch (e: Exception) {
            Log.e(TAG, "Voice registration failed", e)
            VoiceAuthResult(
                success = false,
                message = "Registration failed: ${e.message}"
            )
        }
    }

    /**
     * Authenticate voice command
     */
    suspend fun authenticate(command: String): VoiceAuthResult = withContext(Dispatchers.IO) {
        try {
            // Load registered embedding
            val prefs = context.getSharedPreferences("smart_overlay_prefs", Context.MODE_PRIVATE)
            val embeddingStr = prefs.getString("voice_embedding", null)
            
            if (embeddingStr == null) {
                return@withContext VoiceAuthResult(
                    success = false,
                    message = "No voice profile registered"
                )
            }
            
            registeredEmbedding = embeddingStr.split(",").map { it.toFloat() }.toFloatArray()
            
            // Record and compare
            val audioData = recordAudio(durationSeconds = 2)
            val currentEmbedding = extractVoiceFeatures(audioData)
            
            val similarity = calculateSimilarity(registeredEmbedding!!, currentEmbedding)
            
            Log.d(TAG, "Voice similarity: $similarity")
            
            if (similarity >= SIMILARITY_THRESHOLD) {
                VoiceAuthResult(
                    success = true,
                    message = "Voice authenticated",
                    similarity = similarity
                )
            } else {
                VoiceAuthResult(
                    success = false,
                    message = "Voice not recognized",
                    similarity = similarity
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Authentication failed", e)
            VoiceAuthResult(
                success = false,
                message = "Authentication error: ${e.message}"
            )
        }
    }

    /**
     * Quick authentication without recording (for already processed commands)
     */
    fun quickAuthenticate(): Boolean {
        return registeredEmbedding != null
    }

    private suspend fun recordAudio(durationSeconds: Int): ShortArray = 
        withContext(Dispatchers.IO) {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            val totalSamples = SAMPLE_RATE * durationSeconds
            val audioData = ShortArray(totalSamples)
            var samplesRead = 0
            
            try {
                audioRecord.startRecording()
                
                while (samplesRead < totalSamples) {
                    val remaining = totalSamples - samplesRead
                    val readSize = minOf(bufferSize / 2, remaining)
                    val read = audioRecord.read(audioData, samplesRead, readSize)
                    if (read > 0) {
                        samplesRead += read
                    }
                }
                
                audioRecord.stop()
            } finally {
                audioRecord.release()
            }
            
            audioData
        }

    private fun extractVoiceFeatures(audioData: ShortArray): FloatArray {
        // Simplified feature extraction using MFCC-like approach
        // In production, use a proper ML model like ECAPA-TDNN or Resemblyzer
        
        val frameSize = 512
        val numFrames = audioData.size / frameSize
        val features = mutableListOf<Float>()
        
        for (i in 0 until numFrames) {
            val start = i * frameSize
            val end = minOf(start + frameSize, audioData.size)
            
            // Calculate energy
            var energy = 0.0
            for (j in start until end) {
                energy += audioData[j] * audioData[j]
            }
            energy /= frameSize
            
            // Calculate zero-crossing rate
            var zeroCrossings = 0
            for (j in start + 1 until end) {
                if ((audioData[j] >= 0) != (audioData[j - 1] >= 0)) {
                    zeroCrossings++
                }
            }
            val zcr = zeroCrossings.toFloat() / frameSize
            
            // Simple spectral centroid approximation
            var weightedSum = 0.0
            var sum = 0.0
            for (j in start until end step 4) {
                val magnitude = kotlin.math.abs(audioData[j].toDouble())
                val freq = (j - start).toDouble()
                weightedSum += freq * magnitude
                sum += magnitude
            }
            val spectralCentroid = if (sum > 0) weightedSum / sum else 0.0
            
            features.add(energy.toFloat())
            features.add(zcr)
            features.add(spectralCentroid.toFloat())
        }
        
        // Pad or trim to fixed size
        val fixedSize = 128
        return when {
            features.size >= fixedSize -> features.take(fixedSize).toFloatArray()
            else -> {
                val padded = FloatArray(fixedSize)
                features.forEachIndexed { index, value ->
                    if (index < fixedSize) padded[index] = value
                }
                padded
            }
        }
    }

    private fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) return 0f
        
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }
        
        if (norm1 == 0f || norm2 == 0f) return 0f
        
        return dotProduct / (sqrt(norm1) * sqrt(norm2))
    }

    fun isRegistered(): Boolean {
        val prefs = context.getSharedPreferences("smart_overlay_prefs", Context.MODE_PRIVATE)
        return prefs.contains("voice_embedding")
    }
}

data class VoiceAuthResult(
    val success: Boolean,
    val message: String,
    val similarity: Float = 0f
)
