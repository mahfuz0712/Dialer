package com.mux.phone.dialer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.phone.dialer.data.AppState
import com.mux.phone.dialer.util.Caller
import com.mux.phone.dialer.util.Perms

/** Shown by a screen when it cannot work without a runtime permission. */
@Composable
fun PermissionPanel(text: String, perms: List<String>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        AppState.permTick++
    }
    HudPanel(modifier.fillMaxWidth(), "PERMISSION REQUIRED") {
        HudText(text, color = Hud.colors.text, size = 13.sp)
        Spacer(Modifier.height(8.dp))
        HudButton("GRANT ACCESS", Modifier.fillMaxWidth().height(46.dp), KeyKind.ACCENT, fontSize = 13.sp) {
            val m = perms.filter { !Perms.has(ctx, it) }
            if (m.isEmpty()) AppState.permTick++ else launcher.launch(m.toTypedArray())
        }
        HudButton("APP SETTINGS", Modifier.fillMaxWidth().height(40.dp), KeyKind.FN, fontSize = 12.sp) {
            Caller.openAppSettings(ctx)
        }
    }
}

@Composable
fun HudSearchField(value: String, hint: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val c = Hud.colors
    val shape = HudShape(8.dp)
    Box(
        modifier
            .background(c.primary.copy(alpha = 0.06f), shape)
            .border(1.dp, c.primary.copy(alpha = 0.5f), shape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) HudText(hint, color = c.dim, size = 13.sp, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 15.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(c.primary),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Two-letter avatar block in HUD style. */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier) {
    val c = Hud.colors
    val shape = HudShape(6.dp)
    val initials = name.trim().split(' ').filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "#" }
    Box(
        modifier
            .background(c.primary.copy(alpha = 0.12f), shape)
            .border(1.dp, c.primary.copy(alpha = 0.55f), shape),
        contentAlignment = Alignment.Center
    ) {
        HudText(initials, color = c.primary, size = 14.sp, bold = true, maxLines = 1)
    }
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HudText(text, color = Hud.colors.dim, size = 12.sp)
    }
}
