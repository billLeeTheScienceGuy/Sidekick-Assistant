package com.sidekick.app

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Instances as I
import com.sidekick.core.CalendarOverlap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

data class CalendarClash(val title: String, val start: Long, val end: Long, val allDay: Boolean)
sealed interface CalendarCheck {
    data class Available(val clashes: List<CalendarClash>) : CalendarCheck
    data class Unavailable(val reason: String) : CalendarCheck
}

/** Reads occurrences only on explicit request. Results are never stored or logged. */
class PhoneCalendar(private val context: Context) {
    suspend fun check(start: Long, end: Long, zone: ZoneId): CalendarCheck = withContext(Dispatchers.IO) {
        CalendarOverlap.validate(start, end)
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED)
            return@withContext CalendarCheck.Unavailable("Calendar permission is off. No events were checked.")
        try { read(context.contentResolver, start, end, zone) }
        catch (_: SecurityException) { CalendarCheck.Unavailable("Calendar permission changed. Check permission and try again.") }
        catch (_: Exception) { CalendarCheck.Unavailable("Calendar could not be read. Try again; availability is unknown.") }
    }

    companion object {
        internal fun read(resolver: ContentResolver, start: Long, end: Long, zone: ZoneId): CalendarCheck {
            CalendarOverlap.validate(start, end)
            val uri = I.CONTENT_URI.buildUpon().apply {
                // Expand for UTC-encoded all-day dates, then compare using local date boundaries.
                ContentUris.appendId(this, start - 86_400_000L)
                ContentUris.appendId(this, end + 86_400_000L)
            }.build()
            val columns = arrayOf(I.TITLE, I.BEGIN, I.END, I.ALL_DAY)
            val selection = "${I.VISIBLE}=1 AND (${I.STATUS} IS NULL OR ${I.STATUS}!=?) AND (${I.SELF_ATTENDEE_STATUS} IS NULL OR ${I.SELF_ATTENDEE_STATUS}!=?) AND (${I.AVAILABILITY} IS NULL OR ${I.AVAILABILITY}!=?)"
            val args = arrayOf(I.STATUS_CANCELED.toString(), android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED.toString(), I.AVAILABILITY_FREE.toString())
            val clashes = mutableListOf<CalendarClash>()
            val cursor = resolver.query(uri, columns, selection, args, "${I.BEGIN} ASC")
                ?: return CalendarCheck.Unavailable("Calendar provider is unavailable. Availability is unknown.")
            cursor.use {
                while (it.moveToNext()) {
                    val allDay = it.getInt(3) != 0
                    val from = if (allDay) CalendarOverlap.allDayBoundary(it.getLong(1), zone) else it.getLong(1)
                    val until = if (allDay) CalendarOverlap.allDayBoundary(it.getLong(2), zone) else it.getLong(2)
                    if (CalendarOverlap.overlaps(start, end, from, until)) {
                        clashes += CalendarClash(it.getString(0)?.take(200)?.ifBlank { null } ?: "Untitled event", from, until, allDay)
                        if (clashes.size == 50) break
                    }
                }
            }
            return CalendarCheck.Available(clashes)
        }
    }
}
