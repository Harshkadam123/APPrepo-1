package com.harsh.jarvis.proactive

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Durable proactive scheduling: WorkManager for refreshes, AlarmManager for daily anchors. */
object ProactiveScheduler {
    fun canScheduleExact(context: Context): Boolean = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    } else true
    private const val PERIODIC_WORK = "jarvis_proactive_periodic"
    private const val MORNING_REQUEST = 2802
    private const val EVENING_REQUEST = 2803

    fun schedule(context: Context) {
        schedulePeriodic(context)
        scheduleDailyAlarms(context)
    }

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<ProactiveWorker>(15, TimeUnit.MINUTES)
            .setInputData(Data.Builder().putString(ProactiveWorker.KEY_KIND, ProactiveWorker.KIND_PERIODIC).build())
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleDailyAlarms(context: Context) {
        scheduleDaily(context, 8, MORNING_REQUEST, ProactiveWorker.KIND_MORNING)
        scheduleDaily(context, 20, EVENING_REQUEST, ProactiveWorker.KIND_EVENING)
    }

    private fun scheduleDaily(context: Context, hour: Int, requestCode: Int, kind: String) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ProactiveAlarmReceiver::class.java).putExtra(ProactiveAlarmReceiver.EXTRA_KIND, kind)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val now = LocalDateTime.now()
        var target = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        val trigger = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (canScheduleExact(context)) {
            runCatching { alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending) }
                .onFailure { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending) }
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }

    internal fun enqueueDailyWork(context: Context, kind: String) {
        val data = Data.Builder().putString(ProactiveWorker.KEY_KIND, kind).build()
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<ProactiveWorker>().setInputData(data).build()
        )
    }
}
