package com.harsh.jarvis.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TaskAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("TASK_ID", -1L)
        val title = intent.getStringExtra("TASK_TITLE") ?: "Jarvis task"
        val description = intent.getStringExtra("TASK_DESCRIPTION") ?: ""

        AlarmNotification.show(
            context = context,
            taskId = taskId,
            title = title,
            description = description
        )
    }
}
