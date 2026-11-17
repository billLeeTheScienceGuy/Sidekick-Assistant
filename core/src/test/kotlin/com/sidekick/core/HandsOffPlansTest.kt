package com.sidekick.core

import java.time.*
import kotlin.test.*

class HandsOffPlansTest {
    private val zone = ZoneId.of("America/New_York")
    private val captured = Instant.parse("2026-11-16T15:00:00Z") // Monday 10:00 in New York
    private fun source(text: String, speaker: Speaker = Speaker.USER) = Transcript(text, captured, zone, speaker, true)
    private fun local(text: String) = AnalysisContext.local(source(text))
    private fun plans(text: String, speaker: Speaker = Speaker.USER): List<ProposedCommitment> {
        val input = source(text, speaker)
        val found = if (speaker == Speaker.USER) ActivityTiming.merge(input, RuleIntentExtractor().extract(input))
            else ActivityTiming.extract(input.copy(speaker = Speaker.OTHER), informationalOnly = true)
        return HandsOffPlans.complete(input.copy(speaker = if (speaker == Speaker.USER) Speaker.USER else Speaker.OTHER), found).map(HandsOffPlans::withDefaults)
    }
    private fun at(date: String, time: String = "00:00") = LocalDateTime.parse("${date}T$time").atZone(zone).toInstant()

    @Test fun `owner plan with a clear day and no time saves all day with visible assumption`() {
        val plan = plans("I have a dentist appointment on Friday.").single()
        assertFalse(plan.informational); assertNull(plan.conflict); assertTrue(plan.allDay)
        assertEquals(at("2026-11-20"), plan.activityStartAt); assertEquals(at("2026-11-21"), plan.activityEndAt)
        assertTrue(PlanNotes.ALL_DAY in plan.assumptions)
        assertEquals(at("2026-11-19", "09:00"), plan.reminderAt) // medical, all day: the day before
        assertTrue(PlanNotes.describe(plan.assumptions, plan.notes).startsWith("Assumed: all day (time assumed)"))
    }

    @Test fun `medical appointment gets assumed length and two reminders`() {
        val plan = plans("My dentist appointment is Friday at 3pm.").single()
        assertFalse(plan.informational)
        assertEquals(at("2026-11-20", "15:00"), plan.activityStartAt); assertEquals(at("2026-11-20", "16:00"), plan.activityEndAt)
        assertTrue("1 hour (length assumed)" in plan.assumptions)
        assertEquals(at("2026-11-19", "15:00"), plan.reminderAt)
        assertTrue(PlanNotes.FOLLOW_UP in plan.notes)
        val note = PlanNotes.describe(plan.assumptions, plan.notes)
        val (next, rest) = PlanNotes.followUp(note, plan.activityStartAt!!.toEpochMilli(), at("2026-11-19", "15:00").toEpochMilli())!!
        assertEquals(at("2026-11-20", "14:00").toEpochMilli(), next); assertFalse(rest.contains("Next reminder"))
        assertNull(PlanNotes.followUp(rest, plan.activityStartAt!!.toEpochMilli(), 0))
    }

    @Test fun `negations questions quotes reports and past plans are not saved`() {
        for (text in listOf("I don't have a dentist appointment on Friday.", "Is the dentist appointment on Friday?",
            "He said \"dentist on Friday\".", "I had a dentist appointment yesterday.", "The dentist appointment is not on Friday.",
            "Emma said the dentist appointment is on Friday.")) {
            assertTrue(plans(text).isEmpty(), text)
        }
    }

    @Test fun `hedges wait in Review and never enter the saved list`() {
        assertTrue(local("Maybe dinner with Alex on Friday at 7.").isEmpty())
        val review = plans("Maybe dinner with Alex on Friday at 7.").single()
        assertNotNull(review.conflict); assertNull(review.activityStartAt); assertNull(review.reminderAt)
        assertTrue(review.conflict!!.contains("maybe"))
        assertNotNull(plans("I might go to the dentist on Friday.").single().conflict)
        assertTrue(plans("Maybe Emma has a party on Friday.").isEmpty()) // someone else's hedge: nothing
        assertTrue(plans("Maybe dinner on Friday at 7.", Speaker.OTHER).isEmpty())
    }

