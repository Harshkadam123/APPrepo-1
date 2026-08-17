package com.harsh.jarvis.focus

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build

class FocusAlarmScheduler(private val context: Context) {
    private val alarm=(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
    private val requestCode=98765
    fun schedule(at:Long){val pi=PendingIntent.getBroadcast(context,requestCode,Intent(context,FocusAlarmReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);runCatching { if(Build.VERSION.SDK_INT>=23 && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms())) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi) else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi) }.onFailure { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi) }}
    fun cancel(){val pi=PendingIntent.getBroadcast(context,requestCode,Intent(context,FocusAlarmReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);alarm.cancel(pi)}
}
