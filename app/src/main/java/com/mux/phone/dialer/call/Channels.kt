package com.mux.phone.dialer.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object Channels {
    const val INCOMING = "dialer_incoming"
    const val ONGOING = "dialer_ongoing"
    const val RECORDING = "dialer_recording"

    fun create(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        // Telecom plays the ringtone itself, so the heads-up channel stays silent.
        nm.createNotificationChannel(
            NotificationChannel(INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(ONGOING, "Ongoing calls", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(RECORDING, "Call recording", NotificationManager.IMPORTANCE_LOW)
        )
    }
}
