package com.harsh.jarvis.proactive

import android.content.Context
import java.time.LocalDate

class ProactivePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_proactive_preferences", Context.MODE_PRIVATE)
    var morningEnabled: Boolean get() = prefs.getBoolean("morning", true) set(v) { prefs.edit().putBoolean("morning", v).apply() }
    var eveningEnabled: Boolean get() = prefs.getBoolean("evening", true) set(v) { prefs.edit().putBoolean("evening", v).apply() }
    fun shouldRun(key: String): Boolean {
        val today = LocalDate.now().toString()
        return prefs.getString("last_$key", null) != today
    }
    fun markRun(key: String) { prefs.edit().putString("last_$key", LocalDate.now().toString()).apply() }
}
