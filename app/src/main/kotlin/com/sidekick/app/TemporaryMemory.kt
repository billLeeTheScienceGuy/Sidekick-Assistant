package com.sidekick.app

import com.sidekick.app.data.CommitmentEntity
import com.sidekick.core.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Recent live text and unapproved proposals exist only in this process's memory. */
class TemporaryMemory(private val includeInformational: () -> Boolean = { true }) {
    private val mutableText = MutableStateFlow("")
    val text = mutableText.asStateFlow()
    private val mutableDrafts = MutableStateFlow<List<CommitmentEntity>>(emptyList())
    val drafts = mutableDrafts.asStateFlow()
    private val pipeline = CommitmentPipeline(RuleIntentExtractor())
    private var contextAt = Instant.now()
    private var contextZone = ZoneId.systemDefault()
    private var sessionBaseline = emptySet<String>()
    private var voiceSpeaker = Speaker.UNKNOWN
    private var voiceContext = ""
    private var voiceTurn = 0
    private var voiceAt = Instant.now()

    /** Reset reference context on speaker changes; unknown voices never create personal promises. */
    @Synchronized fun appendAttributedLive(text: String, capturedAt: Instant, zone: ZoneId, speaker: Speaker) {
        if (text.isBlank()) return
        if (voiceContext.isBlank() || speaker != voiceSpeaker) {
            voiceContext = ""; voiceAt = capturedAt; voiceTurn++; voiceSpeaker = speaker
        }
        voiceContext = LiveText.append(voiceContext, text).takeLast(6000)
        val input = Transcript(voiceContext, voiceAt, zone, speaker, true)
        val proposals = if (speaker == Speaker.USER) ActivityTiming.merge(input, pipeline.process(input))
            else ActivityTiming.extract(input.copy(speaker = Speaker.OTHER), informationalOnly = true)
        addProposals(proposals.map { it.copy(sourceKey = "voice:$voiceTurn:${speaker.name}",
            needsReview = (if (speaker == Speaker.USER) "Likely your voice; verify attribution. " else if (speaker == Speaker.OTHER) "Another voice; attendance unconfirmed. " else "Speaker unassigned; attendance unconfirmed. ") + it.needsReview) })
        mutableText.value = LiveText.append(mutableText.value, text).takeLast(6000)
    }
    private data class Reviewed(val saved: CommitmentEntity?, val detectedEnd: Long?, val detectedPlace: String?, val detectedAddress: String?)
    private val reviewed = linkedMapOf<String, Reviewed>()
    private val updateTargets = mutableMapOf<String, String>()
    private fun key(item: CommitmentEntity) = item.evidence.substringBefore('\n').trim().trimEnd('.', '!').lowercase() + "|" + item.activityStartAt + "|" + item.sourceKey

    @Synchronized fun appendLive(text: String, capturedAt: Instant, zone: ZoneId) {
        if (text.isBlank()) return
        if (mutableText.value.isBlank()) { contextAt = capturedAt; contextZone = zone }
        val combined = LiveText.append(mutableText.value, text).takeLast(6_000)
        val input = Transcript(combined, contextAt, contextZone, Speaker.USER, true)
        propose(input)
        mutableText.value = combined
    }

    @Synchronized fun propose(input: Transcript): Int {
        return addProposals(ActivityTiming.merge(input, pipeline.process(input)))
    }

