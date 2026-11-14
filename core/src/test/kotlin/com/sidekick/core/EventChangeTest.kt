package com.sidekick.core

import java.time.*
import kotlin.test.*

class EventChangeTest {
    private val zone = ZoneId.of("America/Chicago")
    private val now = Instant.parse("2026-10-22T17:00:00Z")
    private val previous = Instant.parse("2026-10-23T01:00:00Z")
    @Test fun `change commands handle possessive days and spaced meridiem`() {
        val captured = Instant.parse("2026-11-13T17:00:00Z")
        val start = Instant.parse("2026-11-15T01:00:00Z")
        val eastern = ZoneId.of("America/New_York")
        for (phrase in listOf("Change tomorrow’s party to nine p m.", "Change tomorrow's party to 9 p m.", "Please change the party to nine p.m.")) {
            assertTrue(EventChange.looksLikeChange(phrase))
            val change = EventChange.parse(phrase)!!
            assertTrue(change.matches("The party starts tomorrow at 8pm at the James"))
            assertEquals(Instant.parse("2026-11-15T02:00:00Z"), change.newStart(start, captured, eastern), phrase)
        }
        assertNull(EventChange.parse("Change tomorrow's party to nine pm.")!!.newStart(start.plusSeconds(86400), captured, eastern))
        for (phrase in listOf("Don't change the party to nine pm.", "Could you change the party to nine pm?", "She said change the party to nine pm."))
            assertNull(EventChange.parse(phrase))
    }
    @Test fun `unpunctuated correction keeps a separate venue clause`() {
        val phrase = "change tomorrow’s party to nine p m the party will be at memorial union instead of the james"
        val clauses = Sentences.split(phrase)
        assertEquals(1, Sentences.split("Don't " + phrase).size)
        assertEquals(1, Sentences.split("She said " + phrase).size)
        assertEquals(2, clauses.size)
        assertNotNull(EventChange.parse(clauses.first()))
        assertEquals("memorial union", PlaceHints.extract(clauses.last()).name)
        assertEquals("9pm", EnglishClocks.normalize("nine p m"))
        assertEquals("9pm", EnglishClocks.normalize("9 p m"))
        assertEquals("8am", EnglishClocks.normalize("eight a m"))
        assertEquals("8pm", EnglishClocks.normalize("eight in the evening"))
    }
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
    @Test fun `moves to day after tomorrow and calendar dates`() {
        assertEquals(Instant.parse("2026-10-25T01:00:00Z"), EventChange.parse("Move the party to the day after tomorrow.")!!.newStart(previous, now, zone))
        assertEquals(Instant.parse("2026-11-13T03:00:00Z"), EventChange.parse("Move it to November 12th at 9pm.")!!.newStart(previous, now, zone))
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
