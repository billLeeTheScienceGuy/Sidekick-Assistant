package com.sidekick.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.data.MemoryDatabase
import com.sidekick.app.reminders.ReminderCoordinator
import com.sidekick.core.Speaker
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UncertainUpdateTest {
    private val now = Instant.parse("2026-10-23T17:00:00Z")
    private val zone = ZoneId.of("America/Chicago")
    private fun opening(memory: TemporaryMemory) {
        memory.appendAttributedLive("I'll go to the party tomorrow at 8pm.", now, zone, Speaker.USER)
    }
    @Test fun uncertainCorrectionIsSeparateAndDismissalDoesNotChangeOriginal() {
        val memory = TemporaryMemory(); opening(memory)
        val original = memory.drafts.value.single()
        memory.appendAttributedLive("It ends at midnight.", now.plusSeconds(15), zone, Speaker.UNKNOWN)
        val revision = memory.drafts.value.single { it.id != original.id }
        assertEquals(original.id, memory.updateTarget(revision.id))
        assertTrue(revision.reviewNote.contains("speaker uncertain"))
        assertNull(memory.drafts.value.single { it.id == original.id }.activityEndAt)
        assertNotNull(revision.activityEndAt)
        memory.dismiss(revision.id)
        memory.appendAttributedLive("It ends at midnight.", now.plusSeconds(20), zone, Speaker.UNKNOWN)
        assertEquals(1, memory.drafts.value.size)
        assertNull(memory.drafts.value.single().activityEndAt)
    }
    @Test fun approvedCorrectionWritesOnlyAfterExplicitApproval() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, MemoryDatabase::class.java).build()
        val memory = TemporaryMemory(); opening(memory)
        val original = memory.drafts.value.single()
        val reminders = ReminderCoordinator(context, db.commitments())
        try {
            reminders.save(original.id, original.title, null, zone.id, original, original.activityStartAt, null)
            memory.approve(original.id, db.commitments().get(original.id)!!)
            memory.appendAttributedLive("It ends at midnight.", now.plusSeconds(15), zone, Speaker.UNKNOWN)
            val revision = memory.drafts.value.single()
            assertNull(db.commitments().get(original.id)!!.activityEndAt)
            reminders.save(original.id, revision.title, null, zone.id, activityStartAt = revision.activityStartAt, activityEndAt = revision.activityEndAt)
            memory.approve(revision.id, db.commitments().get(original.id)!!)
            assertEquals(1, db.commitments().all().size)
            assertEquals(revision.activityEndAt, db.commitments().get(original.id)!!.activityEndAt)
        } finally { db.close() }
    }
    @Test fun draftReplacementApprovalRemovesTheOriginalDraftWithoutDuplicatingSavedItems() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, MemoryDatabase::class.java).build()
        val memory = TemporaryMemory(); opening(memory)
        val original = memory.drafts.value.single()
        try {
            memory.appendAttributedLive("It ends at midnight.", now.plusSeconds(15), zone, Speaker.UNKNOWN)
            val revision = memory.drafts.value.single { it.id != original.id }
            ReminderCoordinator(context, db.commitments()).save(original.id, revision.title, null, zone.id,
                revision.copy(id = original.id), revision.activityStartAt, revision.activityEndAt)
            memory.approve(revision.id, db.commitments().get(original.id)!!)
            assertTrue(memory.drafts.value.isEmpty())
            assertEquals(1, db.commitments().all().size)
        } finally { db.close() }
    }
    @Test fun staleAmbiguousAndInterruptedReferencesDoNotGetUpdates() {
        val expired = TemporaryMemory(); opening(expired)
        expired.appendAttributedLive("It ends at midnight.", now.plusSeconds(121), zone, Speaker.UNKNOWN)
        assertEquals(1, expired.drafts.value.size)
        val interrupted = TemporaryMemory(); opening(interrupted)
        interrupted.appendAttributedLive("Let's talk about the weather.", now.plusSeconds(10), zone, Speaker.UNKNOWN)
        interrupted.appendAttributedLive("It ends at midnight.", now.plusSeconds(15), zone, Speaker.UNKNOWN)
        assertEquals(1, interrupted.drafts.value.size)
        val other = TemporaryMemory(); opening(other)
        other.appendAttributedLive("It ends at midnight.", now.plusSeconds(15), zone, Speaker.OTHER)
        assertEquals(1, other.drafts.value.size)
    }
}
