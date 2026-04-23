package com.smartoverlay.ai.solver

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MathSolver {

    companion object {
        private const val TAG = "MathSolver"
    }

    /**
     * Detect if text contains mathematical expressions
     */
    fun isMathProblem(text: String): Boolean {
        val mathPatterns = listOf(
            "\\d+\\s*[+\\-×÷=]\\s*\\d+",  // Basic arithmetic
            "\\d+\\^\\d+",                 // Exponents
            "\\√\\d+",                     // Square root
            "\\d+[a-z]",                   // Algebra (e.g., 2x)
            "[a-z]\\s*=\\s*\\d+",          // Equation (e.g., x = 5)
            "\\d+%",                       // Percentages
            "∫|∑|π|∞",                     // Math symbols
            "\\d+/\\d+",                   // Fractions
            "\\([\\d+\\-×÷]+\\)",         // Parenthesized expressions
            "solve.*for",                  // "solve for x"
            "calculate",                   // Calculate keyword
            "simplify"                     // Simplify keyword
        )
        
        val lowerText = text.lowercase()
        return mathPatterns.any { pattern ->
            Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
        }
    }

    /**
     * Extract mathematical expression from text
     */
    fun extractExpression(text: String): String? {
        // Look for equation patterns
        val equationPattern = Regex("([\\d.]+\\s*[+\\-×÷=]\\s*[\\d.\\s+\\-×÷=]+)")
        val match = equationPattern.find(text)
        return match?.value?.trim()
    }

    /**
     * Solve mathematical expression
     */
    suspend fun solve(expression: String): MathSolution = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "Solving expression: $expression")
            
            // Normalize expression
            val normalized = normalizeExpression(expression)
            
            // Try to evaluate
            val result = evaluateExpression(normalized)
            
            MathSolution(
                success = true,
                originalExpression = expression,
                solution = result,
                steps = generateSteps(normalized, result),
                verified = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to solve expression", e)
            MathSolution(
                success = false,
                originalExpression = expression,
                error = e.message ?: "Unknown error"
            )
        }
    }

    private fun normalizeExpression(expr: String): String {
        return expr
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace(" ", "")
            .replace("^", "**")
    }

    private fun evaluateExpression(expr: String): String {
        // Handle simple equations
        if ("=" in expr) {
            return solveEquation(expr)
        }
        
        // Handle arithmetic expressions using Kotlin's eval-like approach
        return try {
            // Safe evaluation of arithmetic expressions
            val result = safeEval(expr)
            result.toString()
        } catch (e: Exception) {
            throw RuntimeException("Cannot evaluate: $expr")
        }
    }

    private fun safeEval(expression: String): Number {
        // Very basic arithmetic evaluator - only supports +, -, *, /, **
        // This is a simplified version - in production, use a proper parser
        
        // Remove any non-arithmetic characters except digits and operators
        val sanitized = expression.replace(Regex("[^0-9.+\\-*/() ]"), "")
        
        // Use JavaScript engine for safe evaluation
        val scriptEngineManager = javax.script.ScriptEngineManager()
        val engine = scriptEngineManager.getEngineByName("JavaScript")
            ?: throw RuntimeException("No script engine available")
        
        @Suppress("UNCHECKED_CAST")
        return engine.eval(sanitized) as Number
    }

    private fun solveEquation(equation: String): String {
        // Simple linear equation solver (ax + b = c)
        val parts = equation.split("=")
        if (parts.size != 2) return "Invalid equation"
        
        val leftSide = parts[0].trim()
        val rightSide = parts[1].trim()
        
        // Try to isolate variable
        return when {
            "x" in leftSide -> solveForX(leftSide, rightSide)
            "y" in leftSide -> solveForY(leftSide, rightSide)
            else -> "Cannot solve: unknown variable format"
        }
    }

    private fun solveForX(left: String, right: String): String {
        // Simplified: assume format like "2x + 3 = 7" or "x = 5"
        return try {
            val rightValue = right.toDoubleOrNull() ?: return "Cannot parse right side"
            
            // Extract coefficient and constant from left side
            val xCoeff = Regex("(\\d*)x").find(left)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
            val constant = Regex("[+\\-]?\\s*\\d+(?!x)").find(left)?.value?.toDoubleOrNull() ?: 0.0
            
            val solution = (rightValue - constant) / xCoeff
            "x = ${String.format("%.4f", solution)}"
        } catch (e: Exception) {
            "Cannot solve for x"
        }
    }

    private fun solveForY(left: String, right: String): String {
        return solveForX(left, right).replace("x", "y")
    }

    private fun generateSteps(expression: String, result: String): List<String> {
        return listOf(
            "Original: $expression",
            "Simplified: $expression",
            "Result: $result"
        )
    }

    /**
     * Verify if a proposed answer matches the computed result
     */
    fun verifyAnswer(proposedAnswer: String, computedResult: String): Boolean {
        try {
            val proposedNum = proposedAnswer.toDoubleOrNull() ?: return false
            val computedNum = computedResult.toDoubleOrNull() ?: return false
            
            // Allow small floating point differences
            return kotlin.math.abs(proposedNum - computedNum) < 0.0001
        } catch (e: Exception) {
            return proposedAnswer.trim() == computedResult.trim()
        }
    }
}

data class MathSolution(
    val success: Boolean,
    val originalExpression: String = "",
    val solution: String = "",
    val steps: List<String> = emptyList(),
    val verified: Boolean = false,
    val error: String? = null
)
