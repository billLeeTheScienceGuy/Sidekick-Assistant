package com.sidekick.core

import kotlin.test.*

class PhraseFilterTest {
    @Test fun `music and noise scraps are dropped`() {
        assertTrue(PhraseFilter.isNoise("그.", 20_000))          // music, phone and laptop
        assertTrue(PhraseFilter.isNoise("Loぱ発キ.", 8_140))      // music: Japanese kana
        assertTrue(PhraseFilter.isNoise("bামnাخ", 4_000))        // music: Bengali
        assertTrue(PhraseFilter.isNoise("Angel.", 20_000))
    }
    @Test fun `real speech is kept`() {
        assertFalse(PhraseFilter.isNoise("네 좋아요.", 600))
        assertFalse(PhraseFilter.isNoise("네.", 1_500))                  // short answers are never judged by rate
        assertFalse(PhraseFilter.isNoise("I'll go to the gym tomorrow at seven AM.", 5_600))
        assertFalse(PhraseFilter.isNoise("내일 오후 6시에 jam에서 회의가 있어요.", 9_400))
        assertFalse(PhraseFilter.isNoise("друзья всем привет", 3_000))
        assertFalse(PhraseFilter.isNoise("السلام عليكم ورحمة الله", 3_000))
        // One stray character in otherwise good text (FLEURS code-switched Korean) is kept.
        assertFalse(PhraseFilter.isNoise("인터넷은 m스와 인터프sn c뮤ュ니케션 요서를 모두 가추고 있다", 6_000))
        assertFalse(PhraseFilter.isNoise("", 20_000))
    }
}
