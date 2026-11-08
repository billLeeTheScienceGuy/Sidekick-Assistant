package com.sidekick.app

import android.content.Intent
import android.provider.CalendarContract
import com.sidekick.app.data.CommitmentEntity
import org.junit.Assert.*
import org.junit.Test

class CalendarDraftTest {
    private val item = CommitmentEntity("calendar-draft-test", "Party", "Private source words", 0, "America/New_York", "Test",
        state = "ACCEPTED", activityStartAt = 1_900_000_000_000, informational = true, placeName = "The James", placeAddress = "123 Test Street")
    @Test fun draftContainsOnlyKnownFieldsAndPreservesUnknownEnd() {
        val intent = CalendarDraft.intent(item)
        assertEquals(Intent.ACTION_INSERT, intent.action); assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
        assertEquals(item.title, intent.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals(item.activityStartAt, intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
        assertFalse(intent.hasExtra(CalendarContract.EXTRA_EVENT_END_TIME))
        assertEquals(item.placeAddress, intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
        assertFalse(intent.hasExtra(CalendarContract.Events.DESCRIPTION)) // No transcript/evidence export.
        assertEquals("America/New_York", intent.getStringExtra(CalendarContract.Events.EVENT_TIMEZONE))
        val end = item.activityStartAt!! + 3_600_000
        assertEquals(end, CalendarDraft.intent(item.copy(activityEndAt = end)).getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0))
    }
    @Test fun cancelledDraftAndUndatedItemsCannotBeExportedAsActiveEvents() {
        for (bad in listOf(item.copy(state = "CANCELLED"), item.copy(state = "PROPOSED"), item.copy(activityStartAt = null), item.copy(activityEndAt = item.activityStartAt))) {
            try { CalendarDraft.intent(bad); fail("Invalid event exported") } catch (_: IllegalArgumentException) { }
        }
    }
}
