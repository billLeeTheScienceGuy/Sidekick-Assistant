package com.sidekick.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.data.*
import com.sidekick.app.reminders.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class MemoryPrivacyTest {
    @Test fun exportIsValidUnicodeAndOmitsPrivateSourcesUnlessExplicitlyRequested() {
        val item = CommitmentEntity("export-test", "Léa\n\"Birthday\" 생일", "Private saved excerpt", 0, "UTC", "Internal note",
            state = "ACCEPTED", informational = true, placeName = "The James", sourceKey = "sender-private-identifier")
        val stream = ByteArrayOutputStream()
        MemoryExport.write(stream, listOf(item), false, 0)
        val text = stream.toString("UTF-8"); val json = JSONObject(text)
        assertEquals(1, json.getInt("version"))
        val event = json.getJSONArray("events").getJSONObject(0)
        assertEquals(item.title, event.getString("title"))
        assertEquals("1970-01-01T00:00:00Z", event.getString("capturedAt"))
        assertTrue(event.isNull("startAt")); assertTrue(event.getBoolean("informational"))
        assertFalse(event.has("savedExcerpt")); assertFalse(text.contains(item.sourceKey!!))
        assertFalse(text.contains(item.evidence)); assertFalse(text.contains(item.reviewNote))
        val explicit = ByteArrayOutputStream()
        MemoryExport.write(explicit, listOf(item), true, 0)
        assertEquals(item.evidence, JSONObject(explicit.toString("UTF-8")).getJSONArray("events").getJSONObject(0).getString("savedExcerpt"))
        val empty = ByteArrayOutputStream(); MemoryExport.write(empty, emptyList(), false, 0)
        assertEquals(0, JSONObject(empty.toString("UTF-8")).getJSONArray("events").length())
    }

    @Test fun cleanupRemovesOnlyEligibleRowsAndTheirUndoEvidence() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".compacttest") || context.packageName.endsWith(".benchmark"))
        val db = Room.inMemoryDatabaseBuilder(context, MemoryDatabase::class.java).build()
        val cancelled = mutableListOf<String>()
        val alarms = object : ReminderAlarms {
            override fun arm(item: CommitmentEntity) { }
            override fun cancel(item: CommitmentEntity) { cancelled += item.id }
        }
        val day = 86_400_000L; val now = 500 * day
        val old = CommitmentEntity("old", "Old dated event", "Old source", 0, "UTC", "", "ACCEPTED", activityStartAt = day)
        val pending = old.copy(id = "pending", dueAt = day)
        val future = old.copy(id = "future", activityStartAt = now + day)
        val undated = old.copy(id = "undated", activityStartAt = null)
        val done = undated.copy(id = "done", state = "DONE")
        try {
            db.commitments().insertAll(listOf(old, pending, future, undated, done))
            db.commitments().putChange(AutomaticChange(old.id, "token", MemorySnapshot.encode(old), MemorySnapshot.encode(old), 0))
            val coordinator = ReminderCoordinator(context, db.commitments(), alarms)
            assertEquals(0, coordinator.prune(0, now))
            assertEquals(2, coordinator.prune(30, now))
            assertEquals(setOf("pending", "future", "undated"), db.commitments().all().map { it.id }.toSet())
            assertNull(db.commitments().change(old.id))
            assertEquals(setOf("old", "done"), cancelled.toSet())
            assertFalse(coordinator.undoAutomatic(old.id, "token"))
            assertEquals(0, coordinator.prune(30, now))
        } finally { db.close() }
    }
}
