package com.mux.phone.dialer.data

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone as PhoneKind
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mux.phone.dialer.util.Perms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

enum class SrcKind { PHONE, SIM, GOOGLE, OTHER }

/** Where a contact is stored. [account] is the e-mail for Google, empty otherwise. */
data class Src(val kind: SrcKind, val account: String)

data class PhoneNum(val number: String, val label: String)

data class Contact(
    val id: Long,                    // < 0  => read from SIM card (no system contact id)
    val name: String,
    val numbers: List<PhoneNum>,
    val sources: Set<Src>
)

object ContactsRepo {
    var all by mutableStateOf<List<Contact>>(emptyList())
        private set
    var googleAccounts by mutableStateOf<List<String>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set

    private val nameCache = Collections.synchronizedMap(HashMap<String, String?>())

    /** Last 10 digits - good enough to match +8801XXXXXXXXX with 01XXXXXXXXX. */
    fun norm(number: String): String = number.filter { it.isDigit() }.takeLast(10)

    suspend fun reload(ctx: Context) {
        if (!Perms.has(ctx, Manifest.permission.READ_CONTACTS)) {
            all = emptyList()
            googleAccounts = emptyList()
            return
        }
        loading = true
        val r = withContext(Dispatchers.IO) {
            try {
                load(ctx.applicationContext)
            } catch (e: Exception) {
                null
            }
        }
        if (r != null) {
            all = r.first
            googleAccounts = r.second
            nameCache.clear()
        }
        loading = false
    }

    private fun classify(type: String?, name: String?): Src {
        val t = (type ?: "").lowercase()
        val n = name ?: ""
        return when {
            t == "com.google" -> Src(SrcKind.GOOGLE, n)
            t.contains("sim") -> Src(SrcKind.SIM, "SIM")
            t.isEmpty() || t.contains("local") || t.endsWith(".phone") || t.contains("device") || t == "com.xiaomi" ->
                Src(SrcKind.PHONE, "")
            else -> Src(SrcKind.OTHER, t)
        }
    }

    private class Acc(val id: Long, val name: String) {
        val nums = LinkedHashMap<String, PhoneNum>()
        val src = LinkedHashSet<Src>()
    }

