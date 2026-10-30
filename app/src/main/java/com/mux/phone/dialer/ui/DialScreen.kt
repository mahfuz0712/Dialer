package com.mux.phone.dialer.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.phone.dialer.data.AppSettings
import com.mux.phone.dialer.data.Contact
import com.mux.phone.dialer.data.ContactsRepo
import com.mux.phone.dialer.util.Caller
import com.mux.phone.dialer.util.Dtmf

/** Number currently typed on the dial pad (also filled by tel: links and by other screens). */
object DialInput {
    var number by mutableStateOf("")
}

private val padRows = listOf(
    listOf("1" to "", "2" to "ABC", "3" to "DEF"),
    listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
    listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
    listOf("*" to "", "0" to "+", "#" to "")
)

@Composable
fun DialScreen(tablet: Boolean) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val num = DialInput.number
    val all = ContactsRepo.all
    val suggestions = remember(num, all, AppSettings.smartDial) {
        if (AppSettings.smartDial) ContactsRepo.suggest(num) else emptyList()
    }

    fun feedback(ch: Char?) {
        if (ch != null && AppSettings.keyTone) Dtmf.play(ch)
        if (AppSettings.haptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    val onKey: (String) -> Unit = { k ->
        feedback(k[0])
        if (DialInput.number.length < 40) DialInput.number += k
    }
    val onPlus: () -> Unit = {
        feedback(null)
        if (DialInput.number.length < 40) DialInput.number = DialInput.number.dropLast(1) + "+"
    }
    val onDelete: () -> Unit = {
        feedback(null)
        DialInput.number = DialInput.number.dropLast(1)
    }
    val onClear: () -> Unit = { DialInput.number = "" }
    val onCall: () -> Unit = {
        if (DialInput.number.isEmpty()) {
            DialInput.number = com.mux.phone.dialer.data.AppSettings.lastDialed
        } else {
            Caller.call(ctx, DialInput.number)
        }
    }
    val onSave: () -> Unit = { if (DialInput.number.isNotEmpty()) Caller.saveContact(ctx, DialInput.number) }

    if (tablet) {
        Row(Modifier.fillMaxSize()) {
            DialDisplay(num, suggestions, Modifier.weight(1f).fillMaxHeight())
            Spacer(Modifier.width(10.dp))
            Keypad(Modifier.weight(1f).fillMaxHeight(), num.isNotEmpty(), onKey, onPlus, onDelete, onClear, onCall, onSave)
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            DialDisplay(num, suggestions, Modifier.fillMaxWidth().weight(0.95f))
            Spacer(Modifier.height(6.dp))
            Keypad(Modifier.fillMaxWidth().weight(2.7f), num.isNotEmpty(), onKey, onPlus, onDelete, onClear, onCall, onSave)
        }
    }
}

@Composable
private fun DialDisplay(num: String, suggestions: List<Contact>, modifier: Modifier) {
    val c = Hud.colors
    HudPanel(modifier, "DIAL") {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterEnd) {
            val size = when {
                num.length <= 10 -> 38.sp
                num.length <= 14 -> 30.sp
                num.length <= 20 -> 22.sp
                else -> 16.sp
            }
            if (num.isEmpty()) {
                HudText("ENTER NUMBER", color = c.dim, size = 16.sp, align = TextAlign.End, modifier = Modifier.fillMaxWidth())
            } else {
                HudText(num, color = c.primary, size = size, bold = true, align = TextAlign.End, modifier = Modifier.fillMaxWidth())
            }
        }
        for (s in suggestions) {
            val match = s.numbers.firstOrNull { n -> n.number.filter { it.isDigit() }.contains(num) } ?: s.numbers.first()
            HudItem(Modifier.fillMaxWidth(), onClick = { DialInput.number = match.number.filter { it.isDigit() || it == '+' } }) {
                HudText(s.name, color = c.text, size = 13.sp, bold = true, maxLines = 1)
                HudText(match.number + "  ·  " + match.label, color = c.dim, size = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Keypad(
    modifier: Modifier,
    hasNumber: Boolean,
    onKey: (String) -> Unit,
    onPlus: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onCall: () -> Unit,
    onSave: () -> Unit
) {
    Column(modifier) {
        for (row in padRows) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for ((digit, letters) in row) {
                    HudButton(
                        label = digit,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        kind = KeyKind.NUM,
                        fontSize = 26.sp,
                        sub = letters.ifEmpty { null },
                        onLongClick = if (digit == "0") onPlus else null
                    ) { onKey(digit) }
                }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            HudButton(
                "SAVE", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN,
                enabled = hasNumber, fontSize = 14.sp, onClick = onSave
            )
            HudButton(
                "CALL", Modifier.weight(1.4f).fillMaxHeight(), KeyKind.GOOD,
                fontSize = 20.sp, onClick = onCall
            )
            HudButton(
                "⌫", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP,
                enabled = hasNumber, fontSize = 22.sp, onLongClick = onClear, onClick = onDelete
            )
        }
    }
}
