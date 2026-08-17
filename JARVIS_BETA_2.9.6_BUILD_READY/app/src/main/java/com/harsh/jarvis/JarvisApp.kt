package com.harsh.jarvis

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.harsh.jarvis.actions.ActionExecutor
import com.harsh.jarvis.ai.JarvisBrain
import com.harsh.jarvis.memory.MemoryRepository
import com.harsh.jarvis.history.ActionHistoryRepository
import com.harsh.jarvis.security.ConfirmationManager
import com.harsh.jarvis.security.PermissionManager
import com.harsh.jarvis.privacy.PrivacyCapability
import com.harsh.jarvis.privacy.PrivacyMode
import com.harsh.jarvis.privacy.PrivacyPolicyStore
import com.harsh.jarvis.privacy.PrivacyGateway
import com.harsh.jarvis.privacy.PrivacyAuditRepository
import com.harsh.jarvis.tasks.JarvisDatabase
import com.harsh.jarvis.tasks.JarvisViewModel
import com.harsh.jarvis.tools.ToolRegistry
import com.harsh.jarvis.voice.SpeechManager
import com.harsh.jarvis.voice.TtsManager
import com.harsh.jarvis.evolution.EvolutionRepository
import com.harsh.jarvis.evolution.EvolutionQuest
import com.harsh.jarvis.evolution.EvolutionSkill
import com.harsh.jarvis.evolution.EvolutionGoal
import com.harsh.jarvis.proactive.ProactiveEngine
import com.harsh.jarvis.proactive.ProactiveScreen
import com.harsh.jarvis.proactive.ProactiveScheduler
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import com.harsh.jarvis.fitness.FitnessScreen
import com.harsh.jarvis.ai.QwenModelManager
import com.harsh.jarvis.ai.LocalQwenBrain
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisApp(vm: JarvisViewModel = viewModel()) {
    val context = LocalContext.current
    val speech = remember { SpeechManager(context) }
    val tts = remember { TtsManager(context) }
    val permissions = remember { PermissionManager(context) }
    val database = JarvisDatabase.get(context)
    val privacyPolicy = remember { PrivacyPolicyStore(context) }
    val privacyAudit = remember { PrivacyAuditRepository(database.privacyAuditDao()) }
    val privacy = remember { PrivacyGateway(privacyPolicy, privacyAudit) }
    val confirmation = remember { ConfirmationManager() }
    val tools = remember { ToolRegistry(context, vm, permissions, privacy) }
    val executor = remember { ActionExecutor(confirmation, permissions) }
    val history = remember { ActionHistoryRepository(database.actionHistoryDao()) }
    val profile = remember { com.harsh.jarvis.ai.UserProfile(context) }
    val routines = remember { com.harsh.jarvis.ai.RoutineStore(context) }
    val personalData = remember { com.harsh.jarvis.tools.PersonalDataTools(context, privacy) }
    val evolution = remember { EvolutionRepository(database.evolutionDao()) }
    val proactive = remember { ProactiveEngine(database.proactiveDao(), database.taskDao(), evolution, personalData) }
    val qwenManager = remember { QwenModelManager(context) }
    val qwenBrain = remember { LocalQwenBrain(qwenManager) }
    val qwenStatus by qwenBrain.status.collectAsState()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        evolution.ensureProfile()
        ProactiveScheduler.schedule(context)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    DisposableEffect(Unit) { onDispose { qwenManager.close() } }
    val brain = remember { JarvisBrain(MemoryRepository(database.memoryDao()), tools, executor, history, privacy, model = com.harsh.jarvis.ai.PersonalIntentModel(context), personalData = personalData, profile = profile, routines = routines, evolution = evolution, proactive = proactive, qwen = qwenBrain) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("JARVIS 2.9.6 is ready. Qwen3 1.7B is the local generative brain when connected.") }
    var confidence by remember { mutableDoubleStateOf(0.0) }
    var listening by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<String?>(null) }
    var apps by remember { mutableStateOf(tools.availableApps()) }

    fun startSpeech() {
        if (privacy.isBlocked(PrivacyCapability.MICROPHONE)) {
            response = "Microphone access is blocked by your Privacy policy."
            return
        }
        scope.launch {
            privacy.record(PrivacyCapability.MICROPHONE, "User explicitly started a voice command", "Live microphone input; raw audio is not persisted by JARVIS", "STARTED")
        }
        listening = true
        speech.startListening(
            onResult = { listening = false; text = it; process(it) },
            onError = { listening = false; response = it }
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startSpeech() else response = "Microphone access was denied. JARVIS did not record voice input."
    }

    var deferredCommand by remember { mutableStateOf<String?>(null) }
    var deferredPermissionCheck by remember { mutableStateOf<List<String>>(emptyList()) }
    val personalPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val command = deferredCommand
        deferredCommand = null
        deferredPermissionCheck = emptyList()
        if (command != null) {
            scope.launch {
                val result = brain.process(command)
                response = result.text
                confidence = result.confidence
                pending = brain.pendingDescription()
                tts.speak(result.text)
                text = ""
                processing = false
            }
        }
    }

    val qwenModelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            response = "Importing Qwen3 model into JARVIS private storage… This can take a while for a large GGUF."
            runCatching { qwenManager.importFromUri(uri); qwenManager.load() }
                .onSuccess { response = "Qwen3 1.7B brain is connected. JARVIS is now using it for conversation and reasoning." }
                .onFailure { response = "Qwen3 model setup failed: ${it.message ?: "unknown error"}" }
        }
    }
    val qwenFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            response = "Searching the selected folder for Qwen3…"
            runCatching { qwenManager.rememberModelFolder(uri); qwenManager.load() }
                .onSuccess { response = "Qwen3 model found and remembered. JARVIS can use it without asking again." }
                .onFailure { response = "Qwen3 folder search failed: ${it.message ?: "model not found"}" }
        }
    }

    DisposableEffect(Unit) { onDispose { speech.destroy(); tts.destroy() } }

    fun process(input: String) {
        if (input.isBlank() || processing) return
        val lower = input.lowercase()
        val needsContacts = lower.contains("contact") || lower.contains("phone number") || lower.contains("call ") || lower.contains("dial ")
        val needsCalendar = lower.contains("calendar") || lower.contains("schedule") || lower.contains("appointment")
        val requested = mutableListOf<String>()
        if (needsContacts && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED && !privacy.isBlocked(PrivacyCapability.CONTACT_LOOKUP)) requested += Manifest.permission.READ_CONTACTS
        if (needsCalendar && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED && !privacy.isBlocked(PrivacyCapability.CALENDAR_DATA)) requested += Manifest.permission.READ_CALENDAR

        // ASK policy: first let JARVIS request its own action confirmation; Android
        // permission is only requested after the user confirms that action. ALLOW policy
        // may request the required Android permission immediately. NEVER never requests it.
        val allowImmediately = requested.filter { permission ->
            (permission == Manifest.permission.READ_CONTACTS && privacy.mode(PrivacyCapability.CONTACT_LOOKUP) == PrivacyMode.ALLOW) ||
            (permission == Manifest.permission.READ_CALENDAR && privacy.mode(PrivacyCapability.CALENDAR_DATA) == PrivacyMode.ALLOW)
        }
        if (allowImmediately.isNotEmpty()) {
            deferredCommand = input
            deferredPermissionCheck = allowImmediately
            personalPermissionLauncher.launch(allowImmediately.toTypedArray())
            response = "I need the Android permission before I can use that personal data. Your JARVIS Privacy policy still applies."
            return
        }

        if (requested.isNotEmpty() && (privacy.requiresUserApproval(PrivacyCapability.CONTACT_LOOKUP) || privacy.requiresUserApproval(PrivacyCapability.CALENDAR_DATA))) {
            deferredPermissionCheck = requested
        }

        processing = true
        scope.launch {
            try {
                val result = brain.process(input)
                response = result.text
                confidence = result.confidence
                pending = brain.pendingDescription()
                tts.speak(result.text)
                text = ""
            } finally {
                processing = false
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("JARVIS 2.9.6") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            ScrollableTabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Assistant") })
                Tab(tab == 10, { tab = 10 }, text = { Text("Today") })
                Tab(tab == 11, { tab = 11 }, text = { Text("Map") })
                Tab(tab == 12, { tab = 12 }, text = { Text("Fitness") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Tasks") })
                Tab(tab == 2, { tab = 2 }, text = { Text("Memory") })
                Tab(tab == 3, { tab = 3 }, text = { Text("History") })
                Tab(tab == 4, { tab = 4 }, text = { Text("App Access") })
                Tab(tab == 5, { tab = 5 }, text = { Text("Privacy") })
                Tab(tab == 6, { tab = 6 }, text = { Text("Capabilities") })
                Tab(tab == 7, { tab = 7 }, text = { Text("Profile") })
                Tab(tab == 8, { tab = 8 }, text = { Text("Focus") })
                Tab(tab == 9, { tab = 9 }, text = { Text("Evolution") })
            }
            Spacer(Modifier.height(16.dp))
            when (tab) {
                10 -> ProactiveScreen(proactive)
                11 -> com.harsh.jarvis.maps.OfflineMapScreen()
                12 -> FitnessScreen()
                0 -> AssistantScreen(text, listening, processing, response, confidence, pending, qwenStatus,
                    { qwenModelPicker.launch(arrayOf("application/octet-stream", "application/*", "*/*")) },
                    { qwenFolderPicker.launch(null) },
                    { text = it }, {
                        if (privacy.isBlocked(PrivacyCapability.MICROPHONE)) {
                            response = "Microphone access is blocked by your Privacy policy."
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startSpeech()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }, { process(text) },
                    {
                        if (deferredPermissionCheck.isNotEmpty()) {
                            deferredCommand = "yes"
                            personalPermissionLauncher.launch(deferredPermissionCheck.toTypedArray())
                            response = "Android permission is needed after your confirmation. Please allow it to continue."
                        } else {
                            process("yes")
                        }
                    }, {
                        deferredPermissionCheck = emptyList()
                        process("no")
                    })
                1 -> TaskScreen(vm)
                2 -> MemoryScreen(vm)
                3 -> HistoryScreen(history)
                4 -> AppAccessScreen(apps, permissions, { apps = tools.availableApps() })
                5 -> PrivacyScreen(privacyPolicy, privacyAudit)
                6 -> CapabilitiesScreen()
                7 -> ProfileScreen(profile)
                8 -> com.harsh.jarvis.focus.FocusScreen()
                9 -> EvolutionScreen(evolution)
            }
        }
    }
}

@Composable
private fun AssistantScreen(
    text: String, listening: Boolean, processing: Boolean, response: String, confidence: Double, pending: String?, qwenStatus: String,
    onLoadQwen: () -> Unit, onGrantQwenFolder: () -> Unit, onTextChange: (String) -> Unit, onSpeak: () -> Unit, onSend: () -> Unit,
    onConfirm: () -> Unit, onCancel: () -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("Brain: Qwen3 1.7B • auto-find enabled")
                Text(qwenStatus, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onLoadQwen, Modifier.weight(1f)) { Text("Select GGUF") }
                    OutlinedButton(onClick = onGrantQwenFolder, Modifier.weight(1f)) { Text("Grant Folder") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(response)
        if (confidence > 0) { Spacer(Modifier.height(6.dp)); Text("Intent confidence: ${(confidence * 100).toInt()}%") }
        if (pending != null) {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Confirmation required")
                    Text(pending)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onConfirm, Modifier.weight(1f)) { Text("Yes") }
                        OutlinedButton(onCancel, Modifier.weight(1f)) { Text("No") }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(text, onTextChange, Modifier.fillMaxWidth(), label = { Text("Say or type a command") })
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onSpeak, Modifier.weight(1f)) { Text(if (listening) "Listening..." else "🎤 Speak") }
            Button(onClick = onSend, enabled = !processing && !listening, modifier = Modifier.weight(1f)) { Text(if (processing) "Working…" else "Send") }
        }
        Spacer(Modifier.height(16.dp))
        Text("Try: remind me to study Pandas")
        Text("Then answer: tomorrow at 7 PM")
        Text("Try: remember that my project is JARVIS")
        Text("Try: open YouTube (enable it in App Access first)")
        Text("Try: what do you remember about my project?")
    }
}

