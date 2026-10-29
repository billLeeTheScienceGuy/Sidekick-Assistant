package com.sidekick.core

import kotlin.test.*

class SpeakerModelPolicyTest {
    @Test fun `model-specific cutoffs do not change the existing default`() {
        assertEquals(Speaker.UNKNOWN, VoiceMatch.classifyShortScores(.65f, .65f, 2100, 5000))
        assertEquals(Speaker.USER, VoiceMatch.classifyShortScores(.65f, .65f, 2100, 5000, .60f))
        assertEquals(Speaker.UNKNOWN, VoiceMatch.classifyShortScores(.59f, .95f, 2100, 5000, .60f))
        assertEquals(Speaker.UNKNOWN, VoiceMatch.classifyShortScores(.95f, .59f, 2100, 5000, .60f))
        assertFailsWith<IllegalArgumentException> { VoiceMatch.classifyShortScores(.9f, .9f, 2100, 0, .3f) }
    }
    @Test fun `different model dimensions cannot be mixed`() {
        val a = FloatArray(192).also { it[0] = 1f }
        val b = FloatArray(256).also { it[0] = 1f }
        assertEquals(1f, VoiceMatch.similarity(a, a))
        assertFailsWith<IllegalArgumentException> { VoiceMatch.similarity(a, b) }
        val anchors = VoiceAnchors()
        anchors.remember(listOf(a, a), 0)
        assertEquals(1f, anchors.support(a, 1000)!!.score)
    }
}
