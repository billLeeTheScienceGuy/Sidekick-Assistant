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
    data class PendingChange(val change: EventChange, val at: Instant, val zone: ZoneId, val source: String, val speaker: Speaker)
    private val changes = ArrayDeque<PendingChange>()
    private val seenChanges = linkedMapOf<String, String>()
    private val liveChanges = linkedSetOf<String>()
    @Synchronized fun prepareChanges(input: Transcript, source: String): Transcript {
        if (!input.processingAllowed) return input
        val ordinary = Sentences.split(input.text).filter { sentence ->
            if (!EventChange.looksLikeChange(sentence)) true else {
                EventChange.parse(sentence)?.let { change ->
                    val channel = if (source.startsWith("voice:")) "voice" else source
                    val key = sentence.trim().lowercase(java.util.Locale.ROOT)
                    val replay = source == "voice:copied" && key in liveChanges
                    if (source.startsWith("voice:") && source != "voice:copied") {
                        liveChanges += key
                        while (liveChanges.size > 64) liveChanges.remove(liveChanges.first())
                    }
                    if (!replay && seenChanges.put(channel, key) != key) {
                        while (seenChanges.size > 32) seenChanges.remove(seenChanges.keys.first())
                        if (changes.size == 32) changes.removeFirst()
                        changes.addLast(PendingChange(change, input.capturedAt, input.zoneId, source, input.speaker))
                    }
                }
                false // Correction wording must not also create a second event.
            }
        }
        return input.copy(text = ordinary.joinToString("\n"))
    }
    @Synchronized fun takeChanges(): List<PendingChange> = changes.toList().also { changes.clear() }
    private val pipeline = CommitmentPipeline(RuleIntentExtractor())
    private var contextAt = Instant.now()
    private var contextZone = ZoneId.systemDefault()
    private var sessionBaseline = emptySet<String>()
    private var voiceSpeaker = Speaker.UNKNOWN
    private var voiceContext = ""
    private var voiceTurn = 0
    private var voiceAt = Instant.now()
    private var lastVoiceAt = Instant.EPOCH
    private data class CorrectionContext(val id: String, val opening: String, val at: Instant, val zone: ZoneId, val lastAt: Instant, val changed: CommitmentEntity? = null)
    private var correctionContext: CorrectionContext? = null

    private fun sameVoiceActivity(item: CommitmentEntity, evidence: String, start: Long?, capturedAt: Long, source: String?): Boolean {
        if (item.sourceKey?.startsWith("voice:") != true || source?.startsWith("voice:") != true) return false
        if (start == null || item.activityStartAt != start || kotlin.math.abs(item.capturedAt - capturedAt) > 120_000) return false
        fun opening(value: String) = java.text.Normalizer.normalize(value.substringBefore('\n').lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFKC).filter { it.isLetterOrDigit() }
        return opening(item.evidence).let { it.isNotEmpty() && it == opening(evidence) }
    }

    private fun offerUncertainUpdate(text: String, at: Instant) {
        val anchor = correctionContext ?: return
        val age = java.time.Duration.between(anchor.lastAt, at).seconds
        if (age !in 0..120 || text.length > 600 || ActivityTiming.looksLikeActivity(text) || !LocalizedActivities.isContinuation(text)) {
            correctionContext = null; return
        }
        val target = mutableDrafts.value.find { it.id == anchor.id }
            ?: reviewed.values.mapNotNull { it.saved }.firstOrNull { it.id == anchor.id } ?: anchor.changed ?: return
        if (target.informational && !includeInformational()) return
        val input = Transcript(anchor.opening + "\n" + text, anchor.at, anchor.zone, Speaker.OTHER, true)
        val detected = ActivityTiming.extract(input, informationalOnly = true).singleOrNull() ?: return
        val end = detected.activityEndAt?.toEpochMilli()
        val placeChanged = detected.placeName != null && detected.placeName != target.placeName
        val addressChanged = detected.placeAddress != null && detected.placeAddress != target.placeAddress
        if ((end == null || end == target.activityEndAt) && !placeChanged && !addressChanged) return
        val source = "uncertain:${target.id}:${text.lowercase().hashCode()}"
        val pending = mutableDrafts.value.find { updateTargets[it.id] == target.id && it.sourceKey?.startsWith("uncertain:") == true }
        val candidate = target.copy(id = pending?.id ?: UUID.randomUUID().toString(), state = "PROPOSED",
            evidence = (if (anchor.changed != null) target.evidence.substringBefore('\n') else anchor.opening) + "\n" + text, sourceKey = source,
            activityEndAt = end ?: target.activityEndAt,
            placeName = detected.placeName ?: target.placeName,
            placeAddress = if (placeChanged || addressChanged) detected.placeAddress else target.placeAddress,
            reviewNote = "Possible update · speaker uncertain. Confirm this statement refers to ‘${target.title}’ before replacing its details. Original item stays unchanged until you approve.")
        if (reviewed.containsKey(key(candidate))) return
        check(pending != null || mutableDrafts.value.size < 200) { "Review pending proposals first." }
        mutableDrafts.value = mutableDrafts.value.filterNot { it.id == candidate.id } + candidate
        updateTargets[candidate.id] = target.id
    }

    /** Reset reference context on speaker changes; unknown voices never create personal promises. */
    @Synchronized fun appendAttributedLive(text: String, capturedAt: Instant, zone: ZoneId, speaker: Speaker, continuation: Boolean = true) {
        if (text.isBlank()) return
        val source = "voice:$voiceTurn:${speaker.name}"
        val fresh = prepareChanges(Transcript(text, capturedAt, zone, speaker, true), source)
        if (fresh.text.isBlank()) { mutableText.value = LiveText.append(mutableText.value, text, !continuation).takeLast(6000); return }
        if (speaker == Speaker.UNKNOWN || (correctionContext?.changed != null && (speaker == Speaker.USER || correctionContext?.changed?.informational == true))) offerUncertainUpdate(fresh.text, capturedAt)
        else if (speaker == Speaker.OTHER) correctionContext = null
        if (voiceContext.isBlank() || speaker != voiceSpeaker || java.time.Duration.between(lastVoiceAt, capturedAt).seconds !in 0..120 ||
            voiceAt.atZone(zone).toLocalDate() != capturedAt.atZone(zone).toLocalDate()) {
            voiceContext = ""; voiceAt = capturedAt; voiceTurn++; voiceSpeaker = speaker
        }
        lastVoiceAt = capturedAt
        voiceContext = LiveText.append(voiceContext, fresh.text, !continuation).takeLast(6000)
        val input = Transcript(voiceContext, voiceAt, zone, speaker, true)
        val proposals = if (speaker == Speaker.USER) ActivityTiming.merge(input, pipeline.process(input))
            else ActivityTiming.extract(input.copy(speaker = Speaker.OTHER), informationalOnly = true)
        val attributed = proposals.map { it.copy(sourceKey = "voice:$voiceTurn:${speaker.name}",
            needsReview = (if (speaker == Speaker.USER) "Likely your voice; verify attribution. " else if (speaker == Speaker.OTHER) "Another voice; attendance unconfirmed. " else "Speaker unassigned; attendance unconfirmed. ") + it.needsReview) }
        addProposals(attributed)
        if (ActivityTiming.looksLikeActivity(text)) {
            val activity = attributed.filter { it.activityStartAt != null }.singleOrNull()
            val candidates = mutableDrafts.value + reviewed.values.mapNotNull { it.saved }
            val target = activity?.let { found -> candidates.distinctBy { it.id }.singleOrNull {
                it.sourceKey == found.sourceKey && it.activityStartAt == found.activityStartAt?.toEpochMilli() &&
                    it.evidence.substringBefore('\n').trimEnd('.', '!') == found.evidence.substringBefore('\n').trimEnd('.', '!')
            } ?: reviewed[found.evidence.substringBefore('\n').trim().trimEnd('.', '!').lowercase() + "|" + found.activityStartAt?.toEpochMilli() + "|" + found.sourceKey]?.saved
                ?: candidates.distinctBy { it.id }.singleOrNull { sameVoiceActivity(it, found.evidence, found.activityStartAt?.toEpochMilli(), found.capturedAt.toEpochMilli(), found.sourceKey) } }
            correctionContext = target?.let { CorrectionContext(it.id, activity!!.evidence.substringBefore('\n'), voiceAt, zone, capturedAt) }
        }
        mutableText.value = LiveText.append(mutableText.value, text, !continuation).takeLast(6000)
    }
    private data class Reviewed(val saved: CommitmentEntity?, val detectedEnd: Long?, val detectedPlace: String?, val detectedAddress: String?, val source: CommitmentEntity)
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

    @Synchronized fun propose(input: Transcript, source: String? = null): Int {
        val prepared = prepareChanges(input, source ?: "typed")
        return addProposals(ActivityTiming.merge(prepared, pipeline.process(prepared)).map { it.copy(sourceKey = source) })
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
            val incomplete = drafts.filter { it.activityStartAt == null && candidate.activityStartAt != null &&
                it.sourceKey == candidate.sourceKey && it.evidence != "Manually added" &&
                it.evidence.substringBefore('\n').trimEnd('.', '!') == candidate.evidence.substringBefore('\n').trimEnd('.', '!') }.singleOrNull()
            if (incomplete != null) {
                drafts[drafts.indexOf(incomplete)] = candidate.copy(id = incomplete.id)
                continue
            }
            // Overlapping speech can repeat an event after the voice label changes.
            val seen = reviewed.values.filter { sameVoiceActivity(it.source, candidate.evidence, candidate.activityStartAt, candidate.capturedAt, candidate.sourceKey) }
            val matches = (drafts.filter { updateTargets[it.id] == null } + seen.mapNotNull { it.saved })
                .filter { sameVoiceActivity(it, candidate.evidence, candidate.activityStartAt, candidate.capturedAt, candidate.sourceKey) }.distinctBy { it.id }
            if (seen.any { it.saved == null } && matches.isEmpty()) continue
            val duplicate = matches.singleOrNull()?.takeIf { key(it) != sourceKey }
            if (duplicate != null) {
                val previousDetection = seen.firstOrNull { it.saved?.id == duplicate.id }
                val endChanged = candidate.activityEndAt != null && candidate.activityEndAt != duplicate.activityEndAt && candidate.activityEndAt != previousDetection?.detectedEnd
                val placeChanged = candidate.placeName != null && candidate.placeName != duplicate.placeName && candidate.placeName != previousDetection?.detectedPlace
                val addressChanged = candidate.placeAddress != null && candidate.placeAddress != duplicate.placeAddress && candidate.placeAddress != previousDetection?.detectedAddress
                if (!endChanged && !placeChanged && !addressChanged) continue
                val signature = listOf(candidate.activityEndAt, candidate.placeName, candidate.placeAddress).hashCode()
                val pending = drafts.indexOfFirst { updateTargets[it.id] == duplicate.id && it.sourceKey?.startsWith("uncertain:") == true }
                val revision = duplicate.copy(id = if (pending >= 0) drafts[pending].id else UUID.randomUUID().toString(), state = "PROPOSED",
                    evidence = candidate.evidence, sourceKey = "uncertain:${duplicate.id}:repeat:$signature",
                    activityEndAt = if (endChanged) candidate.activityEndAt else duplicate.activityEndAt,
                    placeName = if (placeChanged) candidate.placeName else duplicate.placeName,
                    placeAddress = if (placeChanged || addressChanged) candidate.placeAddress else duplicate.placeAddress,
                    reviewNote = "Possible update · repeated speech. Confirm this repeated event refers to ‘${duplicate.title}’ before replacing its details. Original item stays unchanged until you approve.")
                if (reviewed.containsKey(key(revision))) continue
                updateTargets[revision.id] = duplicate.id
                if (pending >= 0) drafts[pending] = revision else { drafts += revision; added++ }
                continue
            }
            val previous = reviewed[sourceKey]
            if (previous != null) {
                val saved = previous.saved ?: continue // Dismissed source, not an accepted activity.
                val attendanceChanged = candidate.informational != saved.informational &&
                    (candidate.sourceKey == null || candidate.sourceKey.endsWith(":USER")) &&
                    candidate.evidence.lines().any { it !in saved.evidence.lines() && ConversationActivities.attendanceValue(it) != null }
                val endChanged = candidate.activityEndAt != null && candidate.activityEndAt != previous.detectedEnd && candidate.activityEndAt != saved.activityEndAt
                val placeChanged = candidate.placeName != null && candidate.placeName != previous.detectedPlace && candidate.placeName != saved.placeName
                val addressChanged = candidate.placeAddress != null && candidate.placeAddress != previous.detectedAddress && candidate.placeAddress != saved.placeAddress
                if (!attendanceChanged && !endChanged && !placeChanged && !addressChanged) continue
                if (saved.activityStartAt != null && saved.activityStartAt != candidate.activityStartAt) continue
                val pending = drafts.indexOfFirst { updateTargets[it.id] == saved.id }
                val revision = saved.copy(id = if (pending >= 0) drafts[pending].id else UUID.randomUUID().toString(),
                    state = "PROPOSED", informational = if (attendanceChanged) candidate.informational else saved.informational, activityStartAt = saved.activityStartAt ?: candidate.activityStartAt,
                    activityEndAt = if (endChanged) candidate.activityEndAt else saved.activityEndAt,
                    placeName = if (placeChanged) candidate.placeName else saved.placeName,
                    placeAddress = if (placeChanged || addressChanged) candidate.placeAddress else saved.placeAddress, evidence = candidate.evidence,
                    reviewNote = "Suggested activity update. Review before replacing saved details. " + candidate.reviewNote)
                updateTargets[revision.id] = saved.id
                if (pending >= 0) drafts[pending] = revision else { drafts += revision; added++ }
            } else {
                val existing = drafts.indexOfFirst { key(it) == sourceKey }
                if (existing >= 0) {
                    if (candidate.activityEndAt != null || candidate.placeName != null || candidate.placeAddress != null || candidate.informational != drafts[existing].informational) {
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
        correctionContext?.takeIf { it.id == saved.id && it.changed != null }?.let { correctionContext = it.copy(changed = saved) }
        mutableDrafts.value.find { it.id == saved.id && it.id != id }?.let {
            reviewed[key(it)] = Reviewed(saved, it.activityEndAt, it.placeName, it.placeAddress, it)
            remove(it.id)
        }
        reviewed.entries.filter { it.value.saved?.id == saved.id }.forEach { it.setValue(it.value.copy(saved = saved)) }
        mutableDrafts.value.find { it.id == id }?.let { reviewed[key(it)] = Reviewed(saved, it.activityEndAt, it.placeName, it.placeAddress, it) }
        trimReviewed(); remove(id)
    }
    @Synchronized fun dismiss(id: String) {
        if (correctionContext?.id == id) correctionContext = null
        mutableDrafts.value.find { it.id == id }?.let { reviewed[key(it)] = Reviewed(null, it.activityEndAt, it.placeName, it.placeAddress, it) }
        trimReviewed(); remove(id)
    }
    /** Anchor fresh detail statements to the new time, while suppressing the old rolling transcript. */
    @Synchronized fun changedEvent(saved: CommitmentEntity) {
        retire(saved.id)
        if (saved.state != "ACCEPTED" || saved.activityStartAt == null) return
        val noun = Regex("\\b(party|meeting|appointment|class|lecture|concert|dinner|lunch|wedding|birthday|practice|workshop|movie|trip|shift|call)\\b", RegexOption.IGNORE_CASE).find(saved.title)?.value
        val zone = ZoneId.of(saved.zoneId)
        val local = Instant.ofEpochMilli(saved.activityStartAt).atZone(zone)
        val opening = if (noun != null) "There's a $noun on ${local.toLocalDate()} at ${local.toLocalTime().toString().take(5)}."
            else LocalizedActivities.referenceOpening(saved.title, Instant.ofEpochMilli(saved.activityStartAt), zone) ?: return
        correctionContext = CorrectionContext(saved.id, opening, Instant.now(), zone, Instant.now(), saved)
    }
    @Synchronized fun retire(id: String) {
        if (correctionContext?.id == id) correctionContext = null
        reviewed.entries.filter { it.value.saved?.id == id }.forEach { it.setValue(it.value.copy(saved = null)) }
        updateTargets.filterValues { it == id }.keys.toList().forEach(::remove)
    }
    private fun trimReviewed() { while (reviewed.size > 400) reviewed.remove(reviewed.keys.first()) }
    @Synchronized fun clearText() { mutableText.value = ""; voiceContext = ""; correctionContext = null; changes.clear(); seenChanges.clear(); liveChanges.clear() }
    @Synchronized fun beginSession() { clearText(); voiceContext = ""; voiceTurn = 0; reviewed.clear(); sessionBaseline = mutableDrafts.value.map { it.id }.toSet() }
    @Synchronized fun discardSession() {
        clearText()
        voiceContext = ""
        mutableDrafts.value = mutableDrafts.value.filter { it.id in sessionBaseline }
        updateTargets.keys.retainAll(mutableDrafts.value.map { it.id }.toSet())
    }
    @Synchronized fun discard() { clearText(); voiceContext = ""; mutableDrafts.value = emptyList(); sessionBaseline = emptySet(); reviewed.clear(); updateTargets.clear() }
}
