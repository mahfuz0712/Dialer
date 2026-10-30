package com.mux.phone.dialer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mux.phone.dialer.data.AppSettings
import com.mux.phone.dialer.data.AppState
import com.mux.phone.dialer.data.ContactsRepo
import com.mux.phone.dialer.data.RecordingStore
import com.mux.phone.dialer.util.Caller
import com.mux.phone.dialer.util.Perms

enum class Tab(val label: String, val title: String) {
    DIAL("DIAL", "KEYPAD"),
    LOG("LOG", "RECENTS"),
    CONT("CONT", "CONTACTS"),
    REC("REC", "RECORDINGS"),
    SYS("SYS", "SYSTEM")
}

@Composable
fun DialerRoot() {
    val ctx = LocalContext.current
    DialerTheme(if (AppSettings.amber) AmberPalette else CyanPalette) {
        var update by remember { mutableStateOf<UpdateInfo?>(null) }
        LaunchedEffect(Unit) { if (isOnline(ctx)) update = checkUpdate(ctx).second }

        // ask for everything once, on first launch
        val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            AppState.permTick++
        }
        LaunchedEffect(Unit) {
            if (!AppSettings.askedPerms) {
                AppSettings.askedPerms = true
                val m = Perms.missing(ctx)
                if (m.isNotEmpty()) permLauncher.launch(m.toTypedArray())
            }
        }
        // (re)load contacts + clean old recordings when the app comes back or permissions change
        LaunchedEffect(AppState.permTick, AppState.resumeTick) {
            ContactsRepo.reload(ctx)
            RecordingStore.cleanup(ctx)
        }

        val tablet = LocalConfiguration.current.screenWidthDp >= 600
        val current = Tab.values()[AppState.tab]
        val goDial = { AppState.tab = Tab.DIAL.ordinal }

        val screen: @Composable () -> Unit = {
            when (current) {
                Tab.DIAL -> DialScreen(tablet)
                Tab.LOG -> RecentsScreen(tablet, goDial)
                Tab.CONT -> ContactsScreen(tablet)
                Tab.REC -> RecordingsScreen(tablet)
                Tab.SYS -> SystemScreen(tablet) { update = it }
            }
        }

        update?.let { u ->
            UpdateDialog(
                u, appVersion(ctx),
                onUpdate = { openUrl(ctx, u.apkUrl); update = null },
                onCancel = { update = null }
            )
        }
        Caller.pending?.let { p -> SimPickerDialog(p) }

        HudBackground(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
                if (tablet) {
                    Row(Modifier.fillMaxSize()) {
                        Column(Modifier.width(92.dp).fillMaxHeight()) {
                            for (t in Tab.values()) {
                                HudButton(
                                    label = t.label,
                                    modifier = Modifier.fillMaxWidth().height(60.dp),
                                    kind = KeyKind.OP,
                                    selected = t == current,
                                    fontSize = 14.sp
                                ) { AppState.tab = t.ordinal }
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Header(current.title, Modifier.fillMaxWidth().height(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.weight(1f).fillMaxWidth()) { screen() }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Header(current.title, Modifier.fillMaxWidth().height(34.dp))
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.weight(1f).fillMaxWidth()) { screen() }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth().height(46.dp)) {
                            for (t in Tab.values()) {
                                HudButton(
                                    label = t.label,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    kind = KeyKind.OP,
                                    selected = t == current,
                                    fontSize = 12.sp
                                ) { AppState.tab = t.ordinal }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, modifier: Modifier) {
    val c = Hud.colors
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HudText("◈ DIALER", color = c.primary, size = 16.sp, bold = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (com.mux.phone.dialer.call.CallRecorder.active) {
                HudText("● REC", color = c.danger, size = 11.sp, bold = true)
                Spacer(Modifier.width(10.dp))
            }
            HudText(title, color = c.dim, size = 11.sp)
        }
    }
}

@Composable
private fun SimPickerDialog(p: com.mux.phone.dialer.util.PendingCall) {
    val ctx = LocalContext.current
    val c = Hud.colors
    Dialog(onDismissRequest = { Caller.pending = null }) {
        Box(Modifier.fillMaxWidth().background(c.bg, HudShape(12.dp))) {
            HudPanel(Modifier.fillMaxWidth(), "CALL WITH") {
                HudText(p.number, color = c.primary, size = 20.sp, bold = true)
                Spacer(Modifier.height(10.dp))
                for (o in p.options) {
                    HudButton(
                        o.label, Modifier.fillMaxWidth().height(50.dp), KeyKind.GOOD, fontSize = 14.sp
                    ) {
                        Caller.pending = null
                        Caller.place(ctx, p.number, o.handle)
                    }
                }
                HudButton("CANCEL", Modifier.fillMaxWidth().height(44.dp), KeyKind.FN, fontSize = 13.sp) {
                    Caller.pending = null
                }
            }
        }
    }
}
