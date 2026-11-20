package com.sidekick.core

/**
 * After a listening error, SideKick restarts listening by itself (5 s later) instead of stopping, up to [perHour] times
 * in any hour. Errors that need the user (microphone permission removed, a speech file missing, voice setup) are not
 * retried: listening stops and says why.
 */
class ListeningRetry(private val perHour: Int = 5, val delayMs: Long = 5_000) {
    private val recent = ArrayDeque<Long>()

    fun needsUser(message: String?): Boolean {
        val m = message.orEmpty()
        return listOf("permission", "access was removed", "Install the selected speech", "Missing speech model", "Voice setup", "Voice settings changed").any { m.contains(it, ignoreCase = true) }
    }

    /** True if this error should be retried now (and records the attempt). */
    fun allow(message: String?, nowMs: Long): Boolean {
        if (needsUser(message)) return false
        while (recent.isNotEmpty() && nowMs - recent.first() > 3_600_000) recent.removeFirst()
        if (recent.size >= perHour) return false
        recent.addLast(nowMs)
        return true
    }
}
