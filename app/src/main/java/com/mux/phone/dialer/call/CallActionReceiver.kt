package com.mux.phone.dialer.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Handles Answer / Decline / Hang up buttons of the call notification. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT < 23) return
        val call = CallManager.primary() ?: return
        when (intent.action) {
            ANSWER -> {
                CallManager.answer(call)
                CallManager.openUi(ctx)
            }
            DECLINE -> CallManager.reject(call)
            HANGUP -> CallManager.hangup(call)
        }
    }

    companion object {
        const val ANSWER = "com.mux.phone.dialer.ANSWER"
        const val DECLINE = "com.mux.phone.dialer.DECLINE"
        const val HANGUP = "com.mux.phone.dialer.HANGUP"
    }
}
