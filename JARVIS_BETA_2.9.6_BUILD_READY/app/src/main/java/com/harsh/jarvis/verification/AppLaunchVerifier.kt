package com.harsh.jarvis.verification

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import kotlinx.coroutines.delay

/**
 * Independent foreground observer. When Usage Access is granted, JARVIS can
 * verify that the target package became the most recently used app shortly
 * after a launch request. Without it, callers receive explicit PARTIAL evidence.
 */
class AppLaunchVerifier(private val context: Context) {
    suspend fun verifyForeground(packageName: String, windowMs: Long = 5_000L): ForegroundVerification {
        if (!hasUsageAccess()) {
            return ForegroundVerification(false, false, "Usage Access is not enabled for JARVIS.")
        }
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return ForegroundVerification(false, false, "Android UsageStats service is unavailable.")
        repeat(6) { attempt ->
            val now = System.currentTimeMillis()
            val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - windowMs, now)
            val latest = stats.maxByOrNull { it.lastTimeUsed }
            val matched = latest?.packageName == packageName && now - latest.lastTimeUsed <= windowMs
            if (matched) return ForegroundVerification(true, true, "Foreground usage evidence matched $packageName.")
            if (attempt < 5) delay(300)
        }
        return ForegroundVerification(true, false, "Usage Access was available, but $packageName was not observed as the most recently used app after polling.")
    }

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
}

data class ForegroundVerification(val available: Boolean, val verified: Boolean, val evidence: String)
