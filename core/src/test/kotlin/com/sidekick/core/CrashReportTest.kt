package com.sidekick.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashReportTest {
    private val day = 86_400_000L

    @Test fun reportDropsTheExceptionMessageAndKeepsOnlyTechnicalFacts() {
        val secret = "Call Emma Johnson tomorrow at 5 pm about the clinic in Madison"
        val error = IllegalStateException(secret, IllegalArgumentException("Voice sample of Emma"))
        val text = CrashReport.build(1_700_000_000_000, "1.2.3 (45)", "14 (API 34)", "Pixel 8", "Plans", error)
        assertFalse(secret in text); assertFalse("Emma" in text); assertFalse("Madison" in text)
        assertTrue("java.lang.IllegalStateException" in text)
        assertTrue("Caused by: java.lang.IllegalArgumentException" in text)
        assertTrue("App version: 1.2.3 (45)" in text); assertTrue("Android: 14 (API 34)" in text)
        assertTrue("Device: Pixel 8" in text); assertTrue("Screen: Plans" in text)
        assertTrue("CrashReportTest" in text) // class, method and line only
        assertTrue("2023-11-14T22:13:20Z" in text)
    }

    @Test fun labelsCannotCarryASentenceWithPunctuationOrQuotes() {
        assertEquals("Pixel 8 Pro", CrashReport.clean("Pixel 8 Pro"))
        assertEquals("Hello quoted world", CrashReport.clean("Hello \"quoted\", world!"))
        assertEquals("unknown", CrashReport.clean(null))
        assertEquals("unknown", CrashReport.clean("!!!"))
        assertEquals(60, CrashReport.clean("a".repeat(500)).length)
    }

    @Test fun missingScreenIsOmitted() {
        val text = CrashReport.build(0, "1", "14", "Pixel", null, RuntimeException("x"))
        assertFalse("Screen:" in text)
    }

    @Test fun keepsFiveNewestReportsAndDropsOldOnes() {
        val now = 100 * day
        val files = (1..8).map { "r$it" to now - it * day } + ("old" to now - 31 * day)
        val gone = CrashReport.toDelete(files, now)
        assertEquals(setOf("r6", "r7", "r8", "old"), gone.toSet())
        assertEquals(emptyList(), CrashReport.toDelete(listOf("a" to now, "b" to now - day), now))
    }

    @Test fun reportsOlderThanThirtyDaysAreDeletedEvenWhenFew() {
        val now = 100 * day
        assertEquals(listOf("x"), CrashReport.toDelete(listOf("x" to now - 30 * day - 1, "y" to now - 29 * day), now))
    }
}
