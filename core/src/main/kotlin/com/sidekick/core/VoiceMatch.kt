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
        /** Session voice carry ([OwnerSession]); null turns it off. */
        val session: SessionCarry? = null,
        /**
         * Every speaker window must also score at least this many stranger-cohort deviations above strangers
         * ([StrangerCohort]); null skips the check (no cohort for the selected model).
         */
        val cohortZ: Float? = null,
        /**
         * The quiet-room setup profile may use a bar this much lower: in the evaluation, strangers came closer to
         * profiles recorded with background noise (shared room sound) than to quiet-room profiles.
         */
        val quietOffset: Float = 0f,
        /** Idea 2: profile moved toward this session's confirmed turns; null turns it off. */
        val adapt: SessionAdapt? = null,
        /**
         * Idea 4: an unassigned chunk between two owner chunks counts as the owner when its whole-speech signature
         * matches both neighbours at least this much; null turns it off.
         */
        val neighbor: Float? = null,
        /** Quiet-room profile bar chosen by the chunk's length and noise ([QualityBars]) instead of [quietOffset]. */
        val qualityBars: Boolean = false,
    ) {
        init { require(quietOffset in 0f..0.10f) }
        /** The rule for the quiet-room profile on a chunk of [quality] (quality-aware bars when enabled and known). */
        fun quiet(quality: SpeakerAudio.Quality?): Thresholds {
            val bar = if (qualityBars && quality != null) QualityBars.bar(quality) else return quiet()
            return copy(both = bar, balancedMin = bar - .05f, balancedMean = bar + .02f, quietOffset = 0f, qualityBars = false)
        }
        /** The rule for the quiet-room profile. */
        fun quiet(): Thresholds = if (quietOffset == 0f) this
            else copy(both = both - quietOffset, balancedMin = balancedMin - quietOffset, balancedMean = balancedMean - quietOffset, quietOffset = 0f)
        companion object {
            val STANDARD = Thresholds(both = .70f, balancedMin = .65f, balancedMean = .72f, consistency = .65f)
            val BEFORE_NOTICE = Thresholds(both = .75f, balancedMin = .70f, balancedMean = .77f, consistency = .65f, shortPhrases = false)
        }
    }
    /** Session-adapted profile: (1 - weight) x quiet profile + weight x mean of confirmed turns; rule family bar [both]. */
    data class SessionAdapt(val weight: Float, val both: Float) {
        init { require(weight in 0f..0.5f && both in .5f..1f) }
        fun rule(base: Thresholds) = base.copy(both = both, balancedMin = both - .05f, balancedMean = both + .02f, quietOffset = 0f)
        fun profile(quiet: FloatArray, confirmedMean: FloatArray): FloatArray =
            normalized(FloatArray(quiet.size) { (1 - weight) * quiet[it] + weight * confirmedMean[it] })
    }
    /**
     * Quality-aware bars for the quiet-room profile (CAM++), from the laptop evaluation over 454 stranger voices: each
     * group's bar is .08 above the closest stranger in that group, never below .58; groups with too little evidence keep
     * .64. Rows: speech under 5 s, 5-8 s, 8 s or more. Columns: SNR under 15 dB, 15-25 dB, 25 dB or more.
     */
    object QualityBars {
        private val bars = arrayOf(floatArrayOf(.64f, .61f, .65f), floatArrayOf(.64f, .64f, .62f), floatArrayOf(.58f, .58f, .62f))
        fun bar(q: SpeakerAudio.Quality): Float {
            val row = if (q.seconds < 5f) 0 else if (q.seconds < 8f) 1 else 2
            val column = if (q.snrDb < 15f) 0 else if (q.snrDb < 25f) 1 else 2
            return bars[row][column]
        }
    }
    /** A chunk's whole-speech signature against this session's confirmed owner turns: every one, their mean, and the profile. */
    data class SessionCarry(val minimum: Float, val mean: Float, val profileFloor: Float) {
        init { require(listOf(minimum, mean, profileFloor).all { it.isFinite() && it in 0f..1f }) }
    }
    /** Each whole turn must match one profile; never combine windows from different enrollments. */
    fun identifyProfiles(profiles: List<FloatArray>, windows: List<FloatArray>, thresholds: Thresholds = Thresholds.STANDARD,
        cohort: StrangerCohort? = null, quiet: List<Boolean> = emptyList(), quality: SpeakerAudio.Quality? = null): Speaker {
        if (profiles.isEmpty()) return Speaker.UNKNOWN
        val results = profiles.mapIndexed { index, profile ->
            val rule = if (quiet.getOrElse(index) { false }) thresholds.quiet(quality) else thresholds
            identifyTurn(profile, windows, rule).let { if (it == Speaker.USER && !aboveStrangers(profile, windows, rule, cohort)) Speaker.UNKNOWN else it }
        }
        return when {
            results.any { it == Speaker.USER } -> Speaker.USER
            results.all { it == Speaker.OTHER } -> Speaker.OTHER
            else -> Speaker.UNKNOWN
        }
    }
    /**
     * Score normalization: every window must sit [Thresholds.cohortZ] stranger deviations above how strangers score.
     * Fails closed: a rule that asks for it without a matching cohort never accepts.
     */
    fun aboveStrangers(profile: FloatArray, windows: List<FloatArray>, thresholds: Thresholds, cohort: StrangerCohort?): Boolean {
        val z = thresholds.cohortZ ?: return true
        if (cohort == null || cohort.dimension != profile.size || windows.isEmpty()) return false
        val profileStats = cohort.stats(profile)
        return windows.all { window -> cohort.normalize(similarity(profile, window), profileStats, cohort.stats(window)) >= z }
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
