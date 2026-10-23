package com.sidekick.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.sidekick.core.CalendarOverlap
import com.sidekick.core.TimeResolver
import java.time.Instant
import java.time.ZoneId

@Composable
internal fun CalendarSection(start: String, end: String, zone: ZoneId, informational: Boolean = false, askAboutEnd: Boolean = false, onStart: (String) -> Unit, onEnd: (String) -> Unit) {
    var expanded by remember { mutableStateOf(askAboutEnd && end.isBlank()) }
    var acknowledgedUnknown by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("optional-activity")) {
        Text(if (expanded) "Hide optional activity times & calendar" else "Optional activity times & calendar")
    }
    if (!expanded) {
        if (start.isNotBlank() || end.isNotBlank()) Text("Activity: ${start.ifBlank { "Unknown" }} – ${end.ifBlank { "Unknown" }}")
        return
    }
    if (askAboutEnd && end.isBlank() && !acknowledgedUnknown)
        Text("When do you think this will last till?", style = MaterialTheme.typography.titleMedium)
    Text("Activity times are optional and separate from reminders. Start or end can stay unknown; a calendar overlap check needs both.")
    DateTimeChoice("Activity start", start, zone, "activity-start", onChange = onStart)
    DateTimeChoice("Activity end", end, zone, "activity-end", onChange = onEnd)
    TextButton(onClick = { onEnd(""); acknowledgedUnknown = true }) { Text("I don’t know") }
    TextButton(onClick = { onStart(""); onEnd("") }) { Text("Clear activity time") }
    if (informational) Text("Information-only event: overlaps do not block your calendar.")
    else {
        Text("Check visible calendars stored on this phone. Read access is optional; no calendar events are changed or copied into saved memory.")
        key(start, end, zone) { CalendarQuery(start, end, zone) }
    }
}

@Composable
private fun CalendarQuery(start: String, end: String, zone: ZoneId) {
    val context = LocalContext.current
    var request by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CalendarCheck?>(null) }
    var validation by remember { mutableStateOf<String?>(null) }
    val window = runCatching {
        val from = TimeResolver.parseEditor(start, zone).toEpochMilli()
        val until = TimeResolver.parseEditor(end, zone).toEpochMilli()
        CalendarOverlap.validate(from, until)
        from to until
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) request++
        else result = CalendarCheck.Unavailable("Calendar access was not allowed. You can still save; availability is unknown.")
    }
    LaunchedEffect(request) {
        if (request > 0) window.getOrNull()?.let { (from, until) ->
            running = true; result = null
            try { result = PhoneCalendar(context).check(from, until, zone) }
            finally { running = false }
        }
    }
    TextButton(enabled = !running, modifier = Modifier.testTag("check-calendar"), onClick = {
        if (window.isFailure) validation = "Enter valid start and end times in the displayed timezone, with end after start (maximum seven days)."
        else {
            validation = null
            if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) request++
            else permission.launch(Manifest.permission.READ_CALENDAR)
        }
    }) { Text(if (running) "Checking…" else "Check phone calendar") }
    validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    when (val checked = result) {
        is CalendarCheck.Unavailable -> Text(checked.reason)
        is CalendarCheck.Available -> Column {
            Text(if (checked.clashes.isEmpty()) "No busy overlaps found in visible phone calendars at this check."
                else "${if (checked.clashes.size == 50) "50+" else checked.clashes.size} possible calendar overlap(s).")
            checked.clashes.take(10).forEach { event ->
                Text("${event.title} · ${if (event.allDay) "All day" else "${TimeResolver.format(Instant.ofEpochMilli(event.start), zone)} – ${TimeResolver.format(Instant.ofEpochMilli(event.end), zone)}"}")
            }
            Text("Includes recurring and all-day events; ignores free, cancelled and declined events. Calendars may be out of sync. Recheck after calendar changes. Saving does not reserve this time.", style = MaterialTheme.typography.bodySmall)
        }
        null -> Text("Calendar not checked for these times.", style = MaterialTheme.typography.bodySmall)
    }
}
