package com.sidekick.core

import kotlin.test.*

class ShortLanguageHintTest {
    @Test fun `clear script or multiple grammatical clues select a retry language`() {
        for ((text, expected) in listOf("move the meeting to tomorrow" to SpeechLanguage.ENGLISH,
            "anul la réunion" to SpeechLanguage.FRENCH, "ich nehme morgen um achtzehn uhr teil" to SpeechLanguage.GERMAN,
            "перенеси встречу" to SpeechLanguage.RUSSIAN, "회의를 취소하지 마세요" to SpeechLanguage.KOREAN,
            "明天下午有會議" to SpeechLanguage.CHINESE, "سأحضر الاجتماع" to SpeechLanguage.ARABIC))
            assertEquals(expected, ShortLanguageHint.identify(text))
    }
    @Test fun `names lone words and mixed scripts do not force a recognition language`() {
        for (text in listOf("The James", "meeting", "", "12345", "회의가 있어요 明天有聚會", "the réunion", "明日会議", "Зустріч завтра"))
            assertNull(ShortLanguageHint.identify(text), text)
    }
    @Test fun `bilingual phrases and foreign venue names keep unrestricted recognition`() {
        for (text in listOf("내일 회의를 cancel", "회의를 The James에서 할게요", "明天下午会议 at The James",
            "перенеси встречу tomorrow", "회의를 明天 변경해줘", "I'll move the meeting tomorrow à la réunion"))
            assertNull(ShortLanguageHint.identify(text), text)
    }
    @Test fun `short CJK fallback protects other scripts and spoken Latin names`() {
        assertTrue(ShortLanguageHint.cjkFallback("所以我学了拍森",24000))
        for(text in listOf("哦 مرحبا technical","明日かな","내일 привет","hello"))assertFalse(ShortLanguageHint.cjkFallback(text,24000))
        assertFalse(ShortLanguageHint.cjkFallback("회의가 있어요",80000))
        for(text in listOf("내일 오후 3시에 meeting 있어요","회의가 있어요","Sarah랑 lunch 먹을 거예요"))assertTrue(ShortLanguageHint.koreanSpeech(text))
        for(text in listOf("hello meeting tomorrow","明天开会","내일 привет","내 meeting"))assertFalse(ShortLanguageHint.koreanSpeech(text))
        // Real SenseVoice readings of the bilingual fixtures drop the English venue at either edge.
        assertEquals("at the james 내일 오후 6시에 회의가 있어요.",
            ShortLanguageHint.restoreLatinEdges("at the james 내일 오후 여섯시에 회의가 있어요", "내일 오후 6시에 회의가 있어요."))
        assertEquals("내일 오후 6시에 회의가 있어요. at the james",
            ShortLanguageHint.restoreLatinEdges("내일 오후 여섯시에 회의가 있어요 at the james", "내일 오후 6시에 회의가 있어요."))
        // Kept English is not duplicated; English inside Korean is never spliced in.
        assertEquals("at the james 내일 회의", ShortLanguageHint.restoreLatinEdges("at the james 내일 회의", "at the james 내일 회의"))
        val middle = ShortLanguageHint.restoreLatinEdges("금요일 오전 열시에 dentist appointment 있어요", "금요일 오전 10시에 있어요")
        assertEquals("금요일 오전 10시에 있어요", middle)
        assertFalse(ShortLanguageHint.preservesLatin("금요일 오전 열시에 dentist appointment 있어요", middle))
        // Live Pixel run: SenseVoice rewrote "twelve thirty" as "1230"; restoring the edge duplicated the whole sentence.
        val live = "I'm meeting with Sarah for lunch at 1230 on Monday 월요일 점심 약속이에요."
        assertEquals(live, ShortLanguageHint.restoreLatinEdges("i'm meeting with sarah for lunch at twelve thirty on monday 월요일 점심 약속이에요", live))
        // Second live run: these stayed with Omnilingual because dominance was counted in letters.
        assertTrue(ShortLanguageHint.koreanDominant("금요일 오전 열시에 dentist appointment 있어요"))
        assertTrue(ShortLanguageHint.koreanDominant("내일 오후 여섯시에 the james 에서 회의가 있어요"))
        assertFalse(ShortLanguageHint.koreanDominant("i'm meeting with sarah for lunch at twelve thirty on monday 월요일 점심 약속이에요"))
        assertTrue(ShortLanguageHint.latinOnly("an tuse a three p m ive a team meeting downtown"))
        assertTrue(ShortLanguageHint.latinOnly("Demain à 20 heures, réunion."))
        for (text in listOf("내일 meeting", "明天 meeting", "ok", "12:30")) assertFalse(ShortLanguageHint.latinOnly(text), text)
        // Live Pixel line 4: the Whisper reading must win over Omnilingual's when the time agrees.
        assertTrue(RecognitionRetry.select("an tuse a three p m ive a team meeting downtown", "On Tuesday at 3 p.m. I have a team meeting downtown.").accepted)
        assertFalse(ShortLanguageHint.keepsKorean("금요일 오전 열시에 dentist appointment 있어요", "Knu shared dentist appointment Io."))
        assertTrue(ShortLanguageHint.keepsKorean("내일 오 여섯시에 the james 에서 회의가 있어요", "내일 오후 6시에 회의가 있어요."))
        assertFalse(ShortLanguageHint.preservesLatin("회의 at The James","회의 at the jam"))
        assertTrue(ShortLanguageHint.preservesLatin("atthejames明天有会","at the James 明天有会"))
    }
}
