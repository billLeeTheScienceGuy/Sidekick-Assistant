package com.sidekick.core

import com.sidekick.core.SpeechLanguage.*
import com.sidekick.core.SpeechQuality.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class LanguageReadinessTest {
    private val sizes = mapOf("whisper" to 225_643_178L, "sensevoice" to 239_549_735L, "omni" to 365_932_083L)

    @Test fun nothingInstalledOnlyEnglishFasterWorks() {
        assertTrue(LanguageReadiness.status(ENGLISH, FAST, emptySet(), emptySet(), sizes).ready)
        val french = LanguageReadiness.status(FRENCH, ADAPTIVE, emptySet(), emptySet(), sizes)
        assertEquals(setOf("whisper", "omni"), french.download)
        assertEquals(225_643_178L + 365_932_083L, french.downloadBytes)
        assertFalse(french.readyInFaster)
    }

    @Test fun oneSharedFileReadiesManyLanguages() {
        // Whisper alone: every language, and Auto-detect, works in Faster mode.
        assertEquals(listOf(AUTO, KOREAN, CHINESE, FRENCH, GERMAN, RUSSIAN, ARABIC),
            LanguageReadiness.unlocks("whisper", FAST, emptySet(), emptySet()))
        // With Whisper on the phone, Omni makes French, German, Russian, Arabic and Auto-detect ready in Recommended.
        assertEquals(listOf(AUTO, FRENCH, GERMAN, RUSSIAN, ARABIC),
            LanguageReadiness.unlocks("omni", ADAPTIVE, setOf("whisper"), setOf("whisper")))
        assertEquals(listOf(KOREAN, CHINESE), LanguageReadiness.unlocks("sensevoice", ADAPTIVE, setOf("whisper"), setOf("whisper")))
    }

    @Test fun onlyMissingFilesAreAskedFor() {
        val russian = LanguageReadiness.status(RUSSIAN, ADAPTIVE, setOf("whisper"), setOf("whisper"), sizes)
        assertEquals(setOf("omni"), russian.download)
        assertTrue(russian.readyInFaster)
    }

    @Test fun aSwitchedOffFileNeedsNoDownload() {
        val korean = LanguageReadiness.status(KOREAN, ADAPTIVE, setOf("sensevoice"), emptySet(), sizes)
        assertEquals(emptySet(), korean.download)
        assertEquals(setOf("sensevoice"), korean.switchOn)
        assertEquals(0L, korean.downloadBytes)
    }

    @Test fun removingAFileShowsWhatStopsAndWhatFallsBackToFaster() {
        val all = setOf("whisper", "omni", "sensevoice")
        val omni = LanguageReadiness.impact("omni", ADAPTIVE, all, all)
        // Without Omni nothing stops: Whisper still serves these languages in Faster mode.
        assertEquals(emptyList(), omni.stops)
        assertEquals(listOf(AUTO, FRENCH, GERMAN, RUSSIAN, ARABIC), omni.fasterOnly)
        // Without Whisper: Recommended English/French/German/Russian stop; English still works in Faster (built in).
        val whisper = LanguageReadiness.impact("whisper", ADAPTIVE, all, all)
        assertEquals(listOf(FRENCH, GERMAN, RUSSIAN), whisper.stops)
        assertEquals(listOf(ENGLISH), whisper.fasterOnly)
        // Switching off SenseVoice: Korean and Chinese fall back to Faster mode (Whisper).
        assertEquals(listOf(KOREAN, CHINESE), LanguageReadiness.impact("sensevoice", ADAPTIVE, all, all).fasterOnly)
    }
}