    @Test fun `two different times are a conflict, not two saved plans`() {
        val alternatives = plans("I'll call Emma tomorrow at 5 or 6 pm.").single()
        assertNotNull(alternatives.conflict)
        val twice = plans("My dentist appointment is Friday at 3pm. My dentist appointment is Friday at 4pm.")
        assertEquals(2, twice.size)
        assertNull(twice.single { it.activityStartAt == at("2026-11-20", "15:00") }.conflict)
        assertNotNull(twice.single { it.activityStartAt == at("2026-11-20", "16:00") }.conflict)
        // Two generic meetings may be two meetings.
        assertTrue(plans("I have a meeting tomorrow at 2pm. I have a meeting tomorrow at 4pm.").all { it.conflict == null })
    }

    @Test fun `other speakers and third-party plans are information only`() {
        val other = plans("I have a dentist appointment on Friday.", Speaker.OTHER).single()
        assertTrue(other.informational); assertNull(other.reminderAt)
        val mom = plans("Mom lands at 5.").single()
        assertTrue(mom.informational); assertNull(mom.reminderAt)
        assertEquals(at("2026-11-16", "17:00"), mom.activityStartAt)
        assertTrue("5 pm (am/pm assumed)" in mom.assumptions); assertTrue("Mon 16 Nov (day assumed)" in mom.assumptions)
        assertTrue(plans("Emma's party is on Saturday.").single().informational)
        assertTrue(plans("There's a meeting tomorrow at 2pm.").single().informational)
        assertTrue(HandsOffPlans.complete(source("I have a dentist appointment on Friday.", Speaker.UNKNOWN), emptyList()).isEmpty())
    }

    @Test fun `owner wording makes an announced activity personal`() {
        val plan = plans("I have a meeting tomorrow at 2pm.").single()
        assertFalse(plan.informational)
        assertEquals(at("2026-11-17", "14:00"), plan.activityStartAt); assertEquals(at("2026-11-17", "15:00"), plan.activityEndAt)
        assertEquals(plan.activityStartAt, plan.reminderAt)
        assertTrue(plans("I have a meeting tomorrow at 2pm. I won't be there.").single().informational)
    }

    @Test fun `birthdays repeat note and day-before reminder`() {
        val plan = plans("Emma's birthday is on December 3rd.").single()
        assertFalse(plan.informational); assertTrue(plan.allDay)
        assertEquals(at("2026-12-03"), plan.activityStartAt)
        assertEquals(at("2026-12-02", "09:00"), plan.reminderAt)
        assertTrue(plan.notes.any { it.startsWith("Repeats yearly") })
        assertTrue(plans("Our anniversary is December 12th.").single().notes.any { it.startsWith("Repeats yearly") })
    }

    @Test fun `days off span several days without a reminder`() {
        val off = plans("I'm off Monday to Wednesday.").single()
        assertEquals(at("2026-11-23"), off.activityStartAt); assertEquals(at("2026-11-26"), off.activityEndAt)
        assertTrue(off.allDay); assertNull(off.reminderAt); assertFalse(off.informational)
        val vacation = plans("Vacation from December 20 to December 24.").single()
        assertEquals(at("2026-12-20"), vacation.activityStartAt); assertEquals(at("2026-12-25"), vacation.activityEndAt)
        val long = plans("Vacation from December 20 to January 5.").single()
        assertEquals(at("2026-12-21"), long.activityEndAt)
        assertTrue(long.notes.any { it.startsWith("Lasts until Tue 5 Jan") })
    }

