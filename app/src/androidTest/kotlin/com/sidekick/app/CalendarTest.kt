package com.sidekick.app

import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.CalendarContract.Instances as I
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.data.MemoryDatabase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class CalendarTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MemoryDatabase::class.java)
    @Test fun placesUpgradeKeepsExistingTimesAndMarksNoAttendanceAssumptions() {
        migration.createDatabase("places-upgrade", 3).apply {
            execSQL("INSERT INTO commitments (id,title,evidence,capturedAt,zoneId,reviewNote,state,activityStartAt,activityEndAt) VALUES ('kept','Party','Original words',123,'America/Chicago','Saved','ACCEPTED',456,789)")
            close()
        }
        migration.runMigrationsAndValidate("places-upgrade", 4, true, MemoryDatabase.MIGRATION_3_4).use { db ->
            db.query("SELECT activityStartAt,activityEndAt,informational,placeName,placeAddress,sourceKey FROM commitments WHERE id='kept'").use {
                assertTrue(it.moveToFirst()); assertEquals(456L, it.getLong(0)); assertEquals(789L, it.getLong(1))
                assertEquals(0, it.getInt(2)); assertTrue(it.isNull(3)); assertTrue(it.isNull(4)); assertTrue(it.isNull(5))
            }
        }
    }

    @Test fun upgradePreservesCommitmentsAndReminders() {
        migration.createDatabase("calendar-upgrade", 2).apply {
            execSQL("INSERT INTO commitments (id,title,evidence,capturedAt,zoneId,reviewNote,state,dueAt,deliveredAt) VALUES ('kept','Call Emma','Original words',123,'America/Chicago','Saved','ACCEPTED',456,789)")
            close()
        }
        migration.runMigrationsAndValidate("calendar-upgrade", 3, true, MemoryDatabase.MIGRATION_2_3).use { db ->
            db.query("SELECT evidence,dueAt,deliveredAt,activityStartAt,activityEndAt FROM commitments WHERE id='kept'").use {
                assertTrue(it.moveToFirst()); assertEquals("Original words", it.getString(0))
                assertEquals(456L, it.getLong(1)); assertEquals(789L, it.getLong(2))
                assertTrue(it.isNull(3)); assertTrue(it.isNull(4))
            }
        }
    }

    @Test fun syntheticOccurrencesFilterBusyStatusAndRespectBoundaries() {
        val provider = FixtureProvider()
        provider.attachInfo(InstrumentationRegistry.getInstrumentation().targetContext,
            ProviderInfo().apply { authority = "synthetic-calendar" })
        try {
            val zone = ZoneId.of("America/Chicago")
            val start = Instant.parse("2026-10-22T15:00:00Z").toEpochMilli()
            val end = start + 3_600_000
            provider.add("Recurring occurrence", start, end)
            provider.add("Touches start", start - 1000, start)
            provider.add("Touches end", end, end + 1000)
            provider.add("Free", start, end, availability = I.AVAILABILITY_FREE)
            provider.add("Cancelled", start, end, status = I.STATUS_CANCELED)
            provider.add("Declined", start, end, attendee = android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED)
            provider.add("Hidden calendar", start, end, visible = 0)
            provider.add("All day", Instant.parse("2026-10-22T00:00:00Z").toEpochMilli(),
                Instant.parse("2026-10-23T00:00:00Z").toEpochMilli(), allDay = 1)
            val checked = PhoneCalendar.read(ContentResolver.wrap(provider), start, end, zone) as CalendarCheck.Available
            assertEquals(setOf("Recurring occurrence", "All day"), checked.clashes.map { it.title }.toSet())
            assertTrue(provider.lastUri!!.toString().startsWith(I.CONTENT_URI.toString()))
            assertEquals((start - 86_400_000).toString(), provider.lastUri!!.pathSegments.takeLast(2)[0])
            provider.nullCursor = true
            assertTrue(PhoneCalendar.read(ContentResolver.wrap(provider), start, end, zone) is CalendarCheck.Unavailable)
        } finally { provider.db.close() }
    }

    private class FixtureProvider : ContentProvider() {
        val db = SQLiteDatabase.create(null)
        var lastUri: Uri? = null
        var nullCursor = false
        override fun onCreate(): Boolean {
            db.execSQL("CREATE TABLE instances (${I.TITLE} TEXT, ${I.BEGIN} INTEGER, ${I.END} INTEGER, ${I.ALL_DAY} INTEGER, ${I.VISIBLE} INTEGER, ${I.STATUS} INTEGER, ${I.SELF_ATTENDEE_STATUS} INTEGER, ${I.AVAILABILITY} INTEGER)")
            return true
        }
        fun add(title: String, start: Long, end: Long, allDay: Int = 0, visible: Int = 1,
            status: Int = I.STATUS_CONFIRMED, attendee: Int = android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED, availability: Int = I.AVAILABILITY_BUSY) {
            db.insertOrThrow("instances", null, ContentValues().apply {
                put(I.TITLE, title); put(I.BEGIN, start); put(I.END, end); put(I.ALL_DAY, allDay)
                put(I.VISIBLE, visible); put(I.STATUS, status); put(I.SELF_ATTENDEE_STATUS, attendee); put(I.AVAILABILITY, availability)
            })
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? {
            lastUri = uri
            return if (nullCursor) null else db.query("instances", projection, selection, args, null, null, sort)
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("No writes")
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = error("No writes")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = error("No writes")
    }
}
