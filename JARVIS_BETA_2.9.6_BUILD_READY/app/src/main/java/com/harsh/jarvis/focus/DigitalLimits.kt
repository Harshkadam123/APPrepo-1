package com.harsh.jarvis.focus

import android.content.Context

class DigitalLimits(private val context: Context) {
    private val p=context.getSharedPreferences("jarvis_limits",Context.MODE_PRIVATE)
    fun set(packageName:String, minutes:Int){ if(packageName.isNotBlank()) p.edit().putInt(packageName,minutes.coerceIn(1,1440)).apply() }
    fun get(packageName:String):Int=p.getInt(packageName,0)
    fun all(): Map<String,Int> =p.all.mapNotNull{(k,v)->(v as? Int)?.let{k to it}}.toMap()
}
