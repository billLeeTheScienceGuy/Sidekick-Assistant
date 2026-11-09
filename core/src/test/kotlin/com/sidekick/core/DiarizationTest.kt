package com.sidekick.core

import kotlin.test.*

class DiarizationTest {
    @Test fun `speaker partitions cover every sample and preserve distinct turns`() {
        val plan = Diarization.partition(160_000, listOf(SpeakerSpan(1000, 80_000, 0), SpeakerSpan(80_000, 155_000, 1)))
        assertEquals(listOf(SpeakerPart(0, 80_000, 0, false), SpeakerPart(80_000, 160_000, 1, false)), plan)
    }
    @Test fun `overlap and short fragments stay unassigned without losing audio`() {
        val plan = Diarization.partition(160_000, listOf(SpeakerSpan(0, 96_000, 0), SpeakerSpan(64_000, 160_000, 1)))
        assertEquals(SpeakerPart(64_000, 96_000, null, true), plan[1])
        val fragmented = Diarization.partition(160_000, listOf(SpeakerSpan(0, 79_000, 0), SpeakerSpan(81_000, 160_000, 1)))
        assertEquals(0, fragmented.first().start); assertEquals(160_000, fragmented.last().end)
        assertTrue(fragmented.zipWithNext().all { (a,b) -> a.end == b.start })
        assertTrue(fragmented.any { it.ambiguous })
    }
    @Test fun `invalid excessive or absent segmentation cannot grant a speaker`() {
        for (spans in listOf(emptyList(), listOf(SpeakerSpan(-1, 100, 0)), listOf(SpeakerSpan(0, 170_000, 0))))
            assertEquals(listOf(SpeakerPart(0, 160_000, null, true)), Diarization.partition(160_000, spans))
        val rapid = (0 until 8).map { SpeakerSpan(it * 20_000, (it + 1) * 20_000, it % 2) }
        assertTrue(Diarization.partition(160_000, rapid).all { it.ambiguous })
    }
    @Test fun `anonymous voices remain stable but never merge ambiguous vectors or survive expiry`() {
        fun vector(a: Float, b: Float) = FloatArray(192).apply { this[0] = a; this[1] = b }
        val tracker = SessionVoices("test")
        val first = tracker.observe(vector(1f, 0f), 0)!!
        val second = tracker.observe(vector(0f, 1f), 0)!!
        assertNotEquals(first.key, second.key)
        assertEquals(first, tracker.observe(vector(.99f, .01f), 1000))
        assertNull(tracker.observe(vector(1f, 1f), 1000))
        val fresh = tracker.observe(vector(1f, 0f), 901_001)!!
        assertNotEquals(first.key, fresh.key)
        tracker.clear()
        assertNotEquals(fresh.key, tracker.observe(vector(1f, 0f), 901_002)!!.key)
    }
    @Test fun `anonymous tracker is bounded and sources cannot alias old untracked voices`() {
        val tracker = SessionVoices("bounded")
        repeat(8) { i -> assertNotNull(tracker.observe(FloatArray(192).apply { this[i] = 1f }, 0)) }
        assertNull(tracker.observe(FloatArray(192).apply { this[8] = 1f }, 0))
        assertEquals("spk_test_1", VoiceSource.track("voice:2:spk_test_1:OTHER"))
        assertNull(VoiceSource.track("voice:2:OTHER")); assertNull(VoiceSource.track("voice:2:spk_/bad:OTHER"))
    }
}
