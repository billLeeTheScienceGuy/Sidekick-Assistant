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
            note = if (calendars.isEmpty()) "No calendar available. Add an account in Android, or use SideKick reminders." else "Choose where new plans will be copied."
            choosing = calendars.isNotEmpty()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { note = "Can’t open your calendar. SideKick plans and reminders still work." }
        finally { loading = false }
    } }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (AndroidCalendarGateway(context).permitted()) {
            if (enablingAfterPermission) confirming = true
            else if (config.enabled) { app.calendarMirror.requestSync(); note = "Calendar access restored." }
            else load()
        } else note = "Allow calendar access to copy plans. SideKick reminders still work."
        enablingAfterPermission = false
    }
    HorizontalDivider()
    Text("Calendar", style = MaterialTheme.typography.titleMedium)
    QuietLabel("Copy upcoming plans to your calendar. The account may send titles, times and places online. Conversation text and voice setup aren’t copied.")
    QuietLabel("Plans need a start and end time to appear in your calendar. SideKick reminders work without calendar sync.")
    Box {
    TextButton(enabled = !loading && !config.enabled, modifier = Modifier.testTag("choose-sync-calendar"), onClick = {
        if (AndroidCalendarGateway(context).permitted()) load() else permissions.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }) { Text(if (config.calendarId == null) "Choose a calendar" else "Change calendar") }
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
            enabled = config.calendarId != null, modifier = Modifier.testTag("calendar-sync-setting").controlLabel("Copy plans automatically to my selected calendar"))
        Text("Copy plans automatically", Modifier.padding(start = 8.dp))
    }
    Row {
        Checkbox(config.includeInformational, { app.guidance.setCalendarSync(config.copy(includeInformational = it)) },
            modifier = Modifier.testTag("sync-informational").controlLabel("Include plans I’m not attending"))
        Text("Include plans I’m not attending", Modifier.padding(start = 8.dp))
    }
    QuietLabel("Turning sync off leaves existing copies in your calendar. Plans you aren’t attending appear as free time.")
    SettingsDetails("How calendar updates work") {
        QuietLabel("Changes and cancellations update copies you haven’t edited in your calendar. Copies with outside edits, added guests or repeating dates are left alone. Old copies stay in their original calendar if you choose another.")
    }
    Text(status.note, modifier = Modifier.testTag("calendar-sync-status"))
    if (config.enabled) TextButton(onClick = { app.calendarMirror.requestSync() }, modifier = Modifier.testTag("retry-calendar-sync")) { Text("Retry calendar sync") }
    if (config.enabled && !AndroidCalendarGateway(context).permitted()) TextButton(onClick = {
        permissions.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }) { Text("Restore calendar access") }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (note.isNotBlank()) Text(note)
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("Copy plans to your calendar?") },
        text = { Text("Copies upcoming plans with start and end times. Your calendar account may send their titles, times and places online. Conversation text stays private. No invitations are sent. SideKick updates or deletes copies only if you haven’t changed them in your calendar.") },
        confirmButton = { TextButton(onClick = { app.guidance.setCalendarSync(config.copy(enabled = true)); confirming = false }) { Text("Enable sync") } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } })
}
