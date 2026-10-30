package com.mux.phone.dialer.ui

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mux.phone.dialer.data.AppSettings
import com.mux.phone.dialer.data.AppState
import com.mux.phone.dialer.data.Contact
import com.mux.phone.dialer.data.ContactsRepo
import com.mux.phone.dialer.data.SrcKind
import com.mux.phone.dialer.util.Caller
import com.mux.phone.dialer.util.Perms
import kotlinx.coroutines.launch

private fun matchesFilter(c: Contact, f: String): Boolean = when {
    f == "PHONE" -> c.sources.any { it.kind == SrcKind.PHONE }
    f == "SIM" -> c.sources.any { it.kind == SrcKind.SIM }
    f == "OTHER" -> c.sources.any { it.kind == SrcKind.OTHER }
    f.startsWith("G:") -> c.sources.any { it.kind == SrcKind.GOOGLE && it.account == f.removePrefix("G:") }
    else -> {
        // ALL: apply the "include SIM / other accounts" settings
        val onlySim = c.sources.all { it.kind == SrcKind.SIM }
        val onlyOther = c.sources.all { it.kind == SrcKind.OTHER }
        !((onlySim && !AppSettings.includeSim) || (onlyOther && !AppSettings.includeOther))
    }
}

private fun sortKey(c: Contact, byLast: Boolean): String {
    val n = c.name.trim()
    return (if (byLast && n.contains(' ')) n.substringAfterLast(' ') + " " + n else n).lowercase()
}

private fun srcTag(c: Contact): String =
    c.sources.map {
        when (it.kind) {
            SrcKind.PHONE -> "PHONE"
            SrcKind.SIM -> "SIM"
            SrcKind.GOOGLE -> "GOOGLE"
            SrcKind.OTHER -> "OTHER"
        }
    }.distinct().joinToString(" · ")

