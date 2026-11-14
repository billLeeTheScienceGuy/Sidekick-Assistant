package com.sidekick.core

import kotlin.test.*

class RouteMemoryTest {
    @Test fun `three Omnilingual phrases start skipping, every fourth is a full check`() {
        var m = RouteMemory()
        val skipped = mutableListOf<Boolean>()
        repeat(12) { skipped += m.skipToOmnilingual; m = m.after(viaOmnilingual = true) }
        // Phrases 1-3 full route; 4-6 skip; 7 full; 8-10 skip; 11 full; 12 skip.
        assertEquals(listOf(false, false, false, true, true, true, false, true, true, true, false, true), skipped)
    }
    @Test fun `any other result resets the memory`() {
        var m = RouteMemory()
        repeat(5) { m = m.after(true) }
        assertTrue(m.skipToOmnilingual)
        assertFalse(m.after(false).skipToOmnilingual)
        assertEquals(RouteMemory(), m.after(false))
    }
}
