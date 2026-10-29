package com.sidekick.core

import kotlin.test.*

class SpeechSegmenterTest {
    @Test fun `max window continuation and new speech after silence are distinguishable`() {
        val segmenter = SpeechSegmenter(8, 0, 2, 1, 2)
        val first = segmenter.accept(FloatArray(8) { .1f }, true).single()
        assertFalse(first.continuesPrevious)
        segmenter.accept(FloatArray(2) { .1f }, true)
        val tail = segmenter.accept(FloatArray(2), false).single()
        assertTrue(tail.continuesPrevious)
        segmenter.accept(FloatArray(3) { .1f }, true)
        val separate = segmenter.accept(FloatArray(2), false).single()
        assertFalse(separate.continuesPrevious)
    }
    @Test fun `overlap preserves boundary audio without emitting an overlap-only tail`() {
        val segmenter = SpeechSegmenter(4, 0, 2, 1, overlapSamples = 2)
        val first = segmenter.accept(floatArrayOf(.1f, .2f, .3f, .4f), true).single()
        val second = segmenter.accept(floatArrayOf(.5f, .6f), true).single()
        assertEquals(0L, first.startSample)
        assertEquals(2L, second.startSample)
        assertContentEquals(floatArrayOf(.3f, .4f, .5f, .6f), second.samples)
        assertTrue(segmenter.accept(floatArrayOf(0f, 0f), false).isEmpty())
        assertNull(segmenter.finish())
    }
    @Test fun `short tail of ongoing speech is not mistaken for isolated noise`() {
        val segmenter = SpeechSegmenter(4, 0, 2, 3)
        assertEquals(4, segmenter.accept(FloatArray(4) { .1f }, true).single().samples.size)
        segmenter.accept(floatArrayOf(.2f), true)
        assertContentEquals(floatArrayOf(.2f), assertNotNull(segmenter.finish()).samples)
    }

    @Test fun `twenty minute input has contiguous bounded chunks without a session cutoff`() {
        val segmenter = SpeechSegmenter(160_000, 8_000, 8_000, 3_200)
        val frame = FloatArray(1600) { .25f }
        var consumed = 0L
        repeat(20 * 60 * 10) {
            for (chunk in segmenter.accept(frame, true)) {
                assertEquals(consumed, chunk.startSample)
                assertTrue(chunk.samples.size <= 160_000)
                consumed += chunk.samples.size
                chunk.samples.fill(0f)
            }
        }
        assertEquals(20L * 60 * 16_000, consumed)
        assertNull(segmenter.finish())
    }

    @Test fun `silence stays bounded and yields no transcript input`() {
        val segmenter = SpeechSegmenter(10, 2, 2, 2)
        repeat(1000) { assertTrue(segmenter.accept(FloatArray(10), false).isEmpty()) }
        assertNull(segmenter.finish())
    }

    @Test fun `retains pre roll and exact start offset then ends on silence`() {
        val segmenter = SpeechSegmenter(10, 2, 2, 2)
        segmenter.accept(floatArrayOf(.1f, .2f, .3f), false)
        segmenter.accept(floatArrayOf(.4f, .5f), true)
        val chunk = segmenter.accept(floatArrayOf(0f, 0f), false).single()
        assertEquals(1L, chunk.startSample)
        assertContentEquals(floatArrayOf(.2f, .3f, .4f, .5f, 0f, 0f), chunk.samples)
        assertNull(segmenter.finish())
    }

    @Test fun `continuous speech crosses the cap without losing samples`() {
        val segmenter = SpeechSegmenter(4, 0, 2, 1)
        val chunks = mutableListOf<SpeechSegmenter.Segment>()
        repeat(3) { chunks += segmenter.accept(floatArrayOf(.1f, .2f, .3f), true) }
        chunks += assertNotNull(segmenter.finish())
        assertEquals(listOf(0L, 4L, 8L), chunks.map { it.startSample })
        assertEquals(listOf(4, 4, 1), chunks.map { it.samples.size })
        assertEquals(List(3) { listOf(.1f, .2f, .3f) }.flatten(), chunks.flatMap { it.samples.toList() })
    }

    @Test fun `brief noise discarded and clear removes previous session`() {
        val segmenter = SpeechSegmenter(10, 2, 2, 2)
        segmenter.accept(floatArrayOf(.5f), true)
        assertTrue(segmenter.accept(floatArrayOf(0f, 0f), false).isEmpty())
        segmenter.accept(floatArrayOf(.8f, .8f), true)
        segmenter.clear()
        assertNull(segmenter.finish())
        segmenter.accept(floatArrayOf(.2f, .2f), true)
        val chunk = assertNotNull(segmenter.finish())
        assertEquals(0L, chunk.startSample)
        assertContentEquals(floatArrayOf(.2f, .2f), chunk.samples)
    }

    @Test fun `rejects invalid audio without changing the clock`() {
        val segmenter = SpeechSegmenter(4, 0, 2, 1)
        assertFailsWith<IllegalArgumentException> { segmenter.accept(floatArrayOf(Float.NaN), true) }
        assertFailsWith<IllegalArgumentException> { segmenter.accept(FloatArray(5), true) }
        segmenter.accept(floatArrayOf(.1f), true)
        assertEquals(0L, assertNotNull(segmenter.finish()).startSample)
    }
}
