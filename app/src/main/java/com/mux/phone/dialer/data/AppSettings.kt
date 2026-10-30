package com.mux.phone.dialer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Tiny global UI ticks used to refresh screens after resume / permission changes. */
object AppState {
    var resumeTick by mutableIntStateOf(0)
    var permTick by mutableIntStateOf(0)
    var tab by mutableIntStateOf(0)
}

/**
 * All user settings. Every property is Compose state AND persisted in SharedPreferences,
 * so screens recompose automatically and services/receivers can read the same values.
 */
object AppSettings {
    private var sp: SharedPreferences? = null
    private val registry = ArrayList<Pref<*>>()

    fun init(ctx: Context) {
        if (sp != null) return
        sp = ctx.applicationContext.getSharedPreferences("dialer", Context.MODE_PRIVATE)
        registry.forEach { it.load() }
    }

    class Pref<T : Any>(private val key: String, private val def: T) : ReadWriteProperty<Any?, T> {
        private val state = mutableStateOf(def)

        init {
            registry.add(this)
        }

        @Suppress("UNCHECKED_CAST")
        fun load() {
            val s = sp ?: return
            val v: Any = when (def) {
                is Boolean -> s.getBoolean(key, def)
                is Int -> s.getInt(key, def)
                else -> s.getString(key, def as String) ?: def
            }
            state.value = v as T
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            val e = sp?.edit() ?: return
            when (value) {
                is Boolean -> e.putBoolean(key, value)
                is Int -> e.putInt(key, value)
                else -> e.putString(key, value as String)
            }
            e.apply()
        }
    }

    // ---- general
    var amber by Pref("amber", false)
    var askedPerms by Pref("askedPerms", false)

    // ---- dialer
    var keyTone by Pref("keyTone", true)
    var haptic by Pref("haptic", true)
    var smartDial by Pref("smartDial", true)
    var simChoice by Pref("simChoice", "ASK")          // ASK | DEFAULT | SIM1 | SIM2
    var lastDialed by Pref("lastDialed", "")

    // ---- contacts
    var sortBy by Pref("sortBy", "FIRST")              // FIRST | LAST
    var showNumbers by Pref("showNumbers", true)
    var includeSim by Pref("includeSim", true)
    var includeOther by Pref("includeOther", true)

    // ---- recording
    var autoRecord by Pref("autoRecord", "OFF")        // OFF | ALL | SAVED | UNSAVED
    var recSource by Pref("recSource", "AUTO")         // AUTO | MIC | COMM | CALL
    var recQuality by Pref("recQuality", "NORMAL")     // LOW | NORMAL | HIGH
    var recSpeaker by Pref("recSpeaker", false)
    var recKeepDays by Pref("recKeepDays", 0)          // 0 = never delete
}
