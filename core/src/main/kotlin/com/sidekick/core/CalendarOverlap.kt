package com.sidekick.core

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

object CalendarOverlap {
    fun validate(start: Long, end: Long) {
        require(end > start) { "Activity end must be after its start." }
        require(end - start <= 7 * 86_400_000L) { "Check an activity of at most seven days." }
    }

    fun overlaps(start: Long, end: Long, otherStart: Long, otherEnd: Long): Boolean =
        start < end && otherStart < otherEnd && start < otherEnd && otherStart < end

    // Calendar Provider encodes all-day dates at UTC midnight, regardless of local timezone.
    fun allDayBoundary(value: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
}
