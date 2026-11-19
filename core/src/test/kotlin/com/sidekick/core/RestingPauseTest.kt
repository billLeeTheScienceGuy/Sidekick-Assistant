package com.sidekick.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestingPauseTest {
    private val min = 60_000L

    @Test fun pausesOnlyAfterThirtyQuietMinutesWhenTurnedOn() {
        val rest = RestingPause(0)
        assertFalse(rest.shouldPause(29 * min, enabled = true, callActive = false))
        assertTrue(rest.shouldPause(30 * min, enabled = true, callActive = false))
        assertFalse(rest.shouldPause(60 * min, enabled = false, callActive = false), "off by default in settings")
        assertFalse(rest.shouldPause(60 * min, enabled = true, callActive = true), "never during a call")
    }

    @Test fun anyActivityRestartsTheClockAndResumes() {
        val rest = RestingPause(0)
        rest.activity(20 * min) // speech, screen on or carrying
        assertFalse(rest.shouldPause(45 * min, enabled = true, callActive = false))
        assertTrue(rest.shouldPause(50 * min, enabled = true, callActive = false))
        rest.pause()
        assertTrue(rest.paused)
        assertFalse(rest.shouldPause(90 * min, enabled = true, callActive = false), "already paused")
        rest.resume(100 * min)
        assertFalse(rest.paused)
        assertFalse(rest.shouldPause(120 * min, enabled = true, callActive = false))
        assertTrue(rest.shouldPause(130 * min, enabled = true, callActive = false))
    }

    @Test fun silenceGateSitsBelowTheSoftestMeasuredSpeech() {
        assertEquals(-60.0, QuietGate.LEVEL_DBFS)
        assertTrue(QuietGate.silent(FloatArray(4096) { if (it % 2 == 0) 0.0008f else -0.0008f }))   // -62 dBFS
        assertFalse(QuietGate.silent(FloatArray(4096) { if (it % 2 == 0) 0.0016f else -0.0016f }))  // -56 dBFS: softest speech seen
    }
}
