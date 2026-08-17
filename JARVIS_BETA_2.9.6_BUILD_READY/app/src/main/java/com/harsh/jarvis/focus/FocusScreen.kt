package com.harsh.jarvis.focus

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun FocusScreen() {
    val context = LocalContext.current
    val manager = remember { FocusManager(context) }
    val stats = remember { FocusStats(context) }
    val usage = remember { UsageStatsReader(context) }
    val planner = remember { FocusPlannerStore(context) }
    val web = remember { WebsiteStudyMode(context) }
    val limits = remember { DigitalLimits(context) }
    val guard = remember { DistractionContentGuard(context) }
    val sound = remember { FocusSoundEngine() }
    var state by remember { mutableStateOf(manager.state()) }
    var tab by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(25) }
    var shortBreak by remember { mutableIntStateOf(5) }
    var longBreak by remember { mutableIntStateOf(15) }
    var cycles by remember { mutableIntStateOf(4) }
    var strict by remember { mutableStateOf(false) }
    var tag by remember { mutableStateOf("Study") }
    var note by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf(usage.today()) }
    var blockList by remember { mutableStateOf(manager.blockList()) }
    var plans by remember { mutableStateOf(planner.all()) }
    var soundOn by remember { mutableStateOf(false) }
    var goal by remember { mutableIntStateOf(manager.dailyGoal()) }
    var webAllowed by remember { mutableStateOf(web.allowed()) }
    var webBlocked by remember { mutableStateOf(web.blocked()) }
    var url by remember { mutableStateOf("") }
    var limitPackage by remember { mutableStateOf("") }
    var limitMinutes by remember { mutableStateOf("60") }
    var ytChannels by remember { mutableStateOf(web.youtubeAllowedChannels()) }
    var ytChannel by remember { mutableStateOf("") }
    var planTitle by remember { mutableStateOf("") }
    var planDay by remember { mutableStateOf("Mon") }
    var planTime by remember { mutableStateOf("480") }
    var planDuration by remember { mutableStateOf("25") }

    val installedApps = remember {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }
            .distinctBy { it.first }.filter { it.first != context.packageName }.sortedBy { it.second.lowercase() }
    }

    DisposableEffect(Unit) { onDispose { sound.stop() } }
    LaunchedEffect(state?.active) {
        while (state?.active == true) {
            delay(1000)
            val current = manager.state()
            if (current != null && current.mode != "STOPWATCH" && current.remaining() == 0L) {
                if (current.mode == "POMODORO" && manager.advancePomodoro()) state = manager.state()
                else { manager.stop(true); state = null }
            } else state = current
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("JARVIS Focus Center", style = MaterialTheme.typography.headlineSmall)
        Text("Focus timer, blocking, screen-time insights, planning, streaks and local AI coaching.")
        Spacer(Modifier.height(8.dp))
        ScrollableTabRow(selectedTabIndex = tab) {
            listOf("Focus","Stats","Blocker","Planner","Coach","Rooms").forEachIndexed { i, label ->
                Tab(tab == i, { tab = i }, text = { Text(label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            0 -> FocusTab(state, manager, minutes, { minutes = it }, shortBreak, { shortBreak = it }, longBreak, { longBreak = it }, cycles, { cycles = it }, strict, { strict = it }, tag, { tag = it }, note, { note = it }, soundOn, { soundOn = sound.toggle() }, sound, { m, s, l, c, st -> manager.start(m,"POMODORO",st,tag,c,s,l,note); state = manager.state() }, { manager.start(25,"TIMER",false,"Study"); state=manager.state() }, { manager.start(1,"STOPWATCH",false,tag); state=manager.state() })
            1 -> {
                Button({ apps = usage.today() }) { Text("Refresh screen-time") }
                Text("Focused today: ${manager.todayMinutes()} min / $goal min goal")
                Text("7-day focus: ${manager.totalMinutes(7)} min • Streak: ${manager.currentStreak()} days")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    items(apps) { a -> Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(10.dp)) { Text(a.label, Modifier.weight(1f)); Text("${a.minutes} min") } } }
                }
                Text("Achievements", style = MaterialTheme.typography.titleMedium)
                stats.achievements().take(4).forEach { Text("${if (it.unlocked) "✓" else "○"} ${it.title} — ${it.description}") }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    OutlinedTextField(goal.toString(), { it.toIntOrNull()?.let { v -> goal=v.coerceIn(1,1440); manager.setDailyGoal(goal) } }, label={Text("Daily focus goal (min)")}, modifier=Modifier.weight(1f))
                }
            }
            2 -> {
                Text("Distraction blocking", style = MaterialTheme.typography.titleLarge)
                Text("Adult protection stays local and has no JARVIS-side disable/pause control. Accessibility can be enabled in Android Settings.")
                Button({ context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Open Accessibility settings") }
                Text("Selected app blocks: ${blockList.size}")
                LazyColumn(modifier=Modifier.heightIn(max=240.dp), verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    items(installedApps) { app ->
                        val checked=app.first in blockList
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                            Checkbox(checked,{v->blockList=if(v)blockList+app.first else blockList-app.first;manager.setBlockList(blockList)})
                            Text(app.second,Modifier.weight(1f)); Text(app.first,style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically){
                    Checkbox(guard.reelsShortsEnabled(),{guard.setReelsShorts(it)}); Text("Block Reels / Shorts / Spotlight")
                }
                Row(verticalAlignment=Alignment.CenterVertically){
                    Checkbox(guard.youtubeStudyMode(),{guard.setYoutubeStudyMode(it)}); Text("YouTube Study Mode")
                }
                Text("YouTube allowed channels: ${ytChannels.joinToString().ifBlank { "none configured" }}")
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    OutlinedTextField(ytChannel,{ytChannel=it},label={Text("Channel URL/name")},modifier=Modifier.weight(1f))
                    Button({if(ytChannel.isNotBlank()){ytChannels=ytChannels+ytChannel.trim();web.setYoutubeAllowedChannels(ytChannels);ytChannel=""}}){Text("Add")}
                }
                Text("Browser study allow-list")
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    OutlinedTextField(url,{url=it},label={Text("https://domain")},modifier=Modifier.weight(1f))
                    Button({if(web.canOpen(url))context.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)))}){Text("Open")}
                }
                Text("Allowed: ${webAllowed.joinToString()}")
                Text("Blocked: ${webBlocked.joinToString().ifBlank{"none"}}")
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    Button({val host=android.net.Uri.parse(url).host?.lowercase()?.removePrefix("www.");if(!host.isNullOrBlank()){webBlocked=webBlocked+host;web.setBlocked(webBlocked)}}){Text("Block domain")}
                    Button({val host=android.net.Uri.parse(url).host?.lowercase()?.removePrefix("www.");if(!host.isNullOrBlank()){webAllowed=webAllowed+host;web.setAllowed(webAllowed)}}){Text("Allow domain")}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    OutlinedTextField(limitPackage,{limitPackage=it},label={Text("App package")},modifier=Modifier.weight(1f))
                    OutlinedTextField(limitMinutes,{limitMinutes=it},label={Text("Daily min")},modifier=Modifier.weight(1f))
                    Button({limitMinutes.toIntOrNull()?.let{limits.set(limitPackage,it)}}){Text("Set")}
                }
                Text("Daily limits: ${limits.all().entries.joinToString { "${it.key}=${it.value}m" }.ifBlank{"none"}}")
            }
            3 -> {
                Text("Focus Planner", style = MaterialTheme.typography.titleLarge)
                Text("Create recurring weekly focus blocks. These remain available offline.")
                OutlinedTextField(planTitle,{planTitle=it},label={Text("Block title")},modifier=Modifier.fillMaxWidth())
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    OutlinedTextField(planDay,{planDay=it},label={Text("Day")},modifier=Modifier.weight(1f))
                    OutlinedTextField(planTime,{planTime=it},label={Text("Start minute")},modifier=Modifier.weight(1f))
                    OutlinedTextField(planDuration,{planDuration=it},label={Text("Duration")},modifier=Modifier.weight(1f))
                }
                Button({if(planTitle.isNotBlank()){planner.add(planTitle,planDay,planTime.toIntOrNull()?:480,planDuration.toIntOrNull()?:25,tag);plans=planner.all();planTitle=""}}){Text("Add recurring block")}
                LazyColumn(modifier=Modifier.weight(1f)){items(plans){p->Row(Modifier.fillMaxWidth().padding(4.dp)){Text("${p.day} ${p.startMinutes} • ${p.title} • ${p.durationMinutes}m",Modifier.weight(1f));TextButton({planner.delete(p.id);plans=planner.all()}){Text("Delete")}}}}
            }
            4 -> {
                Text("JARVIS Focus Coach", style = MaterialTheme.typography.titleLarge)
                Text(manager.coachMessage())
                Text("The coach is local and uses only your focus history; it does not upload your screen-time data.")
                Spacer(Modifier.height(8.dp))
                Button({manager.start(25,"TIMER",false,"Study");state=manager.state();tab=0}){Text("Start a 25-minute recommendation")}
                Text("Today: ${manager.todayMinutes()} min • 7 days: ${manager.totalMinutes(7)} min • streak: ${manager.currentStreak()} days")
            }
            5 -> {
                Text("Focus Rooms", style = MaterialTheme.typography.titleLarge)
                Text("Local room mode is fully offline. Live worldwide rooms and leaderboards require a server/account layer; this build keeps the room protocol isolated so private focus history is never silently uploaded.")
                var room by remember { mutableStateOf("Study Room") }
                OutlinedTextField(room,{room=it},label={Text("Room name")},modifier=Modifier.fillMaxWidth())
                Button({manager.start(25,"ROOM",false,room);state=manager.state();tab=0}){Text("Start room focus")}
                Text("Local leaderboard: ${manager.sessions().groupBy{it.tag}.entries.sortedByDescending{e->e.value.sumOf{(it.durationMs/60000).toInt()}}.take(5).joinToString{ "${it.key}: ${it.value.sumOf{(it.durationMs/60000).toInt()}}m" }.ifBlank{"no sessions yet"}}")
            }
        }
    }
}

