package com.sidekick.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sidekick.app.audio.ListeningState
import com.sidekick.app.data.CommitmentEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Plain words for a saved plan's state (the stored codes stay unchanged). */
internal fun planStateLabel(state: String): String = when (state) {
    "PROPOSED" -> tr(R.string.needs_checking)
    "ACCEPTED" -> tr(R.string.saved)
    "DONE" -> tr(R.string.done)
    "DISMISSED" -> tr(R.string.removed)
    "CANCELLED" -> tr(R.string.cancelled)
    else -> state.lowercase().replaceFirstChar { it.uppercase() }
}

/** Minimum height of every button on the simple screens (Android's 48 dp minimum, rounded up for easier tapping). */
internal val SimpleTarget = 56.dp

/**
 * The simple "Today" screen: one large Start/Stop button, a plain status line and the next plans.
 * Everything else (notes, summary, details, setup) is under More or Settings. Scrolls, so large system
 * font sizes never clip; nothing is swipe-only.
 */
@Composable
internal fun CompactHome(
    listening: ListeningState, entries: List<CommitmentEntity>, undoable: Map<String, RecentAutomaticChange>,
    handsOff: Boolean, showAnnounce: Boolean, needsModel: Boolean, busy: Boolean, error: String?,
    onStart: () -> Unit, onFinish: () -> Unit, onDiscard: () -> Unit, onAnnounce: () -> Unit, onNoticeGiven: () -> Unit,
    onSetup: () -> Unit, onOpen: (CommitmentEntity) -> Unit, onUndo: (RecentAutomaticChange) -> Unit,
    onRemove: (CommitmentEntity) -> Unit, onReview: () -> Unit, onAllPlans: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val next = entries.filter { it.state == "ACCEPTED" }.mapNotNull { item ->
        (item.activityStartAt ?: item.dueAt)?.takeIf { it >= now }?.let { item to it }
    }.sortedBy { it.second }.take(5)
    val toCheck = entries.count { it.state == "PROPOSED" }
    var removing by remember { mutableStateOf<CommitmentEntity?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp).testTag("one-screen-home"),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CrashReportCard()
        RiskSection()
        Text(when {
            listening.recording -> tr(R.string.listening)
            listening.processing -> tr(R.string.finishing_up)
            else -> tr(R.string.not_listening)
        }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("compact-status"))
        if (listening.recording) Text(if (listening.speechActive) tr(R.string.hearing_speech) else tr(R.string.waiting_for_speech),
            style = MaterialTheme.typography.bodyLarge)
        val stopColors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
        Button(onClick = if (listening.recording) onFinish else onStart,
            enabled = listening.recording || !listening.processing && !busy && !needsModel,
            colors = if (listening.recording) stopColors else ButtonDefaults.buttonColors(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("compact-listen")) {
            Text(if (listening.recording) tr(R.string.stop_listening) else tr(R.string.start_listening), style = MaterialTheme.typography.headlineSmall)
        }
        if (listening.processing) LinearProgressIndicator(Modifier.fillMaxWidth())
        // The rules line stays visible while listening (state rule, or the default reminder when stopped).
        Text(listening.rulesLabel.takeIf { listening.recording && it.isNotBlank() } ?: tr(R.string.follows_your_state_s_rules_no),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("compact-rules"))
        if (listening.recording) {
            if (listening.consent.isNotBlank()) Text(listening.consent, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("consent-status"))
            if (listening.callActive) Text(tr(R.string.call_detected_strictest_rule), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("call-status"))
            if (listening.answer.isNotBlank()) Text(listening.answer, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("plan-answer"))
            else AskHint()
            RestingTip(listening)
            if (listening.noticeNeeded || showAnnounce) {
                OutlinedButton(onClick = onAnnounce, modifier = Modifier.fillMaxWidth().heightIn(min = SimpleTarget).testTag("announce-notice")) {
                    Text(if (listening.noticeActive) tr(R.string.announce_again) else tr(R.string.announce_note_taking), style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(onClick = onNoticeGiven, modifier = Modifier.fillMaxWidth().heightIn(min = SimpleTarget).testTag("notice-given")) {
                    Text(listening.attestLabel, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (listening.recording || listening.processing) TextButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth().heightIn(min = SimpleTarget).testTag("compact-discard")) {
            Text(tr(R.string.stop_and_delete_what_was_heard), style = MaterialTheme.typography.titleMedium)
        }
        if (needsModel) {
            Text(tr(R.string.speech_for_your_language_isn_t), style = MaterialTheme.typography.bodyLarge)
            FilledTonalButton(onClick = onSetup, modifier = Modifier.fillMaxWidth().heightIn(min = SimpleTarget).testTag("compact-setup")) {
                Text(tr(R.string.set_up_speech), style = MaterialTheme.typography.titleMedium)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
        if (toCheck > 0) FilledTonalButton(onClick = onReview, modifier = Modifier.fillMaxWidth().heightIn(min = SimpleTarget).testTag("review-badge")) {
            Text(trn(R.plurals.plans_to_check, toCheck, toCheck), style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider()
        Text(tr(R.string.your_next_plans), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        if (next.isEmpty()) Text(tr(R.string.no_plans_yet_plans_you_mention),
            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("today-empty"))
        next.forEach { (item, at) -> TodayPlanCard(item, at, undoable[item.id], onOpen, onUndo) { removing = item } }
        OutlinedButton(onClick = onAllPlans, modifier = Modifier.fillMaxWidth().heightIn(min = SimpleTarget).testTag("today-all-plans")) {
            Text(tr(R.string.see_all_plans), style = MaterialTheme.typography.titleMedium)
        }
    }
    removing?.let { item ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text(tr(R.string.remove_this_plan)) },
            text = { Text(tr(R.string.n_1_s_and_its_reminder_are, item.title)) },
            confirmButton = { TextButton(onClick = { onRemove(item); removing = null }, modifier = Modifier.heightIn(min = SimpleTarget).testTag("confirm-remove")) { Text(tr(R.string.remove)) } },
            dismissButton = { TextButton(onClick = { removing = null }, modifier = Modifier.heightIn(min = SimpleTarget)) { Text(tr(R.string.keep_it)) } })
    }
}

@Composable
private fun TodayPlanCard(item: CommitmentEntity, at: Long, undo: RecentAutomaticChange?, onOpen: (CommitmentEntity) -> Unit,
                          onUndo: (RecentAutomaticChange) -> Unit, onRemove: () -> Unit) {
    val zone = runCatching { ZoneId.of(item.zoneId) }.getOrDefault(ZoneId.systemDefault())
    val time = Instant.ofEpochMilli(at).atZone(zone)
    val day = when (time.toLocalDate()) {
        LocalDate.now(zone) -> tr(R.string.today)
        LocalDate.now(zone).plusDays(1) -> tr(R.string.tomorrow)
        else -> time.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    }
    val whenText = tr(R.string.n_1_s_at_2_s, day, time.format(DateTimeFormatter.ofPattern("h:mm a")))
    val where = listOfNotNull(item.placeName, item.placeAddress).filter { it.isNotBlank() }.joinToString(", ")
    OutlinedCard(Modifier.fillMaxWidth().testTag("today-plan-${item.id}"), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(whenText, style = MaterialTheme.typography.bodyLarge)
            if (where.isNotEmpty()) Text(tr(R.string.at_1_s, where), style = MaterialTheme.typography.bodyLarge)
            if (item.informational) Text(tr(R.string.for_your_information_you_may_not), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onOpen(item) }, modifier = Modifier.weight(1f).heightIn(min = SimpleTarget).testTag("today-change-${item.id}")
                    .semantics { contentDescription = tr(R.string.change_1_s, item.title) }) { Text(tr(R.string.change)) }
                if (undo != null) OutlinedButton(onClick = { onUndo(undo) }, modifier = Modifier.weight(1f).heightIn(min = SimpleTarget).testTag("today-undo-${item.id}")
                    .semantics { contentDescription = tr(R.string.undo_1_s_2_s, undo.action.lowercase(), item.title) }) { Text(tr(R.string.undo)) }
                else OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f).heightIn(min = SimpleTarget).testTag("today-remove-${item.id}")
                    .semantics { contentDescription = tr(R.string.remove_1_s, item.title) }) { Text(tr(R.string.remove)) }
            }
        }
    }
}

/** The simple layout's "More" page: everything that is not listening or plans, one large button each. */
@Composable
internal fun SimpleMore(manualNotes: Boolean, onSummary: () -> Unit, onConversation: () -> Unit, onSetup: () -> Unit,
                        onSettings: () -> Unit, onDetailed: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp).testTag("more-content"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr(R.string.more), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        MoreItem(tr(R.string.day_summary), tr(R.string.your_plans_for_any_day), "more-summary", onSummary)
        MoreItem(if (manualNotes) tr(R.string.what_was_heard_notes) else tr(R.string.what_was_heard),
            if (manualNotes) tr(R.string.recent_words_and_notes) else tr(R.string.recent_words), "more-conversation", onConversation)
        MoreItem(tr(R.string.setup), tr(R.string.speech_your_voice_permissions), "get-started", onSetup)
        MoreItem(tr(R.string.settings), tr(R.string.screen_listening_voice_reminders_privacy), "more-settings", onSettings)
        MoreItem(tr(R.string.detailed_screen), tr(R.string.shows_every_tool_switch_back_in), "more-detailed", onDetailed)
    }
}

@Composable
private fun MoreItem(title: String, explanation: String, tag: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(tag),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(explanation, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** While listening, until the first question: one example to say and a link to the full list. */
@Composable
internal fun AskHint() {
    val settings = (androidx.compose.ui.platform.LocalContext.current.applicationContext as SidekickApplication).guidance
    val asked by settings.askedQuestion.collectAsState()
    if (asked) return
    val language by settings.speechLanguage.collectAsState()
    val detect by settings.detectLanguages.collectAsState()
    val codes = askLanguages(language, detect)
    var all by remember { mutableStateOf(false) }
    Column(Modifier.testTag("ask-hint")) {
        Text(tr(R.string.try_saying_1_s, com.sidekick.core.PlanQuestion.examples(codes.first()).first()), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { all = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("ask-more")) { Text(tr(R.string.more_questions)) }
    }
    if (all) AlertDialog(onDismissRequest = { all = false }, title = { Text(tr(R.string.ask_sidekick)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { AskExamples(codes) } },
        confirmButton = { TextButton(onClick = { all = false }) { Text(tr(R.string.close)) } })
}
