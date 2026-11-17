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
    "PROPOSED" -> "Needs checking"
    "ACCEPTED" -> "Saved"
    "DONE" -> "Done"
    "DISMISSED" -> "Removed"
    "CANCELLED" -> "Cancelled"
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
        RiskSection()
        Text(when {
            listening.recording -> "Listening"
            listening.processing -> "Finishing up…"
            else -> "Not listening"
        }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("compact-status"))
        if (listening.recording) Text(if (listening.speechActive) "Hearing someone speak" else "Waiting for someone to speak",
            style = MaterialTheme.typography.bodyLarge)
        val stopColors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
        Button(onClick = if (listening.recording) onFinish else onStart,
            enabled = listening.recording || !listening.processing && !busy && !needsModel,
            colors = if (listening.recording) stopColors else ButtonDefaults.buttonColors(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("compact-listen")) {
            Text(if (listening.recording) "Stop listening" else "Start listening", style = MaterialTheme.typography.headlineSmall)
        }
        if (listening.processing) LinearProgressIndicator(Modifier.fillMaxWidth())
        // The rules line stays visible while listening (state rule, or the default reminder when stopped).
        Text(listening.rulesLabel.takeIf { listening.recording && it.isNotBlank() } ?: "Rules follow your state · audio discarded",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("compact-rules"))
        if (listening.recording) {
            if (listening.consent.isNotBlank()) Text(listening.consent, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("consent-status"))
            if (listening.callActive) Text("Call detected: strictest rule", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("call-status"))
            if (listening.noticeNeeded || showAnnounce) {
                OutlinedButton(onClick = onAnnounce, modifier = Modifier.fillMaxWidth().heightIn(min = SimpleTarget).testTag("announce-notice")) {
                    Text(if (listening.noticeActive) "Announce again" else "Announce note-taking", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(onClick = onNoticeGiven, modifier = Modifier.fillMaxWidth().heightIn(min = SimpleTarget).testTag("notice-given")) {
                    Text(listening.attestLabel, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (listening.recording || listening.processing) TextButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth().heightIn(min = SimpleTarget).testTag("compact-discard")) {
            Text(if (handsOff) "Stop and clear what was heard" else "Stop and discard what was heard", style = MaterialTheme.typography.titleMedium)
        }
        if (needsModel) {
            Text("Speech for your language isn’t installed yet.", style = MaterialTheme.typography.bodyLarge)
            FilledTonalButton(onClick = onSetup, modifier = Modifier.fillMaxWidth().heightIn(min = SimpleTarget).testTag("compact-setup")) {
                Text("Set up speech", style = MaterialTheme.typography.titleMedium)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
        if (toCheck > 0) FilledTonalButton(onClick = onReview, modifier = Modifier.fillMaxWidth().heightIn(min = SimpleTarget).testTag("review-badge")) {
            Text(if (toCheck == 1) "1 plan to check" else "$toCheck plans to check", style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider()
        Text("Your next plans", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        if (next.isEmpty()) Text("No plans coming up. Plans you mention while listening appear here.",
            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("today-empty"))
        next.forEach { (item, at) -> TodayPlanCard(item, at, undoable[item.id], onOpen, onUndo) { removing = item } }
        OutlinedButton(onClick = onAllPlans, modifier = Modifier.fillMaxWidth().heightIn(min = SimpleTarget).testTag("today-all-plans")) {
            Text("See all plans", style = MaterialTheme.typography.titleMedium)
        }
    }
    removing?.let { item ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remove this plan?") },
            text = { Text("“${item.title}” and its reminder are removed from your list. You can bring it back from Plans → Past.") },
            confirmButton = { TextButton(onClick = { onRemove(item); removing = null }, modifier = Modifier.heightIn(min = SimpleTarget).testTag("confirm-remove")) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }, modifier = Modifier.heightIn(min = SimpleTarget)) { Text("Keep it") } })
    }
}

@Composable
private fun TodayPlanCard(item: CommitmentEntity, at: Long, undo: RecentAutomaticChange?, onOpen: (CommitmentEntity) -> Unit,
                          onUndo: (RecentAutomaticChange) -> Unit, onRemove: () -> Unit) {
    val zone = runCatching { ZoneId.of(item.zoneId) }.getOrDefault(ZoneId.systemDefault())
    val time = Instant.ofEpochMilli(at).atZone(zone)
    val day = when (time.toLocalDate()) {
        LocalDate.now(zone) -> "Today"
        LocalDate.now(zone).plusDays(1) -> "Tomorrow"
        else -> time.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    }
    val whenText = "$day at ${time.format(DateTimeFormatter.ofPattern("h:mm a"))}"
    val where = listOfNotNull(item.placeName, item.placeAddress).filter { it.isNotBlank() }.joinToString(", ")
    OutlinedCard(Modifier.fillMaxWidth().testTag("today-plan-${item.id}"), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(whenText, style = MaterialTheme.typography.bodyLarge)
            if (where.isNotEmpty()) Text("At $where", style = MaterialTheme.typography.bodyLarge)
            if (item.informational) Text("For your information: you may not be going", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onOpen(item) }, modifier = Modifier.weight(1f).heightIn(min = SimpleTarget).testTag("today-change-${item.id}")
                    .semantics { contentDescription = "Change ${item.title}" }) { Text("Change") }
                if (undo != null) OutlinedButton(onClick = { onUndo(undo) }, modifier = Modifier.weight(1f).heightIn(min = SimpleTarget).testTag("today-undo-${item.id}")
                    .semantics { contentDescription = "Undo ${undo.action.lowercase()} ${item.title}" }) { Text("Undo") }
                else OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f).heightIn(min = SimpleTarget).testTag("today-remove-${item.id}")
                    .semantics { contentDescription = "Remove ${item.title}" }) { Text("Remove") }
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
        Text("More", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        MoreItem("Summary of your day", "Your plans and events for any day.", "more-summary", onSummary)
        MoreItem(if (manualNotes) "What was heard & notes" else "What was heard",
            if (manualNotes) "Recent words, typing a note, and listening details." else "Recent words and listening details.", "more-conversation", onConversation)
        MoreItem("Set up speech and your voice", "Language, speech files, your voice and phone permissions.", "get-started", onSetup)
        MoreItem("Settings", "Listening, your voice, reminders and privacy.", "more-settings", onSettings)
        MoreItem("Use the detailed screen", "Shows every tool at once. You can switch back in Settings.", "more-detailed", onDetailed)
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
