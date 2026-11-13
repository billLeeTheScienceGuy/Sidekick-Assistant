package com.sidekick.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sidekick.app.audio.ListeningState
import com.sidekick.app.data.CommitmentEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** An overview, not a miniature version of every editor and diagnostic panel. */
@Composable
internal fun CompactHome(
    listening: ListeningState, entries: List<CommitmentEntity>, liveText: String,
    manualNotes: Boolean, needsModel: Boolean, busy: Boolean, error: String?,
    onStart: () -> Unit, onFinish: () -> Unit, onDetails: () -> Unit, onSetup: () -> Unit,
    onOpen: (CommitmentEntity) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp).testTag("one-screen-home")) {
        val tight = maxHeight < 350.dp || LocalDensity.current.fontScale * LocalNoteTextFactor.current > 1.8f && maxHeight < 600.dp
        val next = entries.filter { it.state == "ACCEPTED" }.mapNotNull { item ->
            (item.activityStartAt ?: item.dueAt)?.takeIf { it >= System.currentTimeMillis() }?.let { item to it }
        }.minByOrNull { it.second }?.first
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (tight) 6.dp else 12.dp)) {
            if (!tight) Text("Your day", style = MaterialTheme.typography.headlineMedium)
            Text(when {
                listening.recording -> if (listening.speechActive) "Listening · speech detected" else "Listening · microphone is on"
                listening.processing -> "Finishing your notes…"
                else -> if (listening.note == "Microphone is off") "Ready when you are" else listening.note
            }, maxLines = if (tight) 1 else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("compact-status"))
            Button(onClick = if (listening.recording) onFinish else onStart,
                enabled = listening.recording || !listening.processing && !busy && !needsModel,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("compact-listen")) {
                Text(if (listening.recording) "Stop listening" else "Start listening", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when {
                needsModel -> TextButton(onClick = onSetup) { Text("Install speech model") }
                error != null -> Text(error, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
                !tight -> {
                    Card(Modifier.fillMaxWidth().clickable(enabled = next != null) { next?.let(onOpen) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            QuietLabel("NEXT UP")
                            if (next == null) Text("Nothing scheduled yet", style = MaterialTheme.typography.titleMedium)
                            else {
                                Text(next.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val at = next.activityStartAt ?: next.dueAt!!
                                Text(Instant.ofEpochMilli(at).atZone(ZoneId.of(next.zoneId)).format(DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a z")),
                                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (liveText.isNotBlank()) Text(liveText.takeLast(160), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (manualNotes) TextButton(onClick = onDetails, modifier = Modifier.testTag("compact-write")) { Text("Write a note") }
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDetails, modifier = Modifier.testTag("compact-details")) { Text("Detailed view") }
            if (!tight) QuietLabel("On-device speech · audio discarded")
        }
    }
}
