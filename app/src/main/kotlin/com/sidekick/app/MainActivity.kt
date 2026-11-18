package com.sidekick.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sidekick.app.audio.ListeningService
import com.sidekick.app.data.CommitmentEntity
import com.sidekick.core.TimeResolver
import com.sidekick.core.Planner
import com.sidekick.core.PlannerEntry
import com.sidekick.core.PlannerView
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var remindersReady by mutableStateOf(true)
    override fun onResume() {
        super.onResume()
        val app = application as SidekickApplication
        remindersReady = app.reminders.notificationsAllowed()
        MessageNotificationListener.requestConnection(app)
        app.calendarMirror.requestSync()
        app.background.launch { runCatching { app.reminders.restore() } }
    }
    private var sharedText by mutableStateOf<String?>(null)
    private var calendarTarget by mutableStateOf<String?>(null)
    private var widgetStart by mutableStateOf(false)
    private fun receiveShare(incoming: Intent?) {
        if (incoming?.action == PlanWidget.START) { widgetStart = true; incoming.action = null }
        if (incoming?.action == Intent.ACTION_VIEW) calendarTarget = CalendarTarget.parse(incoming.data, packageName)
        if (incoming?.action == Intent.ACTION_SEND && incoming.type == "text/plain")
            sharedText = runCatching { incoming.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.take(6000) }.getOrNull()?.takeIf { it.isNotBlank() }
        intent?.removeExtra(Intent.EXTRA_TEXT)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receiveShare(intent) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveShare(intent)
        setContent {
            SidekickAppTheme((application as SidekickApplication).guidance) { SidekickScreen() }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun SidekickScreen(model: MemoryViewModel = viewModel()) {
        val entries by model.items.collectAsStateWithLifecycle()
        val automaticChanges by model.automaticChanges.collectAsStateWithLifecycle()
        val message by model.message.collectAsStateWithLifecycle()
        val busy by model.busy.collectAsStateWithLifecycle()
        val transcript by model.transcript.collectAsStateWithLifecycle()
        val listening by ListeningService.state.collectAsStateWithLifecycle()
        val liveText by model.liveText.collectAsStateWithLifecycle()
        val speakerTurns by (application as SidekickApplication).temporary.speakerTurns.collectAsStateWithLifecycle()
        val editError by model.editError.collectAsStateWithLifecycle()
        val guidance by model.guidance.collectAsStateWithLifecycle()
        val handsOff by (application as SidekickApplication).guidance.handsOffMode.collectAsStateWithLifecycle()
        val manualNotes by (application as SidekickApplication).guidance.manualNotes.collectAsStateWithLifecycle()
        val oneScreen by (application as SidekickApplication).guidance.oneScreenHome.collectAsStateWithLifecycle()
        var expandedCapture by rememberSaveable { mutableStateOf(false) }
        val automaticStatus by (application as SidekickApplication).automation.status.collectAsStateWithLifecycle()
        val recoveryEnabled by (application as SidekickApplication).assistant.enabled.collectAsStateWithLifecycle()
        val assistantSelected by (application as SidekickApplication).assistant.selected.collectAsStateWithLifecycle()
        val sharedMessagesEnabled by (application as SidekickApplication).guidance.sharedMessages.collectAsStateWithLifecycle()
        val speechLanguage by (application as SidekickApplication).guidance.speechLanguage.collectAsStateWithLifecycle()
        val speechQuality by (application as SidekickApplication).guidance.speechQuality.collectAsStateWithLifecycle()
        var settingsOpen by remember { mutableStateOf(false) }
        var quickStartOpen by remember { mutableStateOf(false) }
        val packRevision by com.sidekick.app.speech.ModelPacks.revision.collectAsStateWithLifecycle()
        val missingPacks = remember(speechLanguage, speechQuality, packRevision) { com.sidekick.app.speech.ModelPacks.missing(this@MainActivity, speechLanguage, speechQuality) }
        var consent by remember { mutableStateOf(false) }
        var micDialog by remember { mutableStateOf(false) }
        var showFullLiveText by rememberSaveable { mutableStateOf(false) }
        var showSessionDetails by rememberSaveable { mutableStateOf(false) }
        var showNoteTools by rememberSaveable { mutableStateOf(false) }
        val alwaysAnnounce by (application as SidekickApplication).guidance.alwaysShowAnnounce.collectAsStateWithLifecycle()
        val nameSuggestion by (application as SidekickApplication).temporary.nameSuggestion.collectAsStateWithLifecycle()
        val noticeRecords by (application as SidekickApplication).database.noticeRecords().observeRecent().collectAsStateWithLifecycle(emptyList())
        val voiceConsentNeeded by (application as SidekickApplication).voiceProfile.consentNeeded.collectAsStateWithLifecycle()
        var namingVoice by remember { mutableStateOf<String?>(null) }
        var preciseDialog by remember { mutableStateOf(false) }
        var voiceConsentDialog by remember { mutableStateOf(false) }
        var voiceConsentAskedThisRun by remember { mutableStateOf(false) }
        var requestListeningAfterChecks: () -> Unit = {}
        var showVoiceDiagnostics by remember { mutableStateOf(false) }
        var showSpeakerTurns by remember { mutableStateOf(false) }
        var deleteDialog by remember { mutableStateOf(false) }
        var edit by remember { mutableStateOf<CommitmentEntity?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var awaitingLiveNotifications by remember { mutableStateOf(false) }
        var plannerOpen by rememberSaveable { mutableStateOf(false) }
        var summaryOpen by rememberSaveable { mutableStateOf(false) }
        var moreOpen by rememberSaveable { mutableStateOf(false) }
        var selectedView by rememberSaveable { mutableStateOf(PlannerView.REVIEW) }
        var search by rememberSaveable { mutableStateOf("") }
        val references = entries.map { PlannerEntry(it.id, it.state, it.dueAt, it.capturedAt) }
        val byId = entries.associateBy { it.id }
        val shownEntries = if (plannerOpen) Planner.select(references, selectedView).mapNotNull { byId[it.id] }
            .filter { search.isBlank() || it.title.contains(search.trim(), true) || it.evidence.contains(search.trim(), true) }
            else entries.take(3)
        LaunchedEffect(sharedText, handsOff, sharedMessagesEnabled) {
            if (handsOff && sharedMessagesEnabled) sharedText?.let { text ->
                sharedText = null; model.analyzeSharedText(text)
                plannerOpen = true; summaryOpen = false; moreOpen = false; expandedCapture = false; selectedView = PlannerView.ALL; search = ""
            }
        }

        fun serviceAction(action: String) {
            try { startService(Intent(this@MainActivity, ListeningService::class.java).setAction(action)) }
            catch (e: Exception) { error = "Could not change listening. Try again." }
        }
        fun startRecording() {
            if (missingPacks.isNotEmpty()) { error = "Speech for this language isn’t installed yet. Tap Set up speech to install it, or choose English Faster."; return }
            try { startForegroundService(Intent(this@MainActivity, ListeningService::class.java)) }
            catch (e: Exception) { error = "Could not start recording. Reopen the app and try again." }
        }
        val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startRecording() else error = "Microphone permission was denied. You can still type."
        }
        val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (awaitingLiveNotifications) {
                awaitingLiveNotifications = false
                if (granted) microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                else error = "Enable notifications for listening controls, then try again."
            } else if (!granted) error = "Notifications are disabled. You can save commitments without reminders."
            else model.notificationPermissionChanged()
        }
        val preciseLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            // Either answer is fine: coarse-only still works with a larger border buffer.
            requestListeningAfterChecks()
        }
        fun beginListening() {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                awaitingLiveNotifications = true; notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        fun requestListening() {
            val app = application as SidekickApplication
            // Existing voice profiles without a consent record: ask before the next session (matching stays off until then).
            if (app.voiceProfile.consentNeeded.value && !voiceConsentAskedThisRun) { voiceConsentAskedThisRun = true; voiceConsentDialog = true; return }
            // Ask once for precise location: more accurate state detection near borders. Skipped with "strictest everywhere".
            if (!app.guidance.strictestEverywhere.value && !app.guidance.preciseLocationAsked &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { preciseDialog = true; return }
            if (app.guidance.skipListeningIntro.value) beginListening()
            else micDialog = true
        }
        // Start from the home-screen widget: the same checks as the Start button (permissions, consent, intro).
        LaunchedEffect(widgetStart) {
            if (widgetStart) { widgetStart = false; if (!ListeningService.state.value.recording) requestListening() }
        }
        requestListeningAfterChecks = ::requestListening
        val wavPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { consent = false; model.importWav(it) }
        }
        fun openEditor(item: CommitmentEntity) {
            model.reopen(); edit = item
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        LaunchedEffect(calendarTarget) {
            val id = calendarTarget ?: return@LaunchedEffect
            (application as SidekickApplication).database.commitments().get(id)?.let { item ->
                model.reopen(); edit = item
            }
            if (calendarTarget == id) calendarTarget = null
        }

        val focus = LocalFocusManager.current
        val captureList = rememberLazyListState()
        val plannerList = rememberLazyListState()
        val summaryList = rememberLazyListState()
        // Simple layout: Today (0), Plans (1) and More (2); summary and the detailed capture page open from More.
        val simpleMoreSelected = oneScreen && (moreOpen || summaryOpen || expandedCapture)
        SidekickFrame(selected = if (oneScreen) { if (simpleMoreSelected && !plannerOpen) 2 else if (plannerOpen) 1 else 0 }
            else if (summaryOpen) 2 else if (plannerOpen) 1 else 0,
            onSelect = { focus.clearFocus(); expandedCapture = false
                // Simple Plans opens on every plan; the "to check" button on Today opens the check list.
                if (oneScreen && it == 1 && !plannerOpen) { selectedView = PlannerView.ALL; search = "" }
                plannerOpen = it == 1
                if (oneScreen) { summaryOpen = false; moreOpen = it == 2 } else summaryOpen = it == 2 },
            onSettings = { focus.clearFocus(); settingsOpen = true }, simple = oneScreen) {
            if (oneScreen && moreOpen && !expandedCapture && !plannerOpen && !summaryOpen) {
                SimpleMore(manualNotes, onSummary = { summaryOpen = true }, onConversation = { expandedCapture = true },
                    onSetup = { quickStartOpen = true }, onSettings = { settingsOpen = true },
                    onDetailed = { moreOpen = false; (application as SidekickApplication).guidance.setOneScreenHome(false) })
            } else if (oneScreen && !expandedCapture && !plannerOpen && !summaryOpen) {
                CompactHome(listening, entries, automaticChanges.associateBy { it.id }, handsOff, alwaysAnnounce, missingPacks.isNotEmpty(), busy, error,
                    onStart = { requestListening() }, onFinish = { serviceAction(ListeningService.FINISH) },
                    onDiscard = { serviceAction(ListeningService.CANCEL) }, onAnnounce = { serviceAction(ListeningService.ANNOUNCE) },
                    onNoticeGiven = { serviceAction(ListeningService.NOTICE_GIVEN) }, onSetup = { quickStartOpen = true }, onOpen = ::openEditor,
                    onUndo = { model.undoAutomatic(it.id, it.token) }, onRemove = { model.dismiss(it.id) },
                    onReview = { plannerOpen = true; selectedView = PlannerView.REVIEW; search = "" },
                    onAllPlans = { plannerOpen = true; selectedView = PlannerView.ALL; search = "" })
            } else {
            LazyColumn(Modifier.fillMaxSize().testTag("main-list"), state = if (summaryOpen) summaryList else if (plannerOpen) plannerList else captureList,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (oneScreen && (expandedCapture || summaryOpen) && !plannerOpen)
                            OutlinedButton(onClick = { expandedCapture = false; summaryOpen = false; moreOpen = true },
                                modifier = Modifier.heightIn(min = SimpleTarget).testTag("simple-back")) { Text("Back to More", style = MaterialTheme.typography.titleMedium) }
                        Text(if (summaryOpen) "Summary" else if (plannerOpen) { if (oneScreen) "Plans" else "Planner" } else if (oneScreen) "What was heard" else "Capture",
                            style = MaterialTheme.typography.headlineMedium)
                        QuietLabel(if (summaryOpen) "Your day at a glance." else if (plannerOpen) { if (oneScreen) "Your plans and reminders. Every action has its own button." else "Plans, reminders and things to review." }
                            else "Turn conversations into plans.")
                        if (oneScreen) Text(if (handsOff) "Plans save automatically. You can undo them here." else "Plans wait until you check them.",
                            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("automation-status"))
                        else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                SuggestionChip(onClick = { settingsOpen = true }, label = { Text(if (handsOff) "Hands-off" else "Review first") })
                                TextButton(onClick = { quickStartOpen = true }, modifier = Modifier.testTag("get-started")) { Text("Set up speech") }
                            }
                            QuietLabel(if (handsOff) "Hands-off mode · $automaticStatus" else "Review mode · approve items to save", Modifier.testTag("automation-status"))
                        }
                    }
                }
                item { RiskSection() }
                if (summaryOpen && (listening.recording || listening.processing)) item {
                    Text(listening.note)
                    if (listening.recording) Button(onClick = { serviceAction(ListeningService.FINISH) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Stop listening") }
                }
                if (summaryOpen) item { SummarySection(entries, liveText, !listening.recording && !listening.processing, model::clearRecentConversation, ::openEditor) }
                else {
                if (plannerOpen && (listening.recording || listening.processing)) item {
                    Text(listening.note)
                    if (listening.recording) Button(onClick = { serviceAction(ListeningService.FINISH) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Stop listening") }
                    TextButton(onClick = { serviceAction(ListeningService.CANCEL) }) { Text(if (handsOff) "Stop & clear temporary text" else "Stop & discard what was heard") }
                }
                if (plannerOpen && !remindersReady) item {
                    Text("Reminder notifications are blocked. Your events are saved; enable notifications for alerts.", modifier = Modifier.testTag("reminders-blocked"))
                    TextButton(onClick = { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }) { Text("Notification settings") }
                }
                if (plannerOpen && automaticChanges.isNotEmpty()) {
                    item { Text("Recently saved for you", style = MaterialTheme.typography.titleMedium) }
                    items(automaticChanges, key = { "automatic-${it.id}" }) { change ->
                        Card(Modifier.fillMaxWidth().testTag("automatic-change-${change.id}")) {
                            Column(Modifier.padding(16.dp)) {
                                Text("${change.action}: ${change.title}")
                                TextButton(onClick = { model.undoAutomatic(change.id, change.token) }, modifier = Modifier.testTag("undo-${change.id}")) { Text("Undo") }
                            }
                        }
                    }
                }
                if (!plannerOpen) item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Live notes", style = MaterialTheme.typography.titleLarge)
                            QuietLabel("${speechLanguage.label} · On-device")
                            Text(listening.note)
                            if (listening.recording) {
                                Text(if (listening.speechActive) "Speech detected" else "Waiting for speech · microphone stays on")
                                Text(listening.voiceStatus)
                                if (listening.consent.isNotBlank()) QuietLabel(listening.consent, Modifier.testTag("consent-status"))
                                if (listening.droppedChunks > 0) QuietLabel("${listening.droppedChunks} phrases from other voices dropped, never saved")
                                if (listening.callActive) QuietLabel("Call detected: strictest rule", Modifier.testTag("call-status"))
                                if (listening.answer.isNotBlank()) Text(listening.answer, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("plan-answer"))
                                if (listening.noticeNeeded || alwaysAnnounce) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { serviceAction(ListeningService.ANNOUNCE) }, modifier = Modifier.testTag("announce-notice")) {
                                        Text(if (listening.noticeActive) "Announce again" else "Announce note-taking")
                                    }
                                    TextButton(onClick = { serviceAction(ListeningService.NOTICE_GIVEN) }, modifier = Modifier.testTag("notice-given")) { Text(listening.attestLabel) }
                                }
                                nameSuggestion?.takeIf { listening.namingAllowed }?.let { suggestion ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        AssistChip(onClick = {
                                            try { startService(Intent(this@MainActivity, ListeningService::class.java).setAction(ListeningService.NAME_VOICE)
                                                .putExtra(ListeningService.VOICE_KEY, suggestion.voiceKey).putExtra(ListeningService.VOICE_NAME, suggestion.name)) }
                                            catch (_: Exception) { error = "Could not name this voice." }
                                        }, label = { Text("${suggestion.name}?") }, modifier = Modifier.testTag("name-suggestion"))
                                        TextButton(onClick = { (application as SidekickApplication).temporary.dismissNameSuggestion() }) { Text("Not now") }
                                    }
                                }
                                LinearProgressIndicator(progress = { (listening.level * 5).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                Text("${listening.seconds / 60}m ${listening.seconds % 60}s · ${listening.chunks} phrases")
                                Button(onClick = { serviceAction(ListeningService.FINISH) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Stop listening") }
                            } else if (listening.processing) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else Button(onClick = { requestListening() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !busy && missingPacks.isEmpty()) { Text("Start listening") }
                            if (missingPacks.isNotEmpty()) Text("Speech files needed. Tap Set up speech to install them, or choose English Faster.")
                            if (listening.recording || listening.processing)
                                TextButton(onClick = { serviceAction(ListeningService.CANCEL) }) { Text(if (handsOff) "Stop & clear temporary text" else "Stop & discard what was heard") }
                            if (liveText.isNotBlank()) {
                                Text("Recent live text", style = MaterialTheme.typography.titleMedium)
                                Text(liveText.takeLast(if (showFullLiveText) 3000 else 600), modifier = Modifier.testTag("live-text"))
                                if (liveText.length > 600) TextButton(onClick = { showFullLiveText = !showFullLiveText }) { Text(if (showFullLiveText) "Show less text" else "Show more text") }
                                if (speakerTurns.isNotEmpty()) {
                                    TextButton(onClick = { showSpeakerTurns = !showSpeakerTurns }, modifier = Modifier.testTag("speaker-turns-toggle")) { Text(if (showSpeakerTurns) "Hide who said what" else "Show who said what") }
                                    if (showSpeakerTurns) {
                                        Text("Voice labels are anonymous and last only until listening stops. They do not identify people or verify consent." +
                                            if (listening.namingAllowed && listening.recording) " Tap a voice label to name it until listening stops." else "", style = MaterialTheme.typography.bodySmall)
                                        speakerTurns.forEachIndexed { index, turn ->
                                            val time = turn.at.atZone(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                                            val nameable = turn.voiceKey != null && listening.namingAllowed && listening.recording
                                            Text("$time · ${turn.label} · ${if (turn.speaker == com.sidekick.core.Speaker.USER) "likely you" else "identity unassigned"}", style = MaterialTheme.typography.labelLarge,
                                                modifier = if (nameable) Modifier.clickable { namingVoice = turn.voiceKey }.testTag("voice-label-$index") else Modifier)
                                            Text(turn.text, modifier = Modifier.testTag("speaker-turn-$index"))
                                        }
                                    }
                                }
                                TextButton(onClick = { model.copyLiveText(); consent = false }) { Text("Copy recent text for editing") }
                            }
                            QuietLabel("Audio is discarded. Recent text is temporary; saved events stay on this device.")
                            TextButton(onClick = { showSessionDetails = !showSessionDetails }, modifier = Modifier.testTag("session-details")) {
                                Text(if (showSessionDetails) "Hide listening details" else "Listening details")
                            }
                            if (showSessionDetails) {
                            val sessionNotices = noticeRecords.filter { it.sessionId == listening.sessionId }
                            if (sessionNotices.isNotEmpty()) {
                                Text("Notice record", style = MaterialTheme.typography.titleSmall)
                                sessionNotices.forEach { record ->
                                    val time = java.time.Instant.ofEpochMilli(record.noticeAt).atZone(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                                    val how = when (record.method) { "SPOKEN" -> "spoken"; "SPOKEN_AUTO" -> "spoken automatically"; else -> "you told everyone" }
                                    val delivery = if (record.audible == null) "" else " · volume ${record.volumePercent ?: 0}%" +
                                        if (record.audible == true) " · heard by the phone" else " · too quiet, not counted"
                                    Text("$time · $how$delivery · ${record.stateCodes.ifBlank { "unknown place" }} · " +
                                        "${if (record.recordingRule == "ALL_PARTY") "all-party" else "one-party"} · voice ${record.biometricRule.lowercase()}" +
                                        (if (record.includesVoice) " · incl. voice line" else "") + " · text v${record.textVersion}",
                                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("notice-record"))
                                }
                            }
                            Text("Speech: ${speechLanguage.label} · turned into text on this phone")
                            Text("New times use phone timezone: ${ZoneId.systemDefault().id}", style = MaterialTheme.typography.bodySmall)
                            Text(if (speechQuality.usesSenseVoice(speechLanguage)) "SenseVoice int8 · ${speechLanguage.label} · on-device" else if (speechQuality.usesOmni(speechLanguage, true)) "Omnilingual int8 · ${speechLanguage.label} · on-device" else speechQuality.label, style = MaterialTheme.typography.bodySmall)
                            if (speechLanguage != com.sidekick.core.SpeechLanguage.ENGLISH) Text("Basic event/date rules support these languages. Review dates, attendance and place; unclear phrases stay unknown.", style = MaterialTheme.typography.bodySmall)
                            if (listening.recognitionDiagnostics.isNotEmpty()) {
                                Text("Time checks", style = MaterialTheme.typography.titleSmall)
                                listening.recognitionDiagnostics.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                            if (listening.chunks > 0) Text("Phrases: likely you ${listening.userVoiceChunks} · other ${listening.otherVoiceChunks} · unassigned ${listening.unknownVoiceChunks}", style = MaterialTheme.typography.bodySmall)
                            if (listening.voiceDiagnostics.isNotEmpty()) {
                                TextButton(onClick = { showVoiceDiagnostics = !showVoiceDiagnostics }) { Text(if (showVoiceDiagnostics) "Hide voice details" else "Voice details") }
                                if (showVoiceDiagnostics) {
                                    listening.recognizer?.let { Text("Last speech engine: $it · on this phone", style = MaterialTheme.typography.bodySmall) }
                                    Text("Telling voices apart took ${listening.diarizationMs} ms · ${listening.speakerParts} parts", style = MaterialTheme.typography.bodySmall)
                                    listening.voiceDiagnostics.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                            if (!listening.recording && !listening.processing) {
                                TextButton(onClick = { wavPicker.launch(arrayOf("audio/wav", "audio/x-wav", "audio/wave")) }, enabled = !busy) { Text("Import a recording (WAV file)") }
                            }
                                if (listening.lastLatencyMs > 0) QuietLabel("Last phrase took ${listening.lastLatencyMs / 1000.0}s")
                                QuietLabel(if (handsOff) "Personal plans with clear times get reminders. Other voices stay information-only. Undo automatic changes from notifications. Set up your voice in Settings."
                                    else "Only approved items are saved. Set up your voice in Settings for personal plans. Voice matching can be wrong.")
                            }
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                if (!plannerOpen && manualNotes) item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Write a note", style = MaterialTheme.typography.titleLarge)
                            OutlinedTextField(value = transcript, onValueChange = { model.setTranscript(it) },
                                label = { Text("Transcript or typed words") }, modifier = Modifier.fillMaxWidth().testTag("transcript"), minLines = 2)
                            FlowRow {
                                TextButton(onClick = { model.setTranscript("I'll call Emma tomorrow at 5 pm. I will send the report on Friday.", Instant.now()); consent = false }) { Text("Use an example") }
                                TextButton(onClick = { model.clearTranscript(); consent = false }) { Text("Clear text") }
                            }
                            Row {
                                Checkbox(checked = consent, onCheckedChange = { consent = it }, modifier = Modifier.testTag("consent").controlLabel("These are my words; analyze and save matching commitments"))
                                Text("These are my words. Save matching commitments on this device.", Modifier.weight(1f).padding(top = 10.dp))
                            }
                            Button(onClick = model::analyze, enabled = consent && transcript.isNotBlank() && !busy && !listening.recording && !listening.processing) {
                                Text(if (busy) "Analyzing…" else "Find commitments")
                            }
                            TextButton(onClick = { model.analyzeSharedText(transcript); consent = false },
                                enabled = transcript.isNotBlank() && !busy && !listening.recording && !listening.processing) {
                                Text("Analyze as message text")
                            }
                            TextButton(onClick = { showNoteTools = !showNoteTools }, modifier = Modifier.testTag("note-tools")) { Text(if (showNoteTools) "Hide note tools" else "More note tools") }
                            if (showNoteTools) {
                                QuietLabel("Notes and live speech use the same local rules; unclear details stay unknown.")
                            }
                            Text(message, style = MaterialTheme.typography.bodySmall)
                            if (busy) TextButton(onClick = model::cancelImport) { Text("Cancel current operation") }
                        }
                    }
                }
                item {
                    Text(if (plannerOpen) "Your plans" else "Recent suggestions", style = MaterialTheme.typography.titleLarge)
                    QuietLabel("Manual entry, event times and reminders are optional.")
                    if (plannerOpen) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlannerView.entries.forEach { view ->
                                val label = when (view) {
                                    PlannerView.REVIEW -> "To check"
                                    PlannerView.REMINDERS -> "With reminder"
                                    PlannerView.UNSCHEDULED -> "No reminder"
                                    PlannerView.HISTORY -> "Past"
                                    PlannerView.ALL -> "All"
                                }
                                FilterChip(selected = selectedView == view, onClick = { selectedView = view },
                                    label = { Text("$label (${Planner.select(references, view).size})", maxLines = 1) }, modifier = Modifier.testTag("filter-${view.name}"))
                            }
                        }
                        OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Search plans") },
                            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("planner-search"))
                    }
                    if (manualNotes) TextButton(onClick = { model.addManual(); selectedView = PlannerView.REVIEW; search = "" }, modifier = Modifier.testTag("manual-add")) { Text("Add a plan") }
                }
                if (shownEntries.isEmpty()) item { Text(if (plannerOpen) "No plans here." else "Plans you mention will appear here.") }
                items(shownEntries, key = { it.id }) { entry ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium)
                            QuietLabel("“${entry.evidence.take(180)}${if (entry.evidence.length > 180) "…" else ""}”")
                            Text(planStateLabel(entry.state), color = MaterialTheme.colorScheme.primary)
                            entry.dueAt?.let {
                                Text("Reminder: ${TimeResolver.format(Instant.ofEpochMilli(it), ZoneId.of(entry.zoneId))} · ${entry.zoneId}")
                                if (entry.state == "ACCEPTED" && it < System.currentTimeMillis())
                                    Text("Reminder time has passed. Mark done or choose another time.", color = MaterialTheme.colorScheme.error)
                            }
                            if (entry.activityStartAt != null || entry.activityEndAt != null) {
                                fun activityTime(value: Long?) = value?.let { TimeResolver.format(Instant.ofEpochMilli(it), ZoneId.of(entry.zoneId)) } ?: "Unknown"
                                Text("Activity: ${activityTime(entry.activityStartAt)} – ${activityTime(entry.activityEndAt)} · ${entry.zoneId}")
                            }
                            if (entry.informational) Text("For your information · doesn’t block your calendar")
                            if (entry.sourceKey?.startsWith("uncertain:") == true) Text("Possible change to a plan · please check it")
                            else if (entry.sourceKey?.startsWith("confirm:") == true) Text("Unclear time · check the time before accepting", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("confirm-time-${entry.id}"))
                            else if (entry.sourceKey?.startsWith("voice:") == true) Text("Heard while listening · check who said it")
                            else if (entry.sourceKey != null) Text("From a message · you may not be going")
                            entry.placeName?.let { Text("Place: $it") }
                            entry.placeAddress?.let { Text(it) }
                            Text(entry.reviewNote, style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { openEditor(entry) }, modifier = Modifier.testTag("review-${entry.id}")) { Text(when (entry.state) {
                                    "PROPOSED" -> "Review & accept"
                                    "DONE", "DISMISSED", "CANCELLED" -> "Reopen"
                                    else -> "Edit"
                                }) }
                                if (entry.state == "PROPOSED") TextButton(onClick = { model.dismiss(entry.id) }) { Text("Dismiss") }
                                if (entry.state == "ACCEPTED") TextButton(onClick = { model.done(entry.id) }) { Text("Done") }
                            }
                            TextButton(onClick = { model.delete(entry.id) }) { Text("Delete") }
                        }
                    }
                }
                if (!plannerOpen && entries.size > 3) item {
                    TextButton(onClick = { plannerOpen = true; selectedView = PlannerView.ALL }, modifier = Modifier.testTag("all-commitments")) { Text("See all ${entries.size} plans") }
                }
                item {
                    if (plannerOpen && entries.isNotEmpty()) TextButton(onClick = { deleteDialog = true }) { Text("Delete all saved plans") }
                    if (plannerOpen) TextButton(onClick = { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }) { Text("Notification settings") }
                    QuietLabel("Listen only when everyone agrees. Optional place lookup uses the internet.")
                }
                }
            }
            }
        }
        namingVoice?.let { key ->
            var name by remember(key) { mutableStateOf("") }
            AlertDialog(onDismissRequest = { namingVoice = null }, title = { Text("Name this voice until listening stops") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.testTag("voice-name-field"))
                    QuietLabel("Forgotten when listening stops. Never saved.")
                } },
                confirmButton = { TextButton(enabled = com.sidekick.core.SessionVoices.cleanName(name) != null, onClick = {
                    try { startService(Intent(this@MainActivity, ListeningService::class.java).setAction(ListeningService.NAME_VOICE)
                        .putExtra(ListeningService.VOICE_KEY, key).putExtra(ListeningService.VOICE_NAME, name)) }
                    catch (_: Exception) { error = "Could not name this voice." }
                    namingVoice = null
                }, modifier = Modifier.testTag("voice-name-save")) { Text("Name") } },
                dismissButton = { TextButton(onClick = { namingVoice = null }) { Text("Cancel") } })
        }
        if (preciseDialog) AlertDialog(onDismissRequest = { preciseDialog = false; (application as SidekickApplication).guidance.markPreciseLocationAsked(); requestListening() },
            title = { Text("Precise location?") },
            text = { Text("More accurate state detection near borders. Location is used only on this phone and never saved. Without it, SideKick uses wider border margins.") },
            confirmButton = { TextButton(onClick = {
                preciseDialog = false; (application as SidekickApplication).guidance.markPreciseLocationAsked()
                preciseLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }, modifier = Modifier.testTag("precise-location-allow")) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { preciseDialog = false; (application as SidekickApplication).guidance.markPreciseLocationAsked(); requestListening() }) { Text("Not now") } })
        if (voiceConsentDialog) AlertDialog(onDismissRequest = { voiceConsentDialog = false; requestListening() },
            title = { Text("Voice consent needed") },
            text = { Text(if (voiceConsentNeeded) "Your saved voice setup needs your written consent. Until then, voice matching is off." else "Thanks. Voice matching is on.") },
            confirmButton = { TextButton(onClick = {
                voiceConsentDialog = false
                startActivity(Intent(this@MainActivity, com.sidekick.app.speaker.VoiceEnrollmentActivity::class.java).putExtra(com.sidekick.app.speaker.VoiceEnrollmentActivity.CONSENT_ONLY, true))
            }, modifier = Modifier.testTag("voice-consent-review")) { Text("Review consent") } },
            dismissButton = { TextButton(onClick = { voiceConsentDialog = false; requestListening() }) { Text("Listen without it") } })
        if (micDialog) ListeningStartDialog(
            settings = (application as SidekickApplication).guidance,
            handsOff = handsOff,
            recovery = assistantSelected && recoveryEnabled,
            onCancel = { micDialog = false },
            onStart = { micDialog = false; beginListening() })
        edit?.let { item ->
            key(item.id) {
                Editor(item, entries, editError, askAboutEnd = !handsOff && guidance == GuidanceMode.ASK,
                    onClose = { edit = null }, onSave = { title, time, zone, start, end, info, place, address -> model.save(item.id, title, time, zone, start, end, info, place, address) { edit = null } })
            }
        }
        if (settingsOpen) AppSettingsDialog((application as SidekickApplication).guidance) { settingsOpen = false; expandedCapture = false }
        if (quickStartOpen) QuickStart { quickStartOpen = false }
        sharedText?.takeIf { !handsOff || !sharedMessagesEnabled }?.let { text ->
            val sharing by (application as SidekickApplication).guidance.sharedMessages.collectAsStateWithLifecycle()
            AlertDialog(onDismissRequest = { sharedText = null }, title = { Text("Shared message") },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(if (sharing) "Analyze selected text for information-only activities? Sender plans are not assumed to be yours. " + (if (handsOff) "Detected events save automatically; full messages stay temporary." else "Nothing saves until approval.") else "Shared-message analysis is off. Enable it in Settings to analyze this text.")
                    Text(text.take(2000))
                } }, confirmButton = { TextButton(enabled = sharing, onClick = { model.analyzeSharedText(text); sharedText = null; plannerOpen = true; summaryOpen = false; moreOpen = false; expandedCapture = false; selectedView = PlannerView.REVIEW; search = "" }) { Text("Find activity suggestions") } },
                dismissButton = { TextButton(onClick = { sharedText = null }) { Text("Discard") } })
        }
        if (deleteDialog) AlertDialog(onDismissRequest = { deleteDialog = false }, title = { Text("Delete all saved plans?") },
            text = { Text("All plans and their reminders will be removed. This can’t be undone.") },
            confirmButton = { TextButton(onClick = { model.deleteAll(); deleteDialog = false }) { Text("Delete all") } },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Cancel") } })
    }
}

