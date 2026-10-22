package com.sidekick.core

import java.time.Instant
import java.time.ZoneId
import kotlin.test.*

class PipelineTest {
    private val pipeline = CommitmentPipeline(RuleIntentExtractor())
    private fun input(text: String) = Transcript(text, Instant.parse("2026-10-22T01:00:00Z"), ZoneId.of("America/Chicago"), Speaker.USER, true)

    @Test fun `keeps evidence and time context without inventing a deadline`() {
        val source = input("I'll call Emma tomorrow at five.")
        val item = pipeline.process(source).single()
        assertEquals("Call Emma tomorrow at five", item.title)
        assertEquals(source.capturedAt, item.capturedAt)
        assertEquals(source.zoneId, item.zoneId)
        assertEquals("I'll call Emma tomorrow at five", item.evidence)
    }
    @Test fun `rejects completed hypothetical quoted and third party speech`() {
        listOf("I already called Emma.", "Maybe I'll call Emma.", "I'll call Emma if I can.",
            "I will not call Emma.", "She said I'll call Emma.", "\"I'll call Emma\"", "Emma will call.")
            .forEach { assertTrue(pipeline.process(input(it)).isEmpty(), it) }
    }
    @Test fun `consent and ownership gate even a permissive extractor`() {
        var invoked = false
        val guarded = CommitmentPipeline(object : IntentExtractor {
            override fun extract(transcript: Transcript): List<ProposedCommitment> { invoked = true; return emptyList() }
        })
        guarded.process(input("I'll call Emma").copy(processingAllowed = false))
        guarded.process(input("I'll call Emma").copy(speaker = Speaker.UNKNOWN))
        guarded.process(input("I'll call Emma").copy(speaker = Speaker.OTHER))
        assertFalse(invoked)
    }
    @Test fun `handles curly apostrophes and deduplicates within a transcript`() {
        assertEquals(1, pipeline.process(input("I’ll send the report. I'll send the report.")).size)
    }
    @Test fun `bounds incoming text`() {
        assertFailsWith<IllegalArgumentException> { pipeline.process(input("x".repeat(10_001))) }
    }
    @Test fun `ring preserves newest samples and wipes on clear`() {
        val ring = PcmRingBuffer(4)
        ring.append(shortArrayOf(1, 2))
        assertContentEquals(shortArrayOf(1, 2), ring.snapshot())
        ring.append(shortArrayOf(3, 4, 5, 6, 7))
        assertContentEquals(shortArrayOf(4, 5, 6, 7), ring.snapshot())
        ring.clear()
        assertEquals(0, ring.size)
        ring.append(shortArrayOf(8))
        assertContentEquals(shortArrayOf(8), ring.snapshot())
    }
    @Test fun `ring rejects invalid sizes`() {
        assertFailsWith<IllegalArgumentException> { PcmRingBuffer(0) }
        assertFailsWith<IllegalArgumentException> { PcmRingBuffer(3).append(shortArrayOf(1), 2) }
    }
}
