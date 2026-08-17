package com.harsh.jarvis.focus

import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class DistractionContentGuard(private val context: android.content.Context) {
    private val prefs = context.getSharedPreferences("jarvis_distraction_guard", android.content.Context.MODE_PRIVATE)
    fun reelsShortsEnabled() = prefs.getBoolean("reels_shorts", true)
    fun youtubeStudyMode() = prefs.getBoolean("youtube_study", false)
    fun setReelsShorts(v: Boolean) = prefs.edit().putBoolean("reels_shorts", v).apply()
    fun setYoutubeStudyMode(v: Boolean) = prefs.edit().putBoolean("youtube_study", v).apply()
    fun shouldBlock(packageName: String, root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val text = StringBuilder(); collect(root, text, 0)
        val s = text.toString().lowercase(Locale.US)
        if (reelsShortsEnabled() && packageName in SOCIAL_APPS && SHORTS_HINTS.any(s::contains)) return true
        if (youtubeStudyMode() && packageName == YOUTUBE && (SHORTS_HINTS.any(s::contains) || DISTRACTING_HINTS.any(s::contains))) return true
        return false
    }
    private fun collect(n: AccessibilityNodeInfo, out: StringBuilder, depth: Int) {
        if (depth > 7) return
        n.text?.let { out.append(' ').append(it) }; n.contentDescription?.let { out.append(' ').append(it) }
        for (i in 0 until n.childCount) n.getChild(i)?.let { child -> collect(child, out, depth + 1); child.recycle() }
    }
    companion object {
        const val YOUTUBE = "com.google.android.youtube"
        val SOCIAL_APPS = setOf("com.instagram.android", "com.google.android.youtube", "com.snapchat.android", "com.facebook.katana")
        val SHORTS_HINTS = setOf("shorts", "reels", "reel", "spotlight")
        val DISTRACTING_HINTS = setOf("recommended", "trending", "shorts")
    }
}
