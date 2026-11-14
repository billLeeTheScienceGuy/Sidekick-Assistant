package com.sidekick.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
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
            catch (_: Exception) { result = "Couldn’t save the copy. The file may be incomplete. Your saved plans are unchanged." }
            finally { working = false }
        }
    }
    HorizontalDivider()
    Text("Saved plans", style = MaterialTheme.typography.titleMedium)
    Row {
        Switch(riskEnabled, app.guidance::setRiskAdvisories, modifier = Modifier.testTag("risk-advisories-setting").controlLabel("Scam warnings"))
        Text("Scam warnings", Modifier.padding(start = 8.dp))
    }
    QuietLabel("Checks common scam phrases in English on this phone. Can miss scams. Alerts don’t save conversation text.")
    QuietLabel("Audio and full conversations aren’t saved. Saved plans may include source quotes. Deleting a plan also deletes its Undo history.")
    Box {
        TextButton(onClick = { menu = true }, modifier = Modifier.testTag("retention-choice")) {
            Text(if (days == 0) "Keep saved plans: keep until I delete" else "Keep saved plans: $days days")
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
    QuietLabel("Cleanup runs while the app runs. Future plans, accepted plans without dates, and pending reminders stay.")
    Row {
        Checkbox(excerpts, { excerpts = it }, enabled = !working, modifier = Modifier.testTag("export-excerpts").controlLabel("Include source quotes"))
        Text("Include source quotes", Modifier.padding(start = 8.dp))
    }
    QuietLabel("Export saves a copy of your plans, not full conversations, audio, voice setup, sender IDs or Undo history. The folder you choose may sync online.")
    TextButton(enabled = !working, onClick = {
        exportingExcerpts = excerpts
        export.launch("SideKick-events-${java.time.LocalDate.now()}.json")
    }, modifier = Modifier.testTag("export-memory")) { Text("Save a copy of my plans") }
    if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (result.isNotBlank()) Text(result)
    if (pendingDays != null) AlertDialog(onDismissRequest = { pendingDays = null },
        title = { Text("Enable automatic cleanup?") },
        text = { Text("Permanently deletes old eligible plans and their quotes after $pendingDays days. Future plans, accepted plans without dates, and pending reminders stay. You can’t undo this. Save a copy first if needed.") },
        confirmButton = { TextButton(onClick = { app.guidance.setRetentionDays(pendingDays!!); pendingDays = null }) { Text("Enable cleanup") } },
        dismissButton = { TextButton(onClick = { pendingDays = null }) { Text("Cancel") } })
}
