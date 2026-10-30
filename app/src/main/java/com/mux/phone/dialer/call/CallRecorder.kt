package com.mux.phone.dialer.call

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mux.phone.dialer.data.AppSettings
import com.mux.phone.dialer.data.ContactsRepo
import com.mux.phone.dialer.data.RecordingStore
import com.mux.phone.dialer.util.Perms
import java.io.File

/**
 * Records the call audio to an .m4a (AAC) file.
 *
 * NOTE: Android does not let normal apps capture the *remote* voice directly on Android 10+.
 * The recorder therefore tries the best available audio source (see [sources]); on most phones the
 * other person is only captured when the call is on speaker. This is an OS limit, not an app bug.
 */
object CallRecorder {
    var active by mutableStateOf(false)
        private set

    private var rec: MediaRecorder? = null
    private var file: File? = null

    /** Auto-record rule from settings. */
    fun shouldAuto(ctx: Context, number: String): Boolean = when (AppSettings.autoRecord) {
        "ALL" -> true
        "SAVED" -> ContactsRepo.nameFor(ctx, number) != null
        "UNSAVED" -> ContactsRepo.nameFor(ctx, number) == null
        else -> false
    }

    private fun sources(): List<Int> {
        val voiceCall = MediaRecorder.AudioSource.VOICE_CALL
        val comm = MediaRecorder.AudioSource.VOICE_COMMUNICATION
        val mic = MediaRecorder.AudioSource.MIC
        return when (AppSettings.recSource) {
            "MIC" -> listOf(mic)
            "COMM" -> listOf(comm, mic)
            "CALL" -> listOf(voiceCall, mic)
            else -> if (Build.VERSION.SDK_INT < 29) listOf(voiceCall, mic, comm) else listOf(mic, comm)
        }
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(ctx: Context): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()

    @Synchronized
    fun start(ctx: Context, number: String, incoming: Boolean): Boolean {
        if (rec != null) return true
        if (!Perms.has(ctx, Manifest.permission.RECORD_AUDIO)) return false
        val (rate, bits) = when (AppSettings.recQuality) {
            "LOW" -> 16000 to 32000
            "HIGH" -> 44100 to 128000
            else -> 22050 to 64000
        }
        val f = File(RecordingStore.dir(ctx), RecordingStore.fileName(number, incoming))
        for (src in sources()) {
            var r: MediaRecorder? = null
            try {
                r = newRecorder(ctx)
                r.setAudioSource(src)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioChannels(1)
                r.setAudioSamplingRate(rate)
                r.setAudioEncodingBitRate(bits)
                r.setOutputFile(f.absolutePath)
                r.prepare()
                r.start()
                rec = r
                file = f
                active = true
                return true
            } catch (e: Exception) {
                try { r?.reset() } catch (e: Exception) {}
                try { r?.release() } catch (e: Exception) {}
                f.delete()
            }
        }
        return false
    }

    @Synchronized
    fun stop(ctx: Context) {
        val r = rec ?: return
        rec = null
        active = false
        try {
            r.stop()
        } catch (e: RuntimeException) {
            file?.delete()      // stopped too early: no valid data
        }
        try { r.release() } catch (e: Exception) {}
        val f = file
        file = null
        if (f != null && f.exists() && f.length() < 2048) f.delete()
        RecordingStore.cleanup(ctx)
    }
}
