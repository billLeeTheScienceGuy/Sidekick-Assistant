package com.sidekick.core

import kotlin.test.*

class RecognitionRetryTest {
    @Test fun `valid conflicting clocks and numbers never silently replace the first reading`() {
        for ((a,b) in listOf("The party is tomorrow at 8pm" to "The party is tomorrow at 6pm",
            "내일 오후 여덟 시에 파티" to "내일 오후 여섯 시에 파티",
            "réunion demain à dix-huit heures" to "réunion demain à vingt heures",
            "morgen um achtzehn uhr" to "morgen um zwanzig uhr",
            "明天下午八点有会议" to "明天下午六点有会议",
            "Room 18 tomorrow" to "Room 8 tomorrow", "Room 18 at 6pm" to "Room 8 at 18:00",
            "الغرفة 18 غدا الساعة السادسة مساء" to "الغرفة 8 غدا الساعة السادسة مساء",
            "meeting tomorrow at 6pm" to "meeting today at 6pm",
            "내일 오후 6시에 회의" to "오늘 오후 6시에 회의",
            "завтра в восемнадцать часов" to "завтра в двадцать часов",
            "غدا الساعة الثامنة مساء" to "غدا الساعة السادسة مساء")) {
            val result=RecognitionRetry.select(a,b)
            assertEquals(a,result.text); assertFalse(result.accepted); assertTrue(result.uncertain,"$a / $b")
        }
    }
    @Test fun `number formatting and supported invalid Korean clock repair remain usable`() {
        assertTrue(RecognitionRetry.select("tomorrow at six pm", "tomorrow at 6pm").accepted)
        assertTrue(RecognitionRetry.select("tomorrow at 6pm", "tomorrow at 18:00").accepted)
        assertTrue(RecognitionRetry.select("завтра в 18 часов", "завтра в 18:00").accepted)
        assertTrue(RecognitionRetry.select("morgen um achtzehn uhr", "morgen um 18 uhr").accepted)
        val repaired=RecognitionRetry.select("내일 오후 열8시에 파티가 있어요", "내일 오후 8시에 파티가 있어요")
        assertEquals("내일 오후 8시에 파티가 있어요",repaired.text); assertTrue(repaired.accepted)
    }
    @Test fun `negation is protected in both directions and empty or truncated retries are rejected`() {
        for ((a,b) in listOf("Do not cancel the meeting" to "Cancel the meeting", "회의를 취소하지 마세요" to "회의를 취소해줘", "不要取消会议" to "取消会议", "我不能参加会议" to "我能参加会议", "저는 안 가요" to "저는 가요", "N’annule pas la réunion" to "Annule la réunion", "Sag das Treffen nicht ab" to "Sag das Treffen ab", "Не отмени встречу" to "Отмени встречу", "لا تلغي الاجتماع" to "ألغي الاجتماع")) {
            assertTrue(RecognitionRetry.select(a,b).uncertain,a);assertTrue(RecognitionRetry.select(b,a).uncertain,b)
        }
        assertFalse(RecognitionRetry.select("There is a meeting tomorrow", "meeting").accepted)
        assertFalse(RecognitionRetry.select("hello", null).accepted)
        // Live Pixel run: SenseVoice turned "금요일 오전 10시에…" into English-sounding words and lost the time.
        assertFalse(RecognitionRetry.select("금요일 오전 10시에 dentist appointment 있어요", "Knu shared dentist appointment Io.").accepted)
    }
}
