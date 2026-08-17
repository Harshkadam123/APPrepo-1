package com.harsh.jarvis.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.harsh.jarvis.tasks.Task
import com.harsh.jarvis.tasks.JarvisDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AlarmScheduler(private val context: Context) {

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(task: Task) {
        val time = task.dueTime ?: return

        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            putExtra("TASK_ID", task.id)
            putExtra("TASK_TITLE", task.title)
            putExtra("TASK_DESCRIPTION", task.description)
        }

        val pending = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
                return
            }
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                time,
                pending
            )
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                time,
                pending
            )
        }
    }

    suspend fun rescheduleAllFuture() = withContext(Dispatchers.IO) {
        val tasks = JarvisDatabase.get(context).taskDao().scheduledAfter(System.currentTimeMillis())
        tasks.forEach(::schedule)
    }

    fun cancel(task: Task) {
        val intent = Intent(context, TaskAlarmReceiver::class.java)

        val pending = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pending)
    }
}
