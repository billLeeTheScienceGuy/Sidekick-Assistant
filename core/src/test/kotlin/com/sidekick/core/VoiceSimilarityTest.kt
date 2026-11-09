package com.sidekick.core

import kotlin.test.*

class VoiceSimilarityTest {
    @Test fun `cosine stays finite and bounded for scaled identical and opposite signatures`() {
        val a = FloatArray(192) { (it + 1) * .017f }
        assertEquals(1f, VoiceMatch.similarity(a, a))
        assertEquals(1f, VoiceMatch.similarity(a, FloatArray(192) { a[it] * 50f }))
        assertEquals(-1f, VoiceMatch.similarity(a, FloatArray(192) { -a[it] }))
        val x = FloatArray(256).apply { this[0] = 1f }
        val y = FloatArray(256).apply { this[0] = .8f; this[1] = .6f }
        assertEquals(.8f, VoiceMatch.similarity(x, y), .000001f)
    }
    @Test fun `unusable signatures fail before they can enter anonymous clustering`() {
        val good = FloatArray(192) { .1f }
        for (bad in listOf(FloatArray(192), FloatArray(192) { Float.NaN }, FloatArray(192) { Float.POSITIVE_INFINITY }, FloatArray(256) { .1f }))
            assertFailsWith<IllegalArgumentException> { VoiceMatch.similarity(good, bad) }
    }
}