    @Test fun `flights keep number, cities, arrival and a three hour reminder`() {
        val flight = plans("My flight UA 123 from Boston to Denver leaves Friday at 6:40am and lands at 10:15am.").single()
        assertEquals("Flight UA 123 from Boston to Denver", flight.title)
        assertEquals(at("2026-11-20", "06:40"), flight.activityStartAt); assertEquals(at("2026-11-20", "10:15"), flight.activityEndAt)
        assertEquals(at("2026-11-20", "03:40"), flight.reminderAt)
        assertTrue(flight.notes.any { it.contains("leave for the airport") })
        assertTrue(flight.assumptions.isEmpty())
    }

    @Test fun `trains have a one hour lead`() {
        val train = plans("I'm taking the train to Chicago tomorrow at 8am.").single()
        assertEquals(at("2026-11-17", "08:00"), train.activityStartAt); assertEquals(at("2026-11-17", "07:00"), train.reminderAt)
    }

    @Test fun `deadlines and bills`() {
        val report = plans("The report is due Friday.").single()
        assertTrue(report.allDay); assertEquals(at("2026-11-20", "09:00"), report.reminderAt)
        val rent = plans("Rent is due on the 1st.").single()
        assertEquals(at("2026-12-01"), rent.activityStartAt)
        assertTrue("Tue 1 Dec (month assumed from 'the 1st')" in rent.assumptions)
        assertEquals(at("2026-11-29", "09:00"), rent.reminderAt)
        assertTrue(rent.notes.any { it.startsWith("Repeats monthly") })
        val submit = plans("I need to submit the slides by Friday.").single()
        assertTrue(submit.allDay); assertEquals(at("2026-11-20", "09:00"), submit.reminderAt)
    }

    @Test fun `pickups, restaurants and service windows`() {
        val pickup = plans("Pick up the kids at 3:15 tomorrow.").single()
        assertEquals(at("2026-11-17", "15:15"), pickup.activityStartAt); assertEquals(at("2026-11-17", "15:00"), pickup.reminderAt)
        assertFalse(pickup.informational)
        val table = plans("We have a table for 4 at Luigi's on Friday at 7:30pm.").single()
        assertEquals("Table for 4 at Luigi's", table.title); assertEquals("Luigi's", table.placeName)
        assertEquals(at("2026-11-20", "19:30"), table.activityStartAt)
        val plumber = plans("The plumber is coming between 9 and 11 tomorrow.").single()
        assertEquals(at("2026-11-17", "09:00"), plumber.activityStartAt); assertEquals(at("2026-11-17", "11:00"), plumber.activityEndAt)
        assertTrue(plumber.assumptions.any { it.contains("am/pm assumed") })
        val delivery = plans("The delivery is between 1pm and 3pm on Thursday.").single()
        assertEquals(at("2026-11-19", "13:00"), delivery.activityStartAt); assertEquals(at("2026-11-19", "15:00"), delivery.activityEndAt)
        assertTrue(delivery.assumptions.isEmpty())
    }

    @Test fun `repeats save the first date and say so`() {
        val yoga = plans("Yoga every Tuesday at 6pm.").single()
        assertEquals(at("2026-11-17", "18:00"), yoga.activityStartAt)
        assertTrue(yoga.notes.any { it == "Repeats every Tuesday — only the first date is saved." })
        val club = plans("Book club meeting on the first Monday of the month.").single()
        assertEquals(at("2026-12-07"), club.activityStartAt)
        assertTrue(club.notes.any { it.startsWith("Repeats on the first Monday") })
    }

    @Test fun `timeless tasks with a clear day become all day, unclear days stay unscheduled`() {
        val groceries = plans("I'll buy groceries tomorrow.").single()
        assertTrue(groceries.allDay); assertEquals(at("2026-11-17", "09:00"), groceries.reminderAt)
        assertNull(plans("I'll attend the meeting next week at 7pm.").single().activityStartAt)
        assertNull(plans("I'll attend the workshop on 11/12 at 8pm.").single().activityStartAt)
    }

