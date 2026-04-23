package com.smartoverlay.ai.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smartoverlay.ai.service.OverlayService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Start overlay service on boot if activated
            val prefs = context.getSharedPreferences("smart_overlay_prefs", Context.MODE_PRIVATE)
            val isActivated = prefs.getBoolean("is_activated", false)
            
            if (isActivated) {
                val serviceIntent = Intent(context, OverlayService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
