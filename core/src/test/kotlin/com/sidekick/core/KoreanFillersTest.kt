package com.sidekick.core

import org.junit.Assert.assertEquals
import org.junit.Test

class KoreanFillersTest {
    @Test fun `a lone filler between words is dropped`() {
        assertEquals("내일 오후 6 시에 회의가 있어요.", KoreanFillers.strip("내일 오후 6 시에 회의가 어 있어요."))
        assertEquals("회의를 내일로", KoreanFillers.strip("회의를 음 음 내일로"))
        assertEquals("회의를 내일로 옮겨 줘", KoreanFillers.strip("회의를 으음 내일로 옮겨 줘"))
    }

    @Test fun `fillers at the start, after punctuation, next to other scripts, and inside words stay`() {
        listOf("어, 알았어", "어 그래", "알았어 어", "회의가, 어 있어요", "meeting 어 있어요", "어머니가 오세요", "그 회의는 취소야", "어제 그 회의는 취소야", "가 나").forEach {
            assertEquals(it, KoreanFillers.strip(it))
        }
    }
}
