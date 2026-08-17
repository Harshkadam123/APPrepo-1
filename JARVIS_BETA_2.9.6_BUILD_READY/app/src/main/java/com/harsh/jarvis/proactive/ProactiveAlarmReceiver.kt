package com.harsh.jarvis.proactive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Thin alarm bridge. All real work is delegated to WorkManager. */
class ProactiveAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ProactiveScheduler.enqueueDailyWork(
            context,
            intent?.getStringExtra(EXTRA_KIND) ?: ProactiveWorker.KIND_PERIODIC
        )
        ProactiveScheduler.scheduleDailyAlarms(context)
    }

    companion object {
        const val EXTRA_KIND = "proactive_kind"
    }
}
