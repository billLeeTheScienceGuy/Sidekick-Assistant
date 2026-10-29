package com.sidekick.core

import kotlin.test.*

class VoiceMatchTest {
    @Test fun `a turn needs agreement across every speaker window`() {
        assertEquals(Speaker.USER, VoiceMatch.identifyTurn(axis(0), listOf(axis(0), axis(0), axis(0))))
        assertEquals(Speaker.UNKNOWN, VoiceMatch.identifyTurn(axis(0), listOf(axis(0), axis(0), axis(1))))
        assertEquals(Speaker.OTHER, VoiceMatch.identifyTurn(axis(0), listOf(axis(1), axis(1), axis(1))))
        assertEquals(Speaker.UNKNOWN, VoiceMatch.identifyTurn(axis(0), listOf(axis(0))))
    }
    @Test fun `short phrases need strong current evidence and a fresh anchor`() {
        assertEquals(Speaker.USER, VoiceMatch.classifyShortScores(.85f, .84f, 2100, 15_000))
        assertEquals(Speaker.UNKNOWN, VoiceMatch.classifyShortScores(.79f, .95f, 2100, 100))
        assertEquals(Speaker.UNKNOWN, VoiceMatch.classifyShortScores(.95f, .79f, 2100, 100))
        assertEquals(Speaker.UNKNOWN, VoiceMatch.classifyShortScores(.95f, .95f, 1499, 100))
        assertEquals(Speaker.UNKNOWN, VoiceMatch.classifyShortScores(.95f, .95f, 2100, 60_001))
        assertEquals(Speaker.UNKNOWN, VoiceMatch.classifyShortScores(.95f, .95f, 2100, -1))
        assertEquals(Speaker.UNKNOWN, VoiceMatch.classifyShortScores(Float.NaN, .95f, 2100, 100))
    }
    @Test fun `balanced windows tolerate limited asymmetry without accepting video range`() {
        assertEquals(Speaker.USER, VoiceMatch.classifyScores(.76f, .68f, .70f))
        assertEquals(Speaker.USER, VoiceMatch.classifyScores(.68f, .77f, .82f))
        assertEquals(Speaker.UNKNOWN, VoiceMatch.classifyScores(.69f, .64f, .87f))
        assertEquals(Speaker.UNKNOWN, VoiceMatch.classifyScores(.65f, .56f, .79f))
        assertEquals(Speaker.UNKNOWN, VoiceMatch.classifyScores(.85f, .59f, .90f))
        assertEquals(Speaker.UNKNOWN, VoiceMatch.classifyScores(.76f, .68f, .64f))
        assertEquals(Speaker.UNKNOWN, VoiceMatch.classifyScores(Float.NaN, .8f, .8f))
    }
    private fun axis(i: Int) = FloatArray(256) { if (it == i) 1f else 0f }
    @Test fun `missing profile and inconsistent windows stay unknown`() {
        assertEquals(Speaker.UNKNOWN, VoiceMatch.identify(null, axis(0), axis(0)))
        assertEquals(Speaker.UNKNOWN, VoiceMatch.identify(axis(0), axis(0), axis(1)))
    }
    @Test fun `only both confident windows identify user or other`() {
        assertEquals(Speaker.USER, VoiceMatch.identify(axis(0), axis(0), axis(0)))
        assertEquals(Speaker.OTHER, VoiceMatch.identify(axis(0), axis(1), axis(1)))
        val ambiguous = axis(1).also { it[0] = .6f; it[1] = .8f }
        assertEquals(Speaker.UNKNOWN, VoiceMatch.identify(axis(0), ambiguous, ambiguous))
    }
    @Test fun `invalid embeddings cannot become matches`() {
        assertFailsWith<IllegalArgumentException> { VoiceMatch.normalized(FloatArray(256)) }
        assertFailsWith<IllegalArgumentException> { VoiceMatch.normalized(FloatArray(256) { Float.NaN }) }
        assertFailsWith<IllegalArgumentException> { VoiceMatch.normalized(FloatArray(255)) }
    }
}
