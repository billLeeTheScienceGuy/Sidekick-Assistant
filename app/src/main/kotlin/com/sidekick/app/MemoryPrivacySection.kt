package com.sidekick.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.*

@Composable
internal fun MemoryPrivacySection() {
    val app = LocalContext.current.applicationContext as SidekickApplication
    val days by app.guidance.retentionDays.collectAsState()
    val riskEnabled by app.guidance.riskAdvisories.collectAsState()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var pendingDays by remember { mutableStateOf<Int?>(null) }
    var excerpts by remember { mutableStateOf(false) }
    var exportingExcerpts by rememberSaveable { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            working = true
            try {
                val count = withContext(Dispatchers.IO) {
                    val items = app.database.commitments().all()
                    val stream = app.contentResolver.openOutputStream(uri, "wt") ?: error("Destination unavailable")
                    com.sidekick.app.data.MemoryExport.write(stream, items, exportingExcerpts)
                    items.size
                }
                result = "Exported $count saved events."
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { result = "Export failed; the destination may contain an incomplete file. Saved memory is unchanged." }
            finally { working = false }
        }
    }
    HorizontalDivider()
    Text("Memory & privacy", style = MaterialTheme.typography.titleMedium)
    Row {
        Switch(riskEnabled, app.guidance::setRiskAdvisories, modifier = Modifier.testTag("risk-advisories-setting").controlLabel("Local risk advisories"))
        Text("Local risk advisories")
    }
    Text("English warning-sign rules cover gift-card payments, private-code requests, remote access and safe-account transfers. No transcript is sent online; no source excerpt is saved in alerts. This is not a general scam detector.")
    Text("Audio is discarded after processing. Recent conversation text is temporary. Saved events may include short source excerpts; deleting an event also removes its Undo history.")
    Box {
        TextButton(onClick = { menu = true }, modifier = Modifier.testTag("retention-choice")) {
            Text(if (days == 0) "Retention: keep until I delete" else "Retention: $days days")
        }
        DropdownMenu(menu, { menu = false }) {
            com.sidekick.core.MemoryRetention.choices.forEach { choice ->
                DropdownMenuItem(text = { Text(if (choice == 0) "Keep until I delete" else "$choice days") }, onClick = {
                    menu = false
                    if (choice == 0) app.guidance.setRetentionDays(0) else if (choice != days) pendingDays = choice
                }, modifier = Modifier.testTag("retention-$choice"))
            }
        }
    }
    Text("Opt-in cleanup runs on startup and daily while SideKick runs. Future items, undated accepted plans and undelivered reminders stay. Default: keep until deleted.")
    Row {
        Checkbox(excerpts, { excerpts = it }, enabled = !working, modifier = Modifier.testTag("export-excerpts").controlLabel("Include saved source excerpts in export"))
        Text("Include saved source excerpts in export")
    }
    Text("Export contains saved events only. Temporary text, audio, voice profiles, message sender IDs and Undo history are excluded. Your chosen destination may sync files online.")
    TextButton(enabled = !working, onClick = {
        exportingExcerpts = excerpts
        export.launch("SideKick-events-${java.time.LocalDate.now()}.json")
    }, modifier = Modifier.testTag("export-memory")) { Text("Export saved events") }
    if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (result.isNotBlank()) Text(result)
    if (pendingDays != null) AlertDialog(onDismissRequest = { pendingDays = null },
        title = { Text("Enable automatic cleanup?") },
        text = { Text("Old eligible memories and their source excerpts will be permanently deleted after $pendingDays days. Future items, undated accepted plans and pending reminders stay. Deleted memories cannot be restored with Undo. Export first if you want a copy.") },
        confirmButton = { TextButton(onClick = { app.guidance.setRetentionDays(pendingDays!!); pendingDays = null }) { Text("Enable cleanup") } },
        dismissButton = { TextButton(onClick = { pendingDays = null }) { Text("Cancel") } })
}
