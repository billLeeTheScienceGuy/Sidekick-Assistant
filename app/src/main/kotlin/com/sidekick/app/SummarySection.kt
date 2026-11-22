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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        Text(tr(R.string.daily_overview), style = MaterialTheme.typography.headlineSmall)
        QuietLabel(tr(R.string.saved_facts_1_s, zone.id))
        calendarError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { selected = day.minusDays(1).toString() }) { Text(tr(R.string.previous)) }
            TextButton(onClick = { selected = LocalDate.now(zone).toString() }) { Text(tr(R.string.today)) }
            TextButton(onClick = { selected = day.plusDays(1).toString() }) { Text(tr(R.string.next)) }
        }
        TextButton(onClick = { picker = true }, modifier = Modifier.testTag("summary-date")) { Text(tr(R.string.choose_date_1_s, day)) }
        fun time(value: Instant?) = value?.let { TimeResolver.format(it, zone) } ?: tr(R.string.unknown)
        @Composable fun group(title: String, items: List<SummaryFact>) {
            Text("$title (${items.size})", style = MaterialTheme.typography.titleMedium)
            if (items.isEmpty()) Text(tr(R.string.none))
            items.take(10).forEach { fact ->
                Card(Modifier.fillMaxWidth().testTag("summary-${fact.id}")) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(fact.title, style = MaterialTheme.typography.titleSmall)
                        Text(if (fact.informational) tr(R.string.information_only_attendance_unconfirmed) else tr(R.string.personal_plan))
                        if (fact.state != "ACCEPTED") Text(tr(R.string.current_status_1_s, planStateLabel(fact.state).lowercase()))
                        Text(tr(R.string.activity_1_s_2_s, time(fact.start), time(fact.end)))
                        Text(tr(R.string.reminder_1_s, fact.reminder?.let { time(it) } ?: tr(R.string.not_set)))
                        fact.place?.let { Text(tr(R.string.place_1_s, it)) }; fact.address?.let { Text(it) }
                        TextButton(onClick = { entries.find { it.id == fact.id }?.let(onOpen) }, modifier = Modifier.testTag("summary-edit-${fact.id}")) { Text(tr(R.string.view_or_edit)) }
                        if (fact.state == "ACCEPTED" && fact.start != null) {
                            TextButton(onClick = {
                                calendarError = null
                                entries.find { it.id == fact.id }?.let { item ->
                                    try { context.startActivity(CalendarDraft.intent(item)) }
                                    catch (_: android.content.ActivityNotFoundException) { calendarError = tr(R.string.no_calendar_editor_is_installed) }
                                    catch (_: RuntimeException) { calendarError = tr(R.string.could_not_open_the_calendar_editor) }
                                }
                            }, modifier = Modifier.testTag("calendar-draft-${fact.id}")) { Text(tr(R.string.open_calendar_editor)) }
                            Text(tr(R.string.review_and_save_in_your_calendar), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (items.size > 10) Text(tr(R.string.showing_10_of_1_s_all, items.size))
        }
        group(tr(R.string.your_plans), digest.plans)
        group(tr(R.string.information_only_events), digest.information)
        group(tr(R.string.marked_done_or_cancelled), digest.history)
        Text(tr(R.string.the_status_shown_is_current_completion), style = MaterialTheme.typography.bodySmall)
        group(tr(R.string.without_a_date_all_saved_items), digest.undated)
        Text(tr(R.string.recent_conversation_highlights), style = MaterialTheme.typography.titleMedium)
        Text(tr(R.string.temporary_quotes_cleared_with_the_recent))
        if (highlights.isEmpty()) Text(tr(R.string.no_temporary_conversation_highlights))
        highlights.forEachIndexed { index, line -> Text(line, modifier = Modifier.testTag("summary-excerpt-$index")) }
        if (liveText.isNotBlank()) TextButton(onClick = onClear, enabled = canClear, modifier = Modifier.testTag("clear-conversation")) { Text(tr(R.string.clear_recent_conversation)) }
    }
    if (picker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), initialDisplayMode = DisplayMode.Picker)
        DatePickerDialog(onDismissRequest = { picker = false }, confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { selected = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; picker = false }) { Text(tr(R.string.choose)) }
        }, dismissButton = { TextButton(onClick = { picker = false }) { Text(tr(R.string.cancel)) } }) {
            DatePicker(state, showModeToggle = false)
        }
    }
}
