package com.sidekick.core

/**
 * The owner's written consent for their own voice signature (Illinois BIPA written release, Texas CUBI, Washington
 * RCW 19.375 notice and consent); applied in every state. Stored next to the profile and deleted with it.
 */
data class VoiceprintConsent(
    val name: String,
    val atEpochMs: Long,
    val noticeVersion: Int,
    /** "checkbox" (typed name + ticked box) or "checkbox+signature" (also drew a signature). */
    val method: String,
    /** Under 18: a parent or guardian agreed. */
    val guardian: Boolean = false,
) {
    val valid: Boolean get() = name.isNotBlank() && atEpochMs > 0 && noticeVersion == VoiceprintPolicy.NOTICE_VERSION
}

object VoiceprintPolicy {
    /** Bump when the consent notice text changes; older records then ask again. */
    const val NOTICE_VERSION = 2
    const val RETENTION_MS = 365L * 24 * 60 * 60 * 1000
    const val NOTICE_SAVED = "A voice signature: numbers that describe your voice. No audio is saved. It slowly adapts to how " +
        "your voice sounds, using only moments when SideKick is very sure it is you, and never moves far from your setup."
    const val NOTICE_PURPOSE = "To recognize your own plans and reminders in conversations."
    const val NOTICE_WHERE = "Only on this phone. Never uploaded, shared or backed up."
    const val NOTICE_RETENTION = "Until you delete it, or automatically after 12 months unused."
    const val NOTICE_DELETE = "Settings → Delete my voice setup deletes it and this consent record."

    /**
     * Learning across sessions: after a session, the saved quiet-room signature moves [LEARN_RATE] toward the mean of that
     * session's very confident owner turns (at least [LEARN_MIN_TURNS], each matching at [LEARN_CONFIDENT] or more), and
     * only if the result stays at least [LEARN_KEEP] similar to the original setup. Evaluation: SPEAKER_TUNING.md.
     */
    const val LEARN_RATE = .15f
    const val LEARN_CONFIDENT = .78f
    const val LEARN_KEEP = .90f
    const val LEARN_MIN_TURNS = 2

    /** The learned signature, or null when nothing should change. */
    fun learned(current: FloatArray, setup: FloatArray, turns: List<FloatArray>): FloatArray? {
        if (turns.size < LEARN_MIN_TURNS || turns.any { it.size != current.size } || setup.size != current.size) return null
        val mean = VoiceMatch.normalized(FloatArray(current.size) { i -> turns.sumOf { it[i].toDouble() }.toFloat() })
        val next = VoiceMatch.normalized(FloatArray(current.size) { (1 - LEARN_RATE) * current[it] + LEARN_RATE * mean[it] })
        mean.fill(0f)
        return if (VoiceMatch.similarity(next, setup) >= LEARN_KEEP) next else { next.fill(0f); null }
    }

    /** Delete the profile and record when unused for 12 months. [lastUsedMs] null means "never used since saving". */
    fun expired(savedMs: Long, lastUsedMs: Long?, nowMs: Long): Boolean = nowMs - maxOf(savedMs, lastUsedMs ?: 0L) > RETENTION_MS
    /** Agree is allowed only with a typed name and the box ticked; under 18 also needs the guardian box. */
    fun canAgree(name: String, ticked: Boolean, under18: Boolean, guardianTicked: Boolean): Boolean =
        name.isNotBlank() && ticked && (!under18 || guardianTicked)
}
