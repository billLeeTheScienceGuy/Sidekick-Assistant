package com.sidekick.core

import java.time.Instant
import java.time.ZoneId
import kotlin.test.*

class CalendarOverlapTest {
    @Test fun `touching boundaries are free but partial and contained intervals clash`() {
        assertFalse(CalendarOverlap.overlaps(100, 200, 0, 100))
        assertFalse(CalendarOverlap.overlaps(100, 200, 200, 300))
        assertTrue(CalendarOverlap.overlaps(100, 200, 50, 101))
        assertTrue(CalendarOverlap.overlaps(100, 200, 110, 120))
        assertTrue(CalendarOverlap.overlaps(100, 200, 0, 300))
        assertFalse(CalendarOverlap.overlaps(100, 200, 150, 150))
    }
    @Test fun `all day dates follow local dates including DST`() {
        val zone = ZoneId.of("America/Chicago")
        val start = CalendarOverlap.allDayBoundary(Instant.parse("2026-03-08T00:00:00Z").toEpochMilli(), zone)
        val end = CalendarOverlap.allDayBoundary(Instant.parse("2026-03-09T00:00:00Z").toEpochMilli(), zone)
        assertEquals(Instant.parse("2026-03-08T06:00:00Z").toEpochMilli(), start)
        assertEquals(23 * 3_600_000L, end - start)
        assertTrue(CalendarOverlap.overlaps(start, end, start + 1, end - 1))
    }
    @Test fun `reject invalid or oversized activity windows`() {
        assertFailsWith<IllegalArgumentException> { CalendarOverlap.validate(100, 100) }
        assertFailsWith<IllegalArgumentException> { CalendarOverlap.validate(200, 100) }
        assertFailsWith<IllegalArgumentException> { CalendarOverlap.validate(0, 8 * 86_400_000L) }
        CalendarOverlap.validate(0, 7 * 86_400_000L)
    }
}
