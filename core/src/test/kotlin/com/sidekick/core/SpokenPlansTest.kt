package com.sidekick.core

import java.time.Instant
import java.time.ZoneId
import kotlin.test.*

class SpokenPlansTest {
    private val captured = Instant.parse("2026-11-08T15:00:00Z")
    private val zone = ZoneId.of("America/New_York")
    private fun source(text: String, speaker: Speaker = Speaker.USER) = Transcript(text, captured, zone, speaker, true)

    @Test fun `explicit spelled clocks preserve evidence and resolve ending`() {
        val text = "I'll attend the party tomorrow at eight in the evening. It ends at twelve at night."
        val plan = AnalysisContext.local(source(text)).single()
        assertEquals(Instant.parse("2026-11-10T01:00:00Z"), plan.activityStartAt)
        assertEquals(Instant.parse("2026-11-10T05:00:00Z"), plan.activityEndAt)
        assertFalse(plan.informational)
        assertTrue(plan.evidence.contains("eight in the evening"))
        assertEquals(plan.activityStartAt, TimeResolver.suggest("Call tomorrow at eight in the evening", captured, zone))
        assertNull(TimeResolver.suggest("Call tomorrow at one at night", captured, zone))
    }

    @Test fun `spoken alternatives and uncertain heading never create timed personal plans`() {
        for (text in listOf("I'll attend the party tomorrow at eight or nine in the evening.",
            "I'll attend the party tomorrow at eight to nine pm.",
            "I'm heading to Maya's birthday tomorrow at 6pm if I can.")) {
            assertTrue(AnalysisContext.local(source(text)).none { !it.informational && it.activityStartAt != null }, text)
            if (text.contains("eight")) assertNull(TimeResolver.suggest(text, captured, zone))
        }
        assertFalse(AnalysisContext.local(source("I'm heading to Maya's birthday tomorrow at 6pm.")).single().informational)
    }

    @Test fun `split spoken clock keeps the supplied date and original words`() {
        val plan = AnalysisContext.local(source("I'll attend the party. Tomorrow at eight in the evening.")).single()
        assertEquals(Instant.parse("2026-11-10T01:00:00Z"), plan.activityStartAt)
        assertFalse(plan.informational)
        assertTrue(plan.evidence.contains("Tomorrow at eight in the evening."))
    }

    @Test fun `appointments have explicit ownership without borrowing a third person`() {
        val personal = listOf("J'ai rendez-vous demain à 14 heures.", "Ich habe morgen um 14 Uhr einen Termin.",
            "У меня завтра приём в 14:00.", "عندي موعد غدا الساعة الثالثة مساء.",
            "Je compte aller à l'anniversaire demain à huit heures du soir.", "我打算明晚八点参加聚会。")
        for (text in personal) {
            val plan = AnalysisContext.local(source(text)).single()
            assertFalse(plan.informational, text)
            assertNotNull(plan.activityStartAt, text)
            assertTrue(ActivityTiming.extract(source(text, Speaker.OTHER), true).all { it.informational }, text)
            assertTrue(AnalysisContext.local(source(text, Speaker.UNKNOWN)).isEmpty(), text)
        }
        for (text in listOf("Ich habe eine Freundin mit einem Termin morgen um 14 Uhr.",
            "У меня друг идёт на приём завтра в 14:00.", "Je ne compte pas aller à l'anniversaire demain à huit heures du soir.",
            "ليس عندي موعد غدا الساعة الثالثة مساء.")) {
            assertTrue(AnalysisContext.local(source(text)).none { !it.informational }, text)
        }
    }

    @Test fun `German evening attendance is explicit but midnight and hypothetical plans stay unknown`() {
        for (hour in listOf("acht", "acht Uhr", "20 Uhr")) {
            val text = "Ich bin morgen Abend um $hour bei Annas Geburtstag dabei."
            val plan = AnalysisContext.local(source(text)).single()
            assertEquals(Instant.parse("2026-11-10T01:00:00Z"), plan.activityStartAt, text)
            assertFalse(plan.informational, text)
        }
        for (text in listOf("Ich bin morgen Abend um zwölf bei Annas Geburtstag dabei.",
            "Ich bin morgen Abend um acht bei Annas Geburtstag vielleicht dabei.")) {
            assertTrue(AnalysisContext.local(source(text)).none { !it.informational && it.activityStartAt != null }, text)
        }
        assertTrue(ActivityTiming.extract(source("Ich bin morgen Abend um acht bei Annas Geburtstag dabei.", Speaker.OTHER), true).single().informational)
    }

    @Test fun `Arabic twelve at night is midnight and small ambiguous night hours are not afternoon guesses`() {
        val midnight = AnalysisContext.local(source("سأحضر حفلة غدا الساعة الثانية عشرة ليلا.")).single()
        assertEquals(Instant.parse("2026-11-09T05:00:00Z"), midnight.activityStartAt)
        assertTrue(AnalysisContext.local(source("سأحضر حفلة غدا الساعة الثانية ليلا.")).none { it.activityStartAt != null })
    }
}
