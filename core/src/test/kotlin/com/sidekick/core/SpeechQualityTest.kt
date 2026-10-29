package com.sidekick.core

import kotlin.test.*

class SpeechQualityTest {
    @Test fun `Omnilingual is adaptive only and routing preserves explicit choices`() {
        assertTrue(SpeechQuality.ADAPTIVE.usesOmni(SpeechLanguage.ARABIC, true))
        assertTrue(SpeechQuality.ADAPTIVE.usesOmni(SpeechLanguage.ARABIC, false))
        for (language in listOf(SpeechLanguage.FRENCH, SpeechLanguage.GERMAN)) {
            assertTrue(SpeechQuality.ADAPTIVE.usesOmni(language, false))
            assertFalse(SpeechQuality.ADAPTIVE.usesOmni(language, true))
        }
        for (language in SpeechLanguage.entries) for (quality in listOf(SpeechQuality.FAST, SpeechQuality.ACCURATE))
            assertFalse(quality.usesOmni(language, false))
        assertFalse(SpeechQuality.ADAPTIVE.usesOmni(SpeechLanguage.KOREAN, true))
    }
    @Test fun `SenseVoice is used for adaptive Korean and Chinese while explicit preferences stay on Whisper`() {
        assertTrue(SpeechQuality.ADAPTIVE.usesSenseVoice(SpeechLanguage.KOREAN))
        assertTrue(SpeechQuality.ADAPTIVE.usesSenseVoice(SpeechLanguage.CHINESE))
        assertFalse(SpeechQuality.ADAPTIVE.usesSenseVoice(SpeechLanguage.ARABIC))
        assertFalse(SpeechQuality.FAST.usesSenseVoice(SpeechLanguage.KOREAN))
        assertFalse(SpeechQuality.ACCURATE.usesSenseVoice(SpeechLanguage.KOREAN))
        assertFalse(SpeechQuality.ADAPTIVE.usesSenseVoice(SpeechLanguage.ENGLISH))
        assertFalse(SpeechQuality.ADAPTIVE.usesSenseVoice(SpeechLanguage.AUTO))
    }
    @Test fun `adaptive boosts English short phrases and keeps continuous and other languages fast`() {
        assertEquals(SpeechQuality.ACCURATE, SpeechQuality.ADAPTIVE.model(SpeechLanguage.ENGLISH, true))
        assertEquals(SpeechQuality.FAST, SpeechQuality.ADAPTIVE.model(SpeechLanguage.ENGLISH, false))
        for (language in listOf(SpeechLanguage.FRENCH, SpeechLanguage.RUSSIAN)) {
            assertEquals(SpeechQuality.ACCURATE, SpeechQuality.ADAPTIVE.model(language, true))
            assertEquals(512, SpeechQuality.ADAPTIVE.liveContext(language, true))
            assertEquals(0, SpeechQuality.ADAPTIVE.liveContext(language, false))
        }
        assertEquals(0, SpeechQuality.FAST.liveContext(SpeechLanguage.GERMAN, true))
        for (language in SpeechLanguage.entries.filter { it !in setOf(SpeechLanguage.ENGLISH, SpeechLanguage.FRENCH, SpeechLanguage.RUSSIAN) })
            assertEquals(SpeechQuality.FAST, SpeechQuality.ADAPTIVE.model(language, true))
    }
    @Test fun `explicit preferences remain unchanged and adaptive is the new default`() {
        assertEquals(SpeechQuality.ADAPTIVE, SpeechQuality.fromName(null))
        for (quality in listOf(SpeechQuality.FAST, SpeechQuality.ACCURATE)) {
            assertEquals(quality, SpeechQuality.fromName(quality.name))
            assertEquals(quality, quality.model(SpeechLanguage.ENGLISH, true))
            assertEquals(quality, quality.model(SpeechLanguage.ENGLISH, false))
        }
    }
}
