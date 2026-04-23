package com.smartoverlay.ai.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class SmartAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SmartAccessibility"
        var instance: SmartAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Handle content changes
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // Handle view clicks
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "Accessibility service destroyed")
    }

    fun getRootInActiveWindowText(): String {
        return try {
            rootInActiveWindow?.let { root ->
                buildString {
                    collectTextFromNode(root, this)
                }
            } ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error getting window text", e)
            ""
        }
    }

    private fun collectTextFromNode(node: android.view.accessibility.AccessibilityNodeInfo, builder: StringBuilder, depth: Int = 0) {
        if (depth > 5) return // Limit depth to avoid performance issues
        
        node.text?.let { text ->
            if (text.isNotEmpty()) {
                builder.append(text.toString()).append("\n")
            }
        }
        
        for (i in 0 until node.childCount.coerceAtMost(20)) { // Limit children
            node.getChild(i)?.let { child ->
                collectTextFromNode(child, builder, depth + 1)
                child.recycle()
            }
        }
    }
}
