package com.harsh.jarvis.focus

import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
import com.harsh.jarvis.security.NotificationSupport

class FocusAlarmReceiver: BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent?){
        if (!NotificationSupport.canNotify(context)) return
        val manager=FocusManager(context); val state=manager.state()
        val next=state?.let { if(it.mode=="POMODORO") manager.advancePomodoro() else { manager.stop(true); false } } ?: false
        val channel="jarvis_focus"; val nm=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(android.os.Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel(channel,"JARVIS Focus",NotificationManager.IMPORTANCE_HIGH))
        val text=if(next) "Focus phase complete. Your next Pomodoro phase has started." else "Your focus session is complete."
        val n=NotificationCompat.Builder(context,channel).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("JARVIS Focus").setContentText(text).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        nm.notify(551,n)
    }
}
