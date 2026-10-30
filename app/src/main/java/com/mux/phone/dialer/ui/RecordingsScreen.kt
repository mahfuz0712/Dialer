package com.mux.phone.dialer.ui

import android.content.Intent
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mux.phone.dialer.call.CallRecorder
import com.mux.phone.dialer.data.AppSettings
import com.mux.phone.dialer.data.AppState
import com.mux.phone.dialer.data.ContactsRepo
import com.mux.phone.dialer.data.Rec
import com.mux.phone.dialer.data.RecordingStore
import com.mux.phone.dialer.util.Caller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val recDate = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault())

private class RecRow(val rec: Rec, val name: String?, val durationMs: Long)

private fun fmtSize(b: Long): String = when {
    b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
    else -> "%d KB".format(b / 1024)
}

@Composable
fun RecordingsScreen(tablet: Boolean) {
    val ctx = LocalContext.current
    val c = Hud.colors
    var rows by remember { mutableStateOf<List<RecRow>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf("") }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    // refresh on resume / permission change / manual reload / when a recording stops
    LaunchedEffect(AppState.resumeTick, AppState.permTick, reload, CallRecorder.active) {
        rows = withContext(Dispatchers.IO) {
            RecordingStore.list(ctx).map {
                RecRow(it, if (it.number.isEmpty()) null else ContactsRepo.nameFor(ctx, it.number), RecordingStore.durationMs(it.file))
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            try { player?.release() } catch (e: Exception) {}
        }
    }

    fun stopPlayback() {
        try { player?.release() } catch (e: Exception) {}
        player = null
        playing = ""
    }

    fun play(r: Rec) {
        stopPlayback()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(r.file.absolutePath)
            mp.setOnCompletionListener {
                try { it.release() } catch (e: Exception) {}
                player = null
                playing = ""
            }
            mp.prepare()
            mp.start()
            player = mp
            playing = r.file.name
        } catch (e: Exception) {
            Caller.toast(ctx, "CANNOT PLAY THIS FILE")
            stopPlayback()
        }
    }

    fun share(r: Rec) {
        try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", r.file)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(i, "Share recording").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Caller.toast(ctx, "SHARE FAILED")
        }
    }

    Column(Modifier.fillMaxSize()) {
        HudPanel(Modifier.fillMaxWidth(), "CALL RECORDER") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HudText(
                    if (CallRecorder.active) "● RECORDING NOW" else "AUTO RECORD: ${AppSettings.autoRecord}",
                    color = if (CallRecorder.active) c.danger else c.primary, size = 13.sp, bold = true,
                    modifier = Modifier.weight(1f)
                )
                HudText("${rows.size} FILES", color = c.dim, size = 11.sp)
            }
            HudText("CHANGE RULES IN SYS > RECORDING SETTINGS", color = c.dim, size = 10.sp)
        }
        Spacer(Modifier.height(6.dp))
        if (rows.isEmpty()) {
            EmptyNote("NO RECORDINGS YET.\nTURN ON AUTO RECORD IN SYS, OR TAP REC DURING A CALL.")
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(rows, key = { it.rec.file.name }) { row ->
                val r = row.rec
                val open = expanded == r.file.name
                HudItem(Modifier.fillMaxWidth(), selected = open, onClick = { expanded = if (open) "" else r.file.name }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HudText(if (r.incoming) "↙" else "↗", color = if (r.incoming) c.primary else c.accent, size = 20.sp, bold = true, modifier = Modifier.width(28.dp))
                        Column(Modifier.weight(1f)) {
                            HudText(row.name ?: r.number.ifEmpty { "UNKNOWN" }, color = c.text, size = 15.sp, bold = true, maxLines = 1)
                            HudText(recDate.format(Date(r.time)), color = c.dim, size = 11.sp, maxLines = 1)
                        }
                        Column {
                            HudText(fmtDuration(row.durationMs / 1000), color = c.dim, size = 11.sp, align = TextAlign.End, modifier = Modifier.fillMaxWidth())
                            HudText(fmtSize(r.size), color = c.dim, size = 10.sp, align = TextAlign.End, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (open) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().height(42.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val isPlaying = playing == r.file.name
                            HudButton(if (isPlaying) "STOP" else "PLAY", Modifier.weight(1f).fillMaxHeight(), KeyKind.GOOD, selected = isPlaying, fontSize = 12.sp) {
                                if (isPlaying) stopPlayback() else play(r)
                            }
                            HudButton("SHARE", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, fontSize = 12.sp) { share(r) }
                            HudButton("CALL", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN, enabled = r.number.isNotEmpty(), fontSize = 12.sp) { Caller.call(ctx, r.number) }
                            val confirm = confirmDelete == r.file.name
                            HudButton(if (confirm) "SURE?" else "DEL", Modifier.weight(1f).fillMaxHeight(), KeyKind.DANGER, selected = confirm, fontSize = 12.sp) {
                                if (!confirm) {
                                    confirmDelete = r.file.name
                                } else {
                                    if (playing == r.file.name) stopPlayback()
                                    r.file.delete()
                                    confirmDelete = ""
                                    reload++
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
