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
                    val stream = app.contentResolver.openOutputStream(uri, "wt") ?: error(tr(R.string.destination_unavailable))
                    com.sidekick.app.data.MemoryExport.write(stream, items, exportingExcerpts)
                    items.size
                }
                result = tr(R.string.exported_1_s_saved_events, count)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { result = tr(R.string.couldn_t_save_the_copy_the) }
            finally { working = false }
        }
    }
    HorizontalDivider()
    Text(tr(R.string.saved_plans), style = MaterialTheme.typography.titleMedium)
    Row {
        Switch(riskEnabled, app.guidance::setRiskAdvisories, modifier = Modifier.testTag("risk-advisories-setting").controlLabel(tr(R.string.scam_warnings)))
        Text(tr(R.string.scam_warnings), Modifier.padding(start = 8.dp))
    }
    QuietLabel(tr(R.string.warns_about_common_scam_signs_in))
    QuietLabel(tr(R.string.no_audio_or_full_conversations_are))
    val learnNames by app.guidance.learnNames.collectAsState()
    val keepNames by app.guidance.keepLearnedNames.collectAsState()
    val learned by app.guidance.learnedNames.collectAsState()
    Text(tr(R.string.names), style = MaterialTheme.typography.titleSmall)
    Row {
        Switch(learnNames, app.guidance::setLearnNames, modifier = Modifier.testTag("learn-names").controlLabel(tr(R.string.learn_names_from_my_plans)))
        Text(tr(R.string.learn_names_from_my_plans), Modifier.padding(start = 8.dp))
    }
    QuietLabel(tr(R.string.korean_names_in_your_saved_plans))
    Row {
        Switch(keepNames, app.guidance::setKeepLearnedNames, enabled = learnNames, modifier = Modifier.testTag("keep-learned-names").controlLabel(tr(R.string.keep_names_after_a_plan_is)))
        Text(tr(R.string.keep_names_after_a_plan_is), Modifier.padding(start = 8.dp))
    }
    if (learned.isEmpty()) QuietLabel(tr(R.string.no_names_learned_yet))
    else {
        QuietLabel(tr(R.string.learned_from_your_plans_on_this))
        learned.sorted().chunked(3).forEach { row ->
            Row { row.forEach { name ->
                TextButton(onClick = { app.guidance.forgetLearnedName(name) }, modifier = Modifier.testTag("learned-name").controlLabel(tr(R.string.remove_1_s, name))) { Text("$name ✕") }
            } }
        }
        TextButton(onClick = app.guidance::clearLearnedNames, modifier = Modifier.testTag("clear-learned-names")) { Text(tr(R.string.clear_learned_names)) }
    }
    Box {
        TextButton(onClick = { menu = true }, modifier = Modifier.testTag("retention-choice")) {
            Text(if (days == 0) tr(R.string.keep_saved_plans_keep_until_i) else tr(R.string.keep_saved_plans_1_s_days, days))
        }
        DropdownMenu(menu, { menu = false }) {
            com.sidekick.core.MemoryRetention.choices.forEach { choice ->
                DropdownMenuItem(text = { Text(if (choice == 0) tr(R.string.keep_until_i_delete) else tr(R.string.n_1_s_days, choice)) }, onClick = {
                    menu = false
                    if (choice == 0) app.guidance.setRetentionDays(0) else if (choice != days) pendingDays = choice
                }, modifier = Modifier.testTag("retention-$choice"))
            }
        }
    }
    QuietLabel(tr(R.string.future_plans_undated_plans_and_pending))
    Row {
        Checkbox(excerpts, { excerpts = it }, enabled = !working, modifier = Modifier.testTag("export-excerpts").controlLabel(tr(R.string.include_source_quotes)))
        Text(tr(R.string.include_source_quotes), Modifier.padding(start = 8.dp))
    }
    QuietLabel(tr(R.string.saves_your_plans_only_no_conversations))
    TextButton(enabled = !working, onClick = {
        exportingExcerpts = excerpts
        export.launch("SideKick-events-${java.time.LocalDate.now()}.json")
    }, modifier = Modifier.testTag("export-memory")) { Text(tr(R.string.save_a_copy_of_my_plans)) }
    if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (result.isNotBlank()) Text(result)
    if (pendingDays != null) AlertDialog(onDismissRequest = { pendingDays = null },
        title = { Text(tr(R.string.enable_automatic_cleanup)) },
        text = { Text(tr(R.string.permanently_deletes_old_plans_and_their, pendingDays)) },
        confirmButton = { TextButton(onClick = { app.guidance.setRetentionDays(pendingDays!!); pendingDays = null }) { Text(tr(R.string.enable_cleanup)) } },
        dismissButton = { TextButton(onClick = { pendingDays = null }) { Text(tr(R.string.cancel)) } })
}
