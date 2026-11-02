package com.mux.phone.dialer.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import com.mux.phone.dialer.data.AppSettings
import com.mux.phone.dialer.data.ContactsRepo
import com.mux.phone.dialer.util.Caller
import com.mux.phone.dialer.util.Perms

/** Holds every live call handed to us by Android's telecom framework (we are the InCallService). */
@RequiresApi(23)
object CallManager {
    val calls = mutableStateListOf<Call>()

    /** Bumped on every call change so Compose re-reads the (non-observable) Call objects. */
    var tick by mutableIntStateOf(0)
    var muted by mutableStateOf(false)
    var route by mutableIntStateOf(CallAudioState.ROUTE_EARPIECE)
    var service: InCallService? = null

    private val incoming = HashSet<Call>()
    private val autoDone = HashSet<Call>()
    private val callbacks = HashMap<Call, Call.Callback>()
    private var recordingCall: Call? = null

    fun isIncoming(call: Call) = incoming.contains(call)

    /** The call the UI should focus on. */
    fun primary(): Call? {
        if (calls.isEmpty()) return null
        return calls.firstOrNull { it.state == Call.STATE_RINGING }
            ?: calls.firstOrNull { it.state == Call.STATE_ACTIVE }
            ?: calls.firstOrNull { it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING }
            ?: calls.firstOrNull { it.state == Call.STATE_HOLDING }
            ?: calls.first()
    }

    fun number(call: Call): String = call.details?.handle?.schemeSpecificPart.orEmpty()

    fun displayName(ctx: Context, call: Call): String {
        val n = number(call)
        ContactsRepo.nameFor(ctx, n)?.let { return it }
        val cn = call.details?.callerDisplayName
        if (!cn.isNullOrBlank()) return cn
        return n.ifEmpty { "UNKNOWN" }
    }

    // ---- lifecycle (called by DialerInCallService) ---------------------------------------------

    fun onAdded(ctx: Context, call: Call) {
        val ringing = call.state == Call.STATE_RINGING
        if (ringing) incoming.add(call)
        val cb = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                stateChanged(ctx, call, state)
            }

            override fun onDetailsChanged(call: Call, details: Call.Details) {
                tick++
            }
        }
        callbacks[call] = cb
        call.registerCallback(cb)
        calls.add(call)
        tick++
        CallNotifier.update(ctx)
        // Open our own call screen right away, for incoming calls too. The notification (with a
        // full-screen intent) stays as a backup, because some phones block background launches.
        openUi(ctx)
    }

    fun onRemoved(ctx: Context, call: Call) {
        callbacks.remove(call)?.let { call.unregisterCallback(it) }
        if (recordingCall === call) stopRecording(ctx)
        calls.remove(call)
        incoming.remove(call)
        autoDone.remove(call)
        tick++
        if (calls.isEmpty()) {
            muted = false
            CallNotifier.cancel(ctx)
        } else {
            CallNotifier.update(ctx)
        }
    }

    private fun stateChanged(ctx: Context, call: Call, state: Int) {
        tick++
        if (state == Call.STATE_ACTIVE && autoDone.add(call)) {
            if (recordingCall == null && CallRecorder.shouldAuto(ctx, number(call))) startRecording(ctx, call)
        }
        if ((state == Call.STATE_DISCONNECTING || state == Call.STATE_DISCONNECTED) && recordingCall === call) {
            stopRecording(ctx)
        }
        CallNotifier.update(ctx)
    }

    fun openUi(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(ctx, InCallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            )
        } catch (e: Exception) {
        }
    }

    // ---- actions ---------------------------------------------------------------------------------

    fun answer(call: Call) = call.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun reject(call: Call) = call.reject(false, null)
    fun hangup(call: Call) = call.disconnect()

    fun toggleHold(call: Call) {
        if (call.state == Call.STATE_HOLDING) call.unhold() else call.hold()
    }

    fun swapTo(other: Call) {
        primary()?.let { if (it.state == Call.STATE_ACTIVE) it.hold() }
        other.unhold()
    }

    fun dtmfStart(call: Call, c: Char) = call.playDtmfTone(c)
    fun dtmfStop(call: Call) = call.stopDtmfTone()

    @Suppress("DEPRECATION")
    fun applyMute(m: Boolean) {
        service?.setMuted(m)
        muted = m
    }

    @Suppress("DEPRECATION")
    fun applyRoute(r: Int) {
        service?.setAudioRoute(r)
    }

    fun toggleSpeaker() {
        applyRoute(if (route == CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER)
    }

    // ---- recording -------------------------------------------------------------------------------

    fun toggleRecording(ctx: Context, call: Call) {
        if (CallRecorder.active) stopRecording(ctx) else startRecording(ctx, call)
    }

    fun startRecording(ctx: Context, call: Call) {
        if (!Perms.has(ctx, Manifest.permission.RECORD_AUDIO)) {
            Caller.toast(ctx, "MICROPHONE PERMISSION REQUIRED")
            return
        }
        if (RecordService.begin(ctx, number(call), isIncoming(call))) {
            recordingCall = call
            if (AppSettings.recSpeaker) applyRoute(CallAudioState.ROUTE_SPEAKER)
        } else {
            Caller.toast(ctx, "RECORDING NOT POSSIBLE ON THIS PHONE")
        }
    }

    fun stopRecording(ctx: Context) {
        recordingCall = null
        RecordService.end(ctx)
    }
}