    private fun load(ctx: Context): Pair<List<Contact>, List<String>> {
        val cr = ctx.contentResolver
        val raw = HashMap<Long, Src>()
        val google = LinkedHashSet<String>()

        // 1) which account does every raw contact belong to
        cr.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf("_id", "account_type", "account_name"),
            "deleted=0", null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val s = classify(c.getString(1), c.getString(2))
                raw[c.getLong(0)] = s
                if (s.kind == SrcKind.GOOGLE && s.account.isNotBlank()) google.add(s.account)
            }
        }

        // 2) accounts that are connected but may have no contacts yet
        try {
            cr.query(
                ContactsContract.Settings.CONTENT_URI,
                arrayOf("account_name", "account_type"), null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val s = classify(c.getString(1), c.getString(0))
                    if (s.kind == SrcKind.GOOGLE && s.account.isNotBlank()) google.add(s.account)
                }
            }
        } catch (e: Exception) {
        }

        // 3) all phone numbers
        val map = LinkedHashMap<Long, Acc>()
        cr.query(
            PhoneKind.CONTENT_URI,
            arrayOf("contact_id", "raw_contact_id", "display_name", "data1", "data2", "data3"),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val cid = c.getLong(0)
                val rid = c.getLong(1)
                val name = c.getString(2) ?: ""
                val num = c.getString(3)?.trim() ?: continue
                if (num.isEmpty()) continue
                val label = try {
                    PhoneKind.getTypeLabel(ctx.resources, c.getInt(4), c.getString(5)).toString().uppercase()
                } catch (e: Exception) {
                    "PHONE"
                }
                val a = map.getOrPut(cid) { Acc(cid, name) }
                a.src.add(raw[rid] ?: Src(SrcKind.PHONE, ""))
                val k = norm(num).ifEmpty { num }
                if (!a.nums.containsKey(k)) a.nums[k] = PhoneNum(num, label)
            }
        }

        val list = ArrayList<Contact>(map.size + 16)
        for (a in map.values) {
            list.add(Contact(a.id, a.name.ifBlank { a.nums.values.first().number }, a.nums.values.toList(), a.src))
        }

        // 4) SIM card (content://icc/adn) - skip entries already present as SIM raw contacts
        val simKnown = HashSet<String>()
        for (c in list) if (c.sources.any { it.kind == SrcKind.SIM }) c.numbers.forEach { simKnown.add(norm(it.number)) }
        for (s in simContacts(ctx)) {
            if (simKnown.add(norm(s.numbers[0].number))) list.add(s)
        }
        return list to google.toList()
    }

    private fun simContacts(ctx: Context): List<Contact> {
        val out = ArrayList<Contact>()
        val seen = HashSet<String>()
        var id = -1L
        val uris = listOf("content://icc/adn", "content://icc/adn/subId/1", "content://icc/adn/subId/2")
        for (u in uris) {
            try {
                ctx.contentResolver.query(Uri.parse(u), arrayOf("name", "number"), null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val name = c.getString(0)?.trim().orEmpty()
                        val num = c.getString(1)?.trim().orEmpty()
                        if (num.isEmpty()) continue
                        if (!seen.add(name.lowercase() + "|" + norm(num))) continue
                        out.add(
                            Contact(
                                id--, name.ifEmpty { num },
                                listOf(PhoneNum(num, "SIM")),
                                setOf(Src(SrcKind.SIM, "SIM"))
                            )
                        )
                    }
                }
            } catch (e: Exception) {
            }
        }
        return out
    }

    /** Name of the saved contact that owns [number], or null. Cached. */
    fun nameFor(ctx: Context, number: String): String? {
        val key = norm(number)
        if (key.length < 3) return null
        synchronized(nameCache) {
            if (nameCache.containsKey(key)) return nameCache[key]
        }
        var found: String? = null
        if (Perms.has(ctx, Manifest.permission.READ_CONTACTS)) {
            try {
                val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
                ctx.contentResolver.query(uri, arrayOf("display_name"), null, null, null)?.use {
                    if (it.moveToFirst()) found = it.getString(0)
                }
            } catch (e: Exception) {
            }
            if (found == null) {
                found = all.firstOrNull { c -> c.numbers.any { norm(it.number) == key } }?.name
            }
        }
        synchronized(nameCache) { nameCache[key] = found }
        return found
    }

    // ---- smart dial ------------------------------------------------------------------------

    private fun t9(name: String): String {
        val sb = StringBuilder(name.length)
        for (ch in name.lowercase()) {
            sb.append(
                when (ch) {
                    in 'a'..'c' -> '2'
                    in 'd'..'f' -> '3'
                    in 'g'..'i' -> '4'
                    in 'j'..'l' -> '5'
                    in 'm'..'o' -> '6'
                    in 'p'..'s' -> '7'
                    in 't'..'v' -> '8'
                    in 'w'..'z' -> '9'
                    in '0'..'9' -> ch
                    else -> ' '
                }
            )
        }
        return sb.toString()
    }

    /** Contacts that match typed digits either by number or by T9 name. */
    fun suggest(input: String, max: Int = 3): List<Contact> {
        if (input.length < 2 || !input.all { it.isDigit() }) return emptyList()
        val byName = ArrayList<Contact>()
        val byNum = ArrayList<Contact>()
        for (c in all) {
            if (t9(c.name).replace(" ", "").contains(input)) byName.add(c)
            else if (c.numbers.any { n -> n.number.filter { it.isDigit() }.contains(input) }) byNum.add(c)
            if (byName.size >= max) break
        }
        return (byName + byNum).take(max)
    }
}
