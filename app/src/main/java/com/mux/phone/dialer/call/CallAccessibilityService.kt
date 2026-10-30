package com.mux.phone.dialer.call

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Does nothing by itself. While an accessibility service of this app is enabled, many phones
 * (Android 10+) stop silencing the microphone for this app during a phone call, so the call
 * recorder gets real audio instead of an empty file.
 */
class CallAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    companion object {
        fun isEnabled(ctx: Context): Boolean = try {
            val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            enabled.contains(CallAccessibilityService::class.java.name)
        } catch (e: Exception) {
            false
        }
    }
}