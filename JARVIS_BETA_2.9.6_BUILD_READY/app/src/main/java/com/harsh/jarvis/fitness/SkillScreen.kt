package com.harsh.jarvis.fitness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SkillScreen() {

    val context = LocalContext.current

    /*
     * SkillRepository is local-only and owns persistence.
     * Remembering it prevents a new repository from being created
     * on every Compose recomposition.
     */
    val repo = remember {
        SkillRepository(context)
    }

    /*
     * Incrementing refresh forces all repository-derived values
     * to be recalculated after an action is recorded.
     */
    var refresh by remember {
        mutableIntStateOf(0)
    }

    val progress = remember(refresh) {
        repo.allProgress()
    }

    /*
     * A mastered skill remains visible until its next skill
     * is actually unlocked.
     */
    val visibleSkills = remember(refresh) {

        SkillCatalog.skills.filterNot { skill ->

            val currentProgress =
                progress[skill.id] ?: SkillProgress()

            val nextSkill =
                skill.promotionId?.let {
                    SkillCatalog.get(it)
                }

            currentProgress.mastered &&
                nextSkill != null &&
                repo.isUnlocked(nextSkill)
        }
    }

    // ---------------------------------------------------------------------
    // INPUT STATE
    // ---------------------------------------------------------------------

    var swimmingMeters by remember {
        mutableIntStateOf(100)
    }

    var activityUnits by remember {
        mutableIntStateOf(1)
    }

    var difficulty by remember {
        mutableIntStateOf(1)
    }

    var activityNote by remember {
        mutableStateOf("")
    }

    var competitionName by remember {
        mutableStateOf("")
    }

    var competitionResult by remember {
        mutableStateOf("")
    }

    var competitionEvidence by remember {
        mutableStateOf("")
    }

    // ---------------------------------------------------------------------
    // SCREEN
    // ---------------------------------------------------------------------

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(12.dp)
    ) {

        // -----------------------------------------------------------------
        // HEADER
        // -----------------------------------------------------------------

        item {

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    Text(
                        text = "HUNTER SKILL SYSTEM",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Text(
                        text = "Every subject has an independent skill bar. Practice earns XP; level 10 means mastery."
                    )

                    Text(
                        text = "Ranks: F → E → D → C → B → A → S → SS → SSS → SSS+"
                    )

                    Text(
                        text = "A mastered basic skill pauses until its prerequisites are satisfied; then the next-rank skill becomes active."
                    )
                }
            }
        }

        // -----------------------------------------------------------------
        // QUICK VERIFIED ACTIVITY
        // -----------------------------------------------------------------

        item {

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Text(
                        text = "Quick verified activity",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Log only genuine practice, completed work, or verified achievements. JARVIS does not treat button presses as proof.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    // -----------------------------------------------------
                    // SWIMMING
                    // -----------------------------------------------------

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        OutlinedTextField(
                            value = swimmingMeters.toString(),
                            onValueChange = { value ->

                                swimmingMeters =
                                    value
                                        .toIntOrNull()
                                        ?.coerceAtLeast(0)
                                        ?: 0
                            },
                            modifier = Modifier.weight(1f),
                            label = {
                                Text("Swimming metres")
                            },
                            singleLine = true
                        )

                        Button(
                            onClick = {

                                if (swimmingMeters > 0) {
                                    repo.recordSwimmingMeters(
                                        swimmingMeters
                                    )

                                    refresh++
                                }
                            }
                        ) {
                            Text("Log")
                        }
                    }

                    Text(
                        text = "Rule: every completed 100 m = +1 Swimming level. JARVIS carries any leftover metres to the next log.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "Current carried distance: ${repo.swimmingRemainderMeters()} m",
                        style = MaterialTheme.typography.bodySmall
                    )

                    // -----------------------------------------------------
                    // DATA CLEANING
                    // -----------------------------------------------------

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        OutlinedTextField(
                            value = activityUnits.toString(),
                            onValueChange = { value ->

                                activityUnits =
                                    value
                                        .toIntOrNull()
                                        ?.coerceAtLeast(0)
                                        ?: 0
                            },
                            modifier = Modifier.weight(1f),
                            label = {
                                Text("Verified work units")
                            },
                            singleLine = true
                        )

                        Button(
                            onClick = {

                                if (activityUnits > 0) {

                                    repo.recordDatasetCleaned(
                                        activityUnits
                                    )

                                    refresh++
                                }
                            }
                        ) {
                            Text("Data")
                        }
                    }

                    Text(
                        text = "Data Cleaning: 1 verified work unit = +10 XP. For example, a 15–30 row/record cleaning task can be logged as a real dataset-work unit.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // -----------------------------------------------------------------
        // SKILLS
        // -----------------------------------------------------------------

        items(
            items = visibleSkills,
            key = { skill ->
                skill.id
            }
        ) { skill ->

            val currentProgress =
                progress[skill.id] ?: SkillProgress()

            val unlocked =
                repo.isUnlocked(skill)

            val prerequisiteText =
                if (skill.prerequisites.isEmpty()) {

                    "No prerequisites"

                } else {

                    skill.prerequisites.joinToString {

                        val prerequisiteSkill =
                            SkillCatalog.get(it.skillId)

                        "${prerequisiteSkill?.name ?: it.skillId} Lv ${it.minLevel}"
                    }
                }

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    // -----------------------------------------------------
                    // SKILL TITLE
                    // -----------------------------------------------------

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {

                            Text(
                                text =
                                    "${skill.rank.label}-RANK • ${skill.name}",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text(
                                text =
                                    "${skill.category} • ${skill.description}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Text(
                            text = when {

                                !unlocked ->
                                    "LOCKED"

                                currentProgress.mastered ->
                                    "MASTERED"

                                else ->
                                    "LV ${currentProgress.level}"
                            }
                        )
                    }

                    // -----------------------------------------------------
                    // PROGRESS BAR
                    // -----------------------------------------------------

                    LinearProgressIndicator(
                        progress = {
                            if (currentProgress.mastered) {
                                1f
                            } else {
                                currentProgress.progress
                                    .coerceIn(0f, 1f)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // -----------------------------------------------------
                    // LEVEL / XP
                    // -----------------------------------------------------

                    Text(
                        text =
                            if (currentProgress.mastered) {

                                "Max level reached. This skill is paused."

                            } else {

                                "Level ${currentProgress.level}/${skill.maxLevel} • " +
                                    "XP ${currentProgress.xp}/${skill.maxLevel * skill.xpPerLevel}"
                            }
                    )

                    Text(
                        text = "Practice unit: ${skill.activityUnit}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "Prerequisites: $prerequisiteText",
                        style = MaterialTheme.typography.bodySmall
                    )

                    // -----------------------------------------------------
                    // PROMOTION
                    // -----------------------------------------------------

                    if (
                        currentProgress.mastered &&
                        skill.promotionId != null
                    ) {

                        val nextSkill =
                            SkillCatalog.get(skill.promotionId)

                        Text(
                            text =
                                "Next: " +
                                    "${nextSkill?.rank?.label ?: "?"}-rank " +
                                    "${nextSkill?.name ?: skill.promotionId}"
                        )

                        Text(
                            text = skill.promotionMessage,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // -----------------------------------------------------
                    // NORMAL SKILL ACTIVITY
                    // -----------------------------------------------------

                    if (
                        unlocked &&
                        !currentProgress.mastered &&
                        skill.id != "swimming" &&
                        skill.id != "data_cleaning" &&
                        skill.id != "competition_wins"
                    ) {

                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(8.dp),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            OutlinedTextField(
                                value = difficulty.toString(),
                                onValueChange = { value ->

                                    difficulty =
                                        value
                                            .toIntOrNull()
                                            ?.coerceIn(1, 5)
                                            ?: 1
                                },
                                label = {
                                    Text("Difficulty 1–5")
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            Button(
                                onClick = {

                                    repo.recordSkillWork(
                                        skill.id,
                                        1,
                                        difficulty.coerceIn(1, 5),
                                        activityNote.trim()
                                    )

                                    refresh++
                                }
                            ) {
                                Text("Log completed activity")
                            }
                        }

                        OutlinedTextField(
                            value = activityNote,
                            onValueChange = {
                                activityNote = it
                            },
                            label = {
                                Text(
                                    "What did you complete? (optional)"
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Text(
                            text = "Difficulty changes XP; it is a self-reported practice log, not independent verification.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // -----------------------------------------------------
                    // COMPETITION ACHIEVEMENT
                    // -----------------------------------------------------

                    if (
                        unlocked &&
                        !currentProgress.mastered &&
                        skill.id == "competition_wins"
                    ) {

                        OutlinedTextField(
                            value = competitionName,
                            onValueChange = {
                                competitionName = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Competition name")
                            },
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = competitionResult,
                            onValueChange = {
                                competitionResult = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Result / rank")
                            },
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = competitionEvidence,
                            onValueChange = {
                                competitionEvidence = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Evidence URL or reference")
                            },
                            singleLine = true
                        )

                        Button(
                            onClick = {

                                /*
                                 * IMPORTANT:
                                 *
                                 * Use NAMED arguments for all three values.
                                 * This prevents the exact kind of parameter
                                 * mismatch that appeared in the GitHub build.
                                 *
                                 * evidence is explicitly supplied.
                                 */

                                val cleanCompetition =
                                    competitionName.trim()

                                val cleanResult =
                                    competitionResult.trim()

                                val cleanEvidence =
                                    competitionEvidence.trim()

                                if (
                                    cleanCompetition.isNotBlank() &&
                                    cleanResult.isNotBlank() &&
                                    cleanEvidence.isNotBlank()
                                ) {

                                    val saved =
                                        repo.recordCompetitionAchievement(
                                            competition = cleanCompetition,
                                            result = cleanResult,
                                            evidence = cleanEvidence
                                        )

                                    if (saved != null) {

                                        competitionName = ""
                                        competitionResult = ""
                                        competitionEvidence = ""

                                        refresh++
                                    }
                                }
                            },
                            enabled =
                                competitionName.isNotBlank() &&
                                    competitionResult.isNotBlank() &&
                                    competitionEvidence.isNotBlank()
                        ) {
                            Text("Record competition achievement")
                        }

                        Text(
                            text = "All three fields are required. JARVIS stores the evidence you provide; it does not independently verify the result.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // -----------------------------------------------------------------
        // RESET
        // -----------------------------------------------------------------

        item {

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            OutlinedButton(
                onClick = {

                    repo.resetAll()

                    // Reset local UI state as well.
                    swimmingMeters = 100
                    activityUnits = 1
                    difficulty = 1
                    activityNote = ""
                    competitionName = ""
                    competitionResult = ""
                    competitionEvidence = ""

                    refresh++
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset Skill Progress")
            }
        }
    }
}
