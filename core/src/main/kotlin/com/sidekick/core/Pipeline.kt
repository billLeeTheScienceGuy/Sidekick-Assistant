package com.sidekick.core

import java.time.Instant
import java.time.ZoneId

enum class Speaker { USER, OTHER, UNKNOWN }

data class Transcript(
    val text: String,
    val capturedAt: Instant,
    val zoneId: ZoneId,
    val speaker: Speaker,
    val processingAllowed: Boolean,
)

data class ProposedCommitment(
    val title: String,
    val evidence: String,
    val capturedAt: Instant,
    val zoneId: ZoneId,
    val needsReview: String,
    val activityStartAt: Instant? = null,
    val activityEndAt: Instant? = null,
    val informational: Boolean = false,
    val placeName: String? = null,
    val placeAddress: String? = null,
    val sourceKey: String? = null,
    /** Midnight-to-midnight plan whose clock time was not said (see [PlanNotes.ALL_DAY]). */
    val allDay: Boolean = false,
    /** Visible hands-off assumptions, e.g. "all day (time assumed)". */
    val assumptions: List<String> = emptyList(),
    /** Visible extra facts such as an unsaved repeat or the reminder plan. */
    val notes: List<String> = emptyList(),
    /** First reminder chosen by the plan kind's documented default; null keeps the older start-time rule. */
    val reminderAt: Instant? = null,
    /** Set when what was said conflicts (two times, hedges): the item waits in Review instead of saving. */
    val conflict: String? = null,
)

/** Implementations must not return invented transcripts when a model is absent. */
interface SpeechTranscriber { suspend fun transcribe(audio: AudioSegment): Transcript }
interface IntentExtractor { fun extract(transcript: Transcript): List<ProposedCommitment> }
interface VoiceActivityDetector { fun isSpeech(frame: FloatArray): Boolean }
interface SpeakerIdentifier { fun identify(audio: AudioSegment): Speaker }
data class AudioSegment(val samples: FloatArray, val sampleRate: Int, val capturedAt: Instant) {
    init { require(sampleRate > 0); require(samples.isNotEmpty()) }
}

/** Consent and ownership are enforced outside any model implementation. */
class CommitmentPipeline(private val extractor: IntentExtractor) {
    fun process(transcript: Transcript): List<ProposedCommitment> {
        require(transcript.text.length <= 10_000) { "Use a transcript of at most 10,000 characters." }
        if (!transcript.processingAllowed || transcript.speaker != Speaker.USER) return emptyList()
        return extractor.extract(transcript)
    }
}
