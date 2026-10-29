package com.sidekick.core

import kotlin.math.sqrt

/** Conservative prototype thresholds; identity is never consent or authentication. */
object VoiceMatch {
    fun identifyTurn(profile: FloatArray, windows: List<FloatArray>): Speaker {
        if (windows.size < 2) return Speaker.UNKNOWN
        val scores = windows.zipWithNext { first, second -> identify(profile, first, second) }
        return when {
            scores.all { it == Speaker.USER } -> Speaker.USER
            scores.all { it == Speaker.OTHER } -> Speaker.OTHER
            else -> Speaker.UNKNOWN
        }
    }
    /** Short phrases need their own strong evidence against both enrollment and a recent long phrase. */
    fun classifyShortScores(profile: Float, recentVoice: Float, durationMs: Long, anchorAgeMs: Long, minimum: Float = .80f): Speaker {
        require(minimum.isFinite() && minimum >= .60f && minimum <= .80f)
        if (durationMs !in 1500L..2999L || anchorAgeMs !in 0L..VoiceAnchors.MAX_AGE_MS) return Speaker.UNKNOWN
        if (!profile.isFinite() || !recentVoice.isFinite() || profile !in -1f..1f || recentVoice !in -1f..1f) return Speaker.UNKNOWN
        return if (profile >= minimum && recentVoice >= minimum) Speaker.USER else Speaker.UNKNOWN
    }
    fun normalized(vector: FloatArray): FloatArray {
        require(vector.size in setOf(192, 256) && vector.all { it.isFinite() })
        val norm = sqrt(vector.sumOf { it.toDouble() * it }).toFloat()
        require(norm > 0.0001f)
        return FloatArray(vector.size) { vector[it] / norm }
    }
    fun similarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Voice signatures belong to different model dimensions." }
        return normalized(a).zip(normalized(b)).sumOf { (x, y) -> (x * y).toDouble() }.toFloat()
    }
    fun identify(profile: FloatArray?, first: FloatArray, second: FloatArray): Speaker {
        if (profile == null) return Speaker.UNKNOWN
        val a = similarity(profile, first)
        val b = similarity(profile, second)
        return classifyScores(a, b, similarity(first, second))
    }
    /** A weaker half may pass only with a strong combined score and consistent voice. */
    fun classifyScores(a: Float, b: Float, consistency: Float): Speaker {
        if (listOf(a, b, consistency).any { !it.isFinite() || it !in -1f..1f }) return Speaker.UNKNOWN
        if (consistency < 0.6f) return Speaker.UNKNOWN
        val balancedMatch = minOf(a, b) >= 0.65f && (a + b) / 2 >= 0.72f && consistency >= 0.65f
        return when {
            (a >= 0.70f && b >= 0.70f) || balancedMatch -> Speaker.USER
            a <= 0.35f && b <= 0.35f -> Speaker.OTHER
            else -> Speaker.UNKNOWN
        }
    }
}
