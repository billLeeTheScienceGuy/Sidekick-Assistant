package com.sidekick.core

import kotlin.math.sin
import kotlin.test.*

class SpeakerWindowTest {
    @Test fun `windows expand around pauses while preserving independent bounded continuous audio`() {
        val samples = FloatArray(96_000) { i -> if (i in 4000..31_999 || i in 64_000..95_999) (sin(i * .2) * .1).toFloat() else 0f }
        val original = samples.copyOf()
        val window = assertNotNull(SpeakerAudio.matchingWindow(samples, 32_000, 48_000))
        assertTrue(window.first < 32_000)
        assertTrue(window.last - window.first + 1 <= 72_000)
        assertTrue(window.last - window.first + 1 < samples.size)
        assertContentEquals(original, samples)
    }
    @Test fun `quiet or insufficient speech cannot become identity evidence by expansion`() {
        assertNull(SpeakerAudio.matchingWindow(FloatArray(80_000), 0, 48_000))
        val click = FloatArray(80_000).apply { this[10] = .5f }
        assertNull(SpeakerAudio.matchingWindow(click, 0, 48_000))
        val spoken = FloatArray(48_000) { (sin(it * .2) * .1).toFloat() }
        assertEquals(0 until 24_000, SpeakerAudio.matchingWindow(spoken, 0, 24_000))
    }
}
