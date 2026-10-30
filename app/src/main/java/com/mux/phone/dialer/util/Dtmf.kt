package com.mux.phone.dialer.util

import android.media.AudioManager
import android.media.ToneGenerator

/** Local key-press beep for the dial pad. */
object Dtmf {
    private var tg: ToneGenerator? = null

    fun play(c: Char) {
        val t = when (c) {
            in '0'..'9' -> c - '0'
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        try {
            if (tg == null) tg = ToneGenerator(AudioManager.STREAM_DTMF, 70)
            tg?.startTone(t, 120)
        } catch (e: Exception) {
            tg = null
        }
    }
}
