package com.mux.phone.dialer.call

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mux.phone.dialer.R

/**
 * Foreground (microphone) service that keeps the recorder alive while a call is running.
 * Android 9+ only gives microphone access to apps that are in the foreground.
 */
class RecordService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Channels.create(this)
        val n: Notification = NotificationCompat.Builder(this, Channels.RECORDING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle("Recording call")
            .setContentText("Dialer is recording this call")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        val type = when {
            Build.VERSION.SDK_INT >= 30 -> 128      // FOREGROUND_SERVICE_TYPE_MICROPHONE
            Build.VERSION.SDK_INT >= 29 -> -1       // FOREGROUND_SERVICE_TYPE_MANIFEST
            else -> 0
        }
        try {
            ServiceCompat.startForeground(this, 2002, n, type)
        } catch (e: Exception) {
            // Not allowed to become foreground right now - still try to record (process is alive).
        }
        val number = intent?.getStringExtra("n") ?: ""
        val incoming = intent?.getBooleanExtra("in", false) ?: false
        if (!CallRecorder.start(this, number, incoming)) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        CallRecorder.stop(this)
        super.onDestroy()
    }

    companion object {
        fun begin(ctx: Context, number: String, incoming: Boolean): Boolean {
            return try {
                val i = Intent(ctx, RecordService::class.java).putExtra("n", number).putExtra("in", incoming)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
                true
            } catch (e: Exception) {
                CallRecorder.start(ctx, number, incoming)
            }
        }

        fun end(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, RecordService::class.java))
            } catch (e: Exception) {
            }
            CallRecorder.stop(ctx)
        }
    }
}
