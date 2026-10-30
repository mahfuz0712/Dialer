package com.mux.phone.dialer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File

data class Rec(
    val file: File,
    val time: Long,
    val incoming: Boolean,
    val number: String,
    val size: Long
)

/**
 * Recordings live in the app's own folder (no storage permission needed on any Android version):
 *   Android/data/com.mux.phone.dialer/files/Recordings/REC_<millis>_<IN|OUT>_<number>.m4a
 */
object RecordingStore {
    fun dir(ctx: Context): File {
        val d = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "Recordings")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun fileName(number: String, incoming: Boolean): String {
        val clean = number.filter { it.isDigit() || it == '+' }.ifEmpty { "unknown" }
        return "REC_${System.currentTimeMillis()}_${if (incoming) "IN" else "OUT"}_$clean.m4a"
    }

    fun list(ctx: Context): List<Rec> {
        val files = dir(ctx).listFiles() ?: return emptyList()
        val out = ArrayList<Rec>()
        for (f in files) {
            if (!f.isFile || !f.name.endsWith(".m4a")) continue
            val p = f.name.removeSuffix(".m4a").split("_", limit = 4)
            val time = p.getOrNull(1)?.toLongOrNull() ?: f.lastModified()
            val incoming = p.getOrNull(2) == "IN"
            val number = p.getOrNull(3)?.takeIf { it != "unknown" } ?: ""
            out.add(Rec(f, time, incoming, number, f.length()))
        }
        return out.sortedByDescending { it.time }
    }

    fun durationMs(file: File): Long = try {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            r.release()
        }
    } catch (e: Exception) {
        0L
    }

    /** Deletes recordings older than the "auto delete" setting. */
    fun cleanup(ctx: Context) {
        val days = AppSettings.recKeepDays
        if (days <= 0) return
        val limit = System.currentTimeMillis() - days * 86_400_000L
        for (r in list(ctx)) if (r.time < limit) r.file.delete()
    }
}
