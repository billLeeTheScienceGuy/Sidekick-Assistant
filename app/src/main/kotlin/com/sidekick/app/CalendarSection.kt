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
internal fun CalendarSection(start: String, end: String, zone: ZoneId, informational: Boolean = false, askAboutEnd: Boolean = false, memoryId: String? = null, onStart: (String) -> Unit, onEnd: (String) -> Unit) {
    var expanded by remember { mutableStateOf(askAboutEnd && end.isBlank()) }
    var acknowledgedUnknown by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("optional-activity")) {
        Text(if (expanded) tr(R.string.hide_optional_activity_times_calendar) else tr(R.string.optional_activity_times_calendar))
    }
    if (!expanded) {
        if (start.isNotBlank() || end.isNotBlank()) Text(tr(R.string.activity_1_s_2_s, start.ifBlank { tr(R.string.unknown) }, end.ifBlank { tr(R.string.unknown) }))
        return
    }
    if (askAboutEnd && end.isBlank() && !acknowledgedUnknown)
        Text(tr(R.string.when_do_you_think_this_will), style = MaterialTheme.typography.titleMedium)
    Text(tr(R.string.optional_separate_from_reminders_checking_your))
    DateTimeChoice(tr(R.string.activity_start), start, zone, "activity-start", onChange = onStart)
    DateTimeChoice(tr(R.string.activity_end), end, zone, "activity-end", onChange = onEnd)
    TextButton(onClick = { onEnd(""); acknowledgedUnknown = true }) { Text(tr(R.string.i_don_t_know)) }
    TextButton(onClick = { onStart(""); onEnd("") }) { Text(tr(R.string.clear_activity_time)) }
    if (informational) Text(tr(R.string.information_only_event_overlaps_do_not))
    else {
        Text(tr(R.string.checks_calendars_on_this_phone_nothing))
        key(start, end, zone) { CalendarQuery(start, end, zone, memoryId) }
    }
}

@Composable
private fun CalendarQuery(start: String, end: String, zone: ZoneId, memoryId: String?) {
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
        else result = CalendarCheck.Unavailable(tr(R.string.calendar_access_was_not_allowed_you))
    }
    LaunchedEffect(request) {
        if (request > 0) window.getOrNull()?.let { (from, until) ->
            running = true; result = null
            try { result = PhoneCalendar(context).check(from, until, zone, memoryId) }
            finally { running = false }
        }
    }
    TextButton(enabled = !running, modifier = Modifier.testTag("check-calendar"), onClick = {
        if (window.isFailure) validation = tr(R.string.enter_valid_start_and_end_times)
        else {
            validation = null
            if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) request++
            else permission.launch(Manifest.permission.READ_CALENDAR)
        }
    }) { Text(if (running) tr(R.string.checking) else tr(R.string.check_phone_calendar)) }
    validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    when (val checked = result) {
        is CalendarCheck.Unavailable -> Text(checked.reason)
        is CalendarCheck.Available -> Column {
            Text(if (checked.clashes.isEmpty()) tr(R.string.no_busy_overlaps_found_in_visible)
                else trn(R.plurals.calendar_overlaps, checked.clashes.size, if (checked.clashes.size == 50) "50+" else checked.clashes.size))
            checked.clashes.take(10).forEach { event ->
                Text("${event.title} · ${if (event.allDay) "All day" else "${TimeResolver.format(Instant.ofEpochMilli(event.start), zone)} – ${TimeResolver.format(Instant.ofEpochMilli(event.end), zone)}"}")
            }
            Text(tr(R.string.ignores_free_cancelled_and_declined_events), style = MaterialTheme.typography.bodySmall)
        }
        null -> Text(tr(R.string.calendar_not_checked_for_these_times), style = MaterialTheme.typography.bodySmall)
    }
}
