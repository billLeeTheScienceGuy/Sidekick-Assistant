package com.sidekick.core

import com.sidekick.core.RecordingJurisdiction.Rule
import kotlin.test.*

class RecordingConsentTest {
    private fun at(lat: Double, lon: Double, accuracy: Double = 50.0, age: Long = 60_000) = RecordingJurisdiction.locate(lat, lon, accuracy, age)

    @Test fun `all-party one-party and mixed states`() {
        assertEquals(Rule.ALL_PARTY, at(37.7749, -122.4194).rule)        // San Francisco, CA
        assertEquals("CA", at(37.7749, -122.4194).code)
        assertEquals(Rule.ALL_PARTY, at(41.8781, -87.6298).rule)         // Chicago, IL
        assertEquals(Rule.ALL_PARTY, at(47.6062, -122.3321).rule)        // Seattle, WA
        assertEquals(Rule.ALL_PARTY, at(39.9526, -75.1652).rule)         // Philadelphia, PA
        assertEquals(Rule.ALL_PARTY, at(36.1699, -115.1398).rule)        // Las Vegas, NV (mixed -> strict)
        assertEquals(Rule.ONE_PARTY, at(43.0731, -89.4012).rule)         // Madison, WI
        assertEquals(Rule.ONE_PARTY, at(40.7128, -74.0060).rule)         // New York, NY
        assertEquals(Rule.ONE_PARTY, at(29.7604, -95.3698).rule)         // Houston, TX
        assertEquals(Rule.ONE_PARTY, at(21.3069, -157.8583).rule)        // Honolulu, HI
    }
    @Test fun `uncertainty near an all-party border resolves strict`() {
        // Kenosha, WI is about 10 km north of the Illinois line.
        assertEquals(Rule.ONE_PARTY, at(42.5847, -87.8212, accuracy = 100.0).rule)
        val coarse = at(42.5847, -87.8212, accuracy = 15_000.0)
        assertEquals(Rule.ALL_PARTY, coarse.rule)
        assertTrue(coarse.reason.contains("Illinois"), coarse.reason)
        assertEquals("WI", coarse.code)
    }
    @Test fun `no fix stale fix and abroad are strict`() {
        assertEquals(Rule.ALL_PARTY, RecordingJurisdiction.locate(null, null, null, null).rule)
        assertEquals(Rule.ALL_PARTY, at(43.0731, -89.4012, age = RecordingJurisdiction.MAX_FIX_AGE_MS + 1).rule)
        assertEquals(Rule.ALL_PARTY, at(37.5665, 126.9780).rule)          // Seoul
        assertEquals(Rule.ALL_PARTY, at(33.8938, 35.5018).rule)           // Beirut
        assertEquals(Rule.ALL_PARTY, at(Double.NaN, -89.4012).rule)
    }
    @Test fun `unknown accuracy assumes a wide circle`() {
        // No accuracy reported: 5 km assumed, so Kenosha (10 km from Illinois) stays one-party, the IL side does not.
        assertEquals(Rule.ONE_PARTY, RecordingJurisdiction.locate(42.5847, -87.8212, null, 60_000).rule)
        assertEquals(Rule.ALL_PARTY, RecordingJurisdiction.locate(42.4900, -87.8300, null, 60_000).rule)
    }
    @Test fun `owner words always kept, others need participation or notice`() {
        val wisconsin = ConsentGate(at(43.0731, -89.4012))
        assertTrue(wisconsin.keep(Speaker.USER, 0))
        assertFalse(wisconsin.keep(Speaker.OTHER, 1_000))            // overheard: owner has not spoken
        wisconsin.ownerSpoke(10_000)
        assertTrue(wisconsin.keep(Speaker.OTHER, 60_000))            // owner in the conversation
        assertTrue(wisconsin.keep(Speaker.UNKNOWN, 130_000))
        assertFalse(wisconsin.keep(Speaker.OTHER, 131_000))          // owner silent > 2 min
        val california = ConsentGate(at(37.7749, -122.4194))
        california.ownerSpoke(0)
        assertFalse(california.keep(Speaker.OTHER, 1_000))           // participation is not enough in CA
        california.noticeGiven(2_000)
        assertTrue(california.keep(Speaker.OTHER, 3_000))
        assertTrue(california.keep(Speaker.OTHER, 3_600_000))        // notice lasts the session
    }
    @Test fun `moving into an all-party state mid-session tightens the rule`() {
        val gate = ConsentGate(at(43.0731, -89.4012))
        gate.ownerSpoke(0)
        assertTrue(gate.keepOthers(1_000))
        gate.moveTo(at(41.8781, -87.6298))
        assertFalse(gate.keepOthers(2_000))
        assertFalse(gate.place.groupsOtherVoices)                     // Illinois: no voice grouping
    }
}
