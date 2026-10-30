package com.mux.phone.dialer.call

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.annotation.RequiresApi
import com.mux.phone.dialer.data.AppSettings

/** Android binds this service when Dialer is the default phone app and a call exists. */
@RequiresApi(23)
class DialerInCallService : InCallService() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        Channels.create(this)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.service = this
        CallManager.onAdded(this, call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.onRemoved(this, call)
    }

    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (audioState != null) {
            CallManager.muted = audioState.isMuted
            CallManager.route = audioState.route
            CallManager.tick++
        }
    }

    override fun onDestroy() {
        if (CallManager.service === this) CallManager.service = null
        super.onDestroy()
    }
}
