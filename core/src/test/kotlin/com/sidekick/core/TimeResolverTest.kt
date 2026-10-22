package com.sidekick.core

import java.time.*
import kotlin.test.*

class TimeResolverTest {
    private val zone = ZoneId.of("America/Chicago")
    private val captured = Instant.parse("2026-10-22T01:00:00Z")
    @Test fun `tomorrow uses capture timezone rather than UTC date`() {
        assertEquals(Instant.parse("2026-10-22T22:00:00Z"), TimeResolver.suggest("I'll call tomorrow at 5 pm", captured, zone))
    }
    @Test fun `ambiguous time and missing day stay unresolved`() {
        assertNull(TimeResolver.suggest("I'll call tomorrow at five", captured, zone))
        assertNull(TimeResolver.suggest("I'll call at 5 pm", captured, zone))
    }
    @Test fun `past dates and invalid dates are rejected`() {
        assertNull(TimeResolver.suggest("I'll call today at 9 am", captured, zone))
        assertNull(TimeResolver.suggest("I'll call 2026-02-30 at 17:00", captured, zone))
        assertFails { TimeResolver.parseEditor("2026-02-30 12:00", zone) }
    }
    @Test fun `DST gap and overlapping times require clarification`() {
        assertFails { TimeResolver.parseEditor("2026-03-08 02:30", zone) }
        assertFails { TimeResolver.parseEditor("2026-11-01 01:30", zone) }
    }
    @Test fun `explicit 24 hour times are supported`() {
        assertEquals(Instant.parse("2026-10-23T22:15:00Z"), TimeResolver.suggest("I'll call 2026-10-23 at 17:15", captured, zone))
    }
}
