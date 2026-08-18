package com.harsh.jarvis.proactive

import android.content.Context
import java.time.LocalDate

class ProactivePreferences(context: Context) {

    private val prefs = context.getSharedPreferences(
        "jarvis_proactive_preferences",
        Context.MODE_PRIVATE
    )

    var morningEnabled: Boolean
        get() = prefs.getBoolean("morning", true)
        set(value: Boolean) {
            prefs.edit()
                .putBoolean("morning", value)
                .apply()
        }

    var eveningEnabled: Boolean
        get() = prefs.getBoolean("evening", true)
        set(value: Boolean) {
            prefs.edit()
                .putBoolean("evening", value)
                .apply()
        }

    fun shouldRun(key: String): Boolean {
        val today: String = LocalDate.now().toString()
        val lastRun: String? = prefs.getString("last_$key", null)

        return lastRun != today
    }

    fun markRun(key: String) {
        val today: String = LocalDate.now().toString()

        prefs.edit()
            .putString("last_$key", today)
            .apply()
    }
}