@Composable
fun ContactsScreen(tablet: Boolean) {
    val ctx = LocalContext.current
    val c = Hud.colors
    val scope = rememberCoroutineScope()
    val granted = remember(AppState.permTick, AppState.resumeTick) { Perms.has(ctx, Manifest.permission.READ_CONTACTS) }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf("ALL") }
    var detail by remember { mutableStateOf<Contact?>(null) }

    if (!granted) {
        PermissionPanel("Contacts access is needed to show phone, SIM and Google contacts.", listOf(Manifest.permission.READ_CONTACTS))
        return
    }

    val all = ContactsRepo.all
    val google = ContactsRepo.googleAccounts
    val byLast = AppSettings.sortBy == "LAST"

    val chips = remember(google, AppSettings.includeSim, AppSettings.includeOther, all) {
        val l = ArrayList<Pair<String, String>>()
        l.add("ALL" to "ALL")
        l.add("PHONE" to "PHONE")
        if (AppSettings.includeSim) l.add("SIM" to "SIM")
        for (g in google) l.add("G:$g" to "G · ${g.uppercase()}")
        if (AppSettings.includeOther && all.any { x -> x.sources.any { it.kind == SrcKind.OTHER } }) l.add("OTHER" to "OTHER")
        l
    }
    // a filter can disappear (account removed / setting off) -> fall back to ALL
    val filter = if (chips.any { it.first == picked }) picked else "ALL"

    val rows: List<Any> = remember(all, filter, query, byLast, AppSettings.includeSim, AppSettings.includeOther) {
        val q = query.trim().lowercase()
        val qd = q.filter { it.isDigit() }
        val list = all.filter { matchesFilter(it, filter) }
            .filter { ct ->
                q.isEmpty() || ct.name.lowercase().contains(q) ||
                    (qd.length >= 2 && ct.numbers.any { n -> n.number.filter { it.isDigit() }.contains(qd) })
            }
            .sortedBy { sortKey(it, byLast) }
        val out = ArrayList<Any>(list.size + 28)
        var last = ""
        for (ct in list) {
            val ch = sortKey(ct, byLast).firstOrNull()
            val h = if (ch != null && ch.isLetter()) ch.uppercase() else "#"
            if (h != last) {
                out.add(h)
                last = h
            }
            out.add(ct)
        }
        out
    }
    val count = rows.count { it is Contact }

    detail?.let { ct -> ContactDetail(ct) { detail = null } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            HudSearchField(query, "SEARCH NAME / NUMBER", Modifier.weight(1f).fillMaxHeight()) { query = it }
            Spacer(Modifier.width(4.dp))
            HudButton("+", Modifier.width(48.dp).fillMaxHeight(), KeyKind.ACCENT, fontSize = 22.sp) { Caller.newContact(ctx) }
            HudButton("⟳", Modifier.width(48.dp).fillMaxHeight(), KeyKind.FN, fontSize = 20.sp) {
                scope.launch { ContactsRepo.reload(ctx) }
            }
        }
        Spacer(Modifier.height(6.dp))
        ChipRow(chips, filter) { picked = it }
        Spacer(Modifier.height(4.dp))
        HudText(
            (if (ContactsRepo.loading) "LOADING..." else "$count CONTACTS") + "  ·  " + filterName(filter),
            color = c.dim, size = 10.sp, modifier = Modifier.padding(vertical = 2.dp)
        )
        if (rows.isEmpty() && !ContactsRepo.loading) {
            EmptyNote(
                if (filter == "SIM") "NO SIM CONTACTS FOUND.\nSOME PHONES DO NOT ALLOW APPS TO READ THE SIM."
                else "NO CONTACTS"
            )
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(rows, key = { if (it is Contact) "c${it.id}" else "h$it" }) { r ->
                if (r is String) {
                    HudText(r, color = c.accent, size = 12.sp, bold = true, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp))
                } else if (r is Contact) {
                    HudItem(Modifier.fillMaxWidth(), onClick = { detail = r }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(r.name, Modifier.size(40.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                HudText(r.name, color = c.text, size = 15.sp, bold = true, maxLines = 1)
                                if (AppSettings.showNumbers) {
                                    HudText(
                                        r.numbers.first().number + if (r.numbers.size > 1) "  +${r.numbers.size - 1}" else "",
                                        color = c.dim, size = 11.sp, maxLines = 1
                                    )
                                }
                            }
                            HudText(srcTag(r), color = c.dim, size = 9.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun filterName(f: String) = when {
    f.startsWith("G:") -> "GOOGLE " + f.removePrefix("G:").uppercase()
    else -> f
}

@Composable
private fun ContactDetail(ct: Contact, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = Hud.colors
    Dialog(onDismissRequest = onClose) {
        HudPanel(Modifier.fillMaxWidth().heightIn(max = 560.dp), "CONTACT") {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(ct.name, Modifier.size(56.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        HudText(ct.name, color = c.primary, size = 20.sp, bold = true)
                        HudText(srcTag(ct), color = c.dim, size = 10.sp)
                        val g = ct.sources.filter { it.kind == SrcKind.GOOGLE }.map { it.account }.distinct()
                        if (g.isNotEmpty()) HudText(g.joinToString(", "), color = c.dim, size = 10.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                for (n in ct.numbers) {
                    HudItem(Modifier.fillMaxWidth()) {
                        HudText(n.label, color = c.dim, size = 10.sp)
                        HudText(n.number, color = c.text, size = 18.sp, bold = true)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            HudButton("CALL", Modifier.weight(1f).fillMaxHeight(), KeyKind.GOOD, fontSize = 12.sp) {
                                onClose()
                                Caller.call(ctx, n.number)
                            }
                            HudButton("SMS", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, fontSize = 12.sp) { Caller.sms(ctx, n.number) }
                            HudButton("COPY", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN, fontSize = 12.sp) { Caller.copy(ctx, n.number) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HudButton("EDIT", Modifier.weight(1f).fillMaxHeight(), KeyKind.ACCENT, enabled = ct.id >= 0, fontSize = 13.sp) {
                        Caller.editContact(ctx, ct.id)
                        onClose()
                    }
                    HudButton("CLOSE", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN, fontSize = 13.sp) { onClose() }
                }
            }
        }
    }
}
