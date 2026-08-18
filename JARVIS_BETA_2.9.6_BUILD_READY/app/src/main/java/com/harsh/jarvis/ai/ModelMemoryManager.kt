package com.harsh.jarvis.ai

import android.app.ActivityManager
import android.content.Context

/**
 * Local Android memory manager for JARVIS model inference.
 *
 * Responsibilities:
 * - Reads the device's current available RAM.
 * - Detects Android low-memory conditions.
 * - Helps prevent starting heavy inference when RAM is insufficient.
 *
 * This class:
 * - Does not allocate large memory blocks.
 * - Does not keep models resident.
 * - Does not kill processes.
 * - Does not require any special permission.
 * - Uses only the Android ActivityManager memory APIs.
 */
class ModelMemoryManager(context: Context) {

    private val appContext: Context = context.applicationContext

    private val activityManager: ActivityManager =
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /**
     * Returns currently available RAM in megabytes.
     *
     * The value comes directly from ActivityManager.getMemoryInfo().
     */
    fun availableMemoryMb(): Long {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)

        return info.availMem
            .div(1024L)
            .div(1024L)
    }

    /**
     * Returns true when the device should avoid starting
     * a heavy inference operation.
     *
     * @param requiredMb estimated RAM required by the operation.
     */
    fun shouldAvoidHeavyInference(requiredMb: Int): Boolean {
        val safeRequiredMb = requiredMb.coerceAtLeast(0)

        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)

        val availableMb = info.availMem
            .div(1024L)
            .div(1024L)

        val safetyMarginMb = 128L
        val requiredWithMargin = safeRequiredMb.toLong() + safetyMarginMb

        return info.lowMemory || availableMb < requiredWithMargin
    }

    /**
     * Returns true when Android currently reports
     * that the device is under memory pressure.
     */
    fun isLowMemory(): Boolean {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)

        return info.lowMemory
    }

    /**
     * Returns the total RAM reported by Android in megabytes.
     */
    fun totalMemoryMb(): Long {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)

        return info.totalMem
            .div(1024L)
            .div(1024L)
    }

    /**
     * Returns the currently available RAM as a percentage
     * of total RAM.
     *
     * The result is between 0.0 and 100.0.
     */
    fun availableMemoryPercent(): Double {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)

        if (info.totalMem <= 0L) {
            return 0.0
        }

        return (info.availMem.toDouble() / info.totalMem.toDouble() * 100.0)
            .coerceIn(0.0, 100.0)
    }

    /**
     * Returns true when there is enough RAM for the requested
     * operation while maintaining the JARVIS safety margin.
     */
    fun hasEnoughMemory(requiredMb: Int): Boolean {
        return !shouldAvoidHeavyInference(requiredMb)
    }

    /**
     * Returns a simple local diagnostic string.
     *
     * This is intentionally lightweight and does not expose
     * any sensitive device information.
     */
    fun memorySummary(): String {
        val available = availableMemoryMb()
        val total = totalMemoryMb()
        val low = isLowMemory()

        return buildString {
            append("Available RAM: ")
            append(available)
            append(" MB, Total RAM: ")
            append(total)
            append(" MB, Low memory: ")
            append(low)
        }
    }
}
