package com.harsh.jarvis.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.harsh.jarvis.security.ActionLevel
import com.harsh.jarvis.actions.ActionResult
import com.harsh.jarvis.actions.ActionStatus
import com.harsh.jarvis.actions.JarvisAction
import com.harsh.jarvis.privacy.PrivacyCapability
import com.harsh.jarvis.privacy.PrivacyGateway

/** Narrow personal-data tools. Android access is policy-gated and minimized. */
class PersonalDataTools(private val context: Context, private val privacy: PrivacyGateway) {

    data class CalendarSignal(val title: String, val begin: Long, val end: Long)

    fun contactAction(name: String): JarvisAction {
        val q = name.trim()
        return JarvisAction(
            name = "contact_lookup",
            description = "look up the contact '$q'",
            level = actionLevel(PrivacyCapability.CONTACT_LOOKUP),
            allowed = { !privacy.isBlocked(PrivacyCapability.CONTACT_LOOKUP) },
            payload = mapOf("name" to q),
            execute = { resolveContact(q) }
        )
    }

    fun calendarAction(query: String): JarvisAction {
        val q = query.trim()
        return JarvisAction(
            name = "calendar_query",
            description = "read upcoming calendar events${q.takeIf { it.isNotBlank() }?.let { " matching '$it'" } ?: ""}",
            level = actionLevel(PrivacyCapability.CALENDAR_DATA),
            allowed = { !privacy.isBlocked(PrivacyCapability.CALENDAR_DATA) },
            payload = mapOf("query" to q),
            execute = { resolveCalendar(q) }
        )
    }

    fun callAction(name: String): JarvisAction {
        val q = name.trim()
        return JarvisAction(
            name = "call_contact",
            description = "open the dialer for '$q'",
            level = maxActionLevel(PrivacyCapability.COMMUNICATIONS, PrivacyCapability.CONTACT_LOOKUP),
            allowed = { !privacy.isBlocked(PrivacyCapability.COMMUNICATIONS) && !privacy.isBlocked(PrivacyCapability.CONTACT_LOOKUP) },
            payload = mapOf("name" to q),
            execute = { callResolved(q) }
        )
    }

    fun messageAction(name: String, body: String): JarvisAction {
        val q = name.trim()
        return JarvisAction(
            name = "message_contact",
            description = "open the message composer for '$q'",
            level = maxActionLevel(PrivacyCapability.COMMUNICATIONS, PrivacyCapability.CONTACT_LOOKUP),
            allowed = { !privacy.isBlocked(PrivacyCapability.COMMUNICATIONS) && !privacy.isBlocked(PrivacyCapability.CONTACT_LOOKUP) },
            payload = mapOf("name" to q, "body" to body),
            execute = { messageResolved(q, body) }
        )
    }

    // Direct personal-data execution is intentionally private. All user-facing actions
    // must be created as JarvisAction and pass through ActionExecutor/PrivacyGateway.

