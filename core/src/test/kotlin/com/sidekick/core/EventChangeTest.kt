package com.sidekick.core

import java.time.*
import kotlin.test.*

class EventChangeTest {
    private val zone = ZoneId.of("America/Chicago")
    private val now = Instant.parse("2026-10-22T17:00:00Z")
    private val previous = Instant.parse("2026-10-23T01:00:00Z")
    @Test fun `moves to explicit day and clock`() {
        val change = EventChange.parse("Actually, move the meeting to Friday at 9 p.m.")!!
        assertTrue(change.matches("Meeting with Alex tomorrow at 8pm"))
        assertFalse(change.matches("Party tomorrow at 8pm"))
        assertEquals(Instant.parse("2026-10-24T02:00:00Z"), change.newStart(previous, now, zone))
    }
    @Test fun `day only preserves local clock and clock only preserves date`() {
        assertEquals(Instant.parse("2026-10-24T01:00:00Z"), EventChange.parse("Move it to Friday.")!!.newStart(previous, now, zone))
        assertEquals(Instant.parse("2026-10-23T02:00:00Z"), EventChange.parse("Reschedule it to 9pm.")!!.newStart(previous, now, zone))
    }
    @Test fun `cancelled and canceled statements and direct commands`() {
        for (text in listOf("The party is cancelled.", "The party has been canceled.", "Cancel the party.")) {
            val change = EventChange.parse(text)!!
            assertTrue(change.cancel); assertTrue(change.matches("Party tomorrow at 8pm"))
        }
    }
    @Test fun `questions hypothetical negated quoted and reported changes are ignored`() {
        for (text in listOf("Cancel the party?", "Maybe cancel the party.", "Don't cancel it.", "The party is not cancelled.", "If the party is cancelled.", "She said cancel the party.", "\"Cancel the party.\"", "Can we move the meeting to 9pm?"))
            assertNull(EventChange.parse(text), text)
    }
    @Test fun `ambiguous ranges unexplained wording and past times are rejected`() {
        for (time in listOf("Friday at 9", "9pm or 10pm", "Friday or Saturday at 9pm", "9pm to 10pm", "yesterday at 9pm", "today at 9am", "next Friday at 9pm"))
            assertNull(EventChange.parse("Move it to $time.")!!.newStart(previous, now, zone), time)
    }
    @Test fun `ambiguous DST and nonexistent clocks are rejected`() {
        for (time in listOf("2026-11-01 at 1:30am", "2027-03-14 at 2:30am"))
            assertNull(EventChange.parse("Move it to $time.")!!.newStart(previous, now, zone))
    }
    @Test fun `cancelled events appear in history only`() {
        val item = PlannerEntry("event", "CANCELLED", null, 1)
        assertEquals(listOf(item), Planner.select(listOf(item), PlannerView.HISTORY))
        assertTrue(Planner.select(listOf(item), PlannerView.REMINDERS).isEmpty())
        assertTrue(Planner.select(listOf(item), PlannerView.UNSCHEDULED).isEmpty())
    }
    @Test fun `conversational commands and spoken hour words stay grounded`() {
        for (text in listOf("Let's move it to nine P M.", "Make it nine p.m.", "Okay, make that 9pm."))
            assertEquals(Instant.parse("2026-10-23T02:00:00Z"), EventChange.parse(text)!!.newStart(previous, now, zone), text)
        assertTrue(EventChange.parse("It's cancelled.")!!.cancel)
        assertTrue(EventChange.parse("Let's cancel it.")!!.cancel)
        assertNull(EventChange.parse("Let's not cancel it."))
        assertNull(EventChange.parse("Cancellate.")) // Never guess a destructive action from a misspelling.
        assertNull(EventChange.parse("Make it nine.")!!.newStart(previous, now, zone))
    }
}
