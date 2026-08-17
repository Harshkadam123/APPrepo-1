package com.harsh.jarvis.privacy

import android.content.Context

enum class PrivacyMode { NEVER, ASK, ALLOW }

enum class PrivacyCapability(
    val label: String,
    val description: String,
    val defaultMode: PrivacyMode
) {
    APP_CONTROL("App control", "Open apps that you explicitly allow.", PrivacyMode.ALLOW),
    JARVIS_MEMORY("Explicit JARVIS memory", "Read or write memories you explicitly save for JARVIS.", PrivacyMode.ALLOW),
    ACTION_HISTORY("Action history", "Let the AI read your local JARVIS action history. The history screen remains local even when this is blocked.", PrivacyMode.NEVER),
    CONTACT_LOOKUP("Contact lookup", "Resolve a named contact without exposing the full contacts database.", PrivacyMode.ASK),
    MESSAGE_CONTENT("Message content", "Read SMS or chat message bodies.", PrivacyMode.NEVER),
    NOTIFICATION_CONTENT("Notification content", "Read notification text or private notification payloads.", PrivacyMode.NEVER),
    FILE_METADATA("File metadata", "See filenames, types and dates without reading file contents.", PrivacyMode.NEVER),
    FILE_CONTENT("File contents", "Read private documents, PDFs, photos or other file contents.", PrivacyMode.NEVER),
    PHOTOS("Photos and videos", "Read or analyze personal photos/videos.", PrivacyMode.NEVER),
    LOCATION("Location", "Access precise or historical device location.", PrivacyMode.NEVER),
    CLIPBOARD("Clipboard", "Read copied text, which may contain passwords or OTPs.", PrivacyMode.NEVER),
    CREDENTIALS("Passwords and secrets", "Access passwords, API keys, tokens, OTPs or authentication secrets.", PrivacyMode.NEVER),
    CALENDAR_DATA("Calendar data", "Read calendar event details with user approval.", PrivacyMode.ASK),
    MICROPHONE("Microphone", "Use the microphone for an explicitly started voice command. JARVIS does not persist raw audio.", PrivacyMode.ASK),
    COMMUNICATIONS("Communication actions", "Open the dialer or messaging composer using a user-selected contact and user-authored content.", PrivacyMode.ASK)
}

class PrivacyPolicyStore(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_privacy_policy", Context.MODE_PRIVATE)

    fun mode(capability: PrivacyCapability): PrivacyMode {
        val stored = prefs.getString(capability.name, null)
        return stored?.let { runCatching { PrivacyMode.valueOf(it) }.getOrNull() } ?: capability.defaultMode
    }

    fun setMode(capability: PrivacyCapability, mode: PrivacyMode) {
        prefs.edit().putString(capability.name, mode.name).apply()
    }

    fun resetDefaults() {
        prefs.edit().clear().apply()
    }
}
