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
}
