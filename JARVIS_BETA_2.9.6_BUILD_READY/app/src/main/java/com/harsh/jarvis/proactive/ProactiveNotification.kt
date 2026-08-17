package com.harsh.jarvis.proactive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.harsh.jarvis.security.NotificationSupport

object ProactiveNotification {
    private const val CHANNEL = "jarvis_proactive"
    fun show(context: Context, event: ProactiveEvent) {
        if (!NotificationSupport.canNotify(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "JARVIS Proactive Intelligence", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(event.title)
            .setContentText(event.detail.ifBlank { event.requiredAction })
            .setPriority(if (event.priority == ProactivePriority.HIGH.name || event.priority == ProactivePriority.CRITICAL.name) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify((event.id % Int.MAX_VALUE).toInt(), notification)
    }
}
