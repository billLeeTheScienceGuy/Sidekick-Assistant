package com.sidekick.core

import java.time.Instant
import java.time.ZoneId
import kotlin.test.*

class AnalysisContextTest {
    @Test fun `long temporary notes validate recent plans without throwing`() {
        val source = input("Background. ".repeat(400) + "I'll attend the party tomorrow at 8pm.")
        assertTrue(source.text.length > 4000)
        assertTrue(AnalysisContext.local(source).any { !it.informational && it.title.contains("party") })
        assertTrue(AnalysisContext.local(input("\"" + source.text + "\"")).none { !it.informational })
    }
    private fun input(text: String) = Transcript(text, Instant.parse("2026-10-27T17:00:00Z"), ZoneId.of("America/Chicago"), Speaker.USER, true)
    @Test fun `known plans do not require a language model`() {
        val source = input("I'll attend workshop tomorrow at 8pm.")
        val local = AnalysisContext.local(source)
        assertTrue(local.isNotEmpty()); assertEquals("", AnalysisContext.unresolved(source, local))
    }
    @Test fun `report and test instructions do not announce real events`() {
        for (text in listOf("Keep your 9pm workshop.", "Repeat I'll attend the workshop tomorrow at 8pm.", "The transcript says there's a party tomorrow at 8pm."))
            assertTrue(AnalysisContext.local(input(text)).isEmpty(), text)
        assertTrue(AnalysisContext.local(input("There's a workshop tomorrow at 8pm.")).isNotEmpty())
        assertTrue(AnalysisContext.local(input("Emma said. I'll water the plants tomorrow.")).isEmpty())
    }
    @Test fun `unresolved first person plans remain available to smart analysis`() {
        val source = input("I'll attend the party tomorrow at 8pm. I intend to organize my bookshelf tomorrow.")
        val unresolved = AnalysisContext.unresolved(source, AnalysisContext.local(source))
        assertTrue(unresolved.contains("bookshelf")); assertFalse(unresolved.contains("party"))
    }
    @Test fun `literal subjects preserve names and meaningful modifiers`() {
        assertEquals(ActivityIdentity.subject("There's a workshop tomorrow at 8pm."), ActivityIdentity.subject("I'll attend the workshop tomorrow at 8 p.m."))
        assertNotEquals(ActivityIdentity.subject("I'll attend Alex's birthday tomorrow at 8pm."), ActivityIdentity.subject("I'll attend Emma's birthday tomorrow at 8pm."))
        assertNotEquals(ActivityIdentity.subject("The meeting with Alex tomorrow at 8pm."), ActivityIdentity.subject("The meeting with Emma tomorrow at 8pm."))
    }
}
