package com.sidekick.core

import java.time.LocalDate
import kotlin.test.*

class EnglishDatesTest {
    private val thursday = LocalDate.of(2026, 10, 22)
    private fun date(text: String, today: LocalDate = thursday) = EnglishDates.date(text, today)

    @Test fun `day after tomorrow is never read as tomorrow`() {
        assertEquals(LocalDate.of(2026, 10, 24), date("dinner the day after tomorrow"))
        assertEquals(LocalDate.of(2026, 10, 24), date("Day after tomorrow works"))
        assertEquals(LocalDate.of(2026, 10, 23), date("dinner tomorrow"))
    }
    @Test fun `this and next weekdays`() {
        assertEquals(thursday, date("this Thursday"))
        assertEquals(LocalDate.of(2026, 10, 29), date("Thursday"))
        assertEquals(LocalDate.of(2026, 10, 29), date("next Thursday"))
        assertEquals(LocalDate.of(2026, 10, 30), date("next Friday"))
        assertEquals(LocalDate.of(2026, 10, 23), date("this Friday"))
        val sunday = LocalDate.of(2026, 10, 25)
        assertEquals(LocalDate.of(2026, 10, 30), date("next Friday", sunday))
        assertTrue(EnglishDates.hasNextWeekday("the meeting is next Friday"))
        assertFalse(EnglishDates.hasNextWeekday("the meeting is this Friday"))
    }
    @Test fun `calendar dates roll into next year only when already past`() {
        assertEquals(LocalDate.of(2026, 11, 12), date("on November 12th"))
        assertEquals(LocalDate.of(2026, 11, 12), date("on the 12th of November"))
        assertEquals(thursday, date("October 22"))
        assertEquals(LocalDate.of(2027, 3, 3), date("March 3rd"))
        assertEquals(LocalDate.of(2027, 12, 25), date("December 25, 2027"))
        assertFails { date("November 31st") }
    }
    @Test fun `relative day and week counts`() {
        assertEquals(LocalDate.of(2026, 10, 24), date("in two days"))
        assertEquals(LocalDate.of(2026, 10, 25), date("in 3 days"))
        assertEquals(LocalDate.of(2026, 10, 29), date("in a week"))
        assertEquals(LocalDate.of(2026, 11, 5), date("in two weeks"))
    }
    @Test fun `conflicting dates and clock-like month phrases stay unresolved`() {
        assertNull(date("today or tomorrow"))
        assertNull(date("call November 3 pm"))
        assertNull(date("in May"))
        assertNull(date("no date here"))
    }
}