    suspend fun calendarSignals(): List<CalendarSignal> {
        // Background proactive work may only consume calendar data after the user
        // explicitly changed the capability from ASK to ALLOW. A worker cannot ask.
        if (!privacy.canUse(PrivacyCapability.CALENDAR_DATA)) return emptyList()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val now = System.currentTimeMillis()
        val end = now + 7L * 24 * 60 * 60 * 1000
        val out = mutableListOf<CalendarSignal>()
        context.contentResolver.query(
            CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
                appendPath(now.toString()); appendPath(end.toString())
            }.build(),
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END),
            null, null, "${CalendarContract.Instances.BEGIN} ASC"
        ).use { c ->
            if (c != null) while (c.moveToNext() && out.size < 20) {
                out += CalendarSignal(c.getString(0) ?: "Untitled event", c.getLong(1), c.getLong(2))
            }
        }
        privacy.record(PrivacyCapability.CALENDAR_DATA, "Use authorized calendar signals for proactive planning", "Event titles and start/end times, limited to 20 events", "SUCCESS")
        return out
    }

    private suspend fun resolveContact(name: String): ActionResult {
        if (privacy.isBlocked(PrivacyCapability.CONTACT_LOOKUP)) return blocked(PrivacyCapability.CONTACT_LOOKUP)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ActionResult(ActionStatus.FAILED, "Contacts permission is available", "JARVIS needs Contacts permission before resolving '$name'.", fix = "Grant Contacts permission and try again.")
        }
        val q = name.trim()
        if (q.isBlank()) return ActionResult(ActionStatus.FAILED, "A contact name is provided", "Tell me the contact name.")
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$q%"), null
        ).use { c ->
            if (c == null || !c.moveToFirst()) return ActionResult(ActionStatus.FAILED, "A matching contact exists", "I couldn't find a contact matching '$q'.")
            val matches = mutableListOf<Pair<String, String>>()
            do {
                val display = c.getString(0).orEmpty()
                val number = c.getString(1).orEmpty()
                if (number.isNotBlank()) matches += display to number
            } while (c.moveToNext() && matches.size < 5)
            if (matches.isEmpty()) return ActionResult(ActionStatus.FAILED, "A usable phone number exists", "I found '$q' but no usable phone number.")
            if (matches.map { it.first }.distinct().size > 1) {
                return ActionResult(ActionStatus.FAILED, "Exactly one matching contact is required", "I found multiple contacts matching '$q': ${matches.joinToString { it.first }}. Please give me the full name.", fix = "Use a more specific contact name.")
            }
            val display = matches.first().first
            val number = matches.first().second
            privacy.record(PrivacyCapability.CONTACT_LOOKUP, "Resolve one named contact", "One matching contact name and phone number", "SUCCESS")
            return ActionResult(ActionStatus.SUCCESS, "Contact '$display' was resolved", "$display: $number", verified = true, evidence = "A single matching contact row was read.")
        }
    }

    private suspend fun resolveCalendar(query: String): ActionResult {
        if (privacy.isBlocked(PrivacyCapability.CALENDAR_DATA)) return blocked(PrivacyCapability.CALENDAR_DATA)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return ActionResult(ActionStatus.FAILED, "Calendar permission is available", "JARVIS needs Calendar permission before reading events.", fix = "Grant Calendar permission and try again.")
        }
        val now = System.currentTimeMillis(); val end = now + 7L * 24 * 60 * 60 * 1000
        context.contentResolver.query(
            CalendarContract.Instances.CONTENT_URI.buildUpon().apply { appendPath(now.toString()); appendPath(end.toString()) }.build(),
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END),
            null, null, "${CalendarContract.Instances.BEGIN} ASC"
        ).use { c ->
            if (c == null) return ActionResult(ActionStatus.FAILED, "Calendar is readable", "I couldn't access your calendar right now.")
            val out = mutableListOf<String>(); var count = 0
            while (c.moveToNext() && count < 8) {
                val title = c.getString(0) ?: "Untitled event"
                if (query.isBlank() || title.contains(query, true)) { out += title; count++ }
            }
            privacy.record(PrivacyCapability.CALENDAR_DATA, "Read upcoming calendar events", "Event titles only, limited to 8 matches", "SUCCESS")
            val text = if (out.isEmpty()) "I couldn't find matching upcoming events." else "Upcoming: " + out.joinToString("; ")
            return ActionResult(ActionStatus.SUCCESS, "Upcoming calendar events were read", text, verified = true, evidence = "Calendar query completed with at most 8 event titles.")
        }
    }

    private suspend fun callResolved(name: String): ActionResult {
        if (privacy.isBlocked(PrivacyCapability.COMMUNICATIONS) || privacy.isBlocked(PrivacyCapability.CONTACT_LOOKUP)) {
            return ActionResult(ActionStatus.FAILED, "Communication and contact access are blocked", "Communication/contact access is blocked by your Privacy policy.")
        }
        val number = findNumber(name) ?: return ActionResult(ActionStatus.FAILED, "A phone number exists for '$name'", "I couldn't find a phone number for '$name'.")
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        privacy.record(PrivacyCapability.COMMUNICATIONS, "Open dialer for a named contact", "One resolved phone number", "SUCCESS")
        return ActionResult(ActionStatus.SUCCESS, "The dialer is open for $name", "Opened the dialer for $name. I did not place the call automatically.", verified = true, evidence = "Android accepted ACTION_DIAL.")
    }

    private suspend fun messageResolved(name: String, body: String): ActionResult {
        if (privacy.isBlocked(PrivacyCapability.COMMUNICATIONS) || privacy.isBlocked(PrivacyCapability.CONTACT_LOOKUP)) {
            return ActionResult(ActionStatus.FAILED, "Communication and contact access are blocked", "Communication/contact access is blocked by your Privacy policy.")
        }
        val number = findNumber(name) ?: return ActionResult(ActionStatus.FAILED, "A phone number exists for '$name'", "I couldn't find a phone number for '$name'.")
        val uri = Uri.parse("smsto:" + Uri.encode(number))
        context.startActivity(Intent(Intent.ACTION_SENDTO, uri).putExtra("sms_body", body).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        privacy.record(PrivacyCapability.COMMUNICATIONS, "Open messaging composer for a named contact", "One resolved phone number and user-authored message body", "SUCCESS")
        return ActionResult(ActionStatus.SUCCESS, "The message composer is open for $name", "Opened the messaging composer for $name. I did not send the message automatically.", verified = true, evidence = "Android accepted ACTION_SENDTO.")
    }

    private fun findNumber(name: String): String? {
        val q = name.trim(); if (q.isBlank()) return null
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$q%"), null
        ).use { c ->
            if (c == null || !c.moveToFirst()) return null
            var chosen: String? = null
            val names = linkedSetOf<String>()
            do {
                val n = c.getString(0).orEmpty()
                val number = c.getString(1).orEmpty()
                if (number.isNotBlank()) { names += n; if (chosen == null) chosen = number }
            } while (c.moveToNext() && names.size < 5)
            return if (names.size == 1) chosen else null
        }
    }

    private fun actionLevel(capability: PrivacyCapability): ActionLevel = when (privacy.mode(capability)) {
        com.harsh.jarvis.privacy.PrivacyMode.NEVER -> ActionLevel.SAFE
        com.harsh.jarvis.privacy.PrivacyMode.ASK -> ActionLevel.CONFIRM
        com.harsh.jarvis.privacy.PrivacyMode.ALLOW -> ActionLevel.SAFE
    }

    private fun maxActionLevel(a: PrivacyCapability, b: PrivacyCapability): ActionLevel =
        if (privacy.isBlocked(a) || privacy.isBlocked(b)) ActionLevel.SAFE
        else if (privacy.requiresUserApproval(a) || privacy.requiresUserApproval(b)) ActionLevel.CONFIRM
        else ActionLevel.SAFE

    private fun blocked(capability: PrivacyCapability) = ActionResult(
        ActionStatus.FAILED,
        "${capability.label} is allowed",
        "${capability.label} is blocked by your Privacy policy.",
        problem = "The privacy firewall denied access."
    )
}
