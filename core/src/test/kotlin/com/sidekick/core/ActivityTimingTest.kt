package com.sidekick.core

import java.time.Instant
import java.time.ZoneId
import kotlin.test.*

class ActivityTimingTest {
    @Test fun `whisper dotted meridiems keep venue and later corrections grounded`() {
        val input = source("There's a party tomorrow at 8 p.m. at the James. It ends at 11 p.m. Actually, it ends at midnight. The address is 25 south Livingston Street.")
        val activity = ActivityTiming.extract(input).single()
        assertEquals(Instant.parse("2026-10-24T01:00:00Z"), activity.activityStartAt)
        assertEquals(Instant.parse("2026-10-24T05:00:00Z"), activity.activityEndAt)
        assertEquals("the James", activity.placeName)
        assertEquals("25 south Livingston Street", activity.placeAddress)
        assertTrue(activity.evidence.startsWith("There's a party tomorrow at 8 p.m. at the James."))
    }
    private fun source(text: String) = Transcript(text, Instant.parse("2026-10-22T17:00:00Z"), ZoneId.of("America/Chicago"), Speaker.USER, true)

    @Test fun `later midnight statement completes party without assuming attendance`() {
        val activity = ActivityTiming.extract(source("Yeah there's a party at 8pm. We can bring snacks. It will end around midnight.")).single()
        assertEquals(Instant.parse("2026-10-23T01:00:00Z"), activity.activityStartAt)
        assertEquals(Instant.parse("2026-10-23T05:00:00Z"), activity.activityEndAt)
        assertTrue(activity.needsReview.contains("Attendance is unconfirmed"))
        assertTrue(activity.needsReview.contains("Date inferred"))
        assertFalse(activity.evidence.contains("snacks"))
    }
    @Test fun `unknown end stays unknown and ambiguous clocks are not invented`() {
        assertNull(ActivityTiming.extract(source("There's a party tomorrow at 8pm.")).single().activityEndAt)
        assertTrue(ActivityTiming.extract(source("There's a party at eight.")).isEmpty())
        assertTrue(ActivityTiming.extract(source("There's a party at 8.")).isEmpty())
    }
    @Test fun `explicit dates and relative durations produce grounded suggestions`() {
        val activity = ActivityTiming.extract(source("The meeting is tomorrow at 2pm and lasts 1.5 hours.")).single()
        assertEquals(Instant.parse("2026-10-23T19:00:00Z"), activity.activityStartAt)
        assertEquals(Instant.parse("2026-10-23T20:30:00Z"), activity.activityEndAt)
    }
    @Test fun `another activity interrupts pronoun association`() {
        val activities = ActivityTiming.extract(source("There's a party at 8pm. There's a concert at 9pm. It ends at midnight."))
        assertEquals(2, activities.size)
        assertNull(activities[0].activityEndAt)
        assertNotNull(activities[1].activityEndAt)
        val interrupted = ActivityTiming.extract(source("There's a party at 8pm. My movie was great. It ends at midnight.")).single()
        assertNull(interrupted.activityEndAt)
    }
    @Test fun `negative conditional and other speaker data cannot create activities`() {
        assertTrue(ActivityTiming.extract(source("Maybe there's a party at 8pm.")).isEmpty())
        assertTrue(ActivityTiming.extract(source("There is not a party at 8pm.")).isEmpty())
        assertTrue(ActivityTiming.extract(source("The meeting was at 8pm.")).isEmpty())
        assertTrue(ActivityTiming.extract(source("There's a party at 8pm.").copy(speaker = Speaker.OTHER)).isEmpty())
        assertTrue(ActivityTiming.extract(source("There's a party at 8pm.").copy(processingAllowed = false)).isEmpty())
    }
    @Test fun `enrichment keeps the users plan instead of creating duplicate attendance facts`() {
        val input = source("I'll go to the party tomorrow at 8pm. It ends around midnight.")
        val merged = ActivityTiming.merge(input, RuleIntentExtractor().extract(input))
        assertEquals(1, merged.size)
        assertTrue(merged.single().title.startsWith("Go"))
        assertNotNull(merged.single().activityEndAt)
        assertFalse(merged.single().needsReview.contains("Attendance is unconfirmed"))
    }
    @Test fun `latest explicit ending can revise an earlier estimate`() {
        val activity = ActivityTiming.extract(source("There's a party at 8pm. It ends at 11pm. Actually it ends at midnight.")).single()
        assertEquals(Instant.parse("2026-10-23T05:00:00Z"), activity.activityEndAt)
    }
    @Test fun `DST ambiguity remains unknown rather than shifting silently`() {
        val input = source("The meeting is 2026-11-01 at 1:30am.")
        assertTrue(ActivityTiming.extract(input).isEmpty())
    }
}
