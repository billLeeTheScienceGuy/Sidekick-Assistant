package com.sidekick.core

/** Opt-in retention for old, dated memories; never silently discard pending reminders. */
object MemoryRetention {
    val choices = listOf(0, 30, 90, 365)
    fun expired(days: Int, now: Long, state: String, capturedAt: Long, start: Long?, end: Long?, due: Long?, delivered: Long?): Boolean {
        require(days in choices)
        if (days == 0 || state !in setOf("ACCEPTED", "DONE", "CANCELLED", "DISMISSED")) return false
        if (state == "ACCEPTED" && (start == null || due != null && delivered == null)) return false
        val latest = listOfNotNull(capturedAt, start, end, due, delivered).maxOrNull()!!
        val cutoff = java.time.Instant.ofEpochMilli(now).minus(java.time.Duration.ofDays(days.toLong())).toEpochMilli()
        return latest < cutoff
    }
}
