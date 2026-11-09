package com.sidekick.core

import kotlin.test.*

class CalendarMirrorPolicyTest {
    @Test fun `calendar sync needs known activity bounds and does not infer duration or upload history`() {
        fun action(state: String? = "ACCEPTED", start: Long? = 2000, end: Long? = 3000,
            info: Boolean = false, include: Boolean = false, linked: Boolean = false, zone: String = "UTC") =
            CalendarMirrorPolicy.action(state, start, end, zone, info, include, linked, 1000)
        assertEquals(CalendarMirrorPolicy.Action.UPSERT, action())
        assertEquals(CalendarMirrorPolicy.Action.WAIT, action(end = null))
        assertEquals(CalendarMirrorPolicy.Action.REMOVE, action(end = null, linked = true))
        assertEquals(CalendarMirrorPolicy.Action.REMOVE, action(info = true))
        assertEquals(CalendarMirrorPolicy.Action.UPSERT, action(info = true, include = true))
        assertEquals(CalendarMirrorPolicy.Action.WAIT, action(start = 0, end = 1))
        assertEquals(CalendarMirrorPolicy.Action.UPSERT, action(start = 0, end = 1, linked = true))
        assertEquals(CalendarMirrorPolicy.Action.WAIT, action(zone = "not-a-zone"))
        assertEquals(CalendarMirrorPolicy.Action.WAIT, action(end = 1999))
        assertEquals(CalendarMirrorPolicy.Action.KEEP, action(state = "DONE", linked = true))
        assertEquals(CalendarMirrorPolicy.Action.REMOVE, action(state = "CANCELLED", linked = true))
        assertEquals(CalendarMirrorPolicy.Action.REMOVE, action(state = null, linked = true))
    }
    @Test fun `fingerprints protect managed edits guests and recurrence without storing plaintext journals`() {
        val event = CalendarMirrorFields(1, "Meeting", 2000, 3000, "UTC", "The James", "Saved from SideKick.", "sidekick://event/id", "com.sidekick.app", 0)
        assertEquals(event.fingerprint(), event.copy().fingerprint())
        assertEquals(event.fingerprint(), event.copy(zone = "Etc/UTC").fingerprint())
        for (edited in listOf(event.copy(title = "Changed"), event.copy(start = 2100), event.copy(end = 3100),
            event.copy(location = "Changed"), event.copy(rrule = "FREQ=DAILY"), event.copy(hasGuests = true),
            event.copy(ownerPackage = "other.app"), event.copy(availability = 1), event.copy(allDay = true)))
            assertNotEquals(event.fingerprint(), edited.fingerprint())
        assertEquals(64, event.fingerprint().length)
    }
}
