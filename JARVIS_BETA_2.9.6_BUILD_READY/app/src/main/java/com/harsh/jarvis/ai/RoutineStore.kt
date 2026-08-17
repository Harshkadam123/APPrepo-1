package com.harsh.jarvis.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local routines: a named list of already-supported safe commands. */
class RoutineStore(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_routines", Context.MODE_PRIVATE)
    fun save(name: String, commands: List<String>) {
        val all = JSONObject(prefs.getString("all", "{}") ?: "{}")
        all.put(name.trim().lowercase(), JSONArray(commands.filter { it.isNotBlank() }))
        prefs.edit().putString("all", all.toString()).apply()
    }
    fun get(name: String): List<String> {
        val a = JSONObject(prefs.getString("all", "{}") ?: "{}").optJSONArray(name.trim().lowercase()) ?: return emptyList()
        return (0 until a.length()).map { a.getString(it) }
    }
    fun names(): List<String> = JSONObject(prefs.getString("all", "{}") ?: "{}").keys().asSequence().toList().sorted()
}
