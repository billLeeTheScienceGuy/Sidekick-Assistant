package com.sidekick.core

import java.time.*
import kotlin.test.*

class MemorySummaryTest {
    private val zone = ZoneId.of("America/New_York")
    private val day = LocalDate.of(2026, 11, 1)
    private fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).atZone(zone).toInstant()
    private fun fact(id: String, state: String = "ACCEPTED", info: Boolean = false, start: Instant? = at(day, 12), end: Instant? = null, due: Instant? = null) =
        SummaryFact(id, id, state, info, start, end, due)
    @Test fun `day boundaries use phone zone and include overnight overlaps exactly once`() {
        val items = listOf(fact("overnight", start = at(day.minusDays(1), 23), end = at(day, 2)),
            fact("ends-at-midnight", start = at(day.minusDays(1), 20), end = at(day, 0)),
            fact("next-day", start = at(day.plusDays(1), 0)), fact("today"))
        val digest = MemorySummary.daily(items + items.first(), day, zone)
        assertEquals(setOf("overnight", "today"), digest.plans.map { it.id }.toSet())
        assertEquals(2, digest.plans.size)
        assertEquals(25, Duration.between(at(day, 0), at(day.plusDays(1), 0)).toHours())
    }
    @Test fun `unknown dates history and information never imply personal plans or completion dates`() {
        val digest = MemorySummary.daily(listOf(fact("draft", "PROPOSED"), fact("hidden", "DISMISSED"),
            fact("info", info = true), fact("done", "DONE"), fact("cancelled", "CANCELLED"),
            fact("unknown", start = null), fact("undated-history", "DONE", start = null)), day, zone)
        assertTrue(digest.plans.isEmpty())
        assertEquals(listOf("info"), digest.information.map { it.id })
        assertEquals(setOf("done", "cancelled"), digest.history.map { it.id }.toSet())
        assertEquals(listOf("unknown"), digest.undated.map { it.id })
    }
    @Test fun `reminder today can refer to a future activity without changing its date`() {
        val item = fact("future", start = at(day.plusDays(2), 12), due = at(day, 10))
        assertEquals(item, MemorySummary.daily(listOf(item), day, zone).plans.single())
    }
    @Test fun `highlights are original bounded excerpts and disappear with temporary text`() {
        val text = "Nice weather. There's a party tomorrow at 8pm. It ends at midnight. Cancel the party."
        val lines = MemorySummary.conversation(text)
        assertFalse(lines.contains("Nice weather.")); assertTrue(lines.contains("Cancel the party."))
        assertTrue(lines.all { text.contains(it) }); assertEquals(2, MemorySummary.conversation(text, 2).size)
        assertTrue(MemorySummary.conversation("").isEmpty())
        assertEquals(1, MemorySummary.conversation("There's a party tomorrow. There's a party tomorrow.").size)
    }
}
