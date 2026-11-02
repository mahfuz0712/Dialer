package com.mux.phone.dialer.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.mux.phone.dialer.data.AppState
import com.mux.phone.dialer.util.Caller

private fun canOverlay(ctx: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(ctx) else true

/** Android 14+ can block full-screen call screens until this is allowed. Older versions: always fine. */
private fun canFullScreen(ctx: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 34) {
        try {
            ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        } catch (e: Exception) {
            true
        }
    } else true

private fun launch(ctx: Context, intent: Intent) {
    try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Caller.openAppSettings(ctx)
    }
}

/** SYS tab: everything Android needs to allow so Dialer's own screen shows for incoming calls. */
@Composable
fun IncomingScreenPanel(modifier: Modifier) {
    val c = Hud.colors
    val ctx = LocalContext.current
    val tick = AppState.resumeTick + AppState.permTick
    val isDefault = remember(tick) { Caller.isDefaultDialer(ctx) }
    val overlay = remember(tick) { canOverlay(ctx) }
    val fullScreen = remember(tick) { canFullScreen(ctx) }
    val notif = remember(tick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }

    HudPanel(modifier, "INCOMING CALL SCREEN") {
        HudText(
            "To show Dialer's own screen for incoming calls (not the phone maker's), all of these must be OK:",
            color = c.text, size = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        StatusLine("DEFAULT PHONE APP", isDefault)
        StatusLine("DISPLAY OVER OTHER APPS", overlay)
        StatusLine("FULL-SCREEN CALL ALERT", fullScreen)
        StatusLine("NOTIFICATIONS", notif)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HudButton("OVER APPS", Modifier.weight(1f).height(44.dp), KeyKind.ACCENT, enabled = !overlay, fontSize = 11.sp) {
                launch(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            }
            HudButton("FULL SCREEN", Modifier.weight(1f).height(44.dp), KeyKind.ACCENT, enabled = !fullScreen, fontSize = 11.sp) {
                if (Build.VERSION.SDK_INT >= 34) {
                    launch(ctx, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HudButton("NOTIFICATIONS", Modifier.weight(1f).height(44.dp), KeyKind.FN, enabled = !notif, fontSize = 11.sp) {
                launch(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
            HudButton("APP SETTINGS", Modifier.weight(1f).height(44.dp), KeyKind.FN, fontSize = 11.sp) { Caller.openAppSettings(ctx) }
        }
        Spacer(Modifier.height(6.dp))
        HudText(
            "HONOR / HUAWEI / XIAOMI: ALSO OPEN APP SETTINGS > BATTERY > APP LAUNCH, SET TO MANAGE MANUALLY, AND TURN ON ALL THREE SWITCHES. IF IT STILL SHOWS THE MAKER'S SCREEN, SET THE DEFAULT PHONE APP AGAIN HERE.",
            color = c.dim, size = 9.sp
        )
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    val c = Hud.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        HudText(label, color = c.text, size = 12.sp, modifier = Modifier.weight(1f))
        HudText(if (ok) "OK" else "TURN ON", color = if (ok) c.good else c.danger, size = 12.sp, bold = true)
    }
}
