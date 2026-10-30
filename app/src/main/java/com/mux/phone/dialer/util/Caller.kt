package com.mux.phone.dialer.util

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.provider.ContactsContract
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mux.phone.dialer.call.TelecomHelper
import com.mux.phone.dialer.data.AppSettings

class SimOption(val label: String, val handle: Parcelable?)
class PendingCall(val number: String, val options: List<SimOption>)

/** Everything "do something with a phone number". */
object Caller {
    /** Non-null while the "which SIM?" dialog should be shown. */
    var pending by mutableStateOf<PendingCall?>(null)

    fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    fun isDefaultDialer(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= 23 && TelecomHelper.isDefault(ctx)

    fun simOptions(ctx: Context): List<SimOption> {
        if (Build.VERSION.SDK_INT < 23 || !Perms.has(ctx, Manifest.permission.READ_PHONE_STATE)) return emptyList()
        return TelecomHelper.simOptions(ctx)
    }

    /** Call [raw]; asks which SIM to use on dual-SIM phones depending on the setting. */
    fun call(ctx: Context, raw: String) {
        val number = raw.trim()
        if (number.isEmpty()) return
        if (!Perms.has(ctx, Manifest.permission.CALL_PHONE)) {
            toast(ctx, "CALL PERMISSION REQUIRED (SYS > PERMISSIONS)")
            return
        }
        val sims = simOptions(ctx)
        if (sims.size > 1) {
            when (AppSettings.simChoice) {
                "ASK" -> {
                    pending = PendingCall(number, sims)
                    return
                }
                "SIM1" -> {
                    place(ctx, number, sims[0].handle)
                    return
                }
                "SIM2" -> {
                    place(ctx, number, sims[1].handle)
                    return
                }
            }
        }
        place(ctx, number, null)
    }

    fun place(ctx: Context, number: String, handle: Parcelable?) {
        val i = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (handle != null) i.putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", handle)
        AppSettings.lastDialed = number
        try {
            ctx.startActivity(i)
        } catch (e: Exception) {
            toast(ctx, "CALL FAILED")
        }
    }

    fun sms(ctx: Context, number: String) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            toast(ctx, "NO MESSAGING APP")
        }
    }

    fun copy(ctx: Context, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("number", text))
        toast(ctx, "COPIED")
    }

    /** Opens the system "add / edit contact" screen with the number pre-filled. */
    fun saveContact(ctx: Context, number: String) {
        try {
            val i = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                putExtra(ContactsContract.Intents.Insert.PHONE, number)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(i)
        } catch (e: Exception) {
            toast(ctx, "NO CONTACTS APP")
        }
    }

    fun newContact(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            toast(ctx, "NO CONTACTS APP")
        }
    }

    fun editContact(ctx: Context, id: Long) {
        try {
            val uri = android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)
            ctx.startActivity(Intent(Intent.ACTION_EDIT).setDataAndType(uri, ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast(ctx, "CANNOT EDIT")
        }
    }

    fun openAppSettings(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
        }
    }
}
