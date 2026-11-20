package com.sidekick.core

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.*

class WakeVoiceTest {
    private fun tone(seconds: Double) = FloatArray((seconds * 16000).toInt()) { (.3 * sin(2 * PI * 220 * it / 16000)).toFloat() }
    private fun silence(seconds: Double) = FloatArray((seconds * 16000).toInt())

    @Test fun cutsTheNameAtThePauseAfterIt() {
        val audio = silence(.3) + tone(.6) + silence(.3) + tone(2.0)
        val segment = assertNotNull(WakeVoice.segment(audio))
        assertEquals(WakeVoice.WINDOW, segment.size)
        val voiced = segment.count { kotlin.math.abs(it) > .01f } / 16000.0
        assertTrue(voiced in .5..0.65, "only the name is kept, got $voiced s")
    }

    @Test fun withoutAPauseTakesTheFirstPart() {
        val segment = assertNotNull(WakeVoice.segment(silence(.2) + tone(3.0)))
        val voiced = segment.count { kotlin.math.abs(it) > .01f } / 16000.0
        // A sine passes zero, so a few samples per cycle count as quiet: 0.9 s cut measures about 0.84 s.
        assertTrue(voiced in .8..0.95, "got $voiced s")
    }

    @Test fun tooLittleSpeechIsRefused() {
        assertNull(WakeVoice.segment(silence(1.0) + tone(.1) + silence(1.0)))
        assertNull(WakeVoice.segment(silence(2.0)))
    }

    private fun vector(seed: Int, noise: Float) = java.util.Random(seed.toLong()).let { r ->
        FloatArray(192) { if (it < 20) 1f + noise * r.nextGaussian().toFloat() else noise * r.nextGaussian().toFloat() }
    }

    @Test fun templateNeedsFiveConsistentSamples() {
        assertNull(WakeVoice.template(List(4) { vector(it, .05f) }))
        assertNotNull(WakeVoice.template(List(5) { vector(it, .05f) }))
        assertNull(WakeVoice.template(List(4) { vector(it, .05f) } + listOf(vector(9, 3f))), "one sample unlike the rest")
    }

    @Test fun findsTheOddSampleSoTheRestCanBeKept() {
        val good = List(4) { vector(it, .05f) }
        assertNull(WakeVoice.outlier(good))
        assertEquals(2, WakeVoice.outlier(good.take(2) + listOf(vector(9, 3f)) + good.drop(2)))
    }

    @Test fun onlyUpgradesAnUnassignedVoice() {
        assertEquals(Speaker.USER, WakeVoice.asker(Speaker.UNKNOWN, .85f))
        assertEquals(Speaker.UNKNOWN, WakeVoice.asker(Speaker.UNKNOWN, .79f))
        assertEquals(Speaker.UNKNOWN, WakeVoice.asker(Speaker.UNKNOWN, null))
        assertEquals(Speaker.UNKNOWN, WakeVoice.asker(Speaker.UNKNOWN, Float.NaN))
        assertEquals(Speaker.OTHER, WakeVoice.asker(Speaker.OTHER, .99f), "never overrides someone else")
        assertEquals(Speaker.USER, WakeVoice.asker(Speaker.USER, null))
    }
}
