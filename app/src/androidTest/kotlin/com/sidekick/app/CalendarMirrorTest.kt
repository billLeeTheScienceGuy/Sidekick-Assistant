package com.sidekick.app

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.data.*
import com.sidekick.core.CalendarMirrorFields
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CalendarMirrorTest {
    private class Gateway : CalendarGateway {
        var access = true; var writable = true
        var queries = 0; var inserts = 0; var updates = 0; var deletes = 0
        var failAfterInsert = false; var failAfterUpdate = false; var failAfterDelete = false
        var changeDuringReplace = false
        val rows = linkedMapOf<Long, CalendarRecord>()
        private var next = 0L
        override fun permitted() = access
        override fun calendars(): List<WritableCalendar> { queries++; return if (writable) listOf(WritableCalendar(1, "Synthetic", "Local", "")) else emptyList() }
        override fun read(id: Long): CalendarRecord? { queries++; return rows[id] }
        override fun find(calendarId: Long, marker: String): List<CalendarRecord> { queries++; return rows.values.filter { it.fields.calendarId == calendarId && it.fields.marker == marker } }
        override fun insert(fields: CalendarMirrorFields): Long {
            inserts++; val id = ++next; rows[id] = CalendarRecord(id, fields)
            if (failAfterInsert) { failAfterInsert = false; error("Simulated lost insert response") }
            return id
        }
        override fun replace(expected: CalendarRecord, fields: CalendarMirrorFields): Boolean {
            if (changeDuringReplace) { changeDuringReplace = false; rows[expected.id] = expected.copy(fields = expected.fields.copy(title = "External concurrent edit")) }
            if (rows[expected.id]?.fields?.fingerprint() != expected.fields.fingerprint()) return false
            updates++; rows[expected.id] = expected.copy(fields = fields)
            if (failAfterUpdate) { failAfterUpdate = false; error("Simulated lost update response") }
            return true
        }
        override fun remove(expected: CalendarRecord): Boolean {
            if (rows[expected.id]?.fields?.fingerprint() != expected.fields.fingerprint()) return false
            deletes++; rows.remove(expected.id)
            if (failAfterDelete) { failAfterDelete = false; error("Simulated lost delete response") }
            return true
        }
    }
    private suspend fun fixture(body: suspend (MemoryDatabase, GuidanceSettings, Gateway, CalendarMirror, CommitmentEntity) -> Unit) {
        val root = InstrumentationRegistry.getInstrumentation().targetContext
        check(root.packageName.endsWith(".compacttest") || root.packageName.endsWith(".benchmark"))
        val prefix = "mirror-${java.util.UUID.randomUUID()}"
        val context = object : ContextWrapper(root) {
            override fun getSharedPreferences(name: String, mode: Int) = root.getSharedPreferences("$prefix-$name", mode)
        }
        val db = Room.inMemoryDatabaseBuilder(root, MemoryDatabase::class.java).build()
        val settings = GuidanceSettings(context); val gateway = Gateway()
        val mirror = CalendarMirror(context, db, settings, gateway)
        val now = System.currentTimeMillis()
        val source = CommitmentEntity("test-mirror", "Synthetic party", "PRIVATE SOURCE MUST NOT LEAVE PHONE", now, "UTC", "Private note",
            state = "ACCEPTED", activityStartAt = now + 3_600_000, activityEndAt = now + 7_200_000, placeName = "Synthetic Hall", sourceKey = "private-sender-id")
        try { body(db, settings, gateway, mirror, source) }
        finally { mirror.close(); db.close(); root.deleteSharedPreferences("$prefix-guidance") }
    }
    @Test fun offByDefaultMakesNoCalendarQueriesAndNoPermissionCannotWrite() = runBlocking<Unit> { fixture { db, settings, gateway, mirror, source ->
        db.commitments().insertAll(listOf(source)); mirror.sync()
        assertEquals(0, gateway.queries); assertEquals(0, gateway.inserts)
        settings.setCalendarSync(CalendarSyncConfig(true, 1)); gateway.access = false; mirror.sync()
        assertEquals(0, gateway.queries); assertEquals(0, gateway.inserts)
        assertEquals(source, db.commitments().get(source.id))
    } }
    @Test fun createMoveInformationalCancelUndoAndDeleteConvergeWithoutDuplicatesOrPrivateExcerpts() = runBlocking<Unit> { fixture { db, settings, gateway, mirror, source ->
        settings.setCalendarSync(CalendarSyncConfig(true, 1, true)); db.commitments().insertAll(listOf(source)); mirror.sync(); mirror.sync()
        assertEquals(1, gateway.inserts); assertEquals(1, gateway.rows.size)
        val first = gateway.rows.values.single()
        assertFalse(first.fields.description.contains(source.evidence)); assertFalse(first.fields.toString().contains(source.sourceKey!!))
        assertFalse(db.calendarLinks().all().toString().contains(source.title))
        val moved = source.copy(activityStartAt = source.activityStartAt!! + 3_600_000, activityEndAt = source.activityEndAt!! + 3_600_000, informational = true)
        db.commitments().update(moved); mirror.sync()
        assertEquals(first.id, gateway.rows.values.single().id)
        assertEquals(moved.activityStartAt, gateway.rows.values.single().fields.start)
        assertEquals(1, gateway.rows.values.single().fields.availability)
        db.commitments().update(moved.copy(state = "CANCELLED")); mirror.sync(); assertTrue(gateway.rows.isEmpty())
        db.commitments().update(moved); mirror.sync(); assertEquals(1, gateway.rows.size)
        db.commitments().delete(source.id); mirror.sync()
        assertTrue(gateway.rows.isEmpty()); assertTrue(db.calendarLinks().all().isEmpty())
    } }
    @Test fun lostProviderResponsesRecoverFromPendingHashesAndMarkersExactlyOnce() = runBlocking<Unit> { fixture { db, settings, gateway, mirror, source ->
        settings.setCalendarSync(CalendarSyncConfig(true, 1)); db.commitments().insertAll(listOf(source))
        gateway.failAfterInsert = true; mirror.sync()
        assertEquals("INSERTING", db.calendarLinks().get(source.id)!!.state)
        mirror.sync(); assertEquals(1, gateway.inserts); assertEquals("ACTIVE", db.calendarLinks().get(source.id)!!.state)
        db.commitments().update(source.copy(title = "Moved title")); gateway.failAfterUpdate = true; mirror.sync()
        assertEquals("UPDATING", db.calendarLinks().get(source.id)!!.state)
        mirror.sync(); assertEquals(1, gateway.updates); assertEquals("ACTIVE", db.calendarLinks().get(source.id)!!.state)
        db.commitments().update(source.copy(state = "CANCELLED")); gateway.failAfterDelete = true; mirror.sync()
        assertEquals("DELETING", db.calendarLinks().get(source.id)!!.state)
        mirror.sync(); assertEquals(1, gateway.deletes); assertEquals("REMOVED", db.calendarLinks().get(source.id)!!.state)
    } }
    @Test fun externalChangesGuestsRecurrenceAndRacingEditsStayProtected() = runBlocking<Unit> { fixture { db, settings, gateway, mirror, source ->
        settings.setCalendarSync(CalendarSyncConfig(true, 1)); db.commitments().insertAll(listOf(source)); mirror.sync()
        gateway.changeDuringReplace = true
        db.commitments().update(source.copy(title = "Local update")); mirror.sync()
        assertEquals("External concurrent edit", gateway.rows.values.single().fields.title)
        assertEquals("CONFLICT", db.calendarLinks().get(source.id)!!.state)
        db.commitments().delete(source.id); mirror.sync()
        assertEquals(1, gateway.rows.size); assertTrue(db.calendarLinks().all().isEmpty())
        for (kind in listOf("guests", "recurrence")) {
            val item = source.copy(id = "protected-$kind")
            db.commitments().insertAll(listOf(item)); mirror.sync()
            val link = db.calendarLinks().get(item.id)!!; val record = gateway.rows[link.eventId]!!
            gateway.rows[record.id] = record.copy(fields = if (kind == "guests") record.fields.copy(hasGuests = true) else record.fields.copy(rrule = "FREQ=DAILY"))
            db.commitments().update(item.copy(state = "CANCELLED")); mirror.sync()
            assertTrue(gateway.rows.containsKey(record.id)); assertEquals("CONFLICT", db.calendarLinks().get(item.id)!!.state)
        }
    } }
    @Test fun externalDeletionDoesNotReappearAndUnavailableCalendarsNeverReceiveNewItems() = runBlocking<Unit> { fixture { db, settings, gateway, mirror, source ->
        settings.setCalendarSync(CalendarSyncConfig(true, 1)); db.commitments().insertAll(listOf(source)); mirror.sync()
        gateway.rows.clear(); mirror.sync()
        assertEquals(1, gateway.inserts); assertEquals("CONFLICT", db.calendarLinks().get(source.id)!!.state)
        gateway.writable = false
        db.commitments().insertAll(listOf(source.copy(id = "another"))); mirror.sync()
        assertEquals(1, gateway.inserts)
    } }
    @Test fun missingEndTimesWaitAndRevokedAccessDefersOnlyTheOwnedDeletion() = runBlocking<Unit> { fixture { db, settings, gateway, mirror, source ->
        settings.setCalendarSync(CalendarSyncConfig(true, 1)); db.commitments().insertAll(listOf(source.copy(activityEndAt = null)))
        mirror.sync(); assertEquals(0, gateway.inserts); assertEquals(1, mirror.status.value.waiting)
        db.commitments().update(source); mirror.sync(); assertEquals(1, gateway.inserts)
        gateway.access = false; db.commitments().delete(source.id); mirror.sync()
        assertEquals(1, gateway.rows.size)
        gateway.access = true; mirror.sync()
        assertTrue(gateway.rows.isEmpty()); assertTrue(db.calendarLinks().all().isEmpty())
    } }
}
