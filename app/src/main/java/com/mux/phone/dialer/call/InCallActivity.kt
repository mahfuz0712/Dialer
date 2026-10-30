package com.mux.phone.dialer.call

import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mux.phone.dialer.MainActivity
import com.mux.phone.dialer.data.AppSettings
import com.mux.phone.dialer.ui.AmberPalette
import com.mux.phone.dialer.ui.CyanPalette
import com.mux.phone.dialer.ui.DialerTheme
import com.mux.phone.dialer.ui.Hud
import com.mux.phone.dialer.ui.HudBackground
import com.mux.phone.dialer.ui.HudButton
import com.mux.phone.dialer.ui.HudPanel
import com.mux.phone.dialer.ui.HudShape
import com.mux.phone.dialer.ui.HudText
import com.mux.phone.dialer.ui.KeyKind
import kotlinx.coroutines.delay

/** The HUD call screen shown while Dialer is the default phone app. */
@RequiresApi(23)
class InCallActivity : ComponentActivity() {
    private var wake: PowerManager.WakeLock? = null

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            InCallScreen(onFinish = { finish() }, onProximity = { setProximity(it) }, onAddCall = {
                startActivity(android.content.Intent(this, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            })
        }
    }

    private fun setProximity(on: Boolean) {
        try {
            if (on) {
                if (wake == null) {
                    val pm = getSystemService(POWER_SERVICE) as PowerManager
                    if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                        wake = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "dialer:proximity")
                    }
                }
                if (wake?.isHeld == false) wake?.acquire(60 * 60 * 1000L)
            } else if (wake?.isHeld == true) {
                wake?.release()
            }
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }
}

private fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

