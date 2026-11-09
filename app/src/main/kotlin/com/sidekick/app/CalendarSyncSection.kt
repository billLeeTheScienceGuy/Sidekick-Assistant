package com.sidekick.app

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
            note = if (calendars.isEmpty()) "No visible writable calendar found. Add a calendar account in Android or use SideKick's local reminders." else "Choose the calendar for new mirrors."
            choosing = calendars.isNotEmpty()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { note = "Calendar access or provider is unavailable. Local events and reminders still work." }
        finally { loading = false }
    } }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (AndroidCalendarGateway(context).permitted()) {
            if (enablingAfterPermission) confirming = true
            else if (config.enabled) { app.calendarMirror.requestSync(); note = "Calendar access restored." }
            else load()
        } else note = "Calendar sync needs both read and write access. Local reminders remain available."
        enablingAfterPermission = false
    }
    HorizontalDivider()
    Text("Optional calendar sync", style = MaterialTheme.typography.titleMedium)
    Text("Mirror saved future activities to a calendar you choose. Titles, times and places may sync through that calendar account. Transcripts and voice profiles are excluded.")
    Text("Known start and end times are required; SideKick does not invent durations. Local reminders work even when an activity cannot be mirrored.")
    Box {
    TextButton(enabled = !loading && !config.enabled, modifier = Modifier.testTag("choose-sync-calendar"), onClick = {
        if (AndroidCalendarGateway(context).permitted()) load() else permissions.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }) { Text(if (config.calendarId == null) "Choose writable calendar" else "Calendar selected · choose another") }
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
            enabled = config.calendarId != null, modifier = Modifier.testTag("calendar-sync-setting").controlLabel("Automatically mirror saved activities to my selected calendar"))
        Text("Automatically mirror saved activities")
    }
    Row {
        Checkbox(config.includeInformational, { app.guidance.setCalendarSync(config.copy(includeInformational = it)) },
            modifier = Modifier.testTag("sync-informational").controlLabel("Include information-only events as free time"))
        Text("Include information-only events as free time")
    }
    Text("Rescheduling, cancellation and deletion update unchanged private mirrors. External edits, deleted copies, recurring events and added guests are protected. Turning sync off pauses writes and leaves calendar copies. Existing mirrors keep their original calendar if you choose another.")
    Text(status.note, modifier = Modifier.testTag("calendar-sync-status"))
    if (config.enabled) TextButton(onClick = { app.calendarMirror.requestSync() }, modifier = Modifier.testTag("retry-calendar-sync")) { Text("Retry calendar sync") }
    if (config.enabled && !AndroidCalendarGateway(context).permitted()) TextButton(onClick = {
        permissions.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }) { Text("Restore calendar access") }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (note.isNotBlank()) Text(note)
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("Enable calendar mirroring?") },
        text = { Text("Saved future activities with known start and end times will be copied to the selected calendar. That account may send their title, times and place online. Supported changes and deletions will update unchanged mirrors. Private source excerpts and transcripts are not copied. No attendees or invitations are created.") },
        confirmButton = { TextButton(onClick = { app.guidance.setCalendarSync(config.copy(enabled = true)); confirming = false }) { Text("Enable sync") } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } })
}
