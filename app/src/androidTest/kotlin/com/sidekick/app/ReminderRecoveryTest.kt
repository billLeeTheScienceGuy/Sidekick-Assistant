package com.sidekick.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.data.*
import com.sidekick.app.reminders.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ReminderRecoveryTest {
    private class Alarms : ReminderAlarms {
        val active = mutableMapOf<String, Long>()
        var failAt: Long? = null
        override fun arm(item: CommitmentEntity) {
            if (failAt == item.dueAt) { failAt = null; error("Injected alarm failure") }
            active[item.id] = item.dueAt!!
        }
        override fun cancel(item: CommitmentEntity) { active.remove(item.id) }
    }
    @Test fun failedUndoRestoresCancelledEventAndJournalAndCanBeRetried() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val db = Room.inMemoryDatabaseBuilder(context, MemoryDatabase::class.java).build()
        val alarms = Alarms(); val reminders = ReminderCoordinator(context, db.commitments(), alarms)
        val due = System.currentTimeMillis() + 86400000
        val proposal = CommitmentEntity("undo-failure", "Test event", "Synthetic", 0, "UTC", "Test")
        try {
            reminders.save(proposal.id, proposal.title, due, "UTC", proposal, due, automatic = true)
            val saved = db.commitments().get(proposal.id)!!
            reminders.changeEvent(saved, null, true)
            val cancelled = db.commitments().get(saved.id)!!; val change = db.commitments().change(saved.id)!!
            alarms.failAt = due
            try { reminders.undoAutomatic(saved.id, change.token); fail("Expected alarm failure") } catch (_: IllegalStateException) { }
            assertEquals(cancelled, db.commitments().get(saved.id))
            assertEquals(change, db.commitments().change(saved.id)); assertTrue(alarms.active.isEmpty())
            assertTrue(reminders.undoAutomatic(saved.id, change.token))
            assertEquals(saved, db.commitments().get(saved.id)); assertEquals(due, alarms.active[saved.id])
            assertFalse(reminders.undoAutomatic(saved.id, change.token))
        } finally { reminders.deleteAll(); db.close() }
    }
    @Test fun failedCreateAndRescheduleLeaveNoGhostAlarmOrLostJournal() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val db = Room.inMemoryDatabaseBuilder(context, MemoryDatabase::class.java).build()
        val alarms = Alarms(); val reminders = ReminderCoordinator(context, db.commitments(), alarms)
        val due = System.currentTimeMillis() + 86400000
        val proposal = CommitmentEntity("save-failure", "Test event", "Synthetic", 0, "UTC", "Test")
        try {
            alarms.failAt = due
            try { reminders.save(proposal.id, proposal.title, due, "UTC", proposal, due, automatic = true); fail() } catch (_: IllegalStateException) { }
            assertNull(db.commitments().get(proposal.id)); assertNull(db.commitments().change(proposal.id)); assertTrue(alarms.active.isEmpty())
            reminders.save(proposal.id, proposal.title, due, "UTC", proposal, due, automatic = true)
            val saved = db.commitments().get(proposal.id)!!; val journal = db.commitments().change(saved.id)
            alarms.failAt = due + 3600000
            try { reminders.changeEvent(saved, due + 3600000, false); fail() } catch (_: IllegalStateException) { }
            assertEquals(saved, db.commitments().get(saved.id)); assertEquals(journal, db.commitments().change(saved.id))
            assertEquals(due, alarms.active[saved.id])
        } finally { reminders.deleteAll(); db.close() }
    }
}
