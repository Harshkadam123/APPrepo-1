package com.harsh.jarvis.fitness

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext

@Composable
fun FitnessScreen() {
    val context = LocalContext.current
    val repo = remember { FitnessRepository(context) }
    var profile by remember { mutableStateOf(repo.profile()) }
    var mode by remember { mutableStateOf(profile.mode) }
    var showSetup by remember { mutableStateOf(false) }
    var showSkills by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(setOf<String>()) }
    val plan = remember(mode) { FitnessCatalog.plan(mode) }

    if (showSkills) {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = { showSkills = false }) { Text("← Fitness") }
            SkillScreen()
        }
        return
    }

    if (showSetup) {
        FitnessSetupScreen(profile, { p ->
            repo.saveMode(p.mode); repo.saveGoal(p.goal); repo.saveMinutes(p.minutes); repo.saveTrainingDays(p.trainingDays); repo.saveEquipment(p.equipment)
            profile = repo.profile(); mode = profile.mode; showSetup = false
        }, { showSetup = false })
        return
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("HUNTER SYSTEM", style = MaterialTheme.typography.headlineSmall)
                    Text("Level ${profile.level}  •  ${profile.xp} XP")
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator((profile.xp % 1000) / 1000f, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("Goal: ${profile.goal}")
                    Text("Mode: ${profile.mode} • ${profile.minutes} min • ${profile.trainingDays} days/week")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Home", "Calisthenics", "Gym").forEach { option ->
                    FilterChip(selected = mode == option, onClick = { mode = option; repo.saveMode(option); profile = repo.profile() }, label = { Text(option) }, modifier = Modifier.weight(1f))
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(plan.title, style = MaterialTheme.typography.titleLarge)
                    Text("Today's quest • ${plan.exercises.size} exercises")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { active = !active; completed = emptySet() }, Modifier.fillMaxWidth()) { Text(if (active) "Restart Quest" else "Start Today's Quest") }
                }
            }
        }
        items(plan.exercises, key = { it.name }) { exercise ->
            val done = exercise.name in completed
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                            Text("${exercise.sets} sets × ${exercise.reps} • ${exercise.restSeconds}s rest")
                            Text("${exercise.category} • ${exercise.difficulty}", style = MaterialTheme.typography.bodySmall)
                        }
                        Checkbox(checked = done, onCheckedChange = { checked ->
                            completed = if (checked) completed + exercise.name else completed - exercise.name
                            if (checked) profile = repo.addExerciseXp(10)
                        })
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(exercise.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Quest rewards", style = MaterialTheme.typography.titleMedium)
                    Text("+10 XP per completed exercise • +100 XP when the workout is completed")
                    Text("Completed: ${completed.size}/${plan.exercises.size}")
                    if (completed.size == plan.exercises.size && active) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { profile = repo.completeWorkout(); active = false }) { Text("Claim +100 XP") }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { showSkills = true }, Modifier.fillMaxWidth()) { Text("⚔ Skill System & Rank Tree") }
        }
        item {
            OutlinedButton(onClick = { showSetup = true }, Modifier.fillMaxWidth()) { Text("Hunter Profile & Training Setup") }
        }
        item {
            Text("Safety: stop if you feel sharp pain, dizziness, chest pain, or unusual shortness of breath. Increase difficulty gradually and prioritize controlled technique and recovery.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FitnessSetupScreen(profile: FitnessProfile, onSave: (FitnessProfile) -> Unit, onCancel: () -> Unit) {
    var goal by remember { mutableStateOf(profile.goal) }
    var mode by remember { mutableStateOf(profile.mode) }
    var minutes by remember { mutableIntStateOf(profile.minutes) }
    var days by remember { mutableIntStateOf(profile.trainingDays) }
    var equipment by remember { mutableStateOf(profile.equipment) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Hunter Profile", style = MaterialTheme.typography.headlineSmall) }
        item { OutlinedTextField(goal, { goal = it }, Modifier.fillMaxWidth(), label = { Text("Goal") }) }
        item { Text("Training mode") }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Home", "Calisthenics", "Gym").forEach { x -> FilterChip(mode == x, { mode = x }, label = { Text(x) }) } } }
        item { Text("Session length: $minutes minutes") }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(20, 30, 45, 60).forEach { x -> FilterChip(minutes == x, { minutes = x }, label = { Text("$x") }) } } }
        item { Text("Training days/week: $days") }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { (2..6).forEach { x -> FilterChip(days == x, { days = x }, label = { Text("$x") }) } } }
        item { OutlinedTextField(equipment, { equipment = it }, Modifier.fillMaxWidth(), label = { Text("Available equipment") }) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onCancel, Modifier.weight(1f)) { Text("Cancel") }; Button({ onSave(profile.copy(goal = goal.ifBlank { "General fitness" }, mode = mode, minutes = minutes, trainingDays = days, equipment = equipment.ifBlank { "Bodyweight" })) }, Modifier.weight(1f)) { Text("Save") } } }
    }
}
