package com.sidekick.core

import kotlin.math.sqrt

/**
 * The owner's voice as heard in this listening session, built only from turns that already matched the saved profile
 * on their own (long turns, full owner thresholds). A later chunk that the profile alone leaves *unassigned* may count as
 * the owner when it sounds like those confirmed turns: same room, same microphone, same day.
 *
 * It can never overrule a "someone else" decision, never learns from a carried chunk (only profile-confirmed turns feed
 * it), and it is off wherever [VoiceMatch.Thresholds.session] is null (all-party places before notice). Owner signatures
 * only; strangers' signatures are never kept. Memory only, cleared at session end and after [KEEP_MS] without a
 * confirmed turn.
 */
class OwnerSession {
    private val confirmed = ArrayDeque<FloatArray>()
    private var lastConfirmedMs = Long.MIN_VALUE
    val size: Int get() = confirmed.size

    /** A long turn that matched the saved profile on its own; [vector] is that chunk's whole-speech signature. */
    fun confirm(vector: FloatArray, nowMs: Long) {
        val copy = VoiceMatch.normalized(vector)
        if (confirmed.isNotEmpty() && copy.size != confirmed.first().size) clear()
        confirmed.addLast(copy)
        while (confirmed.size > MAX_TURNS) confirmed.removeFirst().fill(0f)
        lastConfirmedMs = nowMs
    }

    data class Carry(val owner: Boolean, val session: Float, val profile: Float, val reason: String)

    /**
     * [profileScore] is the chunk's whole-speech similarity to the saved profile. Owner only when the chunk matches every
     * confirmed turn of this session ([VoiceMatch.SessionCarry.minimum]) and their mean, and the profile is not far off.
     */
    fun carry(vector: FloatArray, profileScore: Float, nowMs: Long, thresholds: VoiceMatch.Thresholds): Carry {
        val rule = thresholds.session ?: return Carry(false, Float.NaN, profileScore, "session match off here")
        if (confirmed.isEmpty()) return Carry(false, Float.NaN, profileScore, "no confirmed turn yet")
        if (nowMs - lastConfirmedMs !in 0..KEEP_MS) { clear(); return Carry(false, Float.NaN, profileScore, "confirmed turn too old") }
        if (vector.size != confirmed.first().size || !profileScore.isFinite()) return Carry(false, Float.NaN, profileScore, "model changed")
        val scores = confirmed.map { VoiceMatch.similarity(it, vector) }
        val mean = VoiceMatch.normalized(FloatArray(vector.size) { i -> confirmed.sumOf { it[i].toDouble() }.toFloat() })
        val centre = try { VoiceMatch.similarity(mean, vector) } finally { mean.fill(0f) }
        val lowest = scores.min()
        val owner = lowest >= rule.minimum && centre >= rule.mean && profileScore >= rule.profileFloor
        return Carry(owner, lowest, profileScore, if (owner) "matches your voice earlier in this session" else "not close enough to your voice earlier")
    }

    /** Normalized mean of the confirmed turns (a copy), or null when none is fresh. */
    fun mean(nowMs: Long): FloatArray? {
        if (confirmed.isEmpty() || nowMs - lastConfirmedMs !in 0..KEEP_MS) return null
        val size = confirmed.first().size
        return VoiceMatch.normalized(FloatArray(size) { i -> confirmed.sumOf { it[i].toDouble() }.toFloat() })
    }
    fun clear() { confirmed.forEach { it.fill(0f) }; confirmed.clear(); lastConfirmedMs = Long.MIN_VALUE }

    companion object {
        /** Forgotten after this long without a profile-confirmed turn, like other session voice groups. */
        const val KEEP_MS = SessionVoices.KEEP_MS
        const val MAX_TURNS = 8
    }
}

/**
 * Stranger cohort for adaptive score normalization (AS-norm). A raw similarity is compared with how the same two
 * signatures score against many other people's voices: "is this closer than strangers usually get?". Public voices only
 * (LibriSpeech test-other, CC BY 4.0), stored as signatures; no audio.
 */
