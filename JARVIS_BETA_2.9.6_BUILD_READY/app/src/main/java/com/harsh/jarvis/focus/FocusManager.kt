package com.harsh.jarvis.focus

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

/** Production-oriented local focus engine. Session state, notes and analytics stay on-device. */
class FocusManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_focus", Context.MODE_PRIVATE)
    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    data class State(val active:Boolean,val mode:String,val startedAt:Long,val endsAt:Long,val pausedAt:Long,val totalPaused:Long,val strict:Boolean,val tag:String,val cycles:Int,val completedCycles:Int,val phase:String,val focusMs:Long,val shortBreakMs:Long,val longBreakMs:Long,val note:String) {
        fun remaining(now:Long=System.currentTimeMillis())=if(!active||mode=="STOPWATCH")0L else max(0L,endsAt-now)
        fun elapsed(now:Long=System.currentTimeMillis())=if(!active) 0L else (now-startedAt-totalPaused).coerceAtLeast(0L)
    }
    fun state():State? {
        if (!prefs.getBoolean("active", false)) return null
        return State(
            true,
            prefs.getString("mode", "TIMER") ?: "TIMER",
            prefs.getLong("startedAt", 0),
            prefs.getLong("endsAt", 0),
            prefs.getLong("pausedAt", 0),
            prefs.getLong("totalPaused", 0),
            prefs.getBoolean("strict", false),
            prefs.getString("tag", "General") ?: "General",
            prefs.getInt("cycles", 1),
            prefs.getInt("completedCycles", 0),
            prefs.getString("phase", "FOCUS") ?: "FOCUS",
            prefs.getLong("focusMs", 25 * 60000L),
            prefs.getLong("shortBreakMs", 5 * 60000L),
            prefs.getLong("longBreakMs", 15 * 60000L),
            prefs.getString("note", "") ?: ""
        )
    }
    fun start(minutes:Int,mode:String="TIMER",strict:Boolean=false,tag:String="General",cycles:Int=1,shortBreakMinutes:Int=5,longBreakMinutes:Int=15,note:String="") {
        val now=System.currentTimeMillis(); val focus=minutes.coerceIn(1,720)*60000L
        FocusAlarmScheduler(context).schedule(now+focus)
        prefs.edit().putBoolean("active",true).putString("mode",mode).putLong("startedAt",now).putLong("endsAt",now+focus).putLong("pausedAt",0).putLong("totalPaused",0).putBoolean("strict",strict).putString("tag",tag.ifBlank{"General"}).putInt("cycles",cycles.coerceIn(1,20)).putInt("completedCycles",0).putString("phase",if(mode=="POMODORO")"FOCUS" else mode).putLong("focusMs",focus).putLong("shortBreakMs",shortBreakMinutes.coerceIn(1,60)*60000L).putLong("longBreakMs",longBreakMinutes.coerceIn(1,120)*60000L).putString("note",note.take(500)).apply()
    }
    fun advancePomodoro():Boolean { val s=state()?:return false; if(s.mode!="POMODORO")return false; val now=System.currentTimeMillis(); if(s.phase=="FOCUS"){val completed=s.completedCycles+1;if(completed>=s.cycles){stop(true);return false};val longBreak=completed%4==0;FocusAlarmScheduler(context).schedule(now+if(longBreak)s.longBreakMs else s.shortBreakMs)
            prefs.edit().putInt("completedCycles",completed).putString("phase",if(longBreak)"LONG_BREAK" else "SHORT_BREAK").putLong("startedAt",now).putLong("endsAt",now+if(longBreak)s.longBreakMs else s.shortBreakMs).putLong("pausedAt",0).putLong("totalPaused",0).apply();return true};FocusAlarmScheduler(context).schedule(now+s.focusMs)
        prefs.edit().putString("phase","FOCUS").putLong("startedAt",now).putLong("endsAt",now+s.focusMs).putLong("pausedAt",0).putLong("totalPaused",0).apply();return true }
    fun stop(completed:Boolean=false):FocusSession? { val s=state()?:return null; if(s.strict&&!completed)return null; FocusAlarmScheduler(context).cancel(); val now=System.currentTimeMillis();val elapsed=(now-s.startedAt-s.totalPaused).coerceAtLeast(0);val session=FocusSession(now,s.tag,s.mode,elapsed,completed,s.strict,s.note,s.completedCycles);saveSession(session);prefs.edit().clear().apply();return session }
    fun pause(){val s=state()?:return;if(s.strict||s.pausedAt!=0L)return;prefs.edit().putLong("pausedAt",System.currentTimeMillis()).apply()}
    fun resume(){val s=state()?:return;if(s.strict||s.pausedAt==0L)return;val now=System.currentTimeMillis();val paused=now-s.pausedAt;prefs.edit().putLong("totalPaused",s.totalPaused+paused).putLong("endsAt",s.endsAt+paused).putLong("pausedAt",0).apply()}
    fun setBlockList(packages:Set<String>)=prefs.edit().putStringSet("blocked_packages",packages).apply()
    fun blockList(): Set<String> =prefs.getStringSet("blocked_packages",emptySet())?:emptySet()
    fun setDailyGoal(minutes:Int)=prefs.edit().putInt("daily_goal",minutes.coerceIn(1,1440)).apply()
    fun dailyGoal()=prefs.getInt("daily_goal",120)
    fun sessions():List<FocusSession>{val a=JSONArray(prefs.getString("sessions","[]")?:"[]");return(0 until a.length()).mapNotNull{i->runCatching{val o=a.getJSONObject(i);FocusSession(o.getLong("endedAt"),o.getString("tag"),o.getString("mode"),o.getLong("durationMs"),o.getBoolean("completed"),o.getBoolean("strict"),o.optString("note"),o.optInt("cycles",0))}.getOrNull()}.sortedByDescending{it.endedAt}}
    fun todayMinutes()=sessions().filter{fmt.format(Date(it.endedAt))==fmt.format(Date())}.sumOf{(it.durationMs/60000).toInt()}
    fun totalMinutes(days:Int)=sessions().filter{it.endedAt>=System.currentTimeMillis()-days*86400000L}.sumOf{(it.durationMs/60000).toInt()}
    fun currentStreak():Int{val days=sessions().filter{it.completed&&it.durationMs>=60000}.map{fmt.format(Date(it.endedAt))}.toSet();var n=0;val c=Calendar.getInstance();while(days.contains(fmt.format(c.time))){n++;c.add(Calendar.DAY_OF_YEAR,-1)};return n}
    private fun saveSession(s:FocusSession){val a=JSONArray(prefs.getString("sessions","[]")?:"[]");a.put(JSONObject().apply{put("endedAt",s.endedAt);put("tag",s.tag);put("mode",s.mode);put("durationMs",s.durationMs);put("completed",s.completed);put("strict",s.strict);put("note",s.note);put("cycles",s.cycles)});while(a.length()>2000)a.remove(0);prefs.edit().putString("sessions",a.toString()).apply();ToneGenerator(AudioManager.STREAM_NOTIFICATION,80).startTone(ToneGenerator.TONE_PROP_ACK,180)}
    fun coachMessage():String{val t=todayMinutes();val g=dailyGoal();return when{t==0->"Start with one small focus block. Today's goal is $g minutes.";t>=g->"Excellent. You reached today's $g-minute focus goal.";t>=g/2->"Good progress: $t of $g minutes. Keep going.";else->"You've focused for $t minutes. A 25-minute block is a good next step."}}
    fun rescheduleActiveAlarm() {
        val s = state() ?: return
        if (s.mode == "STOPWATCH") return
        if (s.endsAt > System.currentTimeMillis()) FocusAlarmScheduler(context).schedule(s.endsAt)
    }

}
data class FocusSession(val endedAt:Long,val tag:String,val mode:String,val durationMs:Long,val completed:Boolean,val strict:Boolean,val note:String="",val cycles:Int=0)
