package com.mux.phone.dialer

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mux.phone.dialer.data.AppSettings
import com.mux.phone.dialer.data.AppState
import com.mux.phone.dialer.ui.DialInput
import com.mux.phone.dialer.ui.DialerRoot
import com.mux.phone.dialer.ui.Tab

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        handle(intent)
        setContent { DialerRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        AppState.resumeTick++
    }

    /** tel: links / "dial" intents from other apps fill the keypad. */
    private fun handle(i: Intent?) {
        if (i == null) return
        if (i.action == Intent.ACTION_DIAL || i.action == Intent.ACTION_VIEW || i.action == Intent.ACTION_CALL_BUTTON) {
            val d = i.data
            if (d != null && d.scheme == "tel") {
                DialInput.number = d.schemeSpecificPart.orEmpty().filter { ch -> ch.isDigit() || ch == '+' || ch == '*' || ch == '#' }
            }
            AppState.tab = Tab.DIAL.ordinal
        }
    }
}