/**
 * Idea 4: holds back one unassigned chunk that follows an owner chunk until the next chunk is decided. If that next
 * chunk is the owner too and the held chunk sounds like both neighbours ([VoiceMatch.Thresholds.neighbor]), the held
 * chunk is the owner. Only signatures are held (memory only, at most one chunk, at most [MAX_GAP_MS] apart).
 */
class NeighborChunks {
    private var previousOwner: FloatArray? = null; private var previousAt = 0L
    private var held: FloatArray? = null; private var heldBefore: FloatArray? = null; private var heldAt = 0L
    val holding: Boolean get() = held != null

    /**
     * Call once per chunk with its decision and whole-speech signature (null when unavailable). Returns the decision
     * for the chunk held before this one (true = owner), or null when nothing was held. [hold] tells whether this
     * chunk is now held back.
     */
    fun next(speaker: Speaker, vector: FloatArray?, nowMs: Long, bar: Float?): Pair<Boolean?, Boolean> {
        var decided: Boolean? = null
        if (held != null) {
            val h = held!!; val b = heldBefore!!
            decided = bar != null && speaker == Speaker.USER && vector != null && vector.size == h.size && nowMs - heldAt in 0..MAX_GAP_MS &&
                VoiceMatch.similarity(h, b) >= bar && VoiceMatch.similarity(h, vector) >= bar
            h.fill(0f); b.fill(0f); held = null; heldBefore = null
        }
        var hold = false
        when {
            vector == null || bar == null || speaker == Speaker.OTHER -> clearOwner()
            speaker == Speaker.USER -> { clearOwner(); previousOwner = VoiceMatch.normalized(vector); previousAt = nowMs }
            previousOwner != null && nowMs - previousAt in 0..MAX_GAP_MS && decided != true -> {
                held = VoiceMatch.normalized(vector); heldBefore = previousOwner; heldAt = nowMs; previousOwner = null; hold = true
            }
            else -> clearOwner()
        }
        return decided to hold
    }
    private fun clearOwner() { previousOwner?.fill(0f); previousOwner = null }
    fun clear() { clearOwner(); held?.fill(0f); heldBefore?.fill(0f); held = null; heldBefore = null }
    companion object { const val MAX_GAP_MS = 20_000L }
}

class StrangerCohort(vectors: List<FloatArray>, private val top: Int = 50) {
    private val cohort: List<FloatArray> = vectors.map(VoiceMatch::normalized)
    val dimension: Int = cohort.firstOrNull()?.size ?: 0
    val size: Int get() = cohort.size
    init { require(cohort.size >= top && cohort.all { it.size == dimension }) { "Stranger cohort is too small or mixed." } }

    data class Stats(val mean: Float, val deviation: Float)

    fun stats(vector: FloatArray): Stats {
        require(vector.size == dimension)
        val scores = cohort.map { VoiceMatch.similarity(it, vector) }.sortedDescending().take(top)
        val mean = scores.average()
        val deviation = sqrt(scores.sumOf { (it - mean) * (it - mean) } / scores.size)
        return Stats(mean.toFloat(), maxOf(deviation.toFloat(), 1e-3f))
    }

    /** Symmetric AS-norm score: how many cohort deviations the raw score sits above both sides' strangers. */
    fun normalize(raw: Float, profile: Stats, test: Stats): Float =
        .5f * ((raw - profile.mean) / profile.deviation + (raw - test.mean) / test.deviation)

    companion object {
        /** Little-endian: int count, int dimension, then count × dimension floats. */
        fun read(bytes: ByteArray): StrangerCohort {
            val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val count = buffer.int; val dimension = buffer.int
            require(count in 50..2000 && dimension in setOf(192, 256) && bytes.size == 8 + count * dimension * 4)
            return StrangerCohort(List(count) { FloatArray(dimension) { buffer.float } })
        }
    }
}
