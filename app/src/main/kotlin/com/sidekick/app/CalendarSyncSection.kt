package com.sidekick.app

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.*

@Composable
internal fun CalendarSyncSection() {
    val context = LocalContext.current
    val app = context.applicationContext as SidekickApplication
    val config by app.guidance.calendarSync.collectAsState()
    val status by app.calendarMirror.status.collectAsState()
    val scope = rememberCoroutineScope()
    var calendars by remember { mutableStateOf<List<WritableCalendar>>(emptyList()) }
    var choosing by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var enablingAfterPermission by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    fun load() { scope.launch {
        loading = true
        try {
            calendars = withContext(Dispatchers.IO) { AndroidCalendarGateway(context).calendars() }
            note = if (calendars.isEmpty()) tr(R.string.no_calendar_available_add_an_account) else tr(R.string.choose_where_new_plans_will_be)
            choosing = calendars.isNotEmpty()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { note = tr(R.string.can_t_open_your_calendar_sidekick) }
        finally { loading = false }
    } }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (AndroidCalendarGateway(context).permitted()) {
            if (enablingAfterPermission) confirming = true
            else if (config.enabled) { app.calendarMirror.requestSync(); note = tr(R.string.calendar_access_restored) }
            else load()
        } else note = tr(R.string.allow_calendar_access_to_copy_plans)
        enablingAfterPermission = false
    }
    HorizontalDivider()
    Text(tr(R.string.calendar), style = MaterialTheme.typography.titleMedium)
    QuietLabel(tr(R.string.copies_upcoming_plans_to_your_calendar))
    QuietLabel(tr(R.string.plans_need_a_start_and_end))
    Box {
    TextButton(enabled = !loading && !config.enabled, modifier = Modifier.testTag("choose-sync-calendar"), onClick = {
        if (AndroidCalendarGateway(context).permitted()) load() else permissions.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }) { Text(if (config.calendarId == null) tr(R.string.choose_a_calendar) else tr(R.string.change_calendar)) }
    DropdownMenu(choosing, { choosing = false }) {
        calendars.forEach { calendar -> DropdownMenuItem(text = { Text("${calendar.name} · ${calendar.account}") }, onClick = {
            app.guidance.setCalendarSync(config.copy(calendarId = calendar.id)); choosing = false
        }, modifier = Modifier.testTag("sync-calendar-${calendar.id}")) }
    }
    }
    Row {
        Switch(config.enabled, { enabled -> if (!enabled) app.guidance.setCalendarSync(config.copy(enabled = false))
            else if (AndroidCalendarGateway(context).permitted()) confirming = true
            else { enablingAfterPermission = true; permissions.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) } },
            enabled = config.calendarId != null, modifier = Modifier.testTag("calendar-sync-setting").controlLabel(tr(R.string.copy_plans_automatically_to_my_selected)))
        Text(tr(R.string.copy_plans_automatically), Modifier.padding(start = 8.dp))
    }
    Row {
        Checkbox(config.includeInformational, { app.guidance.setCalendarSync(config.copy(includeInformational = it)) },
            modifier = Modifier.testTag("sync-informational").controlLabel(tr(R.string.include_plans_i_m_not_attending)))
        Text(tr(R.string.include_plans_i_m_not_attending), Modifier.padding(start = 8.dp))
    }
    QuietLabel(tr(R.string.turning_sync_off_keeps_existing_copies))
    SettingsDetails(tr(R.string.how_calendar_updates_work)) {
        QuietLabel(tr(R.string.changes_update_copies_you_haven_t))
    }
    Text(status.note, modifier = Modifier.testTag("calendar-sync-status"))
    if (config.enabled) TextButton(onClick = { app.calendarMirror.requestSync() }, modifier = Modifier.testTag("retry-calendar-sync")) { Text(tr(R.string.retry_calendar_sync)) }
    if (config.enabled && !AndroidCalendarGateway(context).permitted()) TextButton(onClick = {
        permissions.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }) { Text(tr(R.string.restore_calendar_access)) }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (note.isNotBlank()) Text(note)
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text(tr(R.string.copy_plans_to_your_calendar)) },
        text = { Text(tr(R.string.copies_plans_with_start_and_end)) },
        confirmButton = { TextButton(onClick = { app.guidance.setCalendarSync(config.copy(enabled = true)); confirming = false }) { Text(tr(R.string.enable_sync)) } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text(tr(R.string.cancel)) } })
}
