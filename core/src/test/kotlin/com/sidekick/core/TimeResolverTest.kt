package com.sidekick.core

import java.time.*
import kotlin.test.*

class TimeResolverTest {
    @Test fun `unsupported or ambiguous date qualifiers never become next occurrence reminders`() {
        for (text in listOf("I'll call tomorrow at 5 or 6 pm", "I'll attend on 11/12 at 8pm", "I'll attend next week at 7pm"))
            assertNull(TimeResolver.suggest(text, captured, zone), text)
        assertEquals(captured.plusSeconds(1800), TimeResolver.suggest("I'll call in thirty minutes", captured, zone))
    }
    @Test fun `alternative clocks offsets and compound durations stay unknown`() {
        for (text in listOf("I'll call tomorrow at 5pm or 6pm", "I'll call tomorrow at noon or 5pm",
            "I'll call tomorrow at 17:00 or 18:00", "I'll call in 30 minutes or in an hour",
            "I'll call in an hour and thirty minutes", "I'll call in 1 hour 30 minutes")) {
            assertNull(TimeResolver.suggest(text, captured, zone), text)
        }
        assertNotNull(TimeResolver.suggest("I'll call tomorrow at 5:30 pm", captured, zone))
    }
    @Test fun `relative reminders retain the full requested delay including seconds`() {
        val start = captured.plusSeconds(59)
        assertEquals(start.plusSeconds(60), TimeResolver.suggest("I'll call in one minute", start, zone))
        assertEquals(start.plusSeconds(1800), TimeResolver.suggest("I'll call in half an hour", start, zone))
    }
    @Test fun `midnight and relative durations across DST use the intended clock or elapsed time`() {
        assertEquals(Instant.parse("2026-10-22T05:00:00Z"), TimeResolver.suggest("I'll call tomorrow at midnight", captured, zone))
        val beforeGap = Instant.parse("2027-03-14T07:45:30Z")
        assertEquals(beforeGap.plusSeconds(1800), TimeResolver.suggest("I'll call in 30 minutes", beforeGap, zone))
        assertNull(TimeResolver.suggest("I'll call 2027-03-14 at 2:30am", beforeGap, zone))
    }
    private val zone = ZoneId.of("America/Chicago")
    private val captured = Instant.parse("2026-10-22T01:00:00Z")
    @Test fun `tomorrow uses capture timezone rather than UTC date`() {
        assertEquals(Instant.parse("2026-10-22T22:00:00Z"), TimeResolver.suggest("I'll call tomorrow at 5 pm", captured, zone))
    }
    @Test fun `ambiguous time and missing day stay unresolved`() {
        assertNull(TimeResolver.suggest("I'll call tomorrow at five", captured, zone))
        assertNull(TimeResolver.suggest("I'll call at 5 pm", captured, zone))
    }
    @Test fun `past dates and invalid dates are rejected`() {
        assertNull(TimeResolver.suggest("I'll call today at 9 am", captured, zone))
        assertNull(TimeResolver.suggest("I'll call 2026-02-30 at 17:00", captured, zone))
        assertFails { TimeResolver.parseEditor("2026-02-30 12:00", zone) }
    }
    @Test fun `DST gap and overlapping times require clarification`() {
        assertFails { TimeResolver.parseEditor("2026-03-08 02:30", zone) }
        assertFails { TimeResolver.parseEditor("2026-11-01 01:30", zone) }
    }
    @Test fun `explicit 24 hour times are supported`() {
        assertEquals(Instant.parse("2026-10-23T22:15:00Z"), TimeResolver.suggest("I'll call 2026-10-23 at 17:15", captured, zone))
    }
    @Test fun `day after tomorrow calendar dates and noon`() {
        assertEquals(Instant.parse("2026-10-23T22:00:00Z"), TimeResolver.suggest("I'll call the day after tomorrow at 5 pm", captured, zone))
        assertEquals(Instant.parse("2026-11-12T15:00:00Z"), TimeResolver.suggest("I'll call on November 12th at 9 am", captured, zone))
        assertEquals(Instant.parse("2026-10-22T17:00:00Z"), TimeResolver.suggest("I'll call tomorrow at noon", captured, zone))
    }
    @Test fun `minutes and hours from now`() {
        assertEquals(Instant.parse("2026-10-22T01:30:00Z"), TimeResolver.suggest("I'll call Mom in 30 minutes", captured, zone))
        assertEquals(Instant.parse("2026-10-22T01:30:00Z"), TimeResolver.suggest("I'll call Mom in half an hour", captured, zone))
        assertEquals(Instant.parse("2026-10-22T02:00:00Z"), TimeResolver.suggest("I'll call Mom in an hour", captured, zone))
        assertEquals(Instant.parse("2026-10-22T03:00:00Z"), TimeResolver.suggest("I'll call Mom in two hours", captured, zone))
    }
    @Test fun `relative offsets mixed with dates or clocks and next weekdays stay unresolved`() {
        assertNull(TimeResolver.suggest("I'll call in 30 minutes at 5 pm", captured, zone))
        assertNull(TimeResolver.suggest("I'll call tomorrow in an hour", captured, zone))
        assertNull(TimeResolver.suggest("I'll call next Friday at 5 pm", captured, zone))
    }
}
