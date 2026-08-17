package com.harsh.jarvis.security

import android.content.Context

/** Persistent allow-list for apps JARVIS is allowed to launch. */
class AppPermissionStore(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_app_permissions", Context.MODE_PRIVATE)

    fun isAllowed(packageName: String): Boolean = prefs.getBoolean(packageName, false)

    fun setAllowed(packageName: String, allowed: Boolean) {
        prefs.edit().putBoolean(packageName, allowed).apply()
    }

    fun allowedPackages(): Set<String> = prefs.all.filterValues { it == true }.keys
}
