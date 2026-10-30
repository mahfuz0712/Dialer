package com.mux.phone.dialer.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.mux.phone.dialer.data.AppSettings
import com.mux.phone.dialer.util.Caller

/**
 * Recording trigger for when Dialer is NOT the default phone app, and for Android 5.x / 6.0 where
 * the in-call API is not available. When Dialer IS the default app, [CallManager] does the job.
 */
class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        AppSettings.init(ctx)
        if (Caller.isDefaultDialer(ctx)) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val num = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                incoming = true
                if (!num.isNullOrEmpty()) number = num
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (last == TelephonyManager.EXTRA_STATE_IDLE || last == "") {
                    incoming = false
                    number = AppSettings.lastDialed
                }
                if (!started) {
                    if (CallRecorder.shouldAuto(ctx, number)) {
                        RecordService.begin(ctx, number, incoming)
                        started = true
                    }
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (started) RecordService.end(ctx)
                started = false
                incoming = false
                number = ""
            }
        }
        last = state
    }

    companion object {
        private var last = ""
        private var incoming = false
        private var number = ""
        private var started = false
    }
}
