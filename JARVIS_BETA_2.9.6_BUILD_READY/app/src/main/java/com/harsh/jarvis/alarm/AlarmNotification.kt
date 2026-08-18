package com.harsh.jarvis.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.harsh.jarvis.R
import com.harsh.jarvis.security.NotificationSupport

/**
 * Creates and displays JARVIS task/alarm notifications.
 *
 * This class is deliberately self-contained:
 * - No coroutines
 * - No database dependency
 * - No repository dependency
 * - No mutable notification properties
 *
 * It is safe to call from AlarmReceiver, services, or other Android components.
 */
object AlarmNotification {

    private const val CHANNEL_ID = "jarvis_task_alarms"
    private const val CHANNEL_NAME = "JARVIS Task Alarms"
    private const val CHANNEL_DESCRIPTION = "Notifications for scheduled JARVIS task alarms"

    private const val EXTRA_TASK_ID = "TASK_ID"
    private const val EXTRA_TASK_TITLE = "TASK_TITLE"
    private const val EXTRA_TASK_DESCRIPTION = "TASK_DESCRIPTION"

    /**
     * Shows a task alarm notification.
     *
     * If notifications are not allowed, this method simply returns.
     */
    fun show(
        context: Context,
        taskId: Long,
        title: String,
        description: String
    ) {
        val appContext = context.applicationContext

        if (!NotificationSupport.canNotify(appContext)) {
            return
        }

        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE)
                    as? NotificationManager
                ?: return

        createNotificationChannel(notificationManager)

        val fullScreenIntent = Intent(
            appContext,
            AlarmActivity::class.java
        ).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, title)
            putExtra(EXTRA_TASK_DESCRIPTION, description)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val requestCode = makeRequestCode(taskId)

        val pendingIntentFlags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE

        val fullScreenPendingIntent =
            PendingIntent.getActivity(
                appContext,
                requestCode,
                fullScreenIntent,
                pendingIntentFlags
            )

        val notification = NotificationCompat.Builder(
            appContext,
            CHANNEL_ID
        )
            .setSmallIcon(getNotificationIcon())
            .setContentTitle(
                title.ifBlank { "JARVIS Task Reminder" }
            )
            .setContentText(
                description.ifBlank { "You have a scheduled task." }
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        description.ifBlank {
                            "You have a scheduled task."
                        }
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(
                fullScreenPendingIntent,
                true
            )
            .setVisibility(
                NotificationCompat.VISIBILITY_PUBLIC
            )
            .build()

        notificationManager.notify(
            requestCode,
            notification
        )
    }

    /**
     * Cancels one task alarm notification.
     */
    fun cancel(
        context: Context,
        taskId: Long
    ) {
        val appContext = context.applicationContext

        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE)
                    as? NotificationManager
                ?: return

        notificationManager.cancel(
            makeRequestCode(taskId)
        )
    }

    /**
     * Cancels every notification created by this class.
     *
     * This should only be used when the application intentionally
     * wants to clear its alarm notifications.
     */
    fun cancelAll(
        context: Context
    ) {
        val appContext = context.applicationContext

        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE)
                    as? NotificationManager
                ?: return

        notificationManager.cancelAll()
    }

    /**
     * Creates the Android O+ notification channel.
     *
     * Notice that NotificationChannel.description is assigned
     * through the local variable `channelDescription`.
     *
     * This avoids accidental reassignment of a val property.
     */
    private fun createNotificationChannel(
        notificationManager: NotificationManager
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )

        channel.description = CHANNEL_DESCRIPTION

        val soundUri: Uri =
            android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        channel.setSound(
            soundUri,
            audioAttributes
        )

        channel.enableVibration(true)

        notificationManager.createNotificationChannel(
            channel
        )
    }

    /**
     * Generates a stable Android notification ID from the task ID.
     */
    private fun makeRequestCode(
        taskId: Long
    ): Int {
        return (taskId xor (taskId ushr 32)).toInt()
    }

    /**
     * Uses the application's standard launcher icon.
     *
     * If your project has a dedicated notification icon,
     * you can replace this resource with that icon later.
     */
    private fun getNotificationIcon(): Int {
        return R.mipmap.ic_launcher
    }
}
