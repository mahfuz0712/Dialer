package com.mux.phone.dialer.call

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telecom.Call
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mux.phone.dialer.R

@RequiresApi(23)
object CallNotifier {
    private const val ID = 1001

    private fun pi(ctx: Context, code: Int, intent: Intent, activity: Boolean): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (activity) PendingIntent.getActivity(ctx, code, intent, flags)
        else PendingIntent.getBroadcast(ctx, code, intent, flags)
    }

    private fun action(ctx: Context, act: String, code: Int): PendingIntent =
        pi(ctx, code, Intent(ctx, CallActionReceiver::class.java).setAction(act), false)

    fun update(ctx: Context) {
        val call = CallManager.primary()
        if (call == null) {
            cancel(ctx)
            return
        }
        Channels.create(ctx)
        val ringing = call.state == Call.STATE_RINGING
        val open = pi(
            ctx, 10,
            Intent(ctx, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            true
        )
        val text = when (call.state) {
            Call.STATE_RINGING -> "Incoming call"
            Call.STATE_DIALING, Call.STATE_CONNECTING -> "Calling…"
            Call.STATE_HOLDING -> "On hold"
            else -> "Ongoing call"
        }
        val b = NotificationCompat.Builder(ctx, if (ringing) Channels.INCOMING else Channels.ONGOING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(CallManager.displayName(ctx, call))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
        if (ringing) {
            b.setPriority(NotificationCompat.PRIORITY_HIGH)
                .setFullScreenIntent(open, true)
                .addAction(0, "Decline", action(ctx, CallActionReceiver.DECLINE, 11))
                .addAction(0, "Answer", action(ctx, CallActionReceiver.ANSWER, 12))
        } else {
            b.addAction(0, "Hang up", action(ctx, CallActionReceiver.HANGUP, 13))
        }
        try {
            NotificationManagerCompat.from(ctx).notify(ID, b.build())
        } catch (e: SecurityException) {
        }
    }

    fun cancel(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID)
    }
}
