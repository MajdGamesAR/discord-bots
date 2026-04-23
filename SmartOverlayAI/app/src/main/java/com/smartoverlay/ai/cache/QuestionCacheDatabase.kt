package com.smartoverlay.ai.cache

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CachedQuestion::class], version = 1, exportSchema = false)
abstract class QuestionCacheDatabase : RoomDatabase() {
    abstract fun questionCacheDao(): QuestionCacheDao
}
