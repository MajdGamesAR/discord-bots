package com.smartoverlay.ai.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "question_cache")
data class CachedQuestion(
    @PrimaryKey val questionHash: String,
    val questionText: String,
    val answer: String,
    val confidenceScore: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val questionType: String = "text" // text, math, mcq
)
