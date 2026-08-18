package com.harsh.jarvis.security

import android.content.Context
import android.content.pm.PackageManager

class PermissionManager(private val context: Context) {
    private val store = AppPermissionStore(context)

    fun isAppInstalled(packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun isAppAllowed(packageName: String): Boolean = store.isAllowed(packageName)

    fun setAppAllowed(packageName: String, allowed: Boolean) {
        store.setAllowed(packageName, allowed)
    }

    fun allowedPackages(): Set<String> = store.allowedPackages()
}
