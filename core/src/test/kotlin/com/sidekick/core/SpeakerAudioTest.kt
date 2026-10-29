package com.sidekick.core

import kotlin.math.sin
import kotlin.test.*

class SpeakerAudioTest {
    private fun speech(size: Int) = FloatArray(size) { (.06 * sin(it * .13)).toFloat() }
    @Test fun `quiet capture padding does not become speaker evidence`() {
        val padded = FloatArray(4000) + speech(64000) + FloatArray(16000)
        val span = SpeakerAudio.speechSpan(padded)!!
        assertTrue(span.first in 3200..4000)
        assertTrue(span.last in 67999..68800)
    }
    @Test fun `internal pauses are preserved rather than joining voices`() {
        val input = speech(32000) + FloatArray(16000) + speech(32000)
        val span = SpeakerAudio.speechSpan(input)!!
        assertEquals(0, span.first); assertEquals(input.lastIndex, span.last)
    }
    @Test fun `silence DC clicks and nonfinite input cannot establish speech`() {
        for (samples in listOf(FloatArray(32000), FloatArray(32000) { .03f }, FloatArray(32000).also { it[100] = .9f }, floatArrayOf(Float.NaN)))
            assertNull(SpeakerAudio.speechSpan(samples))
    }
    @Test fun `padding does not lengthen a short phrase into a long one`() {
        val input = FloatArray(4096) + speech(24000) + FloatArray(16384)
        assertTrue(SpeakerAudio.speechSpan(input)!!.count() < 48000)
    }
}
