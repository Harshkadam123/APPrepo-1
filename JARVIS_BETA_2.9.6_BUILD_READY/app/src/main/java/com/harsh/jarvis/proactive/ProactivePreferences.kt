package com.harsh.jarvis.proactive

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/**
 * Stores user preferences and daily execution state for the proactive system.
 *
 * This class intentionally uses Android SharedPreferences so it has:
 * - no database dependency
 * - no coroutine dependency
 * - no additional libraries
 * - safe persistence across app restarts
 *
 * The public API is kept compatible with the existing proactive engine.
 */
class ProactivePreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "jarvis_proactive_preferences"

        private const val KEY_MORNING_ENABLED = "morning"
        private const val KEY_EVENING_ENABLED = "evening"
        private const val LAST_RUN_PREFIX = "last_"

        private const val DEFAULT_MORNING_ENABLED = true
        private const val DEFAULT_EVENING_ENABLED = true
    }

    /*
     * Use applicationContext so this class never accidentally keeps
     * an Activity/Service Context alive longer than necessary.
     */
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    /**
     * Whether the morning proactive briefing is enabled.
     */
    var morningEnabled: Boolean
        get() = runCatching {
            prefs.getBoolean(
                KEY_MORNING_ENABLED,
                DEFAULT_MORNING_ENABLED
            )
        }.getOrDefault(DEFAULT_MORNING_ENABLED)

        set(value) {
            runCatching {
                prefs.edit()
                    .putBoolean(KEY_MORNING_ENABLED, value)
                    .apply()
            }
        }

    /**
     * Whether the evening proactive briefing is enabled.
     */
    var eveningEnabled: Boolean
        get() = runCatching {
            prefs.getBoolean(
                KEY_EVENING_ENABLED,
                DEFAULT_EVENING_ENABLED
            )
        }.getOrDefault(DEFAULT_EVENING_ENABLED)

        set(value) {
            runCatching {
                prefs.edit()
                    .putBoolean(KEY_EVENING_ENABLED, value)
                    .apply()
            }
        }

    /**
     * Returns true when the specified proactive action has not been
     * successfully marked as run today.
     *
     * Example:
     *
     * shouldRun("morning")
     * shouldRun("evening")
     * shouldRun("daily_briefing")
     */
    fun shouldRun(key: String): Boolean {
        val normalizedKey = normalizeKey(key)

        /*
         * An invalid/empty key should not accidentally create or read
         * a shared preference such as "last_".
         *
         * Returning false is safer because the caller cannot reliably
         * track execution without a valid key.
         */
        if (normalizedKey == null) {
            return false
        }

        val today = currentDayString()

        val lastRun = runCatching {
            prefs.getString(
                lastRunKey(normalizedKey),
                null
            )
        }.getOrNull()

        return lastRun != today
    }

    /**
     * Marks a proactive action as executed today.
     *
     * Invalid keys are ignored safely.
     */
    fun markRun(key: String) {
        val normalizedKey = normalizeKey(key) ?: return

        val today = currentDayString()

        runCatching {
            prefs.edit()
                .putString(
                    lastRunKey(normalizedKey),
                    today
                )
                .apply()
        }
    }

    /**
     * Clears the recorded execution state for a specific key.
     *
     * Useful when testing or when JARVIS needs to deliberately allow
     * a proactive action to run again today.
     */
    fun clearRun(key: String) {
        val normalizedKey = normalizeKey(key) ?: return

        runCatching {
            prefs.edit()
                .remove(lastRunKey(normalizedKey))
                .apply()
        }
    }

    /**
     * Returns the date on which a key was last marked as run.
     *
     * Returns null if it has never been run.
     */
    fun lastRunDate(key: String): String? {
        val normalizedKey = normalizeKey(key) ?: return null

        return runCatching {
            prefs.getString(
                lastRunKey(normalizedKey),
                null
            )
        }.getOrNull()
    }

    /**
     * Resets all proactive execution markers while preserving
     * the user's morning/evening preferences.
     */
    fun clearRunHistory() {
        runCatching {
            val editor = prefs.edit()

            prefs.all.keys
                .filter { it.startsWith(LAST_RUN_PREFIX) }
                .forEach { key ->
                    editor.remove(key)
                }

            editor.apply()
        }
    }

    /**
     * Resets proactive preferences to their default values and
     * removes execution history.
     */
    fun reset() {
        runCatching {
            prefs.edit()
                .clear()
                .putBoolean(
                    KEY_MORNING_ENABLED,
                    DEFAULT_MORNING_ENABLED
                )
                .putBoolean(
                    KEY_EVENING_ENABLED,
                    DEFAULT_EVENING_ENABLED
                )
                .apply()
        }
    }

    /**
     * Normalizes preference keys so accidental whitespace does not
     * create multiple entries for the same logical action.
     */
    private fun normalizeKey(key: String): String? {
        val normalized = key.trim()

        if (normalized.isEmpty()) {
            return null
        }

        /*
         * Prevent excessively large preference keys.
         * This is defensive only and does not affect normal JARVIS keys.
         */
        if (normalized.length > 100) {
            return normalized.take(100)
        }

        return normalized
    }

    private fun lastRunKey(key: String): String {
        return LAST_RUN_PREFIX + key
    }

    private fun currentDayString(): String {
        return runCatching {
            LocalDate.now().toString()
        }.getOrElse {
            /*
             * LocalDate.now() should normally never fail on Android,
             * but returning a stable fallback keeps the class defensive.
             */
            "unknown-date"
        }
    }
}