@Composable
private fun Editor(item: CommitmentEntity, entries: List<CommitmentEntity>, error: String?, askAboutEnd: Boolean, onClose: () -> Unit, onSave: (String, String, String, String, String, Boolean, String, String) -> Unit) {
    var title by remember { mutableStateOf(item.title) }
    var informational by remember { mutableStateOf(item.informational) }
    var placeName by remember { mutableStateOf(item.placeName.orEmpty()) }
    var placeAddress by remember { mutableStateOf(item.placeAddress.orEmpty()) }
    val zone = remember { ZoneId.of(item.zoneId) }
    val suggested = remember { item.dueAt?.let(Instant::ofEpochMilli) ?: TimeResolver.suggest(item.evidence, Instant.ofEpochMilli(item.capturedAt), zone) }
    var time by remember { mutableStateOf(suggested?.let { TimeResolver.format(it, zone) } ?: "") }
    var activityStart by remember { mutableStateOf(item.activityStartAt?.let { TimeResolver.format(Instant.ofEpochMilli(it), zone) } ?: "") }
    var activityEnd by remember { mutableStateOf(item.activityEndAt?.let { TimeResolver.format(Instant.ofEpochMilli(it), zone) } ?: "") }
    val due = runCatching { TimeResolver.parseEditor(time, zone).toEpochMilli() }.getOrNull()
    val clashes = due?.let { proposed ->
        val ids = Planner.sameReminderMinute(entries.map { PlannerEntry(it.id, it.state, it.dueAt, it.capturedAt) }, item.id, proposed).toSet()
        entries.filter { it.id in ids }.map { it.title }
    }.orEmpty()
    AlertDialog(onDismissRequest = onClose, title = { Text("Check this plan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (item.sourceKey?.startsWith("uncertain:") == true || item.sourceKey?.startsWith("confirm:") == true || item.sourceKey?.startsWith("review:") == true) Text(item.reviewNote, color = MaterialTheme.colorScheme.error)
                OutlinedTextField(value = title, onValueChange = { if (it.length <= 500) title = it }, label = { Text(if (informational) "Event or note" else "What will you do?") }, modifier = Modifier.testTag("edit-title"))
                Row {
                    Checkbox(informational, { informational = it }, modifier = Modifier.testTag("event-informational").controlLabel("Information only; attendance is unconfirmed"))
                    Text("For information only; I’m not planning to attend.", Modifier.padding(top = 12.dp))
                }
                DateTimeChoice("Reminder", time, zone, "edit-time", "pick-reminder", onChange = { time = it })
                Text("Timezone: ${zone.id}. Check suggested times; choose No reminder to clear it.")
                if (!informational && clashes.isNotEmpty()) Text("Other reminders in this minute: ${clashes.take(3).joinToString()}. This does not check event durations or your calendar.")
                Column(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { time = TimeResolver.format(Instant.now().plusSeconds(120), zone) }, modifier = Modifier.fillMaxWidth()) { Text("In 2 minutes", maxLines = 1) }
                    TextButton(onClick = { time = TimeResolver.format(Instant.now().plusSeconds(3600), zone) }, modifier = Modifier.fillMaxWidth()) { Text("In 1 hour", maxLines = 1) }
                    TextButton(onClick = { time = "" }, modifier = Modifier.fillMaxWidth()) { Text("No reminder", maxLines = 1) }
                }
                CalendarSection(activityStart, activityEnd, zone, informational = informational, askAboutEnd = askAboutEnd &&
                    (activityStart.isNotBlank() || com.sidekick.core.ActivityTiming.looksLikeActivity(item.title)),
                    memoryId = item.id,
                    onStart = { activityStart = it }, onEnd = { activityEnd = it })
                PlaceSection(placeName, placeAddress, onName = {
                    if (!placeName.trim().equals(it.trim(), true)) placeAddress = ""
                    placeName = it
                }, onAddress = { placeAddress = it })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(onClick = { onSave(title, time, zone.id, activityStart, activityEnd, informational, placeName, placeAddress) }, enabled = title.isNotBlank()) { Text(if (item.sourceKey?.startsWith("uncertain:") == true) "Confirm & apply update" else "Save & accept") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}
