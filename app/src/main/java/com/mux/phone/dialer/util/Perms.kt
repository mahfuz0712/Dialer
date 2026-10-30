package com.mux.phone.dialer.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Perms {
    fun all(): List<String> {
        val l = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        return l
    }

    fun has(ctx: Context, p: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun missing(ctx: Context): List<String> = all().filter { !has(ctx, it) }

    fun label(p: String): String = when (p) {
        Manifest.permission.READ_CONTACTS -> "CONTACTS"
        Manifest.permission.READ_CALL_LOG -> "CALL LOG (READ)"
        Manifest.permission.WRITE_CALL_LOG -> "CALL LOG (EDIT)"
        Manifest.permission.CALL_PHONE -> "PLACE CALLS"
        Manifest.permission.READ_PHONE_STATE -> "PHONE STATE / SIM"
        Manifest.permission.RECORD_AUDIO -> "MICROPHONE (RECORDING)"
        Manifest.permission.POST_NOTIFICATIONS -> "NOTIFICATIONS"
        else -> p.substringAfterLast('.')
    }
}
