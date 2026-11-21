package com.sidekick.core

import com.sidekick.core.MicHandback.State.*
import kotlin.test.Test
import kotlin.test.assertEquals

class MicHandbackTest {
    private val min = 60_000L

    @Test fun waitsWhileAnotherAppHasTheMicrophoneAndCarriesOn() {
        val mic = MicHandback()
        assertEquals(LISTENING, mic.update(false, 0))
        assertEquals(TAKEN, mic.update(true, 1 * min))
        assertEquals(TAKEN, mic.update(true, 9 * min))
        assertEquals(RETURNED, mic.update(false, 10 * min))
        assertEquals(LISTENING, mic.update(false, 11 * min))
    }

    @Test fun stopsOnlyAfterTenMinutesTaken() {
        val mic = MicHandback()
        mic.update(true, 0)
        assertEquals(TAKEN, mic.update(true, 10 * min - 1))
        assertEquals(GIVE_UP, mic.update(true, 10 * min))
    }

    @Test fun eachTakeStartsANewTenMinutes() {
        val mic = MicHandback()
        mic.update(true, 0); mic.update(false, 8 * min)
        assertEquals(TAKEN, mic.update(true, 9 * min))
        assertEquals(TAKEN, mic.update(true, 18 * min))
        assertEquals(GIVE_UP, mic.update(true, 19 * min))
    }
}
