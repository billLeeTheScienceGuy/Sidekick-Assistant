package com.sidekick.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListeningRetryTest {
    private val min = 60_000L

    @Test fun recoverableErrorsRetryUpToFiveTimesAnHour() {
        val r = ListeningRetry()
        repeat(5) { assertTrue(r.allow("Microphone did not restart after the pause.", it * min)) }
        assertFalse(r.allow("Microphone stopped supplying audio.", 6 * min))
        assertTrue(r.allow("Microphone stopped supplying audio.", 61 * min), "an hour later, retries are available again")
    }

    @Test fun errorsThatNeedTheUserAreNotRetried() {
        val r = ListeningRetry()
        assertFalse(r.allow("Microphone access was removed. Listening stopped.", 0))
        assertFalse(r.allow("Microphone permission is required", 0))
        assertFalse(r.allow("Install the selected speech model pack in Get started, or choose English Fast.", 0))
        assertFalse(r.allow("Missing speech model. Import its model pack", 0))
    }
}
