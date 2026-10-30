package com.mux.phone.dialer

import android.app.Application
import com.mux.phone.dialer.data.AppSettings

class DialerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
    }
}
