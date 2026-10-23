package com.sidekick.core

import java.time.Instant
import java.time.ZoneId
import kotlin.test.*

class PlaceHintsTest {
    private fun activity(details: String) = ActivityTiming.extract(Transcript(
        "There's a party tomorrow at 8pm at The James. It's at 432 W Gorham Street. $details",
        Instant.parse("2026-10-22T17:00:00Z"), ZoneId.of("America/Chicago"), Speaker.USER, true)).single()

    @Test fun `a changed venue cannot inherit the previous address`() {
        val updated = activity("Actually it's at The Sylvee.")
        assertEquals("The Sylvee", updated.placeName)
        assertNull(updated.placeAddress)
    }
    @Test fun `the same venue retains its confirmed spoken address`() {
        val updated = activity("Actually it's at the James.")
        assertEquals("432 W Gorham Street", updated.placeAddress)
    }
    @Test fun `a later address belongs to the corrected venue`() {
        val updated = activity("Actually it's at The Sylvee. It's at 25 S Livingston Street.")
        assertEquals("The Sylvee", updated.placeName)
        assertEquals("25 S Livingston Street", updated.placeAddress)
    }
    @Test fun `clock times never become venue names`() {
        assertNull(PlaceHints.extract("The party is at 8pm.").name)
        assertEquals("The James", PlaceHints.extract("The party is at 8pm at The James.").name)
        assertEquals("The James", PlaceHints.extract("The party is at The James at 8pm.").name)
    }
    @Test fun `unknown venues keep their literal names without an invented address`() {
        val hint = PlaceHints.extract("Meet at The James tomorrow.")
        assertEquals("The James", hint.name)
        assertNull(hint.address)
        assertEquals("432 W Gorham Street", PlaceHints.extract("Its address is 432 W Gorham Street.").address)
    }
    @Test fun `later same venue address completes an activity`() {
        val input = Transcript("There's a party tomorrow at 8pm at The James. The James is at 432 W Gorham Street.",
            Instant.parse("2026-10-22T17:00:00Z"), ZoneId.of("America/Chicago"), Speaker.USER, true)
        val activity = ActivityTiming.extract(input).single()
        assertEquals("The James", activity.placeName)
        assertEquals("432 W Gorham Street", activity.placeAddress)
        assertTrue(activity.informational)
    }
    @Test fun `incoming speaker events require the informational adapter and never imply user ownership`() {
        val input = Transcript("I'll go to the party tomorrow at 8pm at The James.", Instant.parse("2026-10-22T17:00:00Z"),
            ZoneId.of("America/Chicago"), Speaker.OTHER, true)
        assertTrue(ActivityTiming.extract(input).isEmpty())
        assertTrue(ActivityTiming.extract(input, informationalOnly = true).single().informational)
        assertTrue(ActivityTiming.extract(input.copy(processingAllowed = false), informationalOnly = true).isEmpty())
    }
}
