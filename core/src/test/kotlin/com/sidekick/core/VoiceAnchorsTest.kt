package com.sidekick.core

import kotlin.math.sqrt
import kotlin.test.*

class VoiceAnchorsTest {
    private fun axis(i: Int) = FloatArray(256) { if (it == i) 1f else 0f }
    private fun toward(score: Float) = FloatArray(256).also { it[0] = score; it[1] = sqrt(1 - score * score) }
    @Test fun `two agreeing references can support a phrase diluted by the mean`() {
        val bank = VoiceAnchors()
        bank.remember(listOf(toward(.84f), toward(.82f), toward(.40f)), 0)
        val support = bank.support(axis(0), 24_000)!!
        assertTrue(support.score >= .80f)
        assertEquals(Speaker.USER, VoiceMatch.classifyShortScores(.83f, support.score, 2100, support.ageMs))
        assertEquals(Speaker.UNKNOWN, VoiceMatch.classifyShortScores(.79f, support.score, 2100, support.ageMs))
    }
    @Test fun `one favorable reference cannot rescue a weak mean`() {
        val bank = VoiceAnchors()
        bank.remember(listOf(toward(.90f), toward(.40f), toward(.40f)), 0)
        assertTrue(bank.support(axis(0), 100)!!.score < .80f)
    }
    @Test fun `queries do not renew identity and expired or reversed clocks erase references`() {
        val bank = VoiceAnchors()
        bank.remember(listOf(axis(0), axis(0)), 1000)
        assertNotNull(bank.support(axis(0), 60_000))
        assertNotNull(bank.support(axis(0), 61_000))
        assertNull(bank.support(axis(0), 61_001))
        bank.remember(listOf(axis(0), axis(0)), 1000)
        assertNull(bank.support(axis(0), 999))
    }
    @Test fun `bounded recent turns discard older identities and clear removes all support`() {
        val bank = VoiceAnchors()
        bank.remember(listOf(axis(0), axis(0)), 0)
        bank.remember(listOf(axis(1), axis(1)), 1000)
        bank.remember(listOf(axis(1), axis(1)), 2000)
        assertEquals(0f, bank.support(axis(0), 3000)!!.score)
        bank.clear(); assertNull(bank.ageMs(3000))
    }
}
