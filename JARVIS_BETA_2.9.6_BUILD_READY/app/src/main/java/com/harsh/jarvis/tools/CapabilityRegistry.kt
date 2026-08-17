package com.harsh.jarvis.tools

import com.harsh.jarvis.privacy.PrivacyCapability

/**
 * Capability catalog exposed to the future JARVIS planner.
 * Personal-data capabilities are policy-gated and are NOT implemented as
 * unrestricted APIs in this beta.
 */
data class JarvisCapability(
    val id: String,
    val description: String,
    val actionLevel: String,
    val privacyCapability: PrivacyCapability? = null,
    val implemented: Boolean = true
)

class CapabilityRegistry {
    fun all(): List<JarvisCapability> = listOf(
        JarvisCapability("create_reminder", "Create and verify a reminder/task", "SAFE"),
        JarvisCapability("show_tasks", "Read active tasks", "SAFE"),
        JarvisCapability("delete_task", "Delete a selected task", "CONFIRM"),
        JarvisCapability("save_memory", "Persist an explicitly requested memory", "SAFE", PrivacyCapability.JARVIS_MEMORY),
        JarvisCapability("search_memory", "Search explicitly saved JARVIS memories", "SAFE", PrivacyCapability.JARVIS_MEMORY),
        JarvisCapability("open_allowed_app", "Open a user-allowed launcher app", "SAFE", PrivacyCapability.APP_CONTROL),
        JarvisCapability("action_history", "Inspect verified action history", "SAFE", PrivacyCapability.ACTION_HISTORY),
        JarvisCapability("safe_retry", "Retry a persisted safe failed/partial action", "SAFE"),

        // Future personal-data capabilities are declared here but intentionally
        // have no Android data implementation in BETA 2.3 Privacy.
        JarvisCapability("contact_lookup", "Resolve one named contact without exposing the contact database", "ASK by default; ALLOW can be enabled", PrivacyCapability.CONTACT_LOOKUP, true),
        JarvisCapability("message_content", "Read message bodies", "NEVER", PrivacyCapability.MESSAGE_CONTENT, false),
        JarvisCapability("notification_content", "Read private notification text", "NEVER", PrivacyCapability.NOTIFICATION_CONTENT, false),
        JarvisCapability("file_metadata", "Search filenames and metadata without reading contents", "ASK", PrivacyCapability.FILE_METADATA, false),
        JarvisCapability("file_content", "Read private file contents", "NEVER", PrivacyCapability.FILE_CONTENT, false),
        JarvisCapability("photos", "Read or analyze personal photos/videos", "NEVER", PrivacyCapability.PHOTOS, false),
        JarvisCapability("location", "Access precise or historical location", "NEVER", PrivacyCapability.LOCATION, false),
        JarvisCapability("clipboard", "Read clipboard contents", "NEVER", PrivacyCapability.CLIPBOARD, false),
        JarvisCapability("credentials", "Access passwords, API keys, OTPs or tokens", "NEVER", PrivacyCapability.CREDENTIALS, false),
        JarvisCapability("calendar_data", "Read limited upcoming calendar event titles", "ASK by default; ALLOW can be enabled", PrivacyCapability.CALENDAR_DATA, true),
        JarvisCapability("communication_actions", "Open dialer/messaging composer without auto-sending", "ASK", PrivacyCapability.COMMUNICATIONS, true)
    )
}
