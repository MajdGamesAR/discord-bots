package com.smartoverlay.ai.cache

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionCacheDao {

    @Query("SELECT * FROM question_cache WHERE questionHash = :hash")
    suspend fun getCachedQuestion(hash: String): CachedQuestion?

    @Query("SELECT * FROM question_cache ORDER BY timestamp DESC")
    fun getAllCachedQuestions(): Flow<List<CachedQuestion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedQuestion(question: CachedQuestion)

    @Query("DELETE FROM question_cache WHERE questionHash = :hash")
    suspend fun deleteCachedQuestion(hash: String)

    @Query("DELETE FROM question_cache WHERE timestamp < :olderThan")
    suspend fun deleteOlderQuestions(olderThan: Long)

    @Query("SELECT COUNT(*) FROM question_cache")
    suspend fun getCacheSize(): Int

    @Query("DELETE FROM question_cache")
    suspend fun clearCache()
}
