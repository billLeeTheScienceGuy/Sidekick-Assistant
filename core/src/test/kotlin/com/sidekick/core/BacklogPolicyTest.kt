package com.sidekick.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BacklogPolicyTest {
    private val mb = 1024L * 1024

    @Test fun switchesToFasterOnlyWhenThePredictedWaitIsLong() {
        val p = BacklogPolicy()
        p.measured(workMs = 2_000, audioMs = 1_000) // recognizer currently twice as slow as speech
        repeat(10) { p.measured(2_000, 1_000) }
        assertFalse(p.useFaster(waitingAudioMs = 20_000, thermalStatus = 0)) // 40 s wait: keep the accurate one
        assertTrue(p.useFaster(waitingAudioMs = 40_000, thermalStatus = 0))  // 80 s wait: catch up
        assertTrue(p.useFaster(waitingAudioMs = 10_000, thermalStatus = 0))  // 20 s: still catching up (gap)
        assertFalse(p.useFaster(waitingAudioMs = 5_000, thermalStatus = 0))  // 10 s: back to accurate
    }

    @Test fun aHotPhoneCatchesUpSooner() {
        val p = BacklogPolicy()
        repeat(10) { p.measured(2_000, 1_000) }
        assertFalse(p.useFaster(20_000, thermalStatus = 0))
        assertTrue(p.useFaster(20_000, thermalStatus = 3))
    }

    @Test fun theWaitingLineGrowsWithFreeMemoryAndNeverDropsUnlessMemoryIsLow() {
        val p = BacklogPolicy()
        assertEquals(512 * mb, p.limitBytes(freeMemoryBytes = 2048 * mb, memoryLow = false)) // ~4 hours of speech
        assertEquals(32 * mb, p.limitBytes(freeMemoryBytes = 64 * mb, memoryLow = false))     // never below 32 MB
        assertFalse(p.mustDrop(waitingBytes = 900 * mb, freeMemoryBytes = 2048 * mb, memoryLow = false))
        assertTrue(p.mustDrop(waitingBytes = 200 * mb, freeMemoryBytes = 512 * mb, memoryLow = true))
    }
}
