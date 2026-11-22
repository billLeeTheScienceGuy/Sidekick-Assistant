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
    override fun attachBaseContext(newBase: android.content.Context) { super.attachBaseContext(AppLanguage.wrap(newBase)) }
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
        var settingsOpen by rememberSaveable { mutableStateOf(false) }
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
        SideEffect { CrashReports.screen = when { settingsOpen -> "Settings"; plannerOpen -> "Plans"; summaryOpen -> "Summary"; moreOpen -> "More"; quickStartOpen -> "Quick start"; else -> "Home" } }
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
            catch (e: Exception) { error = tr(R.string.could_not_change_listening_try_again) }
        }
        fun startRecording() {
            if (missingPacks.isNotEmpty()) { error = tr(R.string.speech_for_this_language_isn_t); return }
            try { startForegroundService(Intent(this@MainActivity, ListeningService::class.java)) }
            catch (e: Exception) { error = tr(R.string.could_not_start_recording_reopen_the) }
        }
        val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startRecording() else error = tr(R.string.microphone_permission_was_denied_you_can)
        }
        val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (awaitingLiveNotifications) {
                awaitingLiveNotifications = false
                if (granted) microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                else error = tr(R.string.enable_notifications_for_listening_controls_then)
            } else if (!granted) error = tr(R.string.notifications_are_disabled_you_can_save)
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
                                modifier = Modifier.heightIn(min = SimpleTarget).testTag("simple-back")) { Text(tr(R.string.back_to_more), style = MaterialTheme.typography.titleMedium) }
                        Text(if (summaryOpen) tr(R.string.summary) else if (plannerOpen) { if (oneScreen) tr(R.string.plans) else tr(R.string.planner) } else if (oneScreen) tr(R.string.what_was_heard) else tr(R.string.capture),
                            style = MaterialTheme.typography.headlineMedium)
                        QuietLabel(if (summaryOpen) tr(R.string.your_day_at_a_glance) else if (plannerOpen) { if (oneScreen) tr(R.string.your_plans_and_reminders_every_action) else tr(R.string.plans_reminders_and_things_to_review) }
                            else tr(R.string.turn_conversations_into_plans))
                        if (oneScreen) Text(if (handsOff) tr(R.string.plans_save_automatically_you_can_undo) else tr(R.string.plans_wait_until_you_check_them),
                            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("automation-status"))
                        else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                SuggestionChip(onClick = { settingsOpen = true }, label = { Text(if (handsOff) tr(R.string.hands_off) else tr(R.string.review_first)) })
                                TextButton(onClick = { quickStartOpen = true }, modifier = Modifier.testTag("get-started")) { Text(tr(R.string.set_up_speech)) }
                            }
                            QuietLabel(if (handsOff) tr(R.string.hands_off_mode_1_s, automaticStatus) else tr(R.string.review_mode_approve_items_to_save), Modifier.testTag("automation-status"))
                        }
                    }
                }
                item { CrashReportCard() }
                item { RiskSection() }
                if (summaryOpen && (listening.recording || listening.processing)) item {
                    Text(listening.note)
                    if (listening.recording) Button(onClick = { serviceAction(ListeningService.FINISH) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(tr(R.string.stop_listening)) }
                }
                if (summaryOpen) item { SummarySection(entries, liveText, !listening.recording && !listening.processing, model::clearRecentConversation, ::openEditor) }
                else {
                if (plannerOpen && (listening.recording || listening.processing)) item {
                    Text(listening.note)
                    if (listening.recording) Button(onClick = { serviceAction(ListeningService.FINISH) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(tr(R.string.stop_listening)) }
                    TextButton(onClick = { serviceAction(ListeningService.CANCEL) }) { Text(if (handsOff) tr(R.string.stop_clear_temporary_text) else tr(R.string.stop_discard_what_was_heard)) }
                }
                if (plannerOpen && !remindersReady) item {
                    Text(tr(R.string.reminder_alerts_are_off_your_plans), modifier = Modifier.testTag("reminders-blocked"))
                    TextButton(onClick = { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }) { Text(tr(R.string.notification_settings)) }
                }
                if (plannerOpen && automaticChanges.isNotEmpty()) {
                    item { Text(tr(R.string.recently_saved_for_you), style = MaterialTheme.typography.titleMedium) }
                    items(automaticChanges, key = { "automatic-${it.id}" }) { change ->
                        Card(Modifier.fillMaxWidth().testTag("automatic-change-${change.id}")) {
                            Column(Modifier.padding(16.dp)) {
                                Text("${change.action}: ${change.title}")
                                TextButton(onClick = { model.undoAutomatic(change.id, change.token) }, modifier = Modifier.testTag("undo-${change.id}")) { Text(tr(R.string.undo)) }
                            }
                        }
                    }
                }
                if (!plannerOpen) item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(tr(R.string.live_notes), style = MaterialTheme.typography.titleLarge)
                            QuietLabel(tr(R.string.n_1_s_on_device, speechLanguage.label))
                            Text(listening.note)
                            if (listening.recording) {
                                Text(if (listening.speechActive) tr(R.string.speech_detected) else tr(R.string.waiting_for_speech_microphone_stays_on))
                                Text(listening.voiceStatus)
                                if (listening.consent.isNotBlank()) QuietLabel(listening.consent, Modifier.testTag("consent-status"))
                                if (listening.droppedChunks > 0) QuietLabel(trn(R.plurals.phrases_dropped, listening.droppedChunks, listening.droppedChunks))
                                if (listening.callActive) QuietLabel(tr(R.string.call_detected_strictest_rule), Modifier.testTag("call-status"))
                                if (listening.answer.isNotBlank()) Text(listening.answer, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("plan-answer"))
                                RestingTip(listening)
                                if (listening.noticeNeeded || alwaysAnnounce) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { serviceAction(ListeningService.ANNOUNCE) }, modifier = Modifier.testTag("announce-notice")) {
                                        Text(if (listening.noticeActive) tr(R.string.announce_again) else tr(R.string.announce_note_taking))
                                    }
                                    TextButton(onClick = { serviceAction(ListeningService.NOTICE_GIVEN) }, modifier = Modifier.testTag("notice-given")) { Text(listening.attestLabel) }
                                }
                                nameSuggestion?.takeIf { listening.namingAllowed }?.let { suggestion ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        AssistChip(onClick = {
                                            try { startService(Intent(this@MainActivity, ListeningService::class.java).setAction(ListeningService.NAME_VOICE)
                                                .putExtra(ListeningService.VOICE_KEY, suggestion.voiceKey).putExtra(ListeningService.VOICE_NAME, suggestion.name)) }
                                            catch (_: Exception) { error = tr(R.string.could_not_name_this_voice) }
                                        }, label = { Text("${suggestion.name}?") }, modifier = Modifier.testTag("name-suggestion"))
                                        TextButton(onClick = { (application as SidekickApplication).temporary.dismissNameSuggestion() }) { Text(tr(R.string.not_now)) }
                                    }
                                }
                                LinearProgressIndicator(progress = { (listening.level * 5).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                Text(tr(R.string.n_1_sm_2_ss_3_s, listening.seconds / 60, listening.seconds % 60, listening.chunks))
                                AskHint()
                                Button(onClick = { serviceAction(ListeningService.FINISH) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(tr(R.string.stop_listening)) }
                            } else if (listening.processing) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else Button(onClick = { requestListening() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !busy && missingPacks.isEmpty()) { Text(tr(R.string.start_listening)) }
                            if (missingPacks.isNotEmpty()) Text(tr(R.string.speech_files_needed_tap_set_up))
                            if (listening.recording || listening.processing)
                                TextButton(onClick = { serviceAction(ListeningService.CANCEL) }) { Text(if (handsOff) tr(R.string.stop_clear_temporary_text) else tr(R.string.stop_discard_what_was_heard)) }
                            if (liveText.isNotBlank()) {
                                Text(tr(R.string.recent_live_text), style = MaterialTheme.typography.titleMedium)
                                Text(liveText.takeLast(if (showFullLiveText) 3000 else 600), modifier = Modifier.testTag("live-text"))
                                if (liveText.length > 600) TextButton(onClick = { showFullLiveText = !showFullLiveText }) { Text(if (showFullLiveText) tr(R.string.show_less_text) else tr(R.string.show_more_text)) }
                                if (speakerTurns.isNotEmpty()) {
                                    TextButton(onClick = { showSpeakerTurns = !showSpeakerTurns }, modifier = Modifier.testTag("speaker-turns-toggle")) { Text(if (showSpeakerTurns) tr(R.string.hide_who_said_what) else tr(R.string.show_who_said_what)) }
                                    if (showSpeakerTurns) {
                                        Text(tr(R.string.voice_labels_are_anonymous_and_last) +
                                            if (listening.namingAllowed && listening.recording) tr(R.string.tap_a_voice_label_to_name) else "", style = MaterialTheme.typography.bodySmall)
                                        speakerTurns.forEachIndexed { index, turn ->
                                            val time = turn.at.atZone(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                                            val nameable = turn.voiceKey != null && listening.namingAllowed && listening.recording
                                            Text("$time · ${turn.label} · ${if (turn.speaker == com.sidekick.core.Speaker.USER) "likely you" else "identity unassigned"}", style = MaterialTheme.typography.labelLarge,
                                                modifier = if (nameable) Modifier.clickable { namingVoice = turn.voiceKey }.testTag("voice-label-$index") else Modifier)
                                            Text(turn.text, modifier = Modifier.testTag("speaker-turn-$index"))
                                        }
                                    }
                                }
                                TextButton(onClick = { model.copyLiveText(); consent = false }) { Text(tr(R.string.copy_recent_text_for_editing)) }
                            }
                            QuietLabel(tr(R.string.audio_is_discarded_recent_text_is))
                            TextButton(onClick = { showSessionDetails = !showSessionDetails }, modifier = Modifier.testTag("session-details")) {
                                Text(if (showSessionDetails) tr(R.string.hide_listening_details) else tr(R.string.listening_details))
                            }
                            if (showSessionDetails) {
                            val sessionNotices = noticeRecords.filter { it.sessionId == listening.sessionId }
                            if (sessionNotices.isNotEmpty()) {
                                Text(tr(R.string.notice_record), style = MaterialTheme.typography.titleSmall)
                                sessionNotices.forEach { record ->
                                    val time = java.time.Instant.ofEpochMilli(record.noticeAt).atZone(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                                    val how = when (record.method) { "SPOKEN" -> "spoken"; "SPOKEN_AUTO" -> tr(R.string.spoken_automatically); else -> tr(R.string.you_told_everyone) }
                                    val delivery = if (record.audible == null) "" else tr(R.string.volume_1_s, record.volumePercent ?: 0) +
                                        if (record.audible == true) tr(R.string.heard_by_the_phone) else tr(R.string.too_quiet_not_counted)
                                    Text("$time · $how$delivery · ${record.stateCodes.ifBlank { "unknown place" }} · " +
                                        tr(R.string.n_1_s_voice_2_s, if (record.recordingRule == "ALL_PARTY") "all-party" else "one-party", record.biometricRule.lowercase()) +
                                        (if (record.includesVoice) tr(R.string.incl_voice_line) else "") + tr(R.string.text_v_1_s, record.textVersion),
                                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("notice-record"))
                                }
                            }
                            Text(tr(R.string.speech_1_s_turned_into_text, speechLanguage.label))
                            Text(tr(R.string.new_times_use_phone_timezone_1, ZoneId.systemDefault().id), style = MaterialTheme.typography.bodySmall)
                            Text(if (speechQuality.usesSenseVoice(speechLanguage)) "SenseVoice int8 · ${speechLanguage.label} · on-device" else if (speechQuality.usesOmni(speechLanguage, true)) "Omnilingual int8 · ${speechLanguage.label} · on-device" else speechQuality.label, style = MaterialTheme.typography.bodySmall)
                            if (speechLanguage != com.sidekick.core.SpeechLanguage.ENGLISH) Text(tr(R.string.check_dates_and_places_unclear_phrases), style = MaterialTheme.typography.bodySmall)
                            if (listening.recognitionDiagnostics.isNotEmpty()) {
                                Text(tr(R.string.time_checks), style = MaterialTheme.typography.titleSmall)
                                listening.recognitionDiagnostics.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                            if (listening.chunks > 0) Text(tr(R.string.phrases_likely_you_1_s_other, listening.userVoiceChunks, listening.otherVoiceChunks, listening.unknownVoiceChunks), style = MaterialTheme.typography.bodySmall)
                            if (listening.voiceDiagnostics.isNotEmpty()) {
                                TextButton(onClick = { showVoiceDiagnostics = !showVoiceDiagnostics }) { Text(if (showVoiceDiagnostics) tr(R.string.hide_voice_details) else tr(R.string.voice_details)) }
                                if (showVoiceDiagnostics) {
                                    listening.recognizer?.let { Text("Last speech engine: $it · on this phone", style = MaterialTheme.typography.bodySmall) }
                                    Text("Telling voices apart took ${listening.diarizationMs} ms · ${listening.speakerParts} parts", style = MaterialTheme.typography.bodySmall)
                                    listening.voiceDiagnostics.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                            if (!listening.recording && !listening.processing) {
                                TextButton(onClick = { wavPicker.launch(arrayOf("audio/wav", "audio/x-wav", "audio/wave")) }, enabled = !busy) { Text(tr(R.string.import_a_recording_wav_file)) }
                            }
                                if (listening.lastLatencyMs > 0) QuietLabel("Last phrase took ${listening.lastLatencyMs / 1000.0}s")
                                QuietLabel(if (handsOff) tr(R.string.your_plans_with_clear_times_get)
                                    else tr(R.string.only_approved_items_are_saved_set))
                            }
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                if (!plannerOpen && manualNotes) item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(tr(R.string.write_a_note), style = MaterialTheme.typography.titleLarge)
                            OutlinedTextField(value = transcript, onValueChange = { model.setTranscript(it) },
                                label = { Text(tr(R.string.transcript_or_typed_words)) }, modifier = Modifier.fillMaxWidth().testTag("transcript"), minLines = 2)
                            FlowRow {
                                TextButton(onClick = { model.setTranscript(tr(R.string.i_ll_call_emma_tomorrow_at), Instant.now()); consent = false }) { Text(tr(R.string.use_an_example)) }
                                TextButton(onClick = { model.clearTranscript(); consent = false }) { Text(tr(R.string.clear_text)) }
                            }
                            Row {
                                Checkbox(checked = consent, onCheckedChange = { consent = it }, modifier = Modifier.testTag("consent").controlLabel(tr(R.string.these_are_my_words_analyze_and)))
                                Text(tr(R.string.these_are_my_words_save_matching), Modifier.weight(1f).padding(top = 10.dp))
                            }
                            Button(onClick = model::analyze, enabled = consent && transcript.isNotBlank() && !busy && !listening.recording && !listening.processing) {
                                Text(if (busy) tr(R.string.analyzing) else tr(R.string.find_commitments))
                            }
                            TextButton(onClick = { model.analyzeSharedText(transcript); consent = false },
                                enabled = transcript.isNotBlank() && !busy && !listening.recording && !listening.processing) {
                                Text(tr(R.string.analyze_as_message_text))
                            }
                            TextButton(onClick = { showNoteTools = !showNoteTools }, modifier = Modifier.testTag("note-tools")) { Text(if (showNoteTools) tr(R.string.hide_note_tools) else tr(R.string.more_note_tools)) }
                            if (showNoteTools) {
                                QuietLabel(tr(R.string.notes_and_live_speech_use_the))
                            }
                            Text(message, style = MaterialTheme.typography.bodySmall)
                            if (busy) TextButton(onClick = model::cancelImport) { Text(tr(R.string.cancel_current_operation)) }
                        }
                    }
                }
                item {
                    Text(if (plannerOpen) tr(R.string.your_plans) else tr(R.string.recent_suggestions), style = MaterialTheme.typography.titleLarge)
                    QuietLabel(tr(R.string.manual_entry_event_times_and_reminders))
                    if (plannerOpen) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlannerView.entries.forEach { view ->
                                val label = when (view) {
                                    PlannerView.REVIEW -> tr(R.string.to_check)
                                    PlannerView.REMINDERS -> tr(R.string.with_reminder)
                                    PlannerView.UNSCHEDULED -> tr(R.string.no_reminder)
                                    PlannerView.HISTORY -> tr(R.string.past)
                                    PlannerView.ALL -> tr(R.string.all)
                                }
                                FilterChip(selected = selectedView == view, onClick = { selectedView = view },
                                    label = { Text("$label (${Planner.select(references, view).size})", maxLines = 1) }, modifier = Modifier.testTag("filter-${view.name}"))
                            }
                        }
                        OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text(tr(R.string.search_plans)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("planner-search"))
                    }
                    if (manualNotes) TextButton(onClick = { model.addManual(); selectedView = PlannerView.REVIEW; search = "" }, modifier = Modifier.testTag("manual-add")) { Text(tr(R.string.add_a_plan)) }
                }
                if (shownEntries.isEmpty()) item { Text(if (plannerOpen) tr(R.string.no_plans_here) else tr(R.string.plans_you_mention_will_appear_here)) }
                items(shownEntries, key = { it.id }) { entry ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium)
                            QuietLabel("“${entry.evidence.take(180)}${if (entry.evidence.length > 180) "…" else ""}”")
                            Text(planStateLabel(entry.state), color = MaterialTheme.colorScheme.primary)
                            entry.dueAt?.let {
                                Text(tr(R.string.reminder_1_s_2_s, TimeResolver.format(Instant.ofEpochMilli(it), ZoneId.of(entry.zoneId)), entry.zoneId))
                                if (entry.state == "ACCEPTED" && it < System.currentTimeMillis())
                                    Text(tr(R.string.reminder_time_has_passed_mark_done), color = MaterialTheme.colorScheme.error)
                            }
                            if (entry.activityStartAt != null || entry.activityEndAt != null) {
                                fun activityTime(value: Long?) = value?.let { TimeResolver.format(Instant.ofEpochMilli(it), ZoneId.of(entry.zoneId)) } ?: tr(R.string.unknown)
                                Text(tr(R.string.activity_1_s_2_s_3, activityTime(entry.activityStartAt), activityTime(entry.activityEndAt), entry.zoneId))
                            }
                            if (entry.informational) Text(tr(R.string.for_your_information_doesn_t_block))
                            if (entry.sourceKey?.startsWith("uncertain:") == true) Text(tr(R.string.possible_change_to_a_plan_please))
                            else if (entry.sourceKey?.startsWith("confirm:") == true) Text(tr(R.string.unclear_time_check_the_time_before), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("confirm-time-${entry.id}"))
                            else if (entry.sourceKey?.startsWith("voice:") == true) Text(tr(R.string.heard_while_listening_check_who_said))
                            else if (entry.sourceKey != null) Text(tr(R.string.from_a_message_you_may_not))
                            entry.placeName?.let { Text(tr(R.string.place_1_s, it)) }
                            entry.placeAddress?.let { Text(it) }
                            Text(entry.reviewNote, style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { openEditor(entry) }, modifier = Modifier.testTag("review-${entry.id}")) { Text(when (entry.state) {
                                    "PROPOSED" -> tr(R.string.review_accept)
                                    "DONE", "DISMISSED", "CANCELLED" -> tr(R.string.reopen)
                                    else -> tr(R.string.edit)
                                }) }
                                if (entry.state == "PROPOSED") TextButton(onClick = { model.dismiss(entry.id) }) { Text(tr(R.string.dismiss)) }
                                if (entry.state == "ACCEPTED") TextButton(onClick = { model.done(entry.id) }) { Text(tr(R.string.done)) }
                            }
                            TextButton(onClick = { model.delete(entry.id) }) { Text(tr(R.string.delete)) }
                        }
                    }
                }
                if (!plannerOpen && entries.size > 3) item {
                    TextButton(onClick = { plannerOpen = true; selectedView = PlannerView.ALL }, modifier = Modifier.testTag("all-commitments")) { Text(trn(R.plurals.see_all_plans_count, entries.size, entries.size)) }
                }
                item {
                    if (plannerOpen && entries.isNotEmpty()) TextButton(onClick = { deleteDialog = true }) { Text(tr(R.string.delete_all_saved_plans)) }
                    if (plannerOpen) TextButton(onClick = { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }) { Text(tr(R.string.notification_settings)) }
                    QuietLabel(tr(R.string.listen_only_when_everyone_agrees_optional))
                }
                }
            }
            }
        }
        namingVoice?.let { key ->
            var name by remember(key) { mutableStateOf("") }
            AlertDialog(onDismissRequest = { namingVoice = null }, title = { Text(tr(R.string.name_this_voice_until_listening_stops)) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text(tr(R.string.name)) }, singleLine = true, modifier = Modifier.testTag("voice-name-field"))
                    QuietLabel(tr(R.string.forgotten_when_listening_stops_never_saved))
                } },
                confirmButton = { TextButton(enabled = com.sidekick.core.SessionVoices.cleanName(name) != null, onClick = {
                    try { startService(Intent(this@MainActivity, ListeningService::class.java).setAction(ListeningService.NAME_VOICE)
                        .putExtra(ListeningService.VOICE_KEY, key).putExtra(ListeningService.VOICE_NAME, name)) }
                    catch (_: Exception) { error = tr(R.string.could_not_name_this_voice) }
                    namingVoice = null
                }, modifier = Modifier.testTag("voice-name-save")) { Text(tr(R.string.name)) } },
                dismissButton = { TextButton(onClick = { namingVoice = null }) { Text(tr(R.string.cancel)) } })
        }
        if (preciseDialog) AlertDialog(onDismissRequest = { preciseDialog = false; (application as SidekickApplication).guidance.markPreciseLocationAsked(); requestListening() },
            title = { Text(tr(R.string.precise_location)) },
            text = { Text(tr(R.string.more_accurate_state_detection_near_borders)) },
            confirmButton = { TextButton(onClick = {
                preciseDialog = false; (application as SidekickApplication).guidance.markPreciseLocationAsked()
                preciseLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }, modifier = Modifier.testTag("precise-location-allow")) { Text(tr(R.string.continue_text)) } },
            dismissButton = { TextButton(onClick = { preciseDialog = false; (application as SidekickApplication).guidance.markPreciseLocationAsked(); requestListening() }) { Text(tr(R.string.not_now)) } })
        if (voiceConsentDialog) AlertDialog(onDismissRequest = { voiceConsentDialog = false; requestListening() },
            title = { Text(tr(R.string.voice_consent_needed)) },
            text = { Text(if (voiceConsentNeeded) tr(R.string.your_saved_voice_setup_needs_your) else tr(R.string.thanks_voice_matching_is_on)) },
            confirmButton = { TextButton(onClick = {
                voiceConsentDialog = false
                startActivity(Intent(this@MainActivity, com.sidekick.app.speaker.VoiceEnrollmentActivity::class.java).putExtra(com.sidekick.app.speaker.VoiceEnrollmentActivity.CONSENT_ONLY, true))
            }, modifier = Modifier.testTag("voice-consent-review")) { Text(tr(R.string.review_consent)) } },
            dismissButton = { TextButton(onClick = { voiceConsentDialog = false; requestListening() }) { Text(tr(R.string.listen_without_it)) } })
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
            AlertDialog(onDismissRequest = { sharedText = null }, title = { Text(tr(R.string.shared_message)) },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(if (sharing) tr(R.string.analyze_selected_text_for_information_only) + (if (handsOff) tr(R.string.detected_events_save_automatically_full_messages) else tr(R.string.nothing_saves_until_approval)) else tr(R.string.shared_message_analysis_is_off_enable))
                    Text(text.take(2000))
                } }, confirmButton = { TextButton(enabled = sharing, onClick = { model.analyzeSharedText(text); sharedText = null; plannerOpen = true; summaryOpen = false; moreOpen = false; expandedCapture = false; selectedView = PlannerView.REVIEW; search = "" }) { Text(tr(R.string.find_activity_suggestions)) } },
                dismissButton = { TextButton(onClick = { sharedText = null }) { Text(tr(R.string.discard)) } })
        }
        if (deleteDialog) AlertDialog(onDismissRequest = { deleteDialog = false }, title = { Text(tr(R.string.delete_all_saved_plans_2)) },
            text = { Text(tr(R.string.all_plans_and_their_reminders_will)) },
            confirmButton = { TextButton(onClick = { model.deleteAll(); deleteDialog = false }) { Text(tr(R.string.delete_all)) } },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text(tr(R.string.cancel)) } })
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
    AlertDialog(onDismissRequest = onClose, title = { Text(tr(R.string.check_this_plan)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (item.sourceKey?.startsWith("uncertain:") == true || item.sourceKey?.startsWith("confirm:") == true || item.sourceKey?.startsWith("review:") == true) Text(item.reviewNote, color = MaterialTheme.colorScheme.error)
                OutlinedTextField(value = title, onValueChange = { if (it.length <= 500) title = it }, label = { Text(if (informational) tr(R.string.event_or_note) else tr(R.string.what_will_you_do)) }, modifier = Modifier.testTag("edit-title"))
                Row {
                    Checkbox(informational, { informational = it }, modifier = Modifier.testTag("event-informational").controlLabel(tr(R.string.information_only_attendance_is_unconfirmed)))
                    Text(tr(R.string.for_information_only_i_m_not), Modifier.padding(top = 12.dp))
                }
                DateTimeChoice(tr(R.string.reminder), time, zone, "edit-time", "pick-reminder", onChange = { time = it })
                Text(tr(R.string.timezone_1_s_check_suggested_times, zone.id))
                if (!informational && clashes.isNotEmpty()) Text(tr(R.string.other_reminders_in_this_minute_1, clashes.take(3).joinToString()))
                Column(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { time = TimeResolver.format(Instant.now().plusSeconds(120), zone) }, modifier = Modifier.fillMaxWidth()) { Text(tr(R.string.in_2_minutes), maxLines = 1) }
                    TextButton(onClick = { time = TimeResolver.format(Instant.now().plusSeconds(3600), zone) }, modifier = Modifier.fillMaxWidth()) { Text(tr(R.string.in_1_hour), maxLines = 1) }
                    TextButton(onClick = { time = "" }, modifier = Modifier.fillMaxWidth()) { Text(tr(R.string.no_reminder), maxLines = 1) }
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
        }, confirmButton = { TextButton(onClick = { onSave(title, time, zone.id, activityStart, activityEnd, informational, placeName, placeAddress) }, enabled = title.isNotBlank()) { Text(if (item.sourceKey?.startsWith("uncertain:") == true) tr(R.string.confirm_apply_update) else tr(R.string.save_accept)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(tr(R.string.cancel)) } })
}