@Composable
private fun FocusTab(
    state: FocusManager.State?, manager: FocusManager, minutes:Int,setMinutes:(Int)->Unit,short:Int,setShort:(Int)->Unit,long:Int,setLong:(Int)->Unit,cycles:Int,setCycles:(Int)->Unit,strict:Boolean,setStrict:(Boolean)->Unit,tag:String,setTag:(String)->Unit,note:String,setNote:(String)->Unit,soundOn:Boolean,toggleSound:()->Unit,sound:FocusSoundEngine,startPomodoro:(Int,Int,Int,Int,Boolean)->Unit,startTimer:()->Unit,startStopwatch:()->Unit
){
    if(state==null){
        Text("Quick presets",style=MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){Button({startPomodoro(25,5,15,4,false)}){Text("Study 25/5")};Button({startPomodoro(50,10,20,4,false)}){Text("Deep 50/10")};Button({startPomodoro(45,10,20,4,true)}){Text("Exam Strict")}}
        OutlinedTextField(minutes.toString(),{it.toIntOrNull()?.let{v->setMinutes(v.coerceIn(1,720))}},label={Text("Focus minutes")},modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedTextField(short.toString(),{it.toIntOrNull()?.let{v->setShort(v.coerceIn(1,60))}},label={Text("Short break")},modifier=Modifier.weight(1f));OutlinedTextField(long.toString(),{it.toIntOrNull()?.let{v->setLong(v.coerceIn(1,120))}},label={Text("Long break")},modifier=Modifier.weight(1f));OutlinedTextField(cycles.toString(),{it.toIntOrNull()?.let{v->setCycles(v.coerceIn(1,20))}},label={Text("Cycles")},modifier=Modifier.weight(1f))}
        OutlinedTextField(tag,{setTag(it)},label={Text("Subject / tag")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(note,{setNote(it)},label={Text("Optional session note")},modifier=Modifier.fillMaxWidth())
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(strict,{setStrict(it)});Text("Strict mode — no in-app early exit or pause")}
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){Button({startPomodoro(minutes,short,long,cycles,strict)}){Text("Start Pomodoro")};OutlinedButton({startTimer()}){Text("Timer")};OutlinedButton({startStopwatch()}){Text("Stopwatch")}}
        OutlinedButton({toggleSound()}){Text(if(soundOn)"Stop focus sound" else "Play local focus noise")}
    }else{
        val shown=if(state.mode=="STOPWATCH")state.elapsed() else state.remaining(); Text("${shown/60000}:${((shown%60000)/1000).toString().padStart(2,'0')}",style=MaterialTheme.typography.displayMedium)
        Text("${state.tag} • ${state.mode} • ${state.phase}${if(state.strict)" • STRICT" else ""}")
        if(state.mode=="POMODORO")Text("Cycle ${state.completedCycles}/${state.cycles}")
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){if(!state.strict){if(state.pausedAt==0L)Button({manager.pause()}){Text("Pause")}else Button({manager.resume()}){Text("Resume")}};OutlinedButton(enabled=!state.strict,onClick={manager.stop(false)}){Text("End")}}
        Text("Strict sessions intentionally cannot be ended early by JARVIS. Android system-level controls remain outside the app's authority.")
    }
}