@Composable
private fun HistoryScreen(history: ActionHistoryRepository) {
    val records by history.observeLatest().collectAsState(initial = emptyList())
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("JARVIS action history — verified outcomes only") }
        items(records, key = { it.id }) { record ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${record.status} • ${record.lifecycle}: ${record.request}")
                    Text("Expected: ${record.expected}")
                    Text("Actual: ${record.actual}")
                    record.evidence?.takeIf { it.isNotBlank() }?.let { Text("Evidence: $it") }
                    record.problem?.takeIf { it.isNotBlank() }?.let { Text("Problem: $it") }
                    record.cause?.takeIf { it.isNotBlank() }?.let { Text("Cause: $it") }
                    record.fix?.takeIf { it.isNotBlank() }?.let { Text("Fix: $it") }
                }
            }
        }
    }
}

@Composable
private fun AppAccessScreen(
    apps: List<ToolRegistry.InstalledApp>, permissions: PermissionManager, onRefresh: () -> Unit
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Text("Only enabled apps can be opened by JARVIS. Foreground verification uses Android Usage Access.")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, Modifier.weight(1f)) { Text("Usage Access") }
            OutlinedButton(onClick = onRefresh, Modifier.weight(1f)) { Text("Refresh") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(apps, key = { it.packageName }) { app ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(permissions.isAppAllowed(app.packageName), { permissions.setAppAllowed(app.packageName, it); onRefresh() })
                        Text(app.label, Modifier.weight(1f))
                        Text(if (permissions.isAppAllowed(app.packageName)) "Allowed" else "Blocked")
                    }
                }
            }
        }
    }
}


