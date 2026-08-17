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
fun SkillScreen() {
    val context = LocalContext.current
    val repo = remember { SkillRepository(context) }
    var refresh by remember { mutableIntStateOf(0) }
    val progress = remember(refresh) { repo.allProgress() }

    // A mastered skill disappears only when its next skill is actually unlocked.
    val visibleSkills = remember(refresh) {
        SkillCatalog.skills.filterNot { skill ->
            val p = progress[skill.id] ?: SkillProgress()
            val next = skill.promotionId?.let { SkillCatalog.get(it) }
            p.mastered && next != null && repo.isUnlocked(next)
        }
    }

    var swimmingMeters by remember { mutableIntStateOf(100) }
    var activityUnits by remember { mutableIntStateOf(1) }
    var difficulty by remember { mutableIntStateOf(1) }
    var activityNote by remember { mutableStateOf("") }
    var competitionName by remember { mutableStateOf("") }
    var competitionResult by remember { mutableStateOf("") }
    var competitionEvidence by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("HUNTER SKILL SYSTEM", style = MaterialTheme.typography.headlineSmall)
                    Text("Every subject has an independent skill bar. Practice earns XP; level 10 means mastery.")
                    Text("Ranks: F → E → D → C → B → A → S → SS → SSS → SSS+")
                    Text("A mastered basic skill pauses until its prerequisites are satisfied; then the next-rank skill becomes active.")
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quick verified activity", style = MaterialTheme.typography.titleMedium)
                    Text("Log only genuine practice, completed work, or verified achievements. JARVIS does not treat button presses as proof.", style = MaterialTheme.typography.bodySmall)

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            swimmingMeters.toString(),
                            { swimmingMeters = it.toIntOrNull()?.coerceAtLeast(0) ?: 0 },
                            Modifier.weight(1f),
                            label = { Text("Swimming metres") }
                        )
                        Button(onClick = { repo.recordSwimmingMeters(swimmingMeters); refresh++ }) { Text("Log") }
                    }
                    Text("Rule: every completed 100 m = +1 Swimming level. JARVIS carries any leftover metres to the next log.", style = MaterialTheme.typography.bodySmall)
                    Text("Current carried distance: ${repo.swimmingRemainderMeters()} m", style = MaterialTheme.typography.bodySmall)

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            activityUnits.toString(),
                            { activityUnits = it.toIntOrNull()?.coerceAtLeast(0) ?: 0 },
                            Modifier.weight(1f),
                            label = { Text("Verified work units") }
                        )
                        Button(onClick = { repo.recordDatasetCleaned(activityUnits); refresh++ }) { Text("Data") }
                    }
                    Text("Data Cleaning: 1 verified work unit = +10 XP. For example, a 15–30 row/record cleaning task can be logged as a real dataset-work unit.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        items(visibleSkills) { skill ->
            val p = progress[skill.id] ?: SkillProgress()
            val unlocked = repo.isUnlocked(skill)
            val prereqText = if (skill.prerequisites.isEmpty()) {
                "No prerequisites"
            } else {
                skill.prerequisites.joinToString {
                    "${SkillCatalog.get(it.skillId)?.name ?: it.skillId} Lv ${it.minLevel}"
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${skill.rank.label}-RANK • ${skill.name}", style = MaterialTheme.typography.titleMedium)
                            Text("${skill.category} • ${skill.description}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            if (!unlocked) "LOCKED"
                            else if (p.mastered) "MASTERED"
                            else "LV ${p.level}"
                        )
                    }

                    LinearProgressIndicator(
                        if (p.mastered) 1f else p.progress,
                        Modifier.fillMaxWidth()
                    )

                    Text(
                        if (p.mastered) "Max level reached. This skill is paused."
                        else "Level ${p.level}/${skill.maxLevel} • XP ${p.xp}/${skill.maxLevel * skill.xpPerLevel}"
                    )
                    Text("Practice unit: ${skill.activityUnit}", style = MaterialTheme.typography.bodySmall)
                    Text("Prerequisites: $prereqText", style = MaterialTheme.typography.bodySmall)

                    if (p.mastered && skill.promotionId != null) {
                        val next = SkillCatalog.get(skill.promotionId)
                        Text("Next: ${next?.rank?.label ?: "?"}-rank ${next?.name ?: skill.promotionId}")
                        Text(skill.promotionMessage, style = MaterialTheme.typography.bodySmall)
                    }

                    if (unlocked && !p.mastered && skill.id != "swimming" && skill.id != "data_cleaning" && skill.id != "competition_wins") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = difficulty.toString(),
                                onValueChange = { difficulty = it.toIntOrNull()?.coerceIn(1, 5) ?: 1 },
                                label = { Text("Difficulty 1–5") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Button(onClick = { repo.recordSkillWork(skill.id, 1, difficulty, activityNote); refresh++ }) {
                                Text("Log completed activity")
                            }
                        }
                        OutlinedTextField(
                            value = activityNote,
                            onValueChange = { activityNote = it },
                            label = { Text("What did you complete? (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Text("Difficulty changes XP; it is a self-reported practice log, not independent verification.", style = MaterialTheme.typography.bodySmall)
                    }

                    if (unlocked && !p.mastered && skill.id == "competition_wins") {
                        OutlinedTextField(competitionName, { competitionName = it }, Modifier.fillMaxWidth(), label = { Text("Competition name") }, singleLine = true)
                        OutlinedTextField(competitionResult, { competitionResult = it }, Modifier.fillMaxWidth(), label = { Text("Result / rank") }, singleLine = true)
                        OutlinedTextField(competitionEvidence, { competitionEvidence = it }, Modifier.fillMaxWidth(), label = { Text("Evidence URL or reference") }, singleLine = true)
                        Button(onClick = {
                            val saved = repo.recordCompetitionAchievement(competitionName, competitionResult, competitionEvidence)
                            if (saved != null) refresh++
                        }) { Text("Record competition achievement") }
                        Text("JARVIS stores the evidence you provide; it does not independently verify the result.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { repo.resetAll(); refresh++ },
                Modifier.fillMaxWidth()
            ) { Text("Reset Skill Progress") }
        }
    }
}
