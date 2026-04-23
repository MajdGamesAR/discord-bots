package com.smartoverlay.ai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.room.Room
import com.smartoverlay.ai.cache.QuestionCacheDatabase
import com.smartoverlay.ai.util.PreferenceManager

class SmartOverlayApplication : Application() {

    companion object {
        lateinit var instance: SmartOverlayApplication private set
        
        val database: QuestionCacheDatabase by lazy {
            Room.databaseBuilder(
                instance,
                QuestionCacheDatabase::class.java,
                "question_cache_db"
            ).build()
        }
        
        val preferences: PreferenceManager by lazy {
            PreferenceManager(instance)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "screen_capture_service",
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
