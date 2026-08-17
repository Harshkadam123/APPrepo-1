package com.harsh.jarvis.focus

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class FocusPlan(val id: Long, val title: String, val day: String, val startMinutes: Int, val durationMinutes: Int, val tag: String, val enabled: Boolean = true)

class FocusPlannerStore(context: Context) {
    private val p = context.getSharedPreferences("jarvis_focus_planner", Context.MODE_PRIVATE)
    fun all(): List<FocusPlan> {
        val a = JSONArray(p.getString("plans", "[]") ?: "[]")
        return (0 until a.length()).mapNotNull { runCatching { val o=a.getJSONObject(it); FocusPlan(o.getLong("id"),o.getString("title"),o.getString("day"),o.getInt("start"),o.getInt("duration"),o.optString("tag","Study"),o.optBoolean("enabled",true)) }.getOrNull() }
    }
    fun add(title:String, day:String, start:Int, duration:Int, tag:String): FocusPlan {
        val plan=FocusPlan(System.currentTimeMillis(),title.trim(),day.trim(),start.coerceIn(0,1439),duration.coerceIn(1,720),tag.ifBlank{"Study"},true)
        val a=JSONArray(p.getString("plans","[]") ?: "[]")
        a.put(JSONObject().apply { put("id",plan.id); put("title",plan.title); put("day",plan.day); put("start",plan.startMinutes); put("duration",plan.durationMinutes); put("tag",plan.tag); put("enabled",true) })
        p.edit().putString("plans",a.toString()).apply(); return plan
    }
    fun delete(id:Long){ val old=all().filterNot{it.id==id}; val a=JSONArray(); old.forEach{a.put(JSONObject().apply{put("id",it.id);put("title",it.title);put("day",it.day);put("start",it.startMinutes);put("duration",it.durationMinutes);put("tag",it.tag);put("enabled",it.enabled)})}; p.edit().putString("plans",a.toString()).apply() }
}
