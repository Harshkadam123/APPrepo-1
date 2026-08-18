package com.harsh.jarvis.ai

import android.content.Context
import android.content.SharedPreferences

/**
 * Local, user-controlled JARVIS profile and preferences.
 *
 * All data is stored locally in Android SharedPreferences.
 *
 * This class intentionally has no dependency on:
 * - coroutines
 * - Room
 * - network APIs
 * - AI models
 * - repositories
 * - Compose
 *
 * Therefore it is safe to construct from normal Android code.
 */
class UserProfile(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    /**
     * User's preferred name.
     *
     * Null means that the user has not configured a name.
     */
    var name: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) {
            val cleaned = value?.trim()

            if (cleaned.isNullOrEmpty()) {
                prefs.edit()
                    .remove(KEY_NAME)
                    .apply()
            } else {
                prefs.edit()
                    .putString(KEY_NAME, cleaned)
                    .apply()
            }
        }

    /**
     * JARVIS response/personality style.
     *
     * Defaults to "balanced".
     */
    var assistantStyle: String
        get() = prefs.getString(
            KEY_ASSISTANT_STYLE,
            DEFAULT_ASSISTANT_STYLE
        ) ?: DEFAULT_ASSISTANT_STYLE
        set(value) {
            val cleaned = value.trim()

            val finalValue = if (cleaned.isEmpty()) {
                DEFAULT_ASSISTANT_STYLE
            } else {
                cleaned
            }

            prefs.edit()
                .putString(KEY_ASSISTANT_STYLE, finalValue)
                .apply()
        }

    /**
     * Returns a short human-readable profile summary.
     */
    fun summary(): String {
        val currentName = name
        val currentStyle = assistantStyle

        return buildString {
            append("Profile: ")

            if (currentName.isNullOrBlank()) {
                append("name not set")
            } else {
                append("name=")
                append(currentName)
            }

            append(", style=")
            append(currentStyle)
        }
    }

    /**
     * Clears all locally stored profile information.
     */
    fun clear() {
        prefs.edit()
            .clear()
            .apply()
    }

    /**
     * Returns true when the user has configured a name.
     */
    fun hasName(): Boolean {
        return !name.isNullOrBlank()
    }

    /**
     * Sets the user's name.
     *
     * Returns true when a non-empty name was stored.
     */
    fun setName(value: String?): Boolean {
        name = value
        return !name.isNullOrBlank()
    }

    /**
     * Returns the currently configured assistant style.
     */
    fun getAssistantStyle(): String {
        return assistantStyle
    }

    /**
     * Sets the assistant style.
     *
     * Empty input automatically falls back to "balanced".
     */
    fun setAssistantStyle(value: String?) {
        assistantStyle = value ?: DEFAULT_ASSISTANT_STYLE
    }

    companion object {
        private const val PREFS_NAME = "jarvis_profile"

        private const val KEY_NAME = "name"

        private const val KEY_ASSISTANT_STYLE = "style"

        private const val DEFAULT_ASSISTANT_STYLE = "balanced"
    }
}