@RequiresApi(23)
@Composable
fun InCallScreen(onFinish: () -> Unit, onProximity: (Boolean) -> Unit, onAddCall: () -> Unit) {
    DialerTheme(if (AppSettings.amber) AmberPalette else CyanPalette) {
        val ctx = LocalContext.current
        val c = Hud.colors
        val tick = CallManager.tick
        val call = remember(tick, CallManager.calls.size) { CallManager.primary() }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var showPad by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (true) {
                now = System.currentTimeMillis()
                delay(500)
            }
        }
        // close shortly after the last call ended
        LaunchedEffect(call == null) {
            if (call == null) {
                delay(600)
                if (CallManager.primary() == null) onFinish()
            }
        }
        val state = call?.state ?: Call.STATE_DISCONNECTED
        val speaker = CallManager.route == CallAudioState.ROUTE_SPEAKER
        LaunchedEffect(state, speaker, showPad) {
            onProximity(!speaker && !showPad && (state == Call.STATE_ACTIVE || state == Call.STATE_DIALING))
        }

        HudBackground(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(10.dp), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.fillMaxHeight().widthIn(max = 520.dp).fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        HudText("◈ DIALER", color = c.primary, size = 16.sp, bold = true)
                        if (CallRecorder.active) HudText("● REC", color = c.danger, size = 12.sp, bold = true)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (call != null) {
                        val ringing = state == Call.STATE_RINGING
                        val incoming = CallManager.isIncoming(call)
                        HudPanel(Modifier.fillMaxWidth(), if (incoming) "INCOMING LINK" else "OUTGOING LINK") {
                            HudText(CallManager.displayName(ctx, call), color = c.primary, size = 26.sp, bold = true, maxLines = 2)
                            val n = CallManager.number(call)
                            if (n.isNotEmpty()) HudText(n, color = c.dim, size = 14.sp)
                            Spacer(Modifier.height(10.dp))
                            val label = when (state) {
                                Call.STATE_RINGING -> "INCOMING CALL"
                                Call.STATE_DIALING -> "DIALING..."
                                Call.STATE_CONNECTING -> "CONNECTING..."
                                Call.STATE_ACTIVE -> "ACTIVE  " + fmtTime(now - call.details.connectTimeMillis)
                                Call.STATE_HOLDING -> "ON HOLD"
                                else -> "CALL ENDED"
                            }
                            HudText(label, color = if (state == Call.STATE_ACTIVE) c.good else c.accent, size = 18.sp, bold = true)
                        }
                        // other calls (call waiting / on hold)
                        for (o in CallManager.calls.toList()) {
                            if (o === call) continue
                            Spacer(Modifier.height(6.dp))
                            HudPanel(Modifier.fillMaxWidth(), "OTHER CALL") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    HudText(CallManager.displayName(ctx, o), color = c.text, size = 15.sp, bold = true, maxLines = 1, modifier = Modifier.weight(1f))
                                    HudButton("SWAP", Modifier.height(38.dp).widthIn(min = 80.dp), KeyKind.ACCENT, fontSize = 12.sp) { CallManager.swapTo(o) }
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (ringing) {
                            Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                HudButton("DECLINE", Modifier.weight(1f).fillMaxHeight(), KeyKind.DANGER, fontSize = 16.sp) { CallManager.reject(call) }
                                HudButton("ANSWER", Modifier.weight(1f).fillMaxHeight(), KeyKind.GOOD, fontSize = 16.sp) { CallManager.answer(call) }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth().height(66.dp)) {
                                HudButton("MUTE", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, selected = CallManager.muted, fontSize = 13.sp) { CallManager.applyMute(!CallManager.muted) }
                                HudButton("KEYPAD", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, fontSize = 13.sp) { showPad = true }
                                HudButton("SPEAKER", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, selected = speaker, fontSize = 13.sp) { CallManager.toggleSpeaker() }
                            }
                            Row(Modifier.fillMaxWidth().height(66.dp)) {
                                HudButton("HOLD", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, selected = state == Call.STATE_HOLDING, fontSize = 13.sp) { CallManager.toggleHold(call) }
                                HudButton(if (CallRecorder.active) "STOP REC" else "REC", Modifier.weight(1f).fillMaxHeight(), KeyKind.DANGER, selected = CallRecorder.active, fontSize = 13.sp) { CallManager.toggleRecording(ctx, call) }
                                HudButton("ADD", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, fontSize = 13.sp) { onAddCall() }
                            }
                            Spacer(Modifier.height(6.dp))
                            HudButton("END CALL", Modifier.fillMaxWidth().height(70.dp), KeyKind.DANGER, fontSize = 18.sp) { CallManager.hangup(call) }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                        HudText("CALL ENDED", color = c.dim, size = 18.sp, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (showPad && call != null) DtmfDialog(call) { showPad = false }
    }
}

@RequiresApi(23)
@Composable
private fun DtmfDialog(call: Call, onClose: () -> Unit) {
    val c = Hud.colors
    var typed by remember { mutableStateOf("") }
    var press by remember { mutableStateOf<Char?>(null) }
    // hold the tone briefly so the network registers it
    LaunchedEffect(press) {
        val ch = press ?: return@LaunchedEffect
        CallManager.dtmfStart(call, ch)
        delay(150)
        CallManager.dtmfStop(call)
        press = null
    }
    val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'), listOf('*', '0', '#'))
    Dialog(onDismissRequest = onClose) {
        Box(Modifier.fillMaxWidth().background(c.bg, HudShape(12.dp))) {
            HudPanel(Modifier.fillMaxWidth(), "KEYPAD") {
                HudText(typed.ifEmpty { "-" }, color = c.primary, size = 22.sp, bold = true, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                for (r in rows) {
                    Row(Modifier.fillMaxWidth().height(54.dp)) {
                        for (k in r) {
                            HudButton(k.toString(), Modifier.weight(1f).fillMaxHeight(), KeyKind.NUM, fontSize = 22.sp) {
                                typed += k
                                press = k
                            }
                        }
                    }
                }
                HudButton("CLOSE", Modifier.fillMaxWidth().height(44.dp), KeyKind.FN, fontSize = 13.sp) { onClose() }
            }
        }
    }
}