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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sidekick.app.audio.ListeningService
import com.sidekick.app.data.CommitmentEntity
import com.sidekick.core.TimeResolver
import java.time.Instant
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF176B58), background = Color(0xFFF6F8F5))) { SidekickScreen() }
        }
    }

    @Composable
    private fun SidekickScreen(model: MemoryViewModel = viewModel()) {
        val entries by model.items.collectAsStateWithLifecycle()
        val message by model.message.collectAsStateWithLifecycle()
        val busy by model.busy.collectAsStateWithLifecycle()
        val transcript by model.transcript.collectAsStateWithLifecycle()
        val listening by ListeningService.state.collectAsStateWithLifecycle()
        val liveText by model.liveText.collectAsStateWithLifecycle()
        val smartReady by model.smartReady.collectAsStateWithLifecycle()
        val editError by model.editError.collectAsStateWithLifecycle()
        var consent by remember { mutableStateOf(false) }
        var micDialog by remember { mutableStateOf(false) }
        var deleteDialog by remember { mutableStateOf(false) }
        var edit by remember { mutableStateOf<CommitmentEntity?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var awaitingLiveNotifications by remember { mutableStateOf(false) }

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
                }
                item {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Live notes", style = MaterialTheme.typography.titleLarge)
                            Text("Speech detection · English · on-device transcription")
                            Text(listening.note)
                            if (listening.recording) {
                                LinearProgressIndicator(progress = { (listening.level * 5).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                Text("${listening.seconds / 60}m ${listening.seconds % 60}s · ${listening.chunks} speech chunks")
                                Button(onClick = { serviceAction(ListeningService.FINISH) }) { Text("Finish listening") }
                            } else if (listening.processing) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else Button(onClick = { micDialog = true }, enabled = !busy) { Text("Start live notes") }
                            if (!listening.recording && !listening.processing) {
                                TextButton(onClick = { wavPicker.launch(arrayOf("audio/wav", "audio/x-wav", "audio/wave")) }, enabled = !busy) { Text("Import my WAV recording") }
                            }
                            if (listening.recording || listening.processing)
                                TextButton(onClick = { serviceAction(ListeningService.CANCEL) }) { Text("Discard session") }
                            if (listening.lastLatencyMs > 0) Text("Last chunk processed in ${listening.lastLatencyMs / 1000.0}s", style = MaterialTheme.typography.bodySmall)
                            if (liveText.isNotBlank()) {
                                Text("Recent live text", style = MaterialTheme.typography.titleMedium)
                                Text(liveText.takeLast(3000), modifier = Modifier.testTag("live-text"))
                                TextButton(onClick = { model.setTranscript(liveText.takeLast(if (smartReady) 4000 else 6000), Instant.now()); consent = false }) { Text("Copy recent text for editing") }
                            }
                            Text("Listening continues through pauses until you finish. Recent text is temporary; older text leaves this view after commitment detection. Review proposals below. Only approved commitments are saved. Speaker recognition is not enabled.", style = MaterialTheme.typography.bodySmall)
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                item {
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
                            Text(if (smartReady) "After listening finishes, copy recent text here and confirm it is yours. Smart analysis handles up to 4,000 characters. Live suggestions still use basic rules. Model suggestions can be wrong; review every result."
                                else "Commitment detection uses simple English rules until the optional Qwen model is imported. Review every result.", style = MaterialTheme.typography.bodySmall)
                            Text(message, style = MaterialTheme.typography.bodySmall)
                            if (busy) TextButton(onClick = model::cancelImport) { Text("Cancel current operation") }
                        }
                    }
                }
                item {
                    Text("Your memory · ${entries.size}", style = MaterialTheme.typography.titleLarge)
                    Text("Review and accept a commitment. Add a time only if you want a reminder.")
                    TextButton(onClick = model::addManual) { Text("Add a commitment manually") }
                }
                if (entries.isEmpty()) item { Text("No commitments yet. Try the example above.") }
                items(entries, key = { it.id }) { entry ->
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium)
                            Text("“${entry.evidence}”")
                            Text(entry.state.lowercase().replaceFirstChar { it.uppercase() }, color = MaterialTheme.colorScheme.primary)
                            entry.dueAt?.let { Text("Reminder: ${TimeResolver.format(Instant.ofEpochMilli(it), ZoneId.of(entry.zoneId))} · ${entry.zoneId}") }
                            Text(entry.reviewNote, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { openEditor(entry) }) { Text(if (entry.state == "PROPOSED") "Review & accept" else "Edit") }
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
                    Text("No cloud connection. Reminders may be delayed by Android battery saving. Raw audio is never saved.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (micDialog) AlertDialog(onDismissRequest = { micDialog = false }, title = { Text("Start your own live notes?") },
            text = { Text("Use only your own speech where others agree to microphone use. SideKick will transcribe speech and propose commitments for review while listening, including with the screen off. Audio stays in memory and is discarded after processing. Text and unapproved proposals are temporary; only commitments you approve are saved. Tap Finish or Discard in the notification to stop.") },
            confirmButton = { TextButton(onClick = {
                micDialog = false
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    awaitingLiveNotifications = true; notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            }) { Text("Start listening") } },
            dismissButton = { TextButton(onClick = { micDialog = false }) { Text("Cancel") } })
        edit?.let { item ->
            key(item.id) {
                Editor(item, editError, onClose = { edit = null }, onSave = { title, time, zone -> model.save(item.id, title, time, zone) { edit = null } })
            }
        }
        if (deleteDialog) AlertDialog(onDismissRequest = { deleteDialog = false }, title = { Text("Delete all saved memory?") },
            text = { Text("All commitments and their reminders will be removed.") },
            confirmButton = { TextButton(onClick = { model.deleteAll(); deleteDialog = false }) { Text("Delete all") } },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Cancel") } })
    }
}

@Composable
private fun Editor(item: CommitmentEntity, error: String?, onClose: () -> Unit, onSave: (String, String, String) -> Unit) {
    var title by remember { mutableStateOf(item.title) }
    val zone = remember { ZoneId.of(item.zoneId) }
    val suggested = remember { item.dueAt?.let(Instant::ofEpochMilli) ?: TimeResolver.suggest(item.evidence, Instant.ofEpochMilli(item.capturedAt), zone) }
    var time by remember { mutableStateOf(suggested?.let { TimeResolver.format(it, zone) } ?: "") }
    AlertDialog(onDismissRequest = onClose, title = { Text("Review commitment") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { if (it.length <= 500) title = it }, label = { Text("What will you do?") }, modifier = Modifier.testTag("edit-title"))
                OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Reminder: yyyy-MM-dd HH:mm") }, modifier = Modifier.testTag("edit-time"))
                Text("Timezone: ${zone.id}. Check suggested times; leave blank for no reminder.")
                Row {
                    TextButton(onClick = { time = TimeResolver.format(Instant.now().plusSeconds(120), zone) }) { Text("In 2 minutes") }
                    TextButton(onClick = { time = "" }) { Text("No reminder") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(onClick = { onSave(title, time, zone.id) }, enabled = title.isNotBlank()) { Text("Save & accept") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}
