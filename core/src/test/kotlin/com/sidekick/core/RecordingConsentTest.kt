package com.sidekick.core

import com.sidekick.core.RecordingJurisdiction.BiometricRule
import com.sidekick.core.RecordingJurisdiction.Rule
import kotlin.test.*

class RecordingConsentTest {
    private fun at(lat: Double, lon: Double, accuracy: Double? = 50.0, age: Long = 60_000, precise: Boolean = false) =
        RecordingJurisdiction.locate(lat, lon, accuracy, age, precise)
    private fun assertRules(recording: Rule, biometric: BiometricRule, place: RecordingJurisdiction.Place) {
        assertEquals(recording to biometric, place.recording to place.biometric, place.toString())
    }

    @Test fun `all-party one-party and mixed states`() {
        assertEquals(Rule.ALL_PARTY, at(37.7749, -122.4194).recording)        // San Francisco, CA
        assertEquals("CA", at(37.7749, -122.4194).code)
        assertEquals(Rule.ALL_PARTY, at(41.8781, -87.6298).recording)         // Chicago, IL
        assertEquals(Rule.ALL_PARTY, at(47.6062, -122.3321).recording)        // Seattle, WA
        assertEquals(Rule.ALL_PARTY, at(39.9526, -75.1652).recording)         // Philadelphia, PA
        assertEquals(Rule.ALL_PARTY, at(36.1699, -115.1398).recording)        // Las Vegas, NV (mixed -> strict)
        assertEquals(Rule.ONE_PARTY, at(43.0731, -89.4012).recording)         // Madison, WI
        assertEquals(Rule.ONE_PARTY, at(29.7604, -95.3698).recording)         // Houston, TX
        assertEquals(Rule.ONE_PARTY, at(21.3069, -157.8583).recording)        // Honolulu, HI
    }
    @Test fun `a call ends a notice given in the room, even in an all-party state`() {
        val gate = ConsentGate(RecordingJurisdiction.strictestEverywhere())
        gate.noticeGiven(1_000)
        assertTrue(gate.keepOthers(2_000))
        gate.callStarted()
        assertNull(gate.noticeAtMs)
        assertFalse(gate.keepOthers(3_000))
    }
    @Test fun `a faked location never loosens the rules`() {
        // Madison, WI is one-party with no voice rule, but a position from a mock-location app gets the strictest rules.
        val faked = RecordingJurisdiction.locate(43.0731, -89.4012, 5.0, 1_000, precise = true, simulated = true)
        assertRules(Rule.ALL_PARTY, BiometricRule.STRICT, faked)
        assertNull(faked.code)
        assertEquals(RecordingJurisdiction.SIMULATED, faked.reason)
    }
    @Test fun `home screen line follows the place`() {
        val madison = ConsentGate(at(43.0731, -89.4012))
        assertEquals("Wisconsin: one-party, others kept while you take part · audio discarded", madison.homeLabel())
        val border = ConsentGate(at(42.5131, -89.0))
        assertEquals("Wisconsin (near IL): all-party, announce to keep others · audio discarded", border.homeLabel())
        border.noticeGiven(0)
        assertEquals("Wisconsin (near IL): all-party, notice given · audio discarded", border.homeLabel())
        assertEquals("Texas: one-party, voice notice before grouping · audio discarded", ConsentGate(at(29.7604, -95.3698)).homeLabel())
        assertTrue(ConsentGate(RecordingJurisdiction.unknown("No location fix")).homeLabel().contains("strictest rules"))
    }
    @Test fun `bundled data has two rule columns`() {
        assertEquals(Rule.ALL_PARTY to BiometricRule.STRICT, RecordingJurisdiction.rulesFor("IL"))
        assertEquals(Rule.ONE_PARTY to BiometricRule.NOTICE, RecordingJurisdiction.rulesFor("TX"))
        assertEquals(Rule.ALL_PARTY to BiometricRule.NOTICE, RecordingJurisdiction.rulesFor("WA"))
        assertEquals(Rule.ALL_PARTY to BiometricRule.NONE, RecordingJurisdiction.rulesFor("OR"))  // mixed
        assertEquals(Rule.ONE_PARTY to BiometricRule.NONE, RecordingJurisdiction.rulesFor("OH"))
        listOf("CA", "FL", "IL", "MD", "MA", "MT", "NH", "PA", "WA", "CT", "DE", "MI", "NV", "OR", "VT").forEach {
            assertEquals(Rule.ALL_PARTY, RecordingJurisdiction.rulesFor(it)?.first, it)
        }
        // Unknown values in the data resolve strict.
        assertEquals(Rule.ALL_PARTY, RecordingJurisdiction.parseRecording("??"))
        assertEquals(BiometricRule.STRICT, RecordingJurisdiction.parseBiometric("??"))
    }
    @Test fun `handoff cases`() {
        assertRules(Rule.ONE_PARTY, BiometricRule.NONE, at(39.9612, -82.9988))          // deep in Ohio (Columbus)
        assertRules(Rule.ALL_PARTY, BiometricRule.STRICT, at(39.7817, -89.6501))        // deep in Illinois (Springfield)
        val wisconsin = at(42.5131, -89.0)                                               // Wisconsin, 2 km north of Illinois
        assertEquals("WI", wisconsin.code)
        assertRules(Rule.ALL_PARTY, BiometricRule.STRICT, wisconsin)
        assertFalse(wisconsin.groupsOtherVoices)
        assertEquals(listOf("IL"), wisconsin.forcedBy)
        assertTrue(wisconsin.reason.startsWith("Within 2 km of Illinois"), wisconsin.reason)
        assertTrue(wisconsin.reason.contains("no voice grouping"), wisconsin.reason)
        val texas = at(33.5, -103.0)                                                     // Texas, ~2-6 km east of New Mexico
        assertEquals("TX", texas.code)
        assertRules(Rule.ONE_PARTY, BiometricRule.NOTICE, texas)
        val oregon = at(45.982, -118.0)                                                  // Oregon, 2 km south of Washington
        assertEquals("OR", oregon.code)
        assertRules(Rule.ALL_PARTY, BiometricRule.NOTICE, oregon)
        assertEquals(listOf("WA"), oregon.forcedBy)
        val nevada = at(40.5, -119.976)                                                  // Nevada, 2 km east of California
        assertEquals("NV", nevada.code)
        assertRules(Rule.ALL_PARTY, BiometricRule.NONE, nevada)
        assertRules(Rule.ALL_PARTY, BiometricRule.STRICT, RecordingJurisdiction.locate(null, null, null, null))  // no fix
        val coarse = at(42.5311, -89.0, accuracy = 2_000.0)                             // coarse 2 km accuracy, 4 km from Illinois
        assertEquals("WI", coarse.code)
        assertRules(Rule.ALL_PARTY, BiometricRule.STRICT, coarse)
    }
    @Test fun `precise location shrinks the buffer`() {
        assertEquals(1_500.0, RecordingJurisdiction.bufferMeters(50.0, precise = true))
        assertEquals(2_500.0, RecordingJurisdiction.bufferMeters(2_000.0, precise = true))
        assertEquals(5_500.0, RecordingJurisdiction.bufferMeters(2_000.0, precise = false))
        assertEquals(5_500.0, RecordingJurisdiction.bufferMeters(null, precise = true))    // unknown accuracy counts as coarse
        // Wisconsin 2 km from Illinois with a precise 50 m fix is outside the 1.5 km buffer.
        assertRules(Rule.ONE_PARTY, BiometricRule.NONE, at(42.5131, -89.0, precise = true))
        // ...but 1 km from Illinois stays strict.
        assertRules(Rule.ALL_PARTY, BiometricRule.STRICT, at(42.5041, -89.0, precise = true))
    }
    @Test fun `uncertainty near an all-party border resolves strict`() {
        // Kenosha, WI is about 10 km north of the Illinois line.
        assertEquals(Rule.ONE_PARTY, at(42.5847, -87.8212, accuracy = 100.0).recording)
        val coarse = at(42.5847, -87.8212, accuracy = 15_000.0)
        assertRules(Rule.ALL_PARTY, BiometricRule.STRICT, coarse)
        assertTrue(coarse.reason.contains("Illinois"), coarse.reason)
        assertEquals("WI", coarse.code)
    }
    @Test fun `no fix stale fix and abroad are strictest`() {
        assertRules(Rule.ALL_PARTY, BiometricRule.STRICT, at(43.0731, -89.4012, age = RecordingJurisdiction.MAX_FIX_AGE_MS + 1))
        assertRules(Rule.ALL_PARTY, BiometricRule.STRICT, at(37.5665, 126.9780))       // Seoul
        assertRules(Rule.ALL_PARTY, BiometricRule.STRICT, at(33.8938, 35.5018))        // Beirut
        assertRules(Rule.ALL_PARTY, BiometricRule.STRICT, at(Double.NaN, -89.4012))
        assertFalse(RecordingJurisdiction.unknown("x").groupsOtherVoices)              // the old code null bug
        assertRules(Rule.ALL_PARTY, BiometricRule.STRICT, RecordingJurisdiction.strictestEverywhere())
        assertRules(Rule.ALL_PARTY, BiometricRule.STRICT, RecordingJurisdiction.duringCall())
        assertEquals("Call detected: strictest rule", RecordingJurisdiction.duringCall().reason)
    }
    @Test fun `unknown accuracy assumes a wide circle`() {
        assertEquals(Rule.ONE_PARTY, at(42.5847, -87.8212, accuracy = null).recording)
        assertEquals(Rule.ALL_PARTY, at(42.4900, -87.8300, accuracy = null).recording)
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
        assertTrue(california.keep(Speaker.OTHER, 20 * 60_000))      // still the same conversation
        assertFalse(california.keep(Speaker.OTHER, 60 * 60_000))     // 40 min with no other voices: notice ended
    }
    @Test fun `moving into an all-party state mid-session tightens the rule`() {
        val gate = ConsentGate(at(43.0731, -89.4012))
        gate.ownerSpoke(0)
        assertTrue(gate.keepOthers(1_000))
        gate.moveTo(at(41.8781, -87.6298))
        assertFalse(gate.keepOthers(2_000))
        assertFalse(gate.place.groupsOtherVoices)                     // Illinois: no voice grouping
        assertFalse(gate.groupingAllowed())
    }
    @Test fun `notice resets on a stricter move only`() {
        val ohio = at(39.9612, -82.9988)
        val washington = at(47.6062, -122.3321)
        val oregon = at(44.9429, -123.0351)  // Salem, OR: ALL/NONE
        val gate = ConsentGate(ohio)
        gate.noticeGiven(1_000)
        assertTrue(gate.noticeIncludedVoice)                          // the voice line is spoken in every state
        assertTrue(gate.moveTo(washington))                           // stricter in both rules: new notice needed
        assertNull(gate.noticeAtMs)
        assertTrue(gate.noticeNeeded())
        gate.noticeGiven(2_000)
        assertTrue(gate.noticeIncludedVoice)                          // Washington notice includes the voice line
        assertFalse(gate.moveTo(oregon))                              // looser biometric, equal recording: kept
        assertNotNull(gate.noticeAtMs)
        assertFalse(gate.moveTo(washington))                          // back to the notice place: equal, kept
        assertNotNull(gate.noticeAtMs)
        assertFalse(gate.moveTo(ohio))                                // looser: kept
        assertTrue(gate.moveTo(at(39.7817, -89.6501)))               // Illinois: stricter biometric, reset
    }
    @Test fun `texas and washington need the voice notice before grouping`() {
        val texas = ConsentGate(at(29.7604, -95.3698))
        assertEquals(Rule.ONE_PARTY, texas.place.recording)
        assertFalse(texas.groupingAllowed())
        assertTrue(texas.noticeNeeded())                              // buttons show though recording is one-party
        assertEquals("I told everyone (incl. voice analysis)", texas.attestLabel())
        assertEquals(ConsentNotice.text(true), texas.noticeText())
        assertTrue(texas.noticeText().endsWith(ConsentNotice.VOICE))
        texas.ownerSpoke(0)
        assertTrue(texas.keepOthers(1_000))                           // words: still one-party
        assertFalse(texas.namingAllowed(1_000))                       // names: not before the voice notice
        texas.noticeGiven(2_000)
        assertTrue(texas.groupingAllowed())
        assertTrue(texas.namingAllowed(3_000))
        assertFalse(texas.noticeNeeded())
        val record = texas.record(1_700_000_000_000, NoticeMethod.ATTESTED)
        assertEquals(listOf("TX"), record.stateCodes)
        assertEquals(BiometricRule.NOTICE, record.biometric)
        assertEquals(ConsentNotice.VERSION, record.textVersion)
        assertTrue(record.includesVoice)

        val washington = ConsentGate(at(47.6062, -122.3321))
        washington.noticeGiven(0, includesVoice = false)              // base notice only
        assertTrue(washington.keepOthers(1_000))                      // recording consent given
        assertFalse(washington.groupingAllowed())                     // but no grouping without the voice line
        assertTrue(washington.noticeNeeded())
    }
    @Test fun `illinois never groups even after notice`() {
        val illinois = ConsentGate(at(39.7817, -89.6501))
        assertEquals("I told everyone (incl. voice analysis)", illinois.attestLabel())
        illinois.noticeGiven(0)
        assertTrue(illinois.keepOthers(1_000))                        // recording consent still works
        assertFalse(illinois.groupingAllowed())
        assertFalse(illinois.namingAllowed(1_000))
        assertTrue(illinois.status(1_000).contains("no voice grouping"))
    }
    @Test fun `voice line everywhere setting`() {
        val gate = ConsentGate(at(39.9612, -82.9988), voiceNoticeEverywhere = true)
        assertFalse(gate.groupingAllowed())
        assertTrue(gate.noticeNeeded())
        assertEquals(ConsentNotice.text(true), gate.noticeText())
        gate.noticeGiven(0)
        assertTrue(gate.groupingAllowed())
        val off = ConsentGate(at(39.9612, -82.9988))
        assertTrue(off.groupingAllowed())
        assertFalse(off.noticeNeeded())
        assertEquals(ConsentNotice.text(true), off.noticeText())       // spoken everywhere, even with the setting off
    }
    @Test fun `stricter owner match only before notice in all-party places`() {
        val california = ConsentGate(at(37.7749, -122.4194))
        assertTrue(california.strictOwnerMatch())
        california.noticeGiven(0)
        assertFalse(california.strictOwnerMatch())
        assertFalse(ConsentGate(at(39.9612, -82.9988)).strictOwnerMatch())
    }
    @Test fun `a notice covers one conversation, not an all-day session`() {
        val min = 60_000L
        val gate = ConsentGate(at(37.7749, -122.4194))                         // California: all-party
        gate.noticeGiven(0)
        assertTrue(gate.keep(Speaker.OTHER, 10 * min))                          // others keep talking: still covered
        assertTrue(gate.keep(Speaker.OTHER, 39 * min))                          // 29 min after the last other voice
        assertNull(gate.expireIfStale(60 * min))                                // 21 min quiet: not yet
        assertEquals("conversation ended", gate.expireIfStale(70 * min))       // 31 min with no other voices
        assertNull(gate.noticeAtMs)
        assertFalse(gate.keep(Speaker.OTHER, 71 * min))                         // a new conversation needs a new notice
        assertTrue(gate.status(71 * min).contains("notice expired (conversation ended)"), gate.status(71 * min))
        assertTrue(gate.noticeNeeded())
        gate.noticeGiven(72 * min)
        assertFalse(gate.status(73 * min).contains("expired"))
        // A quiet person who heard it is still covered: the owner talking alone does not end it before 30 min.
        gate.ownerSpoke(90 * min)
        assertNull(gate.expireIfStale(100 * min))
    }
    @Test fun `a notice never lasts more than two hours`() {
        val min = 60_000L
        val gate = ConsentGate(at(37.7749, -122.4194))
        gate.noticeGiven(0)
        var t = 0L
        while (t < 119 * min) { t += 10 * min; assertTrue(gate.keep(Speaker.OTHER, t)) }   // continuous meeting
        assertEquals("2 hours passed", gate.expireIfStale(121 * min))
        assertFalse(gate.keepOthers(121 * min))
    }
    @Test fun `texas grouping stops when its notice expires`() {
        val texas = ConsentGate(at(29.7604, -95.3698))
        texas.noticeGiven(0)
        assertTrue(texas.groupingAllowed())
        texas.expireIfStale(ConsentGate.CONVERSATION_GAP_MS + 1)
        assertFalse(texas.groupingAllowed())                                     // the service then forgets other voices
    }
}
