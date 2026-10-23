package com.sidekick.app

import com.sidekick.app.data.CommitmentEntity
import com.sidekick.core.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Recent live text and unapproved proposals exist only in this process's memory. */
class TemporaryMemory {
    private val mutableText = MutableStateFlow("")
    val text = mutableText.asStateFlow()
    private val mutableDrafts = MutableStateFlow<List<CommitmentEntity>>(emptyList())
    val drafts = mutableDrafts.asStateFlow()
    private val pipeline = CommitmentPipeline(RuleIntentExtractor())
    private var context = ""
    private var contextAt = Instant.now()
    private var contextZone = ZoneId.systemDefault()
    private var sessionBaseline = emptySet<String>()

    @Synchronized fun appendLive(text: String, capturedAt: Instant, zone: ZoneId) {
        if (text.isBlank()) return
        if (context.isBlank()) { contextAt = capturedAt; contextZone = zone }
        val combined = LiveText.append(context, text)
        val input = Transcript(combined.take(10_000), contextAt, contextZone, Speaker.USER, true)
        propose(input)
        mutableText.value = LiveText.append(mutableText.value, text).takeLast(6_000)
        // Keep only an unfinished sentence for cross-chunk commitments.
        val end = combined.indexOfLast { it == '.' || it == '!' || it == '?' }
        context = (if (end >= 0) combined.substring(end + 1) else combined).takeLast(1_000)
        if (end >= 0) { contextAt = capturedAt; contextZone = zone }
    }

    @Synchronized fun propose(input: Transcript): Int {
        return addProposals(pipeline.process(input))
    }

    @Synchronized fun addProposals(proposals: List<ProposedCommitment>): Int {
        val existing = mutableDrafts.value.map { it.title.lowercase() }.toSet()
        val fresh = proposals.filter { it.title.lowercase() !in existing }.map {
            CommitmentEntity(UUID.randomUUID().toString(), it.title, it.evidence, it.capturedAt.toEpochMilli(),
                it.zoneId.id, it.needsReview)
        }
        check(mutableDrafts.value.size + fresh.size <= 200) { "Review or discard pending proposals before continuing." }
        mutableDrafts.value += fresh
        return fresh.size
    }

    @Synchronized fun add(item: CommitmentEntity) {
        check(mutableDrafts.value.size < 200) { "Review pending proposals first." }
        mutableDrafts.value += item
    }
    @Synchronized fun remove(id: String) { mutableDrafts.value = mutableDrafts.value.filterNot { it.id == id } }
    @Synchronized fun clearText() { mutableText.value = ""; context = "" }
    @Synchronized fun beginSession() { clearText(); sessionBaseline = mutableDrafts.value.map { it.id }.toSet() }
    @Synchronized fun discardSession() {
        clearText()
        mutableDrafts.value = mutableDrafts.value.filter { it.id in sessionBaseline }
    }
    @Synchronized fun discard() { clearText(); mutableDrafts.value = emptyList(); sessionBaseline = emptySet() }
}
