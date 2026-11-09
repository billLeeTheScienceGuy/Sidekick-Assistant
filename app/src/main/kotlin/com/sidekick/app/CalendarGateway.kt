package com.sidekick.app

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars as C
import android.provider.CalendarContract.Events as E
import android.provider.CalendarContract.Attendees as A
import com.sidekick.core.CalendarMirrorFields

data class WritableCalendar(val id: Long, val name: String, val account: String, val owner: String)
data class CalendarRecord(val id: Long, val fields: CalendarMirrorFields, val raw: Map<String, String?> = emptyMap())

interface CalendarGateway {
    fun permitted(): Boolean
    fun calendars(): List<WritableCalendar>
    fun read(id: Long): CalendarRecord?
    fun find(calendarId: Long, marker: String): List<CalendarRecord>
    fun insert(fields: CalendarMirrorFields): Long
    fun replace(expected: CalendarRecord, fields: CalendarMirrorFields): Boolean
    fun remove(expected: CalendarRecord): Boolean
}

/** Normal app permissions only. Never creates accounts, attendees, invitations or calendar alarms. */
class AndroidCalendarGateway(private val context: Context) : CalendarGateway {
    private val resolver get() = context.contentResolver
    override fun permitted() = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        .all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    override fun calendars(): List<WritableCalendar> {
        check(permitted()) { "Calendar access is off" }
        val result = mutableListOf<WritableCalendar>()
        val columns = arrayOf(C._ID, C.CALENDAR_DISPLAY_NAME, C.ACCOUNT_NAME, C.OWNER_ACCOUNT)
        val cursor = resolver.query(C.CONTENT_URI, columns, "${C.CALENDAR_ACCESS_LEVEL}>=? AND ${C.VISIBLE}=1",
            arrayOf(C.CAL_ACCESS_CONTRIBUTOR.toString()), "${C.CALENDAR_DISPLAY_NAME} ASC") ?: error("Calendar provider unavailable")
        cursor.use { while (it.moveToNext()) result += WritableCalendar(it.getLong(0), it.getString(1).orEmpty(), it.getString(2).orEmpty(), it.getString(3).orEmpty()) }
        return result
    }
    private val columns = arrayOf(E.CALENDAR_ID, E.TITLE, E.DTSTART, E.DTEND, E.EVENT_TIMEZONE, E.EVENT_LOCATION,
        E.DESCRIPTION, E.CUSTOM_APP_URI, E.CUSTOM_APP_PACKAGE, E.AVAILABILITY, E.STATUS, E.ALL_DAY,
        E.RRULE, E.RDATE, E.EXRULE, E.EXDATE)
    private fun owner(calendarId: Long): String {
        val cursor = resolver.query(ContentUris.withAppendedId(C.CONTENT_URI, calendarId), arrayOf(C.OWNER_ACCOUNT), null, null, null)
            ?: error("Calendar provider unavailable")
        return cursor.use { if (it.moveToFirst()) it.getString(0).orEmpty() else "" }
    }
    private fun guestSelection(id: Long, owner: String): Pair<String, Array<String>> =
        if (owner.isBlank()) "${A.EVENT_ID}=?" to arrayOf(id.toString()) else
            "${A.EVENT_ID}=? AND (${A.ATTENDEE_EMAIL} IS NULL OR ${A.ATTENDEE_EMAIL}!=? COLLATE NOCASE)" to arrayOf(id.toString(), owner)
    private fun hasGuests(id: Long, calendarId: Long): Boolean {
        val (selection, args) = guestSelection(id, owner(calendarId))
        val cursor = resolver.query(A.CONTENT_URI, arrayOf(A._ID), selection, args, null) ?: error("Calendar attendees unavailable")
        return cursor.use { it.moveToFirst() }
    }
    override fun read(id: Long): CalendarRecord? {
        check(permitted()) { "Calendar access is off" }
        val cursor = resolver.query(ContentUris.withAppendedId(E.CONTENT_URI, id), columns, "${E.DELETED}=0", null, null)
            ?: error("Calendar provider unavailable")
        return cursor.use {
            if (!it.moveToFirst()) return@use null
            val raw = columns.indices.associate { i -> columns[i] to if (it.isNull(i)) null else it.getString(i) }
            fun value(name: String) = raw[name].orEmpty()
            val calendarId = value(E.CALENDAR_ID).toLong()
            CalendarRecord(id, CalendarMirrorFields(calendarId, value(E.TITLE), value(E.DTSTART).toLong(),
                value(E.DTEND).toLongOrNull() ?: value(E.DTSTART).toLong(), value(E.EVENT_TIMEZONE), value(E.EVENT_LOCATION),
                value(E.DESCRIPTION), value(E.CUSTOM_APP_URI), value(E.CUSTOM_APP_PACKAGE), value(E.AVAILABILITY).toIntOrNull() ?: 0,
                value(E.STATUS).toIntOrNull() ?: 0, value(E.ALL_DAY) == "1", value(E.RRULE), value(E.RDATE), value(E.EXRULE), value(E.EXDATE),
                hasGuests(id, calendarId)), raw)
        }
    }
    override fun find(calendarId: Long, marker: String): List<CalendarRecord> {
        val ids = mutableListOf<Long>()
        val cursor = resolver.query(E.CONTENT_URI, arrayOf(E._ID), "${E.CALENDAR_ID}=? AND ${E.CUSTOM_APP_URI}=? AND ${E.CUSTOM_APP_PACKAGE}=? AND ${E.DELETED}=0",
            arrayOf(calendarId.toString(), marker, context.packageName), null) ?: error("Calendar provider unavailable")
        cursor.use { while (it.moveToNext() && ids.size < 2) ids += it.getLong(0) }
        return ids.mapNotNull(::read)
    }
    private fun values(fields: CalendarMirrorFields, inserting: Boolean) = ContentValues().apply {
        if (inserting) put(E.CALENDAR_ID, fields.calendarId)
        put(E.TITLE, fields.title); put(E.DTSTART, fields.start); put(E.DTEND, fields.end)
        put(E.EVENT_TIMEZONE, fields.zone); put(E.EVENT_LOCATION, fields.location); put(E.DESCRIPTION, fields.description)
        put(E.CUSTOM_APP_URI, fields.marker); put(E.CUSTOM_APP_PACKAGE, fields.ownerPackage)
        put(E.AVAILABILITY, fields.availability); put(E.STATUS, fields.status); put(E.ALL_DAY, if (fields.allDay) 1 else 0)
        if (inserting) put(E.HAS_ALARM, 0)
    }
    override fun insert(fields: CalendarMirrorFields): Long = ContentUris.parseId(
        resolver.insert(E.CONTENT_URI, values(fields, true)) ?: error("Calendar insert did not return an event"))
    private fun guarded(expected: CalendarRecord, replacement: CalendarMirrorFields?): Boolean {
        if (expected.fields.hasGuests || expected.fields.ownerPackage != context.packageName) return false
        val args = mutableListOf(expected.id.toString())
        val comparison = buildList {
            add("${E._ID}=?"); add("${E.DELETED}=0")
            for (column in columns) {
                val value = expected.raw[column]
                if (value == null) add("$column IS NULL") else { add("$column=?"); args += value }
            }
        }.joinToString(" AND ")
        val (guests, guestArgs) = guestSelection(expected.id, owner(expected.fields.calendarId))
        val operations = arrayListOf(ContentProviderOperation.newAssertQuery(A.CONTENT_URI)
            .withSelection(guests, guestArgs).withExpectedCount(0).build())
        operations += if (replacement == null) ContentProviderOperation.newDelete(E.CONTENT_URI)
            .withSelection(comparison, args.toTypedArray()).withExpectedCount(1).build()
        else ContentProviderOperation.newUpdate(E.CONTENT_URI).withSelection(comparison, args.toTypedArray())
            .withValues(values(replacement, false)).withExpectedCount(1).build()
        return try { resolver.applyBatch(CalendarContract.AUTHORITY, operations); true }
        catch (_: OperationApplicationException) { false }
    }
    override fun replace(expected: CalendarRecord, fields: CalendarMirrorFields) = guarded(expected, fields)
    override fun remove(expected: CalendarRecord) = guarded(expected, null)
}