@Composable
private fun PrivacyScreen(policy: PrivacyPolicyStore, audit: PrivacyAuditRepository) {
    val context = LocalContext.current
    val entries by audit.observeLatest().collectAsState(initial = emptyList())
    val capabilities = remember { PrivacyCapability.entries }
    var showAudit by remember { mutableStateOf(false) }
    var policyRevision by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Text("Privacy Firewall", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "JARVIS does not have unrestricted access to your phone data. " +
                "Sensitive data is NEVER or ASK by default. Contact/calendar access requires an explicit approval; background proactive work only uses capabilities you set to ALLOW. " +
                "Memory and user-allowed app control are local-first."
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { policy.resetDefaults(); policyRevision++ }, Modifier.weight(1f)) {
                Text("Reset defaults")
            }
            OutlinedButton(onClick = { showAudit = !showAudit }, Modifier.weight(1f)) {
                Text(if (showAudit) "Privacy policy" else "Access log")
            }
        }
        Spacer(Modifier.height(10.dp))

        if (showAudit) {
            if (entries.isEmpty()) {
                Text("No privacy access events recorded yet.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries, key = { it.id }) { entry ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("${entry.capability} • ${entry.outcome}")
                                Text("Policy: ${entry.mode}")
                                Text("Purpose: ${entry.purpose}")
                                Text("Data exposed: ${entry.dataExposed}")
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(capabilities.toList(), key = { it.name }) { capability ->
                    val current = policy.mode(capability)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(capability.label, style = MaterialTheme.typography.titleMedium)
                            Text(capability.description, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(6.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PrivacyMode.entries.forEach { mode ->
                                    val selected = current == mode
                                    OutlinedButton(
                                        onClick = { policy.setMode(capability, mode); policyRevision++ },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (selected) "✓ ${mode.name.lowercase().replaceFirstChar { it.uppercase() }}" else mode.name.lowercase().replaceFirstChar { it.uppercase() })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskScreen(vm: JarvisViewModel) {
    val tasks by vm.tasks.collectAsState()
    var deleteCandidate by remember { mutableStateOf<com.harsh.jarvis.tasks.Task?>(null) }

    if (deleteCandidate != null) {
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Confirm deletion") },
            text = { Text("Delete '${deleteCandidate!!.title}'? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val task = deleteCandidate
                    deleteCandidate = null
                    if (task != null) vm.deleteTask(task)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") } }
        )
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tasks, key = { it.id }) { task ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(task.completed, { vm.completeTask(task.id) })
                    Column(Modifier.weight(1f)) { Text(task.title); if (task.description.isNotBlank()) Text(task.description) }
                    OutlinedButton({ deleteCandidate = task }) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun MemoryScreen(vm: JarvisViewModel) {
    val memories by vm.memories.collectAsState()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Long-term memories saved explicitly for JARVIS.") }
        items(memories, key = { it.id }) { memory ->
            Card(Modifier.fillMaxWidth()) { Text(memory.text, Modifier.padding(14.dp)) }
        }
    }
}


@Composable
private fun CapabilitiesScreen() {
    val capabilities = com.harsh.jarvis.tools.CapabilityRegistry().all()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("JARVIS capability map", style = MaterialTheme.typography.titleMedium) }
        items(capabilities, key = { it.id }) { c ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(c.id)
                    Text(c.description)
                    Text(if (c.implemented) "Implemented • ${c.actionLevel}" else "Policy-declared / not implemented")
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(profile: com.harsh.jarvis.ai.UserProfile) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Local profile", style = MaterialTheme.typography.titleMedium)
        Text(profile.summary())
        Text("Profile data stays in Android SharedPreferences. Say: 'my name is ...' to set your name.")
    }
}


@Composable
private fun EvolutionScreen(repository: EvolutionRepository) {
    val skills by repository.skills().collectAsState(initial = emptyList())
    val quests by repository.quests().collectAsState(initial = emptyList())
    val goals by repository.goals().collectAsState(initial = emptyList())
    val achievements by repository.achievements().collectAsState(initial = emptyList())
    val progressionHistory by repository.history().collectAsState(initial = emptyList())
    var dashboard by remember { mutableStateOf<com.harsh.jarvis.evolution.EvolutionDashboard?>(null) }
    var newSkill by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("INTELLIGENCE") }
    var newQuest by remember { mutableStateOf("") }
    var newQuestDescription by remember { mutableStateOf("") }
    var newQuestXp by remember { mutableStateOf("50") }
    var newQuestDifficulty by remember { mutableStateOf("1") }
    var newQuestType by remember { mutableStateOf("SIDE") }
    var newQuestDeadlineDays by remember { mutableStateOf("") }
    var newGoal by remember { mutableStateOf("") }
    var renameId by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    if (renameId != null) {
        AlertDialog(
            onDismissRequest = { renameId = null },
            title = { Text("Rename skill") },
            text = { OutlinedTextField(renameText, { renameText = it }, label = { Text("Skill name") }) },
            confirmButton = {
                TextButton(onClick = {
                    val id = renameId
                    if (id != null && renameText.isNotBlank()) scope.launch { repository.renameSkill(id, renameText); renameId = null }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameId = null }) { Text("Cancel") } }
        )
    }

    LaunchedEffect(skills, quests, goals, achievements) { dashboard = repository.dashboard() }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("JARVIS EVOLUTION", style = MaterialTheme.typography.headlineSmall)
            Text("Level: ${dashboard?.profile?.level ?: 1}")
            val totalXp = dashboard?.profile?.totalXp ?: 0L
            val level = dashboard?.profile?.level ?: 1
            val formula = com.harsh.jarvis.evolution.EvolutionFormula()
            val currentXp = formula.currentXp(totalXp, level)
            val next = formula.xpToNextLevel(level)
            Text("XP: $currentXp / $next  • Total $totalXp")
            LinearProgressIndicator(
                progress = { ((currentXp.toFloat() / next.toFloat()).coerceIn(0f, 1f)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Text("PHYSICAL", style = MaterialTheme.typography.titleMedium) }
        items(skills.filter { it.category.equals("PHYSICAL", true) }, key = { it.id }) { skill ->
            EvolutionSkillCard(skill, { renameId = skill.id; renameText = skill.name }, { scope.launch { repository.removeSkill(skill.id) } })
        }
        item { Text("INTELLIGENCE", style = MaterialTheme.typography.titleMedium) }
        items(skills.filter { !it.category.equals("PHYSICAL", true) }, key = { it.id }) { skill ->
            EvolutionSkillCard(skill, { renameId = skill.id; renameText = skill.name }, { scope.launch { repository.removeSkill(skill.id) } })
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ADD CUSTOM SKILL")
                    OutlinedTextField(newSkill, { newSkill = it }, label = { Text("Skill name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newCategory, { newCategory = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        scope.launch { repository.addSkill(newSkill, newCategory); newSkill = "" }
                    }, enabled = newSkill.isNotBlank()) { Text("Add Skill") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ADD CUSTOM QUEST")
                    OutlinedTextField(newQuest, { newQuest = it }, label = { Text("Quest title") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newQuestDescription, { newQuestDescription = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newQuestType, { newQuestType = it }, label = { Text("Type: DAILY / WEEKLY / MAIN / SIDE / CUSTOM") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(newQuestDifficulty, { newQuestDifficulty = it }, label = { Text("Difficulty") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(newQuestXp, { newQuestXp = it }, label = { Text("XP") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(newQuestDeadlineDays, { newQuestDeadlineDays = it }, label = { Text("Deadline in days (optional)") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        scope.launch {
                            if (newQuest.isNotBlank()) {
                                val days = newQuestDeadlineDays.toLongOrNull()
                                repository.addQuest(EvolutionQuest(
                                    title = newQuest, description = newQuestDescription, type = newQuestType.uppercase(),
                                    difficulty = newQuestDifficulty.toIntOrNull()?.coerceIn(1, 10) ?: 1,
                                    xpReward = newQuestXp.toLongOrNull()?.coerceAtLeast(1) ?: 50,
                                    deadline = days?.let { System.currentTimeMillis() + it * 86_400_000L }
                                ))
                                newQuest = ""; newQuestDescription = ""; newQuestDeadlineDays = ""
                            }
                        }
                    }, enabled = newQuest.isNotBlank()) { Text("Add Quest") }
                }
            }
        }
        item { Text("TODAY'S QUESTS", style = MaterialTheme.typography.titleMedium) }
        items(quests.filter { it.status == "PENDING" }.take(10), key = { it.id }) { quest ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("□ ${quest.title}")
                    if (quest.description.isNotBlank()) Text(quest.description)
                    Text("${quest.type} • Difficulty ${quest.difficulty}/10 • +${quest.xpReward} XP")
                    Button(onClick = {
                        scope.launch { repository.completeQuest(quest.id) }
                    }) { Text("Complete") }
                }
            }
        }
        item {
            val next = dashboard?.nextChallenge
            if (next != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("NEXT CHALLENGE", style = MaterialTheme.typography.titleMedium)
                        Text(next.title)
                        Text("Difficulty: ${next.difficulty}/10 • Reward: +${next.xpReward} XP")
                    }
                }
            }
        }
        item { Text("MAIN QUESTS / LONG-TERM GOALS", style = MaterialTheme.typography.titleMedium) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(newGoal, { newGoal = it }, label = { Text("Major objective") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        scope.launch { if (newGoal.isNotBlank()) { repository.addGoal(EvolutionGoal(title = newGoal)); newGoal = "" } }
                    }, enabled = newGoal.isNotBlank()) { Text("Add Goal") }
                }
            }
        }
        items(goals, key = { it.id }) { goal ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(goal.title)
                    Text("${goal.progress}%")
                    LinearProgressIndicator(progress = { (goal.progress.toFloat() / goal.target.coerceAtLeast(1)).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { scope.launch { repository.setGoalProgress(goal.id, goal.progress - 10) } }) { Text("-10%") }
                        OutlinedButton(onClick = { scope.launch { repository.setGoalProgress(goal.id, goal.progress + 10) } }) { Text("+10%") }
                    }
                }
            }
        }
        item { Text("PROGRESSION HISTORY", style = MaterialTheme.typography.titleMedium) }
        items(progressionHistory.take(15), key = { it.id }) { h ->
            Text("${h.entityType}: Lv.${h.fromLevel} → Lv.${h.toLevel} • +${h.xpAwarded} XP • ${h.reason}")
        }
        item { Text("STREAKS", style = MaterialTheme.typography.titleMedium) }
        items((dashboard?.streaks ?: emptyList()), key = { it.key }) { streak ->
            Text("${streak.key}: ${streak.current} days • best ${streak.best}")
        }
        item { Text("ACHIEVEMENTS", style = MaterialTheme.typography.titleMedium) }
        items(achievements.take(10), key = { it.id }) { Text("🏆 ${it.title} — ${it.description}") }
    }
}

@Composable
private fun EvolutionSkillCard(skill: EvolutionSkill, onRename: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(skill.name)
                    Text("Lv.${skill.level} • ${skill.xp} XP")
                }
                Text(skill.category)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onRename) { Text("Rename") }
                OutlinedButton(onRemove) { Text("Remove") }
            }
        }
    }
}
