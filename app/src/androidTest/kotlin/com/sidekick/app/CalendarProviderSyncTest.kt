package com.sidekick.app

import android.Manifest
import android.content.*
import android.os.Build
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars as C
import android.provider.CalendarContract.Events as E
import android.provider.CalendarContract.Attendees as A
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class CalendarProviderSyncTest {
    @Test fun realLocalProviderCreatesUpdatesCancelsRecoversAndProtectsExternalGuests() = runBlocking<Unit> {
        val i = InstrumentationRegistry.getInstrumentation(); val root = i.targetContext
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(root.packageName.endsWith(".compacttest") || root.packageName.endsWith(".benchmark"))
        i.uiAutomation.grantRuntimePermission(root.packageName, Manifest.permission.READ_CALENDAR)
        i.uiAutomation.grantRuntimePermission(root.packageName, Manifest.permission.WRITE_CALENDAR)
        val name = "SideKick-synthetic-${java.util.UUID.randomUUID()}"
        fun calendarUri(id: Long? = null) = (if (id == null) C.CONTENT_URI else ContentUris.withAppendedId(C.CONTENT_URI, id)).buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(C.ACCOUNT_NAME, name).appendQueryParameter(C.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
        var calendarId: Long? = null
        val context = object : ContextWrapper(root) {
            override fun getSharedPreferences(key: String, mode: Int) = root.getSharedPreferences("$name-$key", mode)
        }
        val db = Room.inMemoryDatabaseBuilder(root, MemoryDatabase::class.java).build()
        val settings = GuidanceSettings(context); val gateway = AndroidCalendarGateway(context)
        val mirror = CalendarMirror(context, db, settings, gateway)
        try {
            val values = ContentValues().apply {
                put(C.ACCOUNT_NAME, name); put(C.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(C.NAME, name); put(C.CALENDAR_DISPLAY_NAME, name); put(C.OWNER_ACCOUNT, "owner@example.invalid")
                put(C.CALENDAR_COLOR, 0xff176b58.toInt()); put(C.CALENDAR_ACCESS_LEVEL, C.CAL_ACCESS_OWNER)
                put(C.VISIBLE, 1); put(C.SYNC_EVENTS, 1); put(C.CALENDAR_TIME_ZONE, "UTC")
            }
            calendarId = ContentUris.parseId(root.contentResolver.insert(calendarUri(), values) ?: error("Synthetic local calendar unavailable"))
            assertTrue(gateway.calendars().any { it.id == calendarId })
            settings.setCalendarSync(CalendarSyncConfig(true, calendarId, true))
            val now = System.currentTimeMillis()
            val source = CommitmentEntity("provider-$name", "Synthetic SideKick provider test", "NOT EXPORTED SOURCE", now, "UTC", "",
                state = "ACCEPTED", activityStartAt = now + 3_600_000, activityEndAt = now + 7_200_000, placeName = "Synthetic Hall")
            db.commitments().insertAll(listOf(source)); mirror.sync()
            val link = requireNotNull(db.calendarLinks().get(source.id))
            assertEquals("ACTIVE", link.state)
            val first = requireNotNull(gateway.read(requireNotNull(link.eventId)))
            assertEquals(source.title, first.fields.title); assertFalse(first.fields.description.contains(source.evidence))
            val withSelf = PhoneCalendar(context).check(source.activityStartAt!!, source.activityEndAt!!, java.time.ZoneId.of("UTC")) as CalendarCheck.Available
            assertTrue(withSelf.clashes.any { it.title == source.title })
            val withoutSelf = PhoneCalendar(context).check(source.activityStartAt!!, source.activityEndAt!!, java.time.ZoneId.of("UTC"), source.id) as CalendarCheck.Available
            assertFalse(withoutSelf.clashes.any { it.title == source.title })
            val moved = source.copy(title = "Synthetic moved test", activityStartAt = source.activityStartAt!! + 3_600_000,
                activityEndAt = source.activityEndAt!! + 3_600_000, informational = true)
            db.commitments().update(moved); mirror.sync()
            assertEquals("ACTIVE", db.calendarLinks().get(source.id)!!.state)
            val updated = gateway.read(first.id)!!
            assertEquals(moved.title, updated.fields.title); assertEquals(E.AVAILABILITY_FREE, updated.fields.availability)
            db.commitments().update(moved.copy(state = "CANCELLED")); mirror.sync()
            assertNull(gateway.read(first.id))
            db.commitments().update(moved); mirror.sync()
            val restoredId = db.calendarLinks().get(source.id)!!.eventId!!
            assertEquals(1, gateway.find(calendarId, gateway.read(restoredId)!!.fields.marker).size)
            root.contentResolver.insert(A.CONTENT_URI, ContentValues().apply {
                put(A.EVENT_ID, restoredId); put(A.ATTENDEE_NAME, "Synthetic guest"); put(A.ATTENDEE_EMAIL, "guest@example.invalid")
                put(A.ATTENDEE_RELATIONSHIP, A.RELATIONSHIP_ATTENDEE); put(A.ATTENDEE_TYPE, A.TYPE_REQUIRED); put(A.ATTENDEE_STATUS, A.ATTENDEE_STATUS_ACCEPTED)
            }) ?: error("Synthetic attendee insertion failed")
            db.commitments().update(moved.copy(state = "CANCELLED")); mirror.sync()
            assertEquals("CONFLICT", db.calendarLinks().get(source.id)!!.state)
            assertNotNull(gateway.read(restoredId))
        } finally {
            mirror.close(); db.close()
            calendarId?.let { root.contentResolver.delete(calendarUri(it), null, null) }
            root.deleteSharedPreferences("$name-guidance")
        }
    }
}
