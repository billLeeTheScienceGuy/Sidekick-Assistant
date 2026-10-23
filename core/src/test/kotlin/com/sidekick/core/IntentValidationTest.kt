package com.sidekick.core

import java.time.Instant
import java.time.ZoneId
import kotlin.test.*

class IntentValidationTest {
    @Test fun `allows normal verb inflections and event attendance without inventing entities`() {
        val text = "I will be going to Louie's birthday tomorrow"
        assertEquals(1, IntentValidation.validate(source(text), listOf(IntentCandidate("Go to Louie's birthday", text, "tomorrow"))).size)
        assertEquals(1, IntentValidation.validate(source(text), listOf(IntentCandidate("Attend Louie's birthday", text, "tomorrow"))).size)
        assertTrue(IntentValidation.validate(source(text), listOf(IntentCandidate("Attend Emma's birthday", text, "tomorrow"))).isEmpty())
        assertTrue(IntentValidation.validate(source(text), listOf(IntentCandidate("Attend Louie's birthday in Chicago", text, "tomorrow"))).isEmpty())
    }
    private fun source(text: String) = Transcript(text, Instant.parse("2026-10-22T12:00:00Z"), ZoneId.of("America/Chicago"), Speaker.USER, true)
    @Test fun `preserves evidence and allows actions outside the rules vocabulary`() {
        val text = "I'll water the plants tomorrow."
        val item = IntentValidation.validate(source(text), listOf(IntentCandidate("Water the plants", text, "tomorrow"))).single()
        assertEquals("Water the plants", item.title)
        assertEquals(text, item.evidence)
        assertEquals(source(text).capturedAt, item.capturedAt)
    }
    @Test fun `rejects invented people evidence and time phrases`() {
        val text = "I'll call Emma tomorrow."
        assertTrue(IntentValidation.validate(source(text), listOf(IntentCandidate("Call Bob", text, "tomorrow"))).isEmpty())
        assertTrue(IntentValidation.validate(source(text), listOf(IntentCandidate("Call Emma", text, "Friday"))).isEmpty())
        assertTrue(IntentValidation.validate(source(text), listOf(IntentCandidate("Call Emma", "I'll call Emma Friday.", "Friday"))).isEmpty())
    }
    @Test fun `rejects quoted conditional negative or unauthorized input`() {
        listOf("I'll call Emma if I can.", "I will not call Emma.", "Maybe I'll call Emma.").forEach {
            assertTrue(IntentValidation.validate(source(it), listOf(IntentCandidate("Call Emma", it, ""))).isEmpty())
        }
        val quote = "I'll call Emma tomorrow."
        assertTrue(IntentValidation.validate(source("Maybe $quote"), listOf(IntentCandidate("Call Emma", quote, "tomorrow"))).isEmpty())
        assertTrue(IntentValidation.validate(source("I'll call Emma if I can."), listOf(IntentCandidate("Call Emma", "I'll call Emma", ""))).isEmpty())
        assertTrue(IntentValidation.validate(source("Emma said: $quote"), listOf(IntentCandidate("Call Emma", quote, "tomorrow"))).isEmpty())
        assertTrue(IntentValidation.validate(source(quote).copy(speaker = Speaker.OTHER), listOf(IntentCandidate("Call Emma", quote, "tomorrow"))).isEmpty())
        assertTrue(IntentValidation.validate(source(quote).copy(processingAllowed = false), listOf(IntentCandidate("Call Emma", quote, "tomorrow"))).isEmpty())
    }
}
