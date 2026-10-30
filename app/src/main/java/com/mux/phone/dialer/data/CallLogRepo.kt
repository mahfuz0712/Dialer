package com.mux.phone.dialer.data

import android.Manifest
import android.content.Context
import android.provider.CallLog
import com.mux.phone.dialer.util.Perms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LogEntry(
    val id: Long,
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val duration: Long
)

object CallLogRepo {
    suspend fun load(ctx: Context, limit: Int = 300): List<LogEntry> = withContext(Dispatchers.IO) {
        val out = ArrayList<LogEntry>()
        if (!Perms.has(ctx, Manifest.permission.READ_CALL_LOG)) return@withContext out
        try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf("_id", "number", "type", "date", "duration", "name"),
                null, null, "date DESC"
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val num = c.getString(1) ?: ""
                    val cached = c.getString(5)
                    val name = ContactsRepo.nameFor(ctx, num) ?: cached?.takeIf { it.isNotBlank() }
                    out.add(LogEntry(c.getLong(0), num, name, c.getInt(2), c.getLong(3), c.getLong(4)))
                }
            }
        } catch (e: Exception) {
        }
        out
    }

    fun delete(ctx: Context, id: Long): Boolean = try {
        ctx.contentResolver.delete(CallLog.Calls.CONTENT_URI, "_id=?", arrayOf(id.toString())) > 0
    } catch (e: Exception) {
        false
    }
}
