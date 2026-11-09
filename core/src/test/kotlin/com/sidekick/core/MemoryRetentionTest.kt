package com.sidekick.core

import kotlin.test.*

class MemoryRetentionTest {
    private val day = 86_400_000L
    private val now = 500 * day
    @Test fun `retention is opt in and keeps future undated and pending memories`() {
        assertFalse(MemoryRetention.expired(0, now, "DONE", 0, null, null, null, null))
        assertFalse(MemoryRetention.expired(30, now, "ACCEPTED", 0, null, null, null, null))
        assertFalse(MemoryRetention.expired(30, now, "ACCEPTED", 0, day, null, day, null))
        assertFalse(MemoryRetention.expired(30, now, "DONE", 0, now + day, null, null, null))
        assertFalse(MemoryRetention.expired(30, now, "PROPOSED", 0, day, null, null, null))
        assertFailsWith<IllegalArgumentException> { MemoryRetention.expired(-1, now, "DONE", 0, null, null, null, null) }
    }
    @Test fun `latest known timestamp and strict cutoff govern cleanup`() {
        assertTrue(MemoryRetention.expired(30, now, "DONE", 0, null, null, null, null))
        assertTrue(MemoryRetention.expired(30, now, "ACCEPTED", 0, day, day * 2, day, day))
        assertFalse(MemoryRetention.expired(30, now, "CANCELLED", now - 30 * day, null, null, null, null))
        assertFalse(MemoryRetention.expired(30, now, "ACCEPTED", 0, day, now, null, null))
        assertFalse(MemoryRetention.expired(30, now, "ACCEPTED", now, day, day * 2, null, null))
        assertFalse(MemoryRetention.expired(365, now, "DONE", now - 90 * day, null, null, null, null))
    }
}
