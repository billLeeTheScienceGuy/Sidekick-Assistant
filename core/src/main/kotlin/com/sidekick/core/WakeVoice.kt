package com.sidekick.core

import kotlin.math.sqrt

/**
 * Optional "SideKick voice check": the owner's way of saying SideKick's name, compared like for like (the same word),
 * which is far easier than matching free speech. It only ever upgrades an unassigned voice that starts a question with
 * "SideKick" to the owner (the answer is then spoken, not only shown); it never overrides "someone else" and never
 * affects conversation text. Setup: [SAMPLES] phrases that start with "SideKick"; only their averaged signature is kept.
 */
object WakeVoice {
    const val SAMPLES = 5
    /** Signature window: the speaker model needs 1.5 s; the name is padded with silence to that length. */
    const val WINDOW = 24_000
    /**
     * Strict on purpose. Not yet measured against strangers saying "SideKick" (no such recordings exist); free-speech
     * strangers never reach .80 against a short-phrase profile in the laptop tests. Revisit with real recordings.
     */
    const val BAR = .80f
    private const val FRAME = 320 // 20 ms

    /**
     * The name at the start of [samples]: from the first speech to the first pause of at least 120 ms between 0.35 s
     * and 1.3 s later (people pause after "SideKick,"); without a pause, the first 0.9 s. Returned padded to [WINDOW];
     * null when there is too little speech.
     */
    fun segment(samples: FloatArray): FloatArray? {
        val span = SpeakerAudio.speechSpan(samples) ?: return null
        val start = span.first
        val limit = minOf(samples.size, start + 21_000)
        if (limit - start < 4_800) return null
        val rms = (start until limit step FRAME).map { at ->
            val end = minOf(at + FRAME, limit)
            sqrt((at until end).sumOf { samples[it].toDouble() * samples[it] } / (end - at))
        }
        val peak = rms.maxOrNull() ?: return null
        if (!peak.isFinite() || peak < .005) return null
        val quiet = rms.map { it < peak * .15 }
        var end = start + 14_400
        var run = 0
        for (i in quiet.indices) {
            run = if (quiet[i]) run + 1 else 0
            val runStart = start + (i - run + 1) * FRAME
            if (run >= 6 && runStart - start >= 5_600) { end = runStart; break }
        }
        end = minOf(end, limit, start + WINDOW)
        // At least 0.3 s of actual voice in the cut, not just a click before silence.
        if (end - start < 4_800 || quiet.take((end - start) / FRAME).count { !it } < 15) return null
        return FloatArray(WINDOW).also { samples.copyInto(it, 0, start, end) }
    }

    /** The saved signature: [SAMPLES] consistent setup signatures averaged; null if they differ too much. */
    fun template(vectors: List<FloatArray>): FloatArray? {
        if (vectors.size < SAMPLES || vectors.any { it.size != vectors.first().size }) return null
        val unit = vectors.map(VoiceMatch::normalized)
        val consistent = unit.indices.all { i -> unit.indices.all { j -> i == j || VoiceMatch.similarity(unit[i], unit[j]) >= .60f } }
        return try {
            if (!consistent) null
            else VoiceMatch.normalized(FloatArray(unit.first().size) { d -> unit.sumOf { it[d].toDouble() }.toFloat() })
        } finally { unit.forEach { it.fill(0f) } }
    }

    /** Who asked: only an unassigned voice whose name matches strongly enough becomes the owner. */
    fun asker(base: Speaker, score: Float?): Speaker =
        if (base == Speaker.UNKNOWN && score != null && score.isFinite() && score >= BAR) Speaker.USER else base
}