    @Synchronized fun addProposals(proposals: List<ProposedCommitment>): Int {
        val incoming = proposals.filter { !it.informational || includeInformational() }.map {
            CommitmentEntity(UUID.randomUUID().toString(), it.title, it.evidence, it.capturedAt.toEpochMilli(),
                it.zoneId.id, it.needsReview, activityStartAt = it.activityStartAt?.toEpochMilli(), activityEndAt = it.activityEndAt?.toEpochMilli(),
                informational = it.informational, placeName = it.placeName, placeAddress = it.placeAddress, sourceKey = it.sourceKey)
        }.distinctBy(::key)
        val drafts = mutableDrafts.value.toMutableList()
        var added = 0
        for (candidate in incoming) {
            val sourceKey = key(candidate)
            val previous = reviewed[sourceKey]
            if (previous != null) {
                val saved = previous.saved ?: continue // Dismissed source, not an accepted activity.
                val endChanged = candidate.activityEndAt != null && candidate.activityEndAt != previous.detectedEnd && candidate.activityEndAt != saved.activityEndAt
                val placeChanged = candidate.placeName != null && candidate.placeName != previous.detectedPlace && candidate.placeName != saved.placeName
                val addressChanged = candidate.placeAddress != null && candidate.placeAddress != previous.detectedAddress && candidate.placeAddress != saved.placeAddress
                if (!endChanged && !placeChanged && !addressChanged) continue
                if (saved.activityStartAt != null && saved.activityStartAt != candidate.activityStartAt) continue
                val pending = drafts.indexOfFirst { updateTargets[it.id] == saved.id }
                val revision = saved.copy(id = if (pending >= 0) drafts[pending].id else UUID.randomUUID().toString(),
                    state = "PROPOSED", activityStartAt = saved.activityStartAt ?: candidate.activityStartAt,
                    activityEndAt = if (endChanged) candidate.activityEndAt else saved.activityEndAt,
                    placeName = if (placeChanged) candidate.placeName else saved.placeName,
                    placeAddress = if (placeChanged || addressChanged) candidate.placeAddress else saved.placeAddress, evidence = candidate.evidence,
                    reviewNote = "Suggested activity update. Review before replacing saved details. " + candidate.reviewNote)
                updateTargets[revision.id] = saved.id
                if (pending >= 0) drafts[pending] = revision else { drafts += revision; added++ }
            } else {
                val existing = drafts.indexOfFirst { key(it) == sourceKey }
                if (existing >= 0) {
                    if (candidate.activityEndAt != null || candidate.placeName != null || candidate.placeAddress != null) {
                        val old = drafts[existing]
                        val changedVenue = candidate.placeName != null && old.placeName != null && !candidate.placeName.equals(old.placeName, true)
                        drafts[existing] = candidate.copy(id = old.id, activityEndAt = candidate.activityEndAt ?: old.activityEndAt,
                            placeName = candidate.placeName ?: old.placeName,
                            placeAddress = candidate.placeAddress ?: if (changedVenue) null else old.placeAddress,
                            evidence = (old.evidence.lines() + candidate.evidence.lines()).distinct().joinToString("\n"))
                    }
                } else { drafts += candidate; added++ }
            }
        }
        check(drafts.size <= 200) { "Review or discard pending proposals before continuing." }
        mutableDrafts.value = drafts
        return added
    }

    @Synchronized fun add(item: CommitmentEntity) {
        check(mutableDrafts.value.size < 200) { "Review pending proposals first." }
        mutableDrafts.value += item
    }
    @Synchronized fun remove(id: String) { mutableDrafts.value = mutableDrafts.value.filterNot { it.id == id }; updateTargets.remove(id) }
    @Synchronized fun updateTarget(id: String): String? = updateTargets[id]
    @Synchronized fun approve(id: String, saved: CommitmentEntity) {
        reviewed.entries.filter { it.value.saved?.id == saved.id }.forEach { it.setValue(it.value.copy(saved = saved)) }
        mutableDrafts.value.find { it.id == id }?.let { reviewed[key(it)] = Reviewed(saved, it.activityEndAt, it.placeName, it.placeAddress) }
        trimReviewed(); remove(id)
    }
    @Synchronized fun dismiss(id: String) {
        mutableDrafts.value.find { it.id == id }?.let { reviewed[key(it)] = Reviewed(null, it.activityEndAt, it.placeName, it.placeAddress) }
        trimReviewed(); remove(id)
    }
    @Synchronized fun retire(id: String) {
        reviewed.entries.filter { it.value.saved?.id == id }.forEach { it.setValue(it.value.copy(saved = null)) }
        updateTargets.filterValues { it == id }.keys.toList().forEach(::remove)
    }
    private fun trimReviewed() { while (reviewed.size > 400) reviewed.remove(reviewed.keys.first()) }
    @Synchronized fun clearText() { mutableText.value = ""; voiceContext = "" }
    @Synchronized fun beginSession() { clearText(); voiceContext = ""; voiceTurn = 0; reviewed.clear(); sessionBaseline = mutableDrafts.value.map { it.id }.toSet() }
    @Synchronized fun discardSession() {
        clearText()
        voiceContext = ""
        mutableDrafts.value = mutableDrafts.value.filter { it.id in sessionBaseline }
        updateTargets.keys.retainAll(mutableDrafts.value.map { it.id }.toSet())
    }
    @Synchronized fun discard() { clearText(); voiceContext = ""; mutableDrafts.value = emptyList(); sessionBaseline = emptySet(); reviewed.clear(); updateTargets.clear() }
}
