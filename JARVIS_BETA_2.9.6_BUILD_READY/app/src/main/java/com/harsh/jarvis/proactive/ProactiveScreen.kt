package com.harsh.jarvis.proactive

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.harsh.jarvis.security.NotificationSupport
import java.time.LocalDateTime

@Composable
fun ProactiveScreen(engine: ProactiveEngine) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<ProactiveSnapshot?>(null) }
    var quiet by remember { mutableStateOf(false) }
    val prefs = remember { ProactivePreferences(context) }
    var morningEnabled by remember { mutableStateOf(prefs.morningEnabled) }
    var eveningEnabled by remember { mutableStateOf(prefs.eveningEnabled) }
    LaunchedEffect(Unit) { snapshot = engine.snapshot() }
    val s = snapshot
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text("TODAY", style = MaterialTheme.typography.headlineSmall)
            Text("Current state: ${s?.availabilityLabel ?: "..."}")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { snapshot = engine.snapshot() } }) { Text("Refresh") }
                OutlinedButton(onClick = { quiet = !quiet; scope.launch { engine.clearAvailability(); if (quiet) engine.setAvailability(AvailabilityState.BUSY, 0, 1440, label = "BUSY / protected") } }) { Text(if (quiet) "Protected" else "Protect day") }
            }
        }
        item { Text("IMPORTANT", style = MaterialTheme.typography.titleMedium) }
        items(s?.important.orEmpty(), key = { it.id }) { event ->
            EventCard(event, engine) { scope.launch { snapshot = engine.snapshot() } }
        }
        item { Text("SCHEDULE", style = MaterialTheme.typography.titleMedium) }
        items(s?.schedule.orEmpty(), key = { it.id }) { block -> Text("${java.time.Instant.ofEpochMilli(block.startTime).atZone(java.time.ZoneId.systemDefault()).toLocalTime()} — ${block.title}") }
        item {
            Text("EVOLUTION", style = MaterialTheme.typography.titleMedium)
            Text("Level ${s?.evolutionLevel ?: 0} • ${s?.evolutionXp ?: 0} XP • +${s?.xpToday ?: 0} XP today")
        }
        item {
            Text("RECOMMENDED", style = MaterialTheme.typography.titleMedium)
            Text(s?.recommendation?.title ?: "Nothing urgent. Choose the highest-value useful work.")
        }
        item {
            Text("AVAILABILITY", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(AvailabilityState.FREE, AvailabilityState.STUDY, AvailabilityState.CLASS, AvailabilityState.EXERCISE, AvailabilityState.MEETING).forEach { state ->
                    OutlinedButton(onClick = { scope.launch { engine.clearAvailability(); val now = LocalDateTime.now(); engine.setAvailability(state, now.hour * 60 + now.minute, (now.hour * 60 + now.minute + 60).coerceAtMost(1440), label = state.name); snapshot = engine.snapshot() } }) { Text(state.name.take(5)) }
                }
            }
        }
        item {
            Text("DAILY INTELLIGENCE", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { ProactiveScheduler.schedule(context); snapshot = engine.snapshot() } }) { Text("Schedule check") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Morning briefing")
                Switch(checked = morningEnabled, onCheckedChange = { morningEnabled = it; prefs.morningEnabled = it })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Evening review")
                Switch(checked = eveningEnabled, onCheckedChange = { eveningEnabled = it; prefs.eveningEnabled = it })
            }
            Text("Data sources: local tasks + Evolution. Calendar is used only when explicitly authorized in Privacy and Android permissions. No messages, photos, files, banking data or private conversations are accessed by this engine.")
            Text("Proactive logic is local-first. Notifications respect availability, snooze, dismissal and deduplication.")
            if (!NotificationSupport.isExactAlarmAllowed(context)) {
                Text("Exact alarm access is off. Daily 8 AM/8 PM timing may be delayed.")
                OutlinedButton(onClick = { NotificationSupport.exactAlarmSettingsIntent(context)?.let { context.startActivity(it) } }) {
                    Text("Allow exact alarm timing")
                }
            }
            if (!NotificationSupport.canNotify(context)) {
                Text("Notifications are disabled. Proactive and task alerts will not appear until notifications are enabled.")
            }
        }
    }
}

@Composable
private fun EventCard(event: ProactiveEvent, engine: ProactiveEngine, refresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("${event.priority} • ${event.title}", style = MaterialTheme.typography.titleMedium)
            if (event.detail.isNotBlank()) Text(event.detail)
            event.deadline?.let { Text("Due: ${java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()}") }
            Text("Source: ${event.source}")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { scope.launch { engine.complete(event.id); refresh() } }) { Text("Done") }
                OutlinedButton(onClick = { scope.launch { engine.snooze(event.id, System.currentTimeMillis() + 60 * 60_000L); refresh() } }) { Text("1h") }
                OutlinedButton(onClick = { scope.launch { engine.dismiss(event.id); refresh() } }) { Text("Dismiss") }
                OutlinedButton(onClick = { scope.launch { engine.dismiss(event.id, forever = true); refresh() } }) { Text("Never") }
                OutlinedButton(onClick = { scope.launch { engine.reschedule(event.id, System.currentTimeMillis() + 2 * 60 * 60_000L); refresh() } }) { Text("+2h") }
                OutlinedButton(onClick = { scope.launch { val next = when (event.priority) { ProactivePriority.LOW.name -> ProactivePriority.MEDIUM; ProactivePriority.MEDIUM.name -> ProactivePriority.HIGH; else -> ProactivePriority.LOW }; engine.changePriority(event.id, next); refresh() } }) { Text("Priority") }
            }
        }
    }
}
