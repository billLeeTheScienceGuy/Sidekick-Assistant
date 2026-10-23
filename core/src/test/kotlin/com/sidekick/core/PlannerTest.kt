package com.sidekick.core

import kotlin.test.*

class PlannerTest {
    private val items = listOf(
        PlannerEntry("later", "ACCEPTED", 180_000, 10),
        PlannerEntry("done", "DONE", 60_000, 100),
        PlannerEntry("review", "PROPOSED", null, 20),
        PlannerEntry("earlier", "ACCEPTED", 60_000, 30),
        PlannerEntry("undated", "ACCEPTED", null, 40),
    )
    @Test fun `scheduled reminders include passed times and sort chronologically`() {
        assertEquals(listOf("earlier", "later"), Planner.select(items, PlannerView.REMINDERS).map { it.id })
        assertEquals(listOf("review", "earlier", "later", "undated", "done"), Planner.select(items, PlannerView.ALL).map { it.id })
    }
    @Test fun `review unscheduled and history do not mix saved states`() {
        assertEquals(listOf("review"), Planner.select(items, PlannerView.REVIEW).map { it.id })
        assertEquals(listOf("undated"), Planner.select(items, PlannerView.UNSCHEDULED).map { it.id })
        assertEquals(listOf("done"), Planner.select(items, PlannerView.HISTORY).map { it.id })
    }
    @Test fun `clashes only count other active reminders in the same instant minute`() {
        assertEquals(listOf("earlier"), Planner.sameReminderMinute(items, "new", 119_999))
        assertTrue(Planner.sameReminderMinute(items, "earlier", 60_000).isEmpty())
        assertTrue(Planner.sameReminderMinute(items, "new", 120_000).isEmpty())
    }
}
