package com.sidekick.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sidekick.app.data.CommitmentEntity
import com.sidekick.core.*
import java.time.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SummarySection(entries: List<CommitmentEntity>, liveText: String, canClear: Boolean, onClear: () -> Unit, onOpen: (CommitmentEntity) -> Unit) {
    val zone = ZoneId.systemDefault()
    val context = LocalContext.current
    var calendarError by remember { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf(LocalDate.now(zone).toString()) }
    var picker by remember { mutableStateOf(false) }
    val day = LocalDate.parse(selected)
    val facts = remember(entries) { entries.map { SummaryFact(it.id, it.title, it.state, it.informational,
        it.activityStartAt?.let(Instant::ofEpochMilli), it.activityEndAt?.let(Instant::ofEpochMilli),
        it.dueAt?.let(Instant::ofEpochMilli), it.placeName, it.placeAddress) } }
    val digest = remember(facts, day, zone) { MemorySummary.daily(facts, day, zone) }
    val highlights = remember(liveText) { MemorySummary.conversation(liveText) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().testTag("summary-content")) {
        Text("Daily overview", style = MaterialTheme.typography.headlineSmall)
        Text("Saved facts · ${zone.id}. Times follow the phone's timezone.")
        calendarError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { selected = day.minusDays(1).toString() }) { Text("Previous") }
            TextButton(onClick = { selected = LocalDate.now(zone).toString() }) { Text("Today") }
            TextButton(onClick = { selected = day.plusDays(1).toString() }) { Text("Next") }
        }
        TextButton(onClick = { picker = true }, modifier = Modifier.testTag("summary-date")) { Text("Choose date · $day") }
        fun time(value: Instant?) = value?.let { TimeResolver.format(it, zone) } ?: "Unknown"
        @Composable fun group(title: String, items: List<SummaryFact>) {
            Text("$title (${items.size})", style = MaterialTheme.typography.titleMedium)
            if (items.isEmpty()) Text("None")
            items.take(10).forEach { fact ->
                Card(Modifier.fillMaxWidth().testTag("summary-${fact.id}")) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(fact.title, style = MaterialTheme.typography.titleSmall)
                        Text(if (fact.informational) "Information only · attendance unconfirmed" else "Personal plan")
                        if (fact.state != "ACCEPTED") Text("Current status: ${fact.state.lowercase()}")
                        Text("Activity: ${time(fact.start)} – ${time(fact.end)}")
                        Text("Reminder: ${fact.reminder?.let { time(it) } ?: "Not set"}")
                        fact.place?.let { Text("Place: $it") }; fact.address?.let { Text(it) }
                        TextButton(onClick = { entries.find { it.id == fact.id }?.let(onOpen) }, modifier = Modifier.testTag("summary-edit-${fact.id}")) { Text("View or edit") }
                        if (fact.state == "ACCEPTED" && fact.start != null) {
                            TextButton(onClick = {
                                calendarError = null
                                entries.find { it.id == fact.id }?.let { item ->
                                    try { context.startActivity(CalendarDraft.intent(item)) }
                                    catch (_: android.content.ActivityNotFoundException) { calendarError = "No calendar editor is installed." }
                                    catch (_: RuntimeException) { calendarError = "Could not open the calendar editor. Check the event's times." }
                                }
                            }, modifier = Modifier.testTag("calendar-draft-${fact.id}")) { Text("Open calendar editor") }
                            Text("Review and save in your calendar app.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (items.size > 10) Text("Showing 10 of ${items.size}. All items are available in Planner.")
        }
        group("Your plans", digest.plans)
        group("Information-only events", digest.information)
        group("Marked done or cancelled", digest.history)
        Text("The status shown is current; completion/cancellation dates are not recorded.", style = MaterialTheme.typography.bodySmall)
        group("Without a date · all saved items", digest.undated)
        Text("Recent conversation highlights", style = MaterialTheme.typography.titleMedium)
        Text("Temporary source excerpts. Later corrections may replace earlier details. These excerpts are cleared with the temporary transcript.")
        if (highlights.isEmpty()) Text("No temporary conversation highlights.")
        highlights.forEachIndexed { index, line -> Text(line, modifier = Modifier.testTag("summary-excerpt-$index")) }
        if (liveText.isNotBlank()) TextButton(onClick = onClear, enabled = canClear, modifier = Modifier.testTag("clear-conversation")) { Text("Clear recent conversation") }
    }
    if (picker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), initialDisplayMode = DisplayMode.Picker)
        DatePickerDialog(onDismissRequest = { picker = false }, confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { selected = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; picker = false }) { Text("Choose") }
        }, dismissButton = { TextButton(onClick = { picker = false }) { Text("Cancel") } }) {
            DatePicker(state, showModeToggle = false)
        }
    }
}
