package com.harsh.jarvis.focus

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

class UsageStatsReader(private val context: Context) {
    data class AppUsage(val label:String,val packageName:String,val minutes:Long)
    fun range(days:Int):List<AppUsage>{
        val end=System.currentTimeMillis(); val start=Calendar.getInstance().apply{timeInMillis=end;add(Calendar.DAY_OF_YEAR,-(days-1));set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis
        val mgr=context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats=mgr.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,start,end); val pm=context.packageManager
        return stats.filter{it.totalTimeInForeground>0}.groupBy{it.packageName}.map{(pkg,items)->val label=runCatching{pm.getApplicationLabel(pm.getApplicationInfo(pkg,0)).toString()}.getOrDefault(pkg);AppUsage(label,pkg,items.sumOf{it.totalTimeInForeground}/60000L)}.sortedByDescending{it.minutes}.take(50)
    }
    fun today()=range(1)
    fun week()=range(7)
    fun month()=range(30)
}
