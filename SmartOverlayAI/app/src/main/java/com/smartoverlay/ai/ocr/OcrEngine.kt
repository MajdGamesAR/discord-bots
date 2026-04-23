package com.smartoverlay.ai.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OcrEngine(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractText(bitmap: Bitmap): OcrResult {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(image).await()
            
            val allText = visionText.textBlocks.flatMap { block ->
                block.lines.map { line -> line.text.trim() }
            }.filter { it.isNotEmpty() }
            
            val fullText = allText.joinToString("\n")
            
            OcrResult(
                success = true,
                text = fullText,
                lines = allText,
                confidence = visionText.textBlocks.maxOfOrNull { block ->
                    block.lines.maxOfOrNull { line -> line.confidence ?: 0f } ?: 0f
                } ?: 0f
            )
        } catch (e: Exception) {
            OcrResult(
                success = false,
                text = "",
                lines = emptyList(),
                error = e.message ?: "OCR failed"
            )
        }
    }

    fun detectQuestionAreas(text: String): List<String> {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        
        // Filter out common UI elements and ads
        val ignorePatterns = listOf(
            regex("skip", "advert", "ad ", "sponsor"),
            regex("close", "cancel", "back", "next"),
            regex("home", "menu", "settings", "profile"),
            regex("\\d+:\\d+", "\\d{1,2}:\\d{2}"), // Time patterns
            regex("^https?://", "www\\."), // URLs
            regex("^[@#]", "[A-Z]{3,}$") // Social handles, all caps short text
        )
        
        // Question indicators
        val questionIndicators = listOf(
            "?", "what", "which", "who", "when", "where", "why", "how",
            "calculate", "solve", "find", "determine", "explain",
            "choose", "select", "answer", "question", "mcq",
            "=", "+", "-", "×", "÷", // Math operators
            "^", "√", "∫", "∑", "π" // Math symbols
        )
        
        return lines.filter { line ->
            val lowerLine = line.lowercase()
            
            // Skip if matches ignore patterns
            val shouldIgnore = ignorePatterns.any { pattern ->
                pattern.containsMatchIn(lowerLine)
            }
            
            if (shouldIgnore) return@filter false
            
            // Keep if contains question indicators or is a substantial line
            val hasQuestionIndicator = questionIndicators.any { indicator ->
                lowerLine.contains(indicator)
            }
            
            val isSubstantial = line.length > 10 && 
                !line.startsWith("[") && 
                !line.startsWith("(")
            
            hasQuestionIndicator || isSubstantial
        }.take(10) // Limit to top 10 lines
    }

    private fun regex(vararg patterns: String): Regex {
        return Regex(patterns.joinToString("|"), RegexOption.IGNORE_CASE)
    }

    fun close() {
        recognizer.close()
    }
}

data class OcrResult(
    val success: Boolean,
    val text: String,
    val lines: List<String>,
    val confidence: Float = 0f,
    val error: String? = null
)
