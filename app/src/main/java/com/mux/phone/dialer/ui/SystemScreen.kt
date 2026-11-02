package com.mux.phone.dialer.ui

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.phone.dialer.AppInfo
import com.mux.phone.dialer.call.CallAccessibilityService
import com.mux.phone.dialer.call.TelecomHelper
import com.mux.phone.dialer.data.AppSettings
import com.mux.phone.dialer.data.AppState
import com.mux.phone.dialer.util.Caller
import com.mux.phone.dialer.util.Perms
import kotlinx.coroutines.launch

/** "SYS" tab: system info, default-dialer status, permissions, settings, updates, author. */
@Composable
fun SystemScreen(tablet: Boolean, onUpdateFound: (UpdateInfo) -> Unit) {
    val uri = LocalUriHandler.current
    val open: (String) -> Unit = { link -> runCatching { uri.openUri(link) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (tablet) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    AppPanel(Modifier.fillMaxWidth())
                    Gap(); DefaultDialerPanel(Modifier.fillMaxWidth())
                    Gap(); IncomingScreenPanel(Modifier.fillMaxWidth())
                    Gap(); PermissionsPanel(Modifier.fillMaxWidth())
                    Gap(); UpdatePanel(onUpdateFound, Modifier.fillMaxWidth())
                    Gap(); MoreAppsPanel(open, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    ContactSettingsPanel(Modifier.fillMaxWidth())
                    Gap(); DialerSettingsPanel(Modifier.fillMaxWidth())
                    Gap(); RecordingSettingsPanel(Modifier.fillMaxWidth())
                    Gap(); AccessibilityPanel(Modifier.fillMaxWidth())
                    Gap(); AuthorPanel(open, Modifier.fillMaxWidth())
                }
            }
        } else {
            AppPanel(Modifier.fillMaxWidth())
            Gap(); DefaultDialerPanel(Modifier.fillMaxWidth())
            Gap(); IncomingScreenPanel(Modifier.fillMaxWidth())
            Gap(); PermissionsPanel(Modifier.fillMaxWidth())
            Gap(); ContactSettingsPanel(Modifier.fillMaxWidth())
            Gap(); DialerSettingsPanel(Modifier.fillMaxWidth())
            Gap(); RecordingSettingsPanel(Modifier.fillMaxWidth())
            Gap(); AccessibilityPanel(Modifier.fillMaxWidth())
            Gap(); UpdatePanel(onUpdateFound, Modifier.fillMaxWidth())
            Gap(); AuthorPanel(open, Modifier.fillMaxWidth())
            Gap(); MoreAppsPanel(open, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Gap() = Spacer(Modifier.height(8.dp))

// ---------------------------------------------------------------------------------------------
// setting rows
// ---------------------------------------------------------------------------------------------

@Composable
private fun SettingRow(label: String, hint: String?, value: String, kind: KeyKind = KeyKind.OP, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            HudText(label, color = Hud.colors.text, size = 12.sp)
            if (hint != null) HudText(hint, color = Hud.colors.dim, size = 9.sp)
        }
        HudButton(value, Modifier.width(118.dp).height(38.dp), kind, fontSize = 11.sp, onClick = onClick)
    }
}

@Composable
private fun ToggleRow(label: String, hint: String? = null, value: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(label, hint, if (value) "ON" else "OFF", if (value) KeyKind.GOOD else KeyKind.FN) { onChange(!value) }
}

/** Taps cycle through [options] (value, label). */
@Composable
private fun CycleRow(label: String, hint: String? = null, options: List<Pair<String, String>>, current: String, onChange: (String) -> Unit) {
    val i = options.indexOfFirst { it.first == current }.coerceAtLeast(0)
    SettingRow(label, hint, options[i].second) { onChange(options[(i + 1) % options.size].first) }
}

// ---------------------------------------------------------------------------------------------
// panels
// ---------------------------------------------------------------------------------------------

@Composable
private fun AppPanel(modifier: Modifier) {
    val c = Hud.colors
    val version = appVersion(LocalContext.current)
    HudPanel(modifier, "SYSTEM") {
        HudText("◈ ${AppInfo.NAME.uppercase()}", color = c.primary, size = 28.sp, bold = true)
        HudText("VERSION $version", color = c.accent, size = 12.sp)
        Spacer(Modifier.height(8.dp))
        HudText(
            "Keypad, call log, contacts from phone / SIM / Google accounts, and call recording in a sci-fi HUD interface. Adaptive layout for phones and tablets.",
            color = c.text, size = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        Row {
            HudText("THEME", color = c.dim, size = 10.sp, modifier = Modifier.weight(1f))
        }
        SettingRow("COLOR THEME", null, if (AppSettings.amber) "AMBER" else "CYAN", KeyKind.ACCENT) {
            AppSettings.amber = !AppSettings.amber
        }
    }
}

@Composable
private fun DefaultDialerPanel(modifier: Modifier) {
    val c = Hud.colors
    val ctx = LocalContext.current
    val tick = AppState.resumeTick + AppState.permTick
    var bump by remember { mutableStateOf(0) }
    val isDefault = remember(tick, bump) { Caller.isDefaultDialer(ctx) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { bump++ }

    HudPanel(modifier, "DEFAULT PHONE APP") {
        if (Build.VERSION.SDK_INT < 23) {
            HudText("ANDROID 5.x: THE SYSTEM HANDLES THE CALL SCREEN.", color = c.accent, size = 12.sp, bold = true)
            Spacer(Modifier.height(4.dp))
            HudText(
                "Dialer places calls through the system and can still record them. Setting a default phone app needs Android 6.0 or newer.",
                color = c.text, size = 12.sp
            )
        } else {
            HudText(
                if (isDefault) "● DIALER IS THE DEFAULT PHONE APP" else "○ NOT THE DEFAULT PHONE APP",
                color = if (isDefault) c.good else c.accent, size = 13.sp, bold = true
            )
            Spacer(Modifier.height(4.dp))
            HudText(
                if (isDefault) "Dialer shows the call screen, rings through the system, and records calls."
                else "Make Dialer the default to use its HUD call screen. Recording also works without it, but it is more reliable as the default app.",
                color = c.text, size = 12.sp
            )
            if (!isDefault) {
                Spacer(Modifier.height(8.dp))
                HudButton("SET AS DEFAULT", Modifier.fillMaxWidth().height(46.dp), KeyKind.ACCENT, fontSize = 13.sp) {
                    val i = TelecomHelper.requestIntent(ctx)
                    if (i != null) {
                        try { launcher.launch(i) } catch (e: Exception) { Caller.toast(ctx, "NOT SUPPORTED ON THIS PHONE") }
                    } else {
                        Caller.toast(ctx, "NOT AVAILABLE ON THIS PHONE")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsPanel(modifier: Modifier) {
    val c = Hud.colors
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { AppState.permTick++ }
    val status = remember(AppState.permTick, AppState.resumeTick) { Perms.all().map { it to Perms.has(ctx, it) } }
    HudPanel(modifier, "PERMISSIONS") {
        for ((p, ok) in status) {
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                HudText(Perms.label(p), color = c.text, size = 12.sp, modifier = Modifier.weight(1f))
                HudText(if (ok) "OK" else "MISSING", color = if (ok) c.good else c.danger, size = 12.sp, bold = true)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HudButton("GRANT ALL", Modifier.weight(1f).height(44.dp), KeyKind.ACCENT, enabled = status.any { !it.second }, fontSize = 12.sp) {
                val m = Perms.missing(ctx)
                if (m.isNotEmpty()) launcher.launch(m.toTypedArray())
            }
            HudButton("APP SETTINGS", Modifier.weight(1f).height(44.dp), KeyKind.FN, fontSize = 12.sp) { Caller.openAppSettings(ctx) }
        }
    }
}

@Composable
private fun ContactSettingsPanel(modifier: Modifier) {
    HudPanel(modifier, "CONTACT SETTINGS") {
        CycleRow("SORT CONTACTS BY", null, listOf("FIRST" to "FIRST NAME", "LAST" to "LAST NAME"), AppSettings.sortBy) { AppSettings.sortBy = it }
        ToggleRow("SHOW NUMBER IN LIST", null, AppSettings.showNumbers) { AppSettings.showNumbers = it }
        ToggleRow("INCLUDE SIM CONTACTS", "SHOWN UNDER ALL + SIM CHIP", AppSettings.includeSim) { AppSettings.includeSim = it }
        ToggleRow("INCLUDE OTHER ACCOUNTS", "WHATSAPP, EXCHANGE, ETC.", AppSettings.includeOther) { AppSettings.includeOther = it }
    }
}

@Composable
private fun DialerSettingsPanel(modifier: Modifier) {
    HudPanel(modifier, "DIALER SETTINGS") {
        ToggleRow("KEY TONES", "BEEP WHEN A KEY IS PRESSED", AppSettings.keyTone) { AppSettings.keyTone = it }
        ToggleRow("VIBRATE ON KEY PRESS", null, AppSettings.haptic) { AppSettings.haptic = it }
        ToggleRow("SMART DIAL SUGGESTIONS", "MATCH CONTACTS BY NUMBER OR T9 NAME", AppSettings.smartDial) { AppSettings.smartDial = it }
        CycleRow(
            "CALL WITH (DUAL SIM)", "ONLY USED WHEN 2 SIMS ARE ACTIVE",
            listOf("ASK" to "ASK EVERY TIME", "DEFAULT" to "SYSTEM DEFAULT", "SIM1" to "ALWAYS SIM 1", "SIM2" to "ALWAYS SIM 2"),
            AppSettings.simChoice
        ) { AppSettings.simChoice = it }
    }
}

@Composable
private fun RecordingSettingsPanel(modifier: Modifier) {
    val c = Hud.colors
    HudPanel(modifier, "RECORDING SETTINGS") {
        CycleRow(
            "AUTO RECORD", "START WHEN A CALL IS CONNECTED",
            listOf("OFF" to "OFF", "ALL" to "ALL CALLS", "SAVED" to "SAVED NUMBERS", "UNSAVED" to "UNKNOWN NUMBERS"),
            AppSettings.autoRecord
        ) { AppSettings.autoRecord = it }
        CycleRow(
            "AUDIO SOURCE", "TRY ANOTHER ONE IF A RECORDING IS SILENT",
            listOf("AUTO" to "AUTO", "MIC" to "MICROPHONE", "COMM" to "VOICE COMM", "CALL" to "VOICE CALL*"),
            AppSettings.recSource
        ) { AppSettings.recSource = it }
        CycleRow(
            "QUALITY", null,
            listOf("LOW" to "LOW (SMALL)", "NORMAL" to "NORMAL", "HIGH" to "HIGH"),
            AppSettings.recQuality
        ) { AppSettings.recQuality = it }
        ToggleRow("SPEAKER WHILE RECORDING", "CAPTURES THE OTHER PERSON BETTER", AppSettings.recSpeaker) { AppSettings.recSpeaker = it }
        CycleRow(
            "AUTO DELETE OLD", null,
            listOf("0" to "NEVER", "7" to "AFTER 7 DAYS", "30" to "AFTER 30 DAYS", "90" to "AFTER 90 DAYS"),
            AppSettings.recKeepDays.toString()
        ) { AppSettings.recKeepDays = it.toInt() }
        Spacer(Modifier.height(6.dp))
        HudText(
            "ANDROID 10+ BLOCKS APPS FROM HEARING THE OTHER SIDE OF A CALL. THE OTHER PERSON IS USUALLY RECORDED ONLY ON SPEAKER. *VOICE CALL WORKS ONLY ON A FEW PHONES / CUSTOM ROMS. RECORD ONLY WHERE THE LAW ALLOWS IT.",
            color = c.dim, size = 9.sp
        )
    }
}

@Composable
private fun AccessibilityPanel(modifier: Modifier) {
    val c = Hud.colors
    val ctx = LocalContext.current
    val on = remember(AppState.resumeTick) { CallAccessibilityService.isEnabled(ctx) }
    HudPanel(modifier, "RECORDING BOOST") {
        HudText(
            if (on) "● BOOST IS ON" else "○ BOOST IS OFF",
            color = if (on) c.good else c.accent, size = 13.sp, bold = true
        )
        Spacer(Modifier.height(4.dp))
        HudText(
            "If recordings are silent, turn this on. It keeps microphone access during calls on many phones. It does not read your screen.",
            color = c.text, size = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        HudText(
            "STEPS: 1) APP SETTINGS > ⋮ MENU > ALLOW RESTRICTED SETTINGS (IF SHOWN). 2) OPEN ACCESSIBILITY > INSTALLED APPS > DIALER > TURN ON.",
            color = c.dim, size = 9.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HudButton("OPEN ACCESSIBILITY", Modifier.weight(1f).height(44.dp), KeyKind.ACCENT, fontSize = 11.sp) {
                try {
                    ctx.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    Caller.toast(ctx, "NOT AVAILABLE ON THIS PHONE")
                }
            }
            HudButton("APP SETTINGS", Modifier.weight(1f).height(44.dp), KeyKind.FN, fontSize = 11.sp) { Caller.openAppSettings(ctx) }
        }
    }
}

@Composable
private fun UpdatePanel(onUpdateFound: (UpdateInfo) -> Unit, modifier: Modifier) {
    val c = Hud.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    HudPanel(modifier, "UPDATES") {
        HudButton(
            label = "CHECK FOR UPDATES",
            modifier = Modifier.fillMaxWidth().height(46.dp),
            kind = KeyKind.OP,
            fontSize = 13.sp
        ) {
            scope.launch {
                status = "CHECKING..."
                if (!isOnline(ctx)) {
                    status = "NO INTERNET CONNECTION"
                    return@launch
                }
                val (ok, info) = checkUpdate(ctx)
                status = when {
                    !ok -> "CHECK FAILED"
                    info == null -> "UP TO DATE"
                    else -> "NEW VERSION AVAILABLE: v${info.version}"
                }
                if (info != null) onUpdateFound(info)
            }
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HudText(status, color = c.dim, size = 12.sp)
        }
    }
}

@Composable
private fun AuthorPanel(open: (String) -> Unit, modifier: Modifier) {
    HudPanel(modifier, "AUTHOR") {
        LinkRow("NAME", AppInfo.AUTHOR, null, open)
        LinkRow("EMAIL", AppInfo.EMAIL, "mailto:${AppInfo.EMAIL}", open)
        LinkRow("GITHUB", "github.com/mahfuz0712", AppInfo.GITHUB, open)
        LinkRow("PORTFOLIO", "portfolio-mahfuz0712.vercel.app", AppInfo.PORTFOLIO, open)
    }
}

@Composable
private fun MoreAppsPanel(open: (String) -> Unit, modifier: Modifier) {
    val c = Hud.colors
    HudPanel(modifier, "MORE APPS") {
        HudText("Discover more apps by the developer on zApps Store.", color = c.text, size = 13.sp)
        Spacer(Modifier.height(8.dp))
        HudButton(
            label = "OPEN zApps STORE ▸",
            modifier = Modifier.fillMaxWidth().height(48.dp),
            kind = KeyKind.ACCENT,
            fontSize = 14.sp
        ) { open(AppInfo.MORE_APPS) }
    }
}

@Composable
private fun LinkRow(label: String, value: String, link: String?, open: (String) -> Unit) {
    val c = Hud.colors
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (link != null) Modifier.clickable { open(link) } else Modifier)
            .padding(vertical = 6.dp)
    ) {
        HudText(label, color = c.dim, size = 10.sp)
        HudText(value, color = if (link != null) c.primary else c.text, size = 16.sp)
    }
}
