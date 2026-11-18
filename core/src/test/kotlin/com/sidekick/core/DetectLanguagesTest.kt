package com.sidekick.core

import com.sidekick.core.SpeechLanguage.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DetectLanguagesTest {
    private val all = SpeechLanguage.entries.filter { it != AUTO }.toSet()

    @Test fun everythingOnChangesNothing() {
        assertNull(DetectLanguages.fallback("Привет, как дела?", all))
    }

    @Test fun aLanguageThatIsOffIsReadAgainInOneThatIsOn() {
        val koreanEnglish = setOf(KOREAN, ENGLISH)
        assertEquals(ENGLISH, DetectLanguages.fallback("Привет, как дела у тебя сегодня?", koreanEnglish))
        assertEquals(KOREAN, DetectLanguages.fallback("我明天有个会议", setOf(KOREAN)))
        assertNull(DetectLanguages.fallback("내일 오후 세 시에 회의가 있어요", koreanEnglish))
        assertNull(DetectLanguages.fallback("I have a meeting tomorrow at three", koreanEnglish))
        assertNull(DetectLanguages.fallback("", koreanEnglish))
    }

    @Test fun oneLanguageMeansNoGuessing() {
        assertEquals(KOREAN, DetectLanguages.single(setOf(KOREAN)))
        assertNull(DetectLanguages.single(setOf(KOREAN, ENGLISH)))
    }
}
