package com.sidekick.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.data.*
import com.sidekick.app.reminders.ReminderCoordinator
import com.sidekick.core.*
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Hands-off saving: all-day plans, visible assumptions, overrides, cancellations and Review-only conflicts. */
class HandsOffSavingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("America/Chicago")
    private val day = LocalDate.now(zone).plusDays(3)
    private val phrase = day.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d", java.util.Locale.ENGLISH))

    @Test fun allDayPlanSavesWithAssumptionAndAStatedTimeReplacesIt() = runBlocking { fixture { _, memory, dao, _, auto ->
        val now = Instant.now()
        memory.appendAttributedLive("I have a dentist appointment on $phrase.", now, zone, Speaker.USER, false); auto.process()
        val allDay = dao.all().single()
        assertEquals("ACCEPTED", allDay.state); assertFalse(allDay.informational)
        assertEquals(day.atStartOfDay(zone).toInstant().toEpochMilli(), allDay.activityStartAt)
        assertTrue(allDay.reviewNote, allDay.reviewNote.contains("all day (time assumed)"))
        assertTrue(memory.drafts.value.isEmpty())
        // A later conversation states the time: same plan, assumption gone.
        val later = TemporaryMemory()
        later.appendAttributedLive("My dentist appointment is on $phrase at 3pm.", now.plusSeconds(600), zone, Speaker.USER, false)
        AutomaticMemory(context, GuidanceSettings(context), later, dao, ReminderCoordinator(context, dao)).process()
        val timed = dao.all().single()
        assertEquals(allDay.id, timed.id)
        assertEquals(day.atTime(15, 0).atZone(zone).toInstant().toEpochMilli(), timed.activityStartAt)
        assertFalse(timed.reviewNote, timed.reviewNote.contains("time assumed"))
        assertTrue(timed.reviewNote, timed.reviewNote.contains("1 hour (length assumed)"))
    } }

    @Test fun ownerCancelsByNameInALaterSessionOthersOnlyReachReview() = runBlocking { fixture { settings, memory, dao, reminders, auto ->
        val now = Instant.now()
        memory.appendAttributedLive("I have a dentist appointment on $phrase at 3pm.", now, zone, Speaker.USER, false); auto.process()
        val plan = dao.all().single()
        val other = TemporaryMemory(); val otherAuto = AutomaticMemory(context, settings, other, dao, reminders)
        other.appendAttributedLive("The dentist appointment is cancelled.", now.plusSeconds(600), zone, Speaker.OTHER, false); otherAuto.process()
        assertEquals("ACCEPTED", dao.get(plan.id)!!.state)
        assertTrue(other.drafts.value.any { it.sourceKey == "review:cancel:${plan.id}" })
        val bare = TemporaryMemory(); val bareAuto = AutomaticMemory(context, settings, bare, dao, reminders)
        bare.appendAttributedLive("That's cancelled.", now.plusSeconds(700), zone, Speaker.USER, false); bareAuto.process()
        assertEquals("ACCEPTED", dao.get(plan.id)!!.state)
        val owner = TemporaryMemory(); val ownerAuto = AutomaticMemory(context, settings, owner, dao, reminders)
        owner.appendAttributedLive("The dentist appointment is cancelled.", now.plusSeconds(800), zone, Speaker.USER, false); ownerAuto.process()
        val cancelled = dao.get(plan.id)!!
        assertEquals("CANCELLED", cancelled.state); assertNull(cancelled.dueAt)
        assertTrue(ownerAuto.undo(plan.id, dao.change(plan.id)!!.token)); assertEquals("ACCEPTED", dao.get(plan.id)!!.state)
    } }

    @Test fun hedgesWaitInReviewAndOthersPlansAreInformationOnly() = runBlocking { fixture { _, memory, dao, _, auto ->
        val now = Instant.now()
        memory.appendAttributedLive("Maybe dinner with Alex on $phrase at 7pm.", now, zone, Speaker.USER, false); auto.process()
        assertTrue(dao.all().isEmpty())
        assertTrue(memory.drafts.value.single().sourceKey!!.startsWith("review:conflict:"))
        memory.appendAttributedLive("Mom lands on $phrase at 5pm.", now.plusSeconds(10), zone, Speaker.USER, false); auto.process()
        val mom = dao.all().single()
        assertTrue(mom.informational); assertNull(mom.dueAt)
    } }

    @Test fun clashesAreSavedAndFlagged() = runBlocking { fixture { _, memory, dao, _, auto ->
        val now = Instant.now()
        memory.appendAttributedLive("I have a meeting on $phrase at 3pm.", now, zone, Speaker.USER, false); auto.process()
        val next = TemporaryMemory()
        next.appendAttributedLive("My dentist appointment is on $phrase at 3:30pm.", now.plusSeconds(600), zone, Speaker.USER, false)
        AutomaticMemory(context, GuidanceSettings(context), next, dao, ReminderCoordinator(context, dao)).process()
        assertEquals(2, dao.all().size)
        val dentist = dao.all().single { it.title.contains("dentist", true) }
        assertTrue(dentist.reviewNote, dentist.reviewNote.contains("May overlap with"))
    } }

    @Test fun staleReviewItemsClearThemselves() {
        val memory = TemporaryMemory()
        val past = Instant.now().minus(Duration.ofDays(2))
        memory.add(CommitmentEntity("stale", "Old", "Old plan", past.toEpochMilli(), zone.id, "Test",
            activityStartAt = past.toEpochMilli(), activityEndAt = past.plusSeconds(3600).toEpochMilli(), sourceKey = "review:conflict:typed"))
        assertEquals(1, memory.pruneStale(System.currentTimeMillis()))
        assertTrue(memory.drafts.value.isEmpty())
    }

    private suspend fun fixture(block: suspend (GuidanceSettings, TemporaryMemory, CommitmentDao, ReminderCoordinator, AutomaticMemory) -> Unit) {
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val db = Room.inMemoryDatabaseBuilder(context, MemoryDatabase::class.java).build()
        val settings = GuidanceSettings(context); val previous = settings.handsOffMode.value
        val memory = TemporaryMemory(); val reminders = ReminderCoordinator(context, db.commitments())
        try { settings.setHandsOff(true); block(settings, memory, db.commitments(), reminders, AutomaticMemory(context, settings, memory, db.commitments(), reminders)) }
        finally { db.commitments().all().forEach { reminders.delete(it.id) }; settings.setHandsOff(previous); db.close() }
    }
}
