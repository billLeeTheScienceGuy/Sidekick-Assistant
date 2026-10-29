package com.sidekick.core

import kotlin.test.*

class SpeechLanguageTest {
    @Test fun `requested languages choose multilingual model and unknown settings fall back safely`() {
        assertEquals(setOf("en", "auto", "ko", "zh", "fr", "de", "ru", "ar"), SpeechLanguage.entries.map { it.code }.toSet())
        SpeechLanguage.entries.forEach { assertEquals(it, SpeechLanguage.fromCode(it.code)) }
        assertFalse(SpeechLanguage.ENGLISH.multilingual)
        assertTrue(SpeechLanguage.entries.filter { it != SpeechLanguage.ENGLISH }.all { it.multilingual })
        assertEquals(SpeechLanguage.ENGLISH, SpeechLanguage.fromCode("invalid"))
    }
}
