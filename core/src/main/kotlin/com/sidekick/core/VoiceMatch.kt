package com.sidekick.core

import kotlin.math.sqrt

/** Conservative prototype thresholds; identity is never consent or authentication. */
object VoiceMatch {
    /**
     * Owner-match thresholds. [STANDARD] is the long-standing set; [BEFORE_NOTICE] applies in all-party places before
     * notice, when a false owner match would keep someone else's words. Final values come from docs/evaluation/SPEAKER_TUNING.md.
     */
    data class Thresholds(
        val both: Float, val balancedMin: Float, val balancedMean: Float, val consistency: Float,
        val other: Float = .35f, val shortPhrases: Boolean = true,
    ) {
        companion object {
            val STANDARD = Thresholds(both = .70f, balancedMin = .65f, balancedMean = .72f, consistency = .65f)
            val BEFORE_NOTICE = Thresholds(both = .75f, balancedMin = .70f, balancedMean = .77f, consistency = .65f, shortPhrases = false)
        }
    }
    /** Each whole turn must match one profile; never combine windows from different enrollments. */
    fun identifyProfiles(profiles: List<FloatArray>, windows: List<FloatArray>, thresholds: Thresholds = Thresholds.STANDARD): Speaker {
        if (profiles.isEmpty()) return Speaker.UNKNOWN
        val results = profiles.map { identifyTurn(it, windows, thresholds) }
        return when {
            results.any { it == Speaker.USER } -> Speaker.USER
            results.all { it == Speaker.OTHER } -> Speaker.OTHER
            else -> Speaker.UNKNOWN
        }
    }
    fun identifyTurn(profile: FloatArray, windows: List<FloatArray>, thresholds: Thresholds = Thresholds.STANDARD): Speaker {
        if (windows.size < 2) return Speaker.UNKNOWN
        val scores = windows.zipWithNext { first, second -> identify(profile, first, second, thresholds) }
        return when {
            scores.all { it == Speaker.USER } -> Speaker.USER
            scores.all { it == Speaker.OTHER } -> Speaker.OTHER
            else -> Speaker.UNKNOWN
        }
    }
    /** Short phrases need their own strong evidence against both enrollment and a recent long phrase. */
    fun classifyShortScores(profile: Float, recentVoice: Float, durationMs: Long, anchorAgeMs: Long, minimum: Float = .80f,
        thresholds: Thresholds = Thresholds.STANDARD): Speaker {
        require(minimum.isFinite() && minimum >= .60f && minimum <= .80f)
        if (!thresholds.shortPhrases) return Speaker.UNKNOWN
        if (durationMs !in 1500L..2999L || anchorAgeMs !in 0L..VoiceAnchors.MAX_AGE_MS) return Speaker.UNKNOWN
        if (!profile.isFinite() || !recentVoice.isFinite() || profile !in -1f..1f || recentVoice !in -1f..1f) return Speaker.UNKNOWN
        return if (profile >= minimum && recentVoice >= minimum) Speaker.USER else Speaker.UNKNOWN
    }
    /**
     * Task 10 (a): every voice is turned into a signature in memory only to compare it once, then discarded. The
     * signature is zeroed after use, also when the comparison throws; it is never stored, logged or exported.
     */
    inline fun <T> useOnce(vector: FloatArray, block: (FloatArray) -> T): T = try { block(vector) } finally { vector.fill(0f) }
    fun normalized(vector: FloatArray): FloatArray {
        require(vector.size in setOf(192, 256) && vector.all { it.isFinite() })
        val norm = sqrt(vector.sumOf { it.toDouble() * it }).toFloat()
        require(norm > 0.0001f)
        return FloatArray(vector.size) { vector[it] / norm }
    }
    fun similarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Voice signatures belong to different model dimensions." }
        require(a.size in setOf(192, 256))
        var dot = 0.0; var squareA = 0.0; var squareB = 0.0
        for (index in a.indices) {
            val x = a[index].toDouble(); val y = b[index].toDouble()
            require(x.isFinite() && y.isFinite())
            dot += x * y; squareA += x * x; squareB += y * y
        }
        require(squareA > 0.00000001 && squareB > 0.00000001)
        return (dot / sqrt(squareA * squareB)).toFloat().coerceIn(-1f, 1f)
    }
    fun identify(profile: FloatArray?, first: FloatArray, second: FloatArray, thresholds: Thresholds = Thresholds.STANDARD): Speaker {
        if (profile == null) return Speaker.UNKNOWN
        val a = similarity(profile, first)
        val b = similarity(profile, second)
        return classifyScores(a, b, similarity(first, second), thresholds)
    }
    /** A weaker half may pass only with a strong combined score and consistent voice. */
    fun classifyScores(a: Float, b: Float, consistency: Float, thresholds: Thresholds = Thresholds.STANDARD): Speaker {
        if (listOf(a, b, consistency).any { !it.isFinite() || it !in -1f..1f }) return Speaker.UNKNOWN
        if (consistency < 0.6f) return Speaker.UNKNOWN
        val t = thresholds
        val balancedMatch = minOf(a, b) >= t.balancedMin && (a + b) / 2 >= t.balancedMean && consistency >= t.consistency
        return when {
            (a >= t.both && b >= t.both) || balancedMatch -> Speaker.USER
            a <= t.other && b <= t.other -> Speaker.OTHER
            else -> Speaker.UNKNOWN
        }
    }
}
