package com.harsh.jarvis.focus

import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import java.net.URI
import java.util.Locale

/**
 * Regain-style local adult-site protection.
 * It intentionally has no public disable/stop method or UI toggle.
 * It only inspects browser address-bar text and accessibility-visible URL/title text.
 */
class AdultProtectionManager(private val context: Context) {
    companion object {
        private val BROWSERS = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.sec.android.app.sbrowser",
            "com.duckduckgo.mobile.android",
            "com.vivaldi.browser",
            "com.kiwibrowser.browser",
            "com.UCMobile.intl",
            "com.mi.globalbrowser"
        )

        // Seed list. This is deliberately conservative: exact hosts/subdomains only.
        private val BLOCKED_HOSTS = setOf(
            "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com",
            "youporn.com", "tube8.com", "spankbang.com", "eporner.com", "beeg.com",
            "hqporner.com", "pornone.com", "tnaflix.com", "drtuber.com", "porntrex.com",
            "porn.com", "thumbzilla.com", "nuvid.com", "motherless.com", "rule34.xxx",
            "xhamsterlive.com", "stripchat.com", "chaturbate.com", "livejasmin.com",
            "cam4.com", "camsoda.com", "manyvids.com", "onlyfans.com"
        )

        private val URL_HINTS = setOf(
            "porn", "xxx", "sexcam", "sex-video", "adult-video", "nsfw", "hentai", "redtube",
            "xvideos", "xnxx", "xhamster", "youporn", "spankbang", "chaturbate", "stripchat"
        )

        fun isBrowser(packageName: String): Boolean = packageName in BROWSERS

        fun isBlockedUrl(raw: String): Boolean {
            val value = raw.trim().lowercase(Locale.US)
            if (value.isBlank()) return false
            val host = runCatching {
                val normalized = if (value.contains("://")) value else "https://$value"
                URI(normalized).host?.lowercase(Locale.US)?.removePrefix("www.")
            }.getOrNull()
            if (host != null && BLOCKED_HOSTS.any { host == it || host.endsWith(".$it") }) return true
            return URL_HINTS.any { value.contains(it) }
        }
    }

    fun inspectBrowser(eventPackage: String, root: AccessibilityNodeInfo?): Boolean {
        if (!isBrowser(eventPackage) || root == null) return false
        val candidates = ArrayList<String>()
        collectAddressCandidates(root, candidates)
        if (candidates.any(::isBlockedUrl)) {
            val intent = Intent(context, AdultBlockedActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startActivity(intent)
            return true
        }
        return false
    }

    private fun collectAddressCandidates(node: AccessibilityNodeInfo, out: MutableList<String>) {
        val className = node.className?.toString() ?: ""
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()

        // Prefer address-bar-like EditText fields and URI-looking text. Avoid collecting
        // arbitrary page text to reduce privacy exposure and false positives.
        if (className.contains("EditText", ignoreCase = true)) {
            if (text.contains(".") || text.startsWith("http", true) || text.startsWith("www.", true)) out += text
        }
        if (text.startsWith("http", true) || text.startsWith("www.", true)) out += text
        if (description.startsWith("http", true) || description.startsWith("www.", true)) out += description

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectAddressCandidates(child, out)
                child.recycle()
            }
        }
    }
}
