package com.sidekick.core

import java.time.*
import kotlin.test.*

class ArabicActivitiesTest {
    private val now = Instant.parse("2026-10-28T14:00:00Z")
    private val zone = ZoneId.of("America/New_York")
    private fun extract(text: String, speaker: Speaker = Speaker.USER) = ActivityTiming.extract(Transcript(text, now, zone, if (speaker == Speaker.UNKNOWN) Speaker.OTHER else speaker, true), speaker != Speaker.USER)
    @Test fun `Arabic words numerals and diacritics resolve while evidence stays original`() {
        assertFalse(extract("سأحضر اجتماعًا غدا الساعة 6 مساء.").single().informational)
        val split = extract("سَأَحْضُرُ الِاجْتِمَاعَ غَدّا. السَّاعَةَ السَّادِسَةُ مَسَاء.").single()
        assertEquals(18, split.activityStartAt!!.atZone(zone).hour)
        assertEquals(2, split.evidence.lines().size)
        for (clock in listOf("٨ مساءً", "8 مساء", "الثامنة مساءً", "٢٠:٠٠")) {
            val text = "سأحضر حفلة غدًا الساعة $clock في قاعة النور."
            val item = extract(text).single()
            assertEquals(20, item.activityStartAt!!.atZone(zone).hour, text)
            assertFalse(item.informational); assertEquals(text, item.evidence)
            assertEquals("قاعة النور", item.placeName)
        }
    }
    @Test fun `ambiguous conflicting negative and reported plans never become personal guesses`() {
        for (text in listOf("سأحضر حفلة غدا الساعة 8.", "لن أحضر حفلة غدا الساعة 8 مساء.", "هل سأحضر حفلة غدا الساعة 8 مساء؟", "سأحضر حفلة غدا اليوم الساعة 8 مساء.", "سأحضر حفلة غدا الساعة 18 مساء.")) assertTrue(extract(text).isEmpty(), text)
        assertTrue(extract("قال علي سأحضر حفلة غدا الساعة 8 مساء.").single().informational)
        assertTrue(extract("سأحضر حفلة غدا الساعة 8 مساء.", Speaker.UNKNOWN).single().informational)
    }
    @Test fun `midnight ending and duration preserve next day`() {
        val combined = extract("سأحضر حفلة غدا الساعة 8 مساء وتنتهي عند منتصف الليل.").single()
        assertEquals(4, Duration.between(combined.activityStartAt, combined.activityEndAt).toHours())
        val item = extract("سأحضر حفلة غدا الساعة 8 مساء. تنتهي عند منتصف الليل.").single()
        assertEquals(4, Duration.between(item.activityStartAt, item.activityEndAt).toHours())
        val duration = extract("سأحضر حفلة غدا الساعة 8 مساء. تستمر لمدة ساعتين.").single()
        assertEquals(2, Duration.between(duration.activityStartAt, duration.activityEndAt).toHours())
    }
    @Test fun `explicit corrections retain matching and reject unsafe alternatives`() {
        val start = now.atZone(zone).toLocalDate().plusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val move = EventChange.parse("انقل الاجتماع إلى غدا الساعة 7 مساء.")!!
        assertEquals(19, move.newStart(start, now, zone)!!.atZone(zone).hour)
        assertTrue(move.matches("회의")); assertTrue(EventChange.parse("ألغي الاجتماع.")!!.cancel)
        assertTrue(EventChange.looksLikeChange("ألغي الاجتماع."))
        assertNull(EventChange.parse("لا تلغي الاجتماع."))
        assertNull(move.copy(timing = "غدا الساعة 7 مساء او 8 مساء").newStart(start, now, zone))
        assertFalse(EventChange.parse("ألغي اجتماع علي.")!!.matches("اجتماع سارة"))
    }
}
