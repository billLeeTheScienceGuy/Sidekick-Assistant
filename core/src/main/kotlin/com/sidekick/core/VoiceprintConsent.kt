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
    const val NOTICE_VERSION = 1
    const val RETENTION_MS = 365L * 24 * 60 * 60 * 1000
    const val NOTICE_SAVED = "A voice signature: numbers that describe your voice. No audio is saved."
    const val NOTICE_PURPOSE = "To recognize your own plans and reminders in conversations."
    const val NOTICE_WHERE = "Only on this phone. Never uploaded, shared or backed up."
    const val NOTICE_RETENTION = "Until you delete it, or automatically after 12 months unused."
    const val NOTICE_DELETE = "Settings → Delete my voice setup deletes it and this consent record."

    /** Delete the profile and record when unused for 12 months. [lastUsedMs] null means "never used since saving". */
    fun expired(savedMs: Long, lastUsedMs: Long?, nowMs: Long): Boolean = nowMs - maxOf(savedMs, lastUsedMs ?: 0L) > RETENTION_MS
    /** Agree is allowed only with a typed name and the box ticked; under 18 also needs the guardian box. */
    fun canAgree(name: String, ticked: Boolean, under18: Boolean, guardianTicked: Boolean): Boolean =
        name.isNotBlank() && ticked && (!under18 || guardianTicked)
}