    @Test fun `next weekday guesses are visible and remind`() {
        val plan = plans("I'll attend the workshop next Friday at 8pm.").single()
        assertEquals(at("2026-11-27", "20:00"), plan.activityStartAt)
        assertTrue("Fri 27 Nov (assumed from 'next friday')" in plan.assumptions)
        assertEquals(plan.activityStartAt, plan.reminderAt)
    }

    @Test fun `multilingual all-day plans use the existing grammar`() {
        val german = plans("Ich habe morgen einen Termin.").single()
        assertTrue(german.allDay); assertFalse(german.informational); assertEquals(at("2026-11-17"), german.activityStartAt)
        val french = plans("J'ai rendez-vous demain.").single()
        assertTrue(french.allDay); assertFalse(french.informational)
        assertTrue(plans("Demain il y a une fête.").single().informational)
        assertTrue(plans("Il n'y a pas de fête demain.").isEmpty())
    }

    @Test fun `stated time overrides an assumption, same plan merges, clashes are flagged`() {
        val allDay = HandsOffPlans.PlanRef("I have a dentist appointment on Friday", "I have a dentist appointment on Friday",
            at("2026-11-20").toEpochMilli(), at("2026-11-21").toEpochMilli(), "Saved automatically. Assumed: all day (time assumed).", false)
        val timed = HandsOffPlans.PlanRef("The dentist is Friday at 3pm", "The dentist is Friday at 3pm",
            at("2026-11-20", "15:00").toEpochMilli(), at("2026-11-20", "16:00").toEpochMilli(), "Assumed: 1 hour (length assumed).", false)
        assertTrue(HandsOffPlans.refines(allDay, timed, zone))
        assertFalse(HandsOffPlans.refines(allDay, timed.copy(start = at("2026-11-21", "15:00").toEpochMilli()), zone))
        assertFalse(HandsOffPlans.refines(allDay, timed.copy(evidence = "Party on Friday at 3pm", title = "Party"), zone))
        assertFalse(HandsOffPlans.refines(allDay.copy(note = "Saved automatically."), timed, zone)) // manual edits are final
        val again = allDay.copy(evidence = "Dentist on Friday", title = "Dentist on Friday")
        assertTrue(HandsOffPlans.samePlan(allDay, again, zone))
        val alex = allDay.copy(evidence = "Alex's birthday is Friday"); val emma = allDay.copy(evidence = "Emma's birthday is Friday")
        assertFalse(HandsOffPlans.samePlan(alex, emma, zone))
        val meeting = HandsOffPlans.PlanRef("Team meeting", "I have a team meeting Friday at 3:30pm", at("2026-11-20", "15:30").toEpochMilli(), null, "", false)
        assertEquals(listOf(meeting), HandsOffPlans.clashes(timed, listOf(meeting, allDay), zone))
        assertTrue(HandsOffPlans.clashes(allDay, listOf(meeting), zone).isEmpty())
        assertTrue(HandsOffPlans.clashNote(listOf("Team meeting")).startsWith("May overlap with ‘Team meeting’"))
    }

    @Test fun `notes carry only hands-off sentences and stale review items are detected`() {
        val note = "Likely your voice; verify attribution. Assumed: all day (time assumed). Repeats yearly — only this year's date is saved. Reminder plan: the day before at 9:00."
        assertEquals("Assumed: all day (time assumed). Repeats yearly — only this year's date is saved. Reminder plan: the day before at 9:00.", PlanNotes.carry(note))
        assertTrue(HandsOffPlans.stale(1_000, 2_000, null, 3_000))
        assertFalse(HandsOffPlans.stale(1_000, 5_000, null, 3_000))
        assertTrue(HandsOffPlans.stale(null, null, 1_000, 3_000))
        assertFalse(HandsOffPlans.stale(null, null, null, 3_000))
    }
}
