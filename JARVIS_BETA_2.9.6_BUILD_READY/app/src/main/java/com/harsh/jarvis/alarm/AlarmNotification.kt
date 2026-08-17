package com.harsh.jarvis.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.harsh.jarvis.R
import com.harsh.jarvis.security.NotificationSupport

object AlarmNotification {

    private const val CHANNEL_ID = "jarvis_task_alarms"

    fun show(
        context: Context,
        taskId: Long,
        title: String,
        description: String
    ) {
        if (!NotificationSupport.canNotify(context)) return
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Task Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Task alarms from JARVIS"
                setSound(
                    android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .build()
                )
                enableVibration(true)
            }

            manager.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            putExtra("TASK_ID", taskId)
            putExtra("TASK_TITLE", title)
            putExtra("TASK_DESCRIPTION", description)
        }

        val fullScreenPending = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("JARVIS TASK ALARM")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPending)
            .apply { if (NotificationSupport.canUseFullScreenIntent(context)) setFullScreenIntent(fullScreenPending, true) }
            .build()

        manager.notify(taskId.toInt(), builder)
    }
}
