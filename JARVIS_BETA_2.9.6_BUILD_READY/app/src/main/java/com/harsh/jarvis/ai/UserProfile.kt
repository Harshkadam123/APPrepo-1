package com.harsh.jarvis.ai

import android.content.Context

/** Local, user-controlled profile/preferences. Never sent anywhere. */
class UserProfile(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_profile", Context.MODE_PRIVATE)
    var name: String? get() = prefs.getString("name", null) set(v) = prefs.edit().putString("name", v).apply()
    var assistantStyle: String get() = prefs.getString("style", "balanced") ?: "balanced" set(v) = prefs.edit().putString("style", v).apply()
    fun summary(): String = buildString {
        append("Profile: ")
        append(name?.let { "name=$it" } ?: "name not set")
        append(", style=$assistantStyle")
    }
}
