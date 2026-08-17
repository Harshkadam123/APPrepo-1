package com.harsh.jarvis.ai

import android.app.ActivityManager
import android.content.Context

/** Small memory policy layer; it never forces a model to remain resident. */
class ModelMemoryManager(context: Context) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun availableMemoryMb(): Long = activityManager.memoryInfo.availMem / (1024L * 1024L)

    fun shouldAvoidHeavyInference(requiredMb: Int): Boolean {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        val available = info.availMem / (1024L * 1024L)
        return info.lowMemory || available < requiredMb.toLong() + 128L
    }
}
