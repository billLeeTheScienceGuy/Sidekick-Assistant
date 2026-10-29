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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
        app.background.launch { runCatching { app.reminders.restore() } }
    }
    private var sharedText by mutableStateOf<String?>(null)
    private fun receiveShare(incoming: Intent?) {
        if (incoming?.action == Intent.ACTION_SEND && incoming.type == "text/plain")
            sharedText = runCatching { incoming.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.take(6000) }.getOrNull()?.takeIf { it.isNotBlank() }
        intent?.removeExtra(Intent.EXTRA_TEXT)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receiveShare(intent) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveShare(intent)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF176B58), background = Color(0xFFF6F8F5))) { SidekickScreen() }
        }
    }

    @Composable
    private fun SidekickScreen(model: MemoryViewModel = viewModel()) {
        val entries by model.items.collectAsStateWithLifecycle()
        val automaticChanges by model.automaticChanges.collectAsStateWithLifecycle()
        val message by model.message.collectAsStateWithLifecycle()
        val busy by model.busy.collectAsStateWithLifecycle()
        val transcript by model.transcript.collectAsStateWithLifecycle()
        val listening by ListeningService.state.collectAsStateWithLifecycle()
        val liveText by model.liveText.collectAsStateWithLifecycle()
        val smartReady by model.smartReady.collectAsStateWithLifecycle()
        val editError by model.editError.collectAsStateWithLifecycle()
        val guidance by model.guidance.collectAsStateWithLifecycle()
        val handsOff by (application as SidekickApplication).guidance.handsOffMode.collectAsStateWithLifecycle()
        val automaticStatus by (application as SidekickApplication).automation.status.collectAsStateWithLifecycle()
        val sharedMessagesEnabled by (application as SidekickApplication).guidance.sharedMessages.collectAsStateWithLifecycle()
        val speechLanguage by (application as SidekickApplication).guidance.speechLanguage.collectAsStateWithLifecycle()
        val speechQuality by (application as SidekickApplication).guidance.speechQuality.collectAsStateWithLifecycle()
        var settingsOpen by remember { mutableStateOf(false) }
        var consent by remember { mutableStateOf(false) }
        var micDialog by remember { mutableStateOf(false) }
        var showVoiceDiagnostics by remember { mutableStateOf(false) }
        var deleteDialog by remember { mutableStateOf(false) }
        var edit by remember { mutableStateOf<CommitmentEntity?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var awaitingLiveNotifications by remember { mutableStateOf(false) }
        var plannerOpen by rememberSaveable { mutableStateOf(false) }
        var selectedView by rememberSaveable { mutableStateOf(PlannerView.REVIEW) }
        var search by rememberSaveable { mutableStateOf("") }
        val references = entries.map { PlannerEntry(it.id, it.state, it.dueAt, it.capturedAt) }
        val byId = entries.associateBy { it.id }
        val shownEntries = if (plannerOpen) Planner.select(references, selectedView).mapNotNull { byId[it.id] }
            .filter { search.isBlank() || it.title.contains(search.trim(), true) || it.evidence.contains(search.trim(), true) }
            else entries
        LaunchedEffect(sharedText, handsOff, sharedMessagesEnabled) {
            if (handsOff && sharedMessagesEnabled) sharedText?.let { text ->
                sharedText = null; model.analyzeSharedText(text)
                plannerOpen = true; selectedView = PlannerView.ALL; search = ""
            }
        }

        fun serviceAction(action: String) {
            try { startService(Intent(this@MainActivity, ListeningService::class.java).setAction(action)) }
            catch (e: Exception) { error = "Could not update the recording session." }
        }
        fun startRecording() {
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
        val wavPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { consent = false; model.importWav(it) }
        }
        val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(model::importIntentModel)
        }
        fun openEditor(item: CommitmentEntity) {
            model.reopen(); edit = item
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LazyColumn(Modifier.safeDrawingPadding().imePadding().testTag("main-list"),
                contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Text("SideKick", style = MaterialTheme.typography.headlineLarge)
                    Text("A little less to remember", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { settingsOpen = true }, modifier = Modifier.testTag("settings")) { Text("Settings") }
                    Text(if (handsOff) "Hands-off mode · $automaticStatus" else "Review mode · approve items to save", modifier = Modifier.testTag("automation-status"))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(selected = !plannerOpen, onClick = { plannerOpen = false }, label = { Text("Capture") })
                        FilterChip(selected = plannerOpen, onClick = { plannerOpen = true }, label = { Text("Planner") }, modifier = Modifier.testTag("planner-tab"))
                    }
                }
                if (plannerOpen && (listening.recording || listening.processing)) item {
                    Text(listening.note)
                    if (listening.recording) Button(onClick = { serviceAction(ListeningService.FINISH) }) { Text("Finish listening") }
                    TextButton(onClick = { serviceAction(ListeningService.CANCEL) }) { Text(if (handsOff) "Stop & clear temporary text" else "Discard session") }
                }
                if (plannerOpen && !remindersReady) item {
                    Text("Reminder notifications are blocked. Your events are saved; enable notifications for alerts.", modifier = Modifier.testTag("reminders-blocked"))
                    TextButton(onClick = { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }) { Text("Notification settings") }
                }
                if (plannerOpen && automaticChanges.isNotEmpty()) {
                    item { Text("Recent automatic changes", style = MaterialTheme.typography.titleMedium) }
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
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Live notes", style = MaterialTheme.typography.titleLarge)
                            Text("Speech detection · ${speechLanguage.label} · on-device transcription")
                            Text("New times use phone timezone: ${ZoneId.systemDefault().id}", style = MaterialTheme.typography.bodySmall)
                            Text(if (speechQuality.usesSenseVoice(speechLanguage)) "SenseVoice int8 · ${speechLanguage.label} · on-device" else if (speechQuality.usesOmni(speechLanguage, true)) "Omnilingual int8 · ${speechLanguage.label} · on-device" else speechQuality.label, style = MaterialTheme.typography.bodySmall)
                            if (speechLanguage != com.sidekick.core.SpeechLanguage.ENGLISH) Text("Basic event/date rules support these languages. Review dates, attendance and place; unclear phrases stay unknown.", style = MaterialTheme.typography.bodySmall)
                            Text(listening.note)
                            if (listening.recognitionDiagnostics.isNotEmpty()) {
                                Text("Clock recognition checks", style = MaterialTheme.typography.titleSmall)
                                listening.recognitionDiagnostics.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                            if (listening.chunks > 0) Text("Voice chunks: likely you ${listening.userVoiceChunks} · other ${listening.otherVoiceChunks} · unassigned ${listening.unknownVoiceChunks}", style = MaterialTheme.typography.bodySmall)
                            if (listening.voiceDiagnostics.isNotEmpty()) {
                                TextButton(onClick = { showVoiceDiagnostics = !showVoiceDiagnostics }) { Text(if (showVoiceDiagnostics) "Hide voice diagnostics" else "Voice diagnostics") }
                                if (showVoiceDiagnostics) listening.voiceDiagnostics.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                            if (listening.recording) {
                                Text(if (listening.speechActive) "Speech detected" else "Waiting for speech · microphone stays on")
                                Text(listening.voiceStatus)
                                LinearProgressIndicator(progress = { (listening.level * 5).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                Text("${listening.seconds / 60}m ${listening.seconds % 60}s · ${listening.chunks} speech chunks")
                                Button(onClick = { serviceAction(ListeningService.FINISH) }) { Text("Finish listening") }
                            } else if (listening.processing) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else Button(onClick = { micDialog = true }, enabled = !busy) { Text("Start live notes") }
                            if (!listening.recording && !listening.processing) {
                                TextButton(onClick = { wavPicker.launch(arrayOf("audio/wav", "audio/x-wav", "audio/wave")) }, enabled = !busy) { Text("Import my WAV recording") }
                            }
                            if (listening.recording || listening.processing)
                                TextButton(onClick = { serviceAction(ListeningService.CANCEL) }) { Text(if (handsOff) "Stop & clear temporary text" else "Discard session") }
                            if (listening.lastLatencyMs > 0) Text("Last chunk processed in ${listening.lastLatencyMs / 1000.0}s", style = MaterialTheme.typography.bodySmall)
                            if (liveText.isNotBlank()) {
                                Text("Recent live text", style = MaterialTheme.typography.titleMedium)
                                Text(liveText.takeLast(3000), modifier = Modifier.testTag("live-text"))
                                TextButton(onClick = { model.copyLiveText(); consent = false }) { Text("Copy recent text for editing") }
                            }
                            Text(if (handsOff) "Events and matching corrections save automatically. Clear times on personal plans get reminders; others stay information-only. Use Undo in notifications to reverse a change. Stopping clears temporary text but keeps saved events. Set up your voice in Settings for personal plans."
                                else "Listening continues through pauses until you finish. Recent text is temporary; only approved items are saved. Set up your voice in Settings for personal plans. Other or uncertain voices produce information-only suggestions. Voice matches can be wrong.", style = MaterialTheme.typography.bodySmall)
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                if (!plannerOpen) item {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Edit or type notes", style = MaterialTheme.typography.titleLarge)
                            Text(if (smartReady) "Smart analysis · Qwen3-0.6B · installed" else "Basic rules · smart model not installed", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { modelPicker.launch(arrayOf("*/*")) }, enabled = !busy && !listening.recording && !listening.processing) {
                                Text(if (smartReady) "Replace local intent model" else "Import local intent model (484 MB)")
                            }
                            OutlinedTextField(value = transcript, onValueChange = { model.setTranscript(it) },
                                label = { Text("Transcript or typed words") }, modifier = Modifier.fillMaxWidth().testTag("transcript"), minLines = 3)
                            Row {
                                TextButton(onClick = { model.setTranscript("I'll call Emma tomorrow at 5 pm. I will send the report on Friday.", Instant.now()); consent = false }) { Text("Use an example") }
                                TextButton(onClick = { model.clearTranscript(); consent = false }) { Text("Clear text") }
                            }
                            Row {
                                Checkbox(checked = consent, onCheckedChange = { consent = it }, modifier = Modifier.testTag("consent"))
                                Text("These are my words. Save matching commitments on this device.", Modifier.padding(top = 10.dp))
                            }
                            Button(onClick = model::analyze, enabled = consent && transcript.isNotBlank() && !busy && !listening.recording && !listening.processing) {
                                Text(if (busy) "Analyzing…" else "Find commitments")
                            }
                            TextButton(onClick = { model.analyzeSharedText(transcript); consent = false },
                                enabled = transcript.isNotBlank() && !busy && !listening.recording && !listening.processing) {
                                Text("Analyze as message text")
                            }
                            Text(if (smartReady) "After listening finishes, copy recent text here and confirm it is yours. Smart analysis handles up to 4,000 characters. Live suggestions still use basic rules. Model suggestions can be wrong; review every result."
                                else "Commitment detection uses simple English rules until the optional Qwen model is imported. Review every result.", style = MaterialTheme.typography.bodySmall)
                            Text(message, style = MaterialTheme.typography.bodySmall)
                            if (busy) TextButton(onClick = model::cancelImport) { Text("Cancel current operation") }
                        }
                    }
                }
                item {
                    Text(if (plannerOpen) "Your planner" else "Your memory · ${entries.size}", style = MaterialTheme.typography.titleLarge)
                    Text("Review suggestions before saving. Manual entry, activity times and reminders are optional.")
                    if (plannerOpen) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlannerView.entries.forEach { view ->
                                val label = when (view) {
                                    PlannerView.REVIEW -> "Review"
                                    PlannerView.REMINDERS -> "Reminders"
                                    PlannerView.UNSCHEDULED -> "No reminder"
                                    PlannerView.HISTORY -> "History"
                                    PlannerView.ALL -> "All"
                                }
                                FilterChip(selected = selectedView == view, onClick = { selectedView = view },
                                    label = { Text("$label (${Planner.select(references, view).size})") }, modifier = Modifier.testTag("filter-${view.name}"))
                            }
                        }
                        OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Search commitments") },
                            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("planner-search"))
                    }
                    TextButton(onClick = { model.addManual(); selectedView = PlannerView.REVIEW; search = "" }) { Text("Add a commitment manually") }
                }
                if (shownEntries.isEmpty()) item { Text(if (plannerOpen) "No commitments in this view." else "No commitments yet. Try the example above.") }
                items(shownEntries, key = { it.id }) { entry ->
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium)
                            Text("“${entry.evidence}”")
                            Text(entry.state.lowercase().replaceFirstChar { it.uppercase() }, color = MaterialTheme.colorScheme.primary)
                            entry.dueAt?.let {
                                Text("Reminder: ${TimeResolver.format(Instant.ofEpochMilli(it), ZoneId.of(entry.zoneId))} · ${entry.zoneId}")
                                if (entry.state == "ACCEPTED" && it < System.currentTimeMillis())
                                    Text("Reminder time has passed. Mark done or choose another time.", color = MaterialTheme.colorScheme.error)
                            }
                            if (entry.activityStartAt != null || entry.activityEndAt != null) {
                                fun activityTime(value: Long?) = value?.let { TimeResolver.format(Instant.ofEpochMilli(it), ZoneId.of(entry.zoneId)) } ?: "Unknown"
                                Text("Activity: ${activityTime(entry.activityStartAt)} – ${activityTime(entry.activityEndAt)} · ${entry.zoneId}")
                            }
                            if (entry.informational) Text("Information only · not blocking your calendar")
                            if (entry.sourceKey?.startsWith("uncertain:") == true) Text("Possible conversation update · confirmation required")
                            else if (entry.sourceKey?.startsWith("voice:") == true) Text("From live speech · verify speaker attribution")
                            else if (entry.sourceKey != null) Text("From an incoming message · attendance unconfirmed")
                            entry.placeName?.let { Text("Place: $it") }
                            entry.placeAddress?.let { Text(it) }
                            Text(entry.reviewNote, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                item {
                    if (entries.isNotEmpty()) TextButton(onClick = { deleteDialog = true }) { Text("Delete all saved memory") }
                    TextButton(onClick = { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }) { Text("Notification settings") }
                    Text("Speech and intent run on this phone. Optional place lookup uses an online provider. Reminders may be delayed by battery saving. Raw audio is never saved.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (micDialog) AlertDialog(onDismissRequest = { micDialog = false }, title = { Text("Start live notes?") },
            text = { Text("Start when everyone included has agreed to live transcription. Stop if someone joins without agreeing or withdraws consent. Voice matching does not verify consent. Listening continues with the screen off. Audio is discarded after processing; recent text stays temporary. " +
                (if (handsOff) "Detected events and matching corrections save automatically, with Undo in notifications. Stopping does not delete saved events. " else "Only items you approve are saved. ") +
                (if ((application as SidekickApplication).assistant.isSelected() && (application as SidekickApplication).assistant.enabled.value) "Assistant recovery will resume this enabled session after system restarts, including reboots, until you press Stop. " else "") +
                "Use the notification controls to stop listening.") },
            confirmButton = { TextButton(onClick = {
                micDialog = false
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    awaitingLiveNotifications = true; notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            }) { Text("Start listening") } },
            dismissButton = { TextButton(onClick = { micDialog = false }) { Text("Cancel") } })
        edit?.let { item ->
            key(item.id) {
                Editor(item, entries, editError, askAboutEnd = !handsOff && guidance == GuidanceMode.ASK,
                    onClose = { edit = null }, onSave = { title, time, zone, start, end, info, place, address -> model.save(item.id, title, time, zone, start, end, info, place, address) { edit = null } })
            }
        }
        if (settingsOpen) AppSettingsDialog((application as SidekickApplication).guidance) { settingsOpen = false }
        sharedText?.takeIf { !handsOff || !sharedMessagesEnabled }?.let { text ->
            val sharing by (application as SidekickApplication).guidance.sharedMessages.collectAsStateWithLifecycle()
            AlertDialog(onDismissRequest = { sharedText = null }, title = { Text("Shared message") },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(if (sharing) "Analyze selected text for information-only activities? Sender plans are not assumed to be yours. " + (if (handsOff) "Detected events save automatically; full messages stay temporary." else "Nothing saves until approval.") else "Shared-message analysis is off. Enable it in Settings to analyze this text.")
                    Text(text.take(2000))
                } }, confirmButton = { TextButton(enabled = sharing, onClick = { model.analyzeSharedText(text); sharedText = null; plannerOpen = true; selectedView = PlannerView.REVIEW; search = "" }) { Text("Find activity suggestions") } },
                dismissButton = { TextButton(onClick = { sharedText = null }) { Text("Discard") } })
        }
        if (deleteDialog) AlertDialog(onDismissRequest = { deleteDialog = false }, title = { Text("Delete all saved memory?") },
            text = { Text("All commitments and their reminders will be removed.") },
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
    AlertDialog(onDismissRequest = onClose, title = { Text("Review commitment") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (item.sourceKey?.startsWith("uncertain:") == true) Text(item.reviewNote, color = MaterialTheme.colorScheme.error)
                OutlinedTextField(value = title, onValueChange = { if (it.length <= 500) title = it }, label = { Text(if (informational) "Event or note" else "What will you do?") }, modifier = Modifier.testTag("edit-title"))
                Row {
                    Checkbox(informational, { informational = it }, modifier = Modifier.testTag("event-informational"))
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
