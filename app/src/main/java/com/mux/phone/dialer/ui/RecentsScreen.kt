package com.mux.phone.dialer.ui

import android.Manifest
import android.provider.CallLog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.phone.dialer.data.AppState
import com.mux.phone.dialer.data.CallLogRepo
import com.mux.phone.dialer.data.LogEntry
import com.mux.phone.dialer.util.Caller
import com.mux.phone.dialer.util.Perms
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())

private fun isMissed(t: Int) = t == CallLog.Calls.MISSED_TYPE || t == CallLog.Calls.REJECTED_TYPE || t == CallLog.Calls.BLOCKED_TYPE
private fun isIn(t: Int) = t == CallLog.Calls.INCOMING_TYPE || t == CallLog.Calls.ANSWERED_EXTERNALLY_TYPE
private fun isOut(t: Int) = t == CallLog.Calls.OUTGOING_TYPE

private fun typeLabel(t: Int) = when (t) {
    CallLog.Calls.INCOMING_TYPE -> "INCOMING"
    CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
    CallLog.Calls.MISSED_TYPE -> "MISSED"
    CallLog.Calls.REJECTED_TYPE -> "REJECTED"
    CallLog.Calls.BLOCKED_TYPE -> "BLOCKED"
    CallLog.Calls.VOICEMAIL_TYPE -> "VOICEMAIL"
    CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> "ANSWERED ELSEWHERE"
    else -> "CALL"
}

private fun typeIcon(t: Int) = when {
    isOut(t) -> "↗"
    isMissed(t) -> "✕"
    else -> "↙"
}

fun fmtDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
fun RecentsScreen(tablet: Boolean, onOpenDial: () -> Unit) {
    val ctx = LocalContext.current
    val c = Hud.colors
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var filter by remember { mutableStateOf("ALL") }
    var expanded by remember { mutableLongStateOf(-1L) }
    val granted = remember(AppState.permTick, AppState.resumeTick) { Perms.has(ctx, Manifest.permission.READ_CALL_LOG) }

    LaunchedEffect(AppState.permTick, AppState.resumeTick, granted) {
        if (granted) entries = CallLogRepo.load(ctx)
    }

    Column(Modifier.fillMaxSize()) {
        if (!granted) {
            PermissionPanel("Call log access is needed to show your recent calls.", listOf(Manifest.permission.READ_CALL_LOG))
            return@Column
        }
        ChipRow(
            listOf("ALL" to "ALL", "MISSED" to "MISSED", "IN" to "INCOMING", "OUT" to "OUTGOING"),
            filter
        ) { filter = it }
        Spacer(Modifier.height(6.dp))
        val shown = when (filter) {
            "MISSED" -> entries.filter { isMissed(it.type) }
            "IN" -> entries.filter { isIn(it.type) }
            "OUT" -> entries.filter { isOut(it.type) }
            else -> entries
        }
        if (shown.isEmpty()) EmptyNote("NO CALLS")
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(shown, key = { it.id }) { e ->
                val open = expanded == e.id
                val tint = if (isMissed(e.type)) c.danger else if (isOut(e.type)) c.accent else c.primary
                HudItem(Modifier.fillMaxWidth(), selected = open, onClick = { expanded = if (open) -1L else e.id }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        HudText(typeIcon(e.type), color = tint, size = 20.sp, bold = true, modifier = Modifier.width(28.dp))
                        Column(Modifier.weight(1f)) {
                            HudText(e.name ?: e.number.ifEmpty { "UNKNOWN" }, color = if (isMissed(e.type)) c.danger else c.text, size = 15.sp, bold = true, maxLines = 1)
                            HudText(
                                (if (e.name != null) e.number + "  ·  " else "") + typeLabel(e.type),
                                color = c.dim, size = 11.sp, maxLines = 1
                            )
                        }
                        Column {
                            HudText(dateFmt.format(Date(e.date)), color = c.dim, size = 11.sp, align = TextAlign.End)
                            if (e.duration > 0) HudText(fmtDuration(e.duration), color = c.dim, size = 11.sp, align = TextAlign.End, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (open) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().height(42.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            HudButton("CALL", Modifier.weight(1f).fillMaxHeight(), KeyKind.GOOD, enabled = e.number.isNotEmpty(), fontSize = 12.sp) { Caller.call(ctx, e.number) }
                            HudButton("SMS", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, enabled = e.number.isNotEmpty(), fontSize = 12.sp) { Caller.sms(ctx, e.number) }
                            HudButton("PAD", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN, enabled = e.number.isNotEmpty(), fontSize = 12.sp) {
                                DialInput.number = e.number
                                onOpenDial()
                            }
                            if (e.name == null) {
                                HudButton("SAVE", Modifier.weight(1f).fillMaxHeight(), KeyKind.ACCENT, enabled = e.number.isNotEmpty(), fontSize = 12.sp) { Caller.saveContact(ctx, e.number) }
                            }
                            HudButton("DEL", Modifier.weight(1f).fillMaxHeight(), KeyKind.DANGER, fontSize = 12.sp) {
                                scope.launch {
                                    if (CallLogRepo.delete(ctx, e.id)) entries = entries.filter { it.id != e.id }
                                    else Caller.toast(ctx, "ONLY THE DEFAULT PHONE APP CAN DELETE")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
