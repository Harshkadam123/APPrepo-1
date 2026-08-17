package com.harsh.jarvis.focus

import android.content.Context
import android.net.Uri

class WebsiteStudyMode(context: Context) {
    private val p = context.getSharedPreferences("jarvis_web", Context.MODE_PRIVATE)
    fun blocked(): Set<String> = p.getStringSet("blocked", emptySet()) ?: emptySet()
    fun allowed(): Set<String> = p.getStringSet("allowed", DEFAULT_ALLOWED) ?: DEFAULT_ALLOWED
    fun setBlocked(v: Set<String>) = p.edit().putStringSet("blocked", v).apply()
    fun setAllowed(v: Set<String>) = p.edit().putStringSet("allowed", v).apply()
    fun youtubeAllowedChannels(): Set<String> = p.getStringSet("yt_channels", emptySet()) ?: emptySet()
    fun setYoutubeAllowedChannels(v: Set<String>) = p.edit().putStringSet("yt_channels", v).apply()
    fun canOpen(url: String): Boolean {
        val host = Uri.parse(url).host?.lowercase()?.removePrefix("www.") ?: return false
        if (blocked().any { host == it || host.endsWith(".$it") }) return false
        return allowed().any { host == it || host.endsWith(".$it") }
    }
    fun isBlocked(host: String): Boolean = blocked().any { host == it || host.endsWith(".$it") }
    companion object { val DEFAULT_ALLOWED = setOf("khanacademy.org", "coursera.org", "docs.google.com", "wikipedia.org") }
}
