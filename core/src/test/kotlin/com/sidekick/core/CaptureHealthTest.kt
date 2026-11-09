package com.sidekick.core

import kotlin.test.*

class CaptureHealthTest {
    @Test fun `empty reads stop after a bounded gap and a native error stops immediately`() {
        val health = CaptureHealth(100)
        health.check(0, 5_099)
        assertFailsWith<IllegalStateException> { health.check(0, 5_100) }
        assertFailsWith<IllegalStateException> { CaptureHealth(100).check(-6, 101) }
    }
    @Test fun `short empty intervals and silent PCM can continue for hours`() {
        val health = CaptureHealth(0)
        for (time in 0L..3_600_000L step 256L) {
            health.check(4096, time) // A frame of all-zero samples still has this positive read count.
            health.check(0, time + 10)
        }
    }
    @Test fun `new samples reset the starvation deadline without extending it on empty reads`() {
        val health = CaptureHealth(0)
        health.check(0, 4_000); health.check(1, 4_999); health.check(0, 9_998)
        assertFailsWith<IllegalStateException> { health.check(0, 9_999) }
    }
}
