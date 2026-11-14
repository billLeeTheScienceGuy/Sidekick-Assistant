package com.sidekick.core

import kotlin.test.*

class KoreanClockRepairTest {
    @Test fun `mixed script clock and repeated ending use guarded acoustic retry`() {
        for (clock in listOf("열8", "열 8", "열여덟")) {
            val captured = "저는 내일 오후 ${clock}시에 파티에 갈 거예요에요."
            assertTrue(KoreanClockRepair.needsRetry(captured), captured)
            assertEquals("저는 내일 오후 8시에 파티에 갈 거예요에요.", KoreanClockRepair.select(captured, "저는 내일 오후 8시에 파티에 갈 거예요."))
            assertEquals(captured, KoreanClockRepair.select(captured, "저는 오늘 오후 8시에 파티에 갈 거예요."))
        }
        assertFalse(KoreanClockRepair.needsRetry("저는 내일 오후 여덟시에 파티에 갈 거예요."))
    }
    private val bad = "저는 내일 오후 18시에 파티에 갈 거예요."
    @Test fun `invalid clock can use independent acoustic reading with identical context`() {
        val good = "저는 내일 오후 여덟 시에 파티에 갈 거예요"
        assertTrue(KoreanClockRepair.needsRetry(bad))
        assertEquals("저는 내일 오후 여덟 시에 파티에 갈 거예요.", KoreanClockRepair.select(bad, good))
        assertEquals("오후 18시", KoreanClockRepair.clockText(bad))
    }
    @Test fun `valid clocks identities negations dates and minutes cannot be rewritten`() {
        val good = bad.replace("18", "8")
        assertFalse(KoreanClockRepair.needsRetry(good))
        assertFalse(KoreanClockRepair.needsRetry(bad + "?"))
        assertFalse(KoreanClockRepair.needsRetry("“$bad”"))
        assertEquals(good, KoreanClockRepair.select(good, bad))
        for (other in listOf(good.replace("내일", "오늘"), good.replace("갈", "안 갈"), good.replace("오후", "오전"), good.replace("8시", "8시 30분"), bad))
            assertEquals(bad, KoreanClockRepair.select(bad, other))
        // Other wording differs: only the hour may change, the original wording stays.
        assertEquals(good, KoreanClockRepair.select(bad, good.replace("파티", "회의")))
    }
    @Test fun `unresolved clocks offer likely readings for confirmation only`() {
        assertEquals(listOf("제가 내일 저녁 8시에 셀와 생일 파티에 갈거.", "제가 내일 저녁 6시에 셀와 생일 파티에 갈거."),
            KoreanClockRepair.likelyReadings("제가 내일 저녁 18시에 셀와 생일 파티에 갈거."))
        assertEquals(listOf("오후 3시에 봐요", "오후 1시에 봐요"), KoreanClockRepair.likelyReadings("오후 13시에 봐요"))
        assertEquals(listOf("밤 11시에 봐요"), KoreanClockRepair.likelyReadings("밤 23시에 봐요"))
        assertEquals(emptyList(), KoreanClockRepair.likelyReadings("저녁 8시에 봐요"))
        assertEquals("저녁 18시", KoreanClockRepair.clockOf("제가 내일 저녁 18시에 갈게요"))
    }
    @Test fun `extra ten from linked speech is dropped when another model hears the hour`() {
        // Owner's Pixel recording of "제가 내일 저녁 8시에 Sarah 생일 파티에 갈 거예요": SenseVoice vs Omnilingual.
        val sense = "제가 내일 저녁 18시에 셀와 생일 파티에 갈거에."
        assertTrue(KoreanClockRepair.needsRetry(sense))
        assertEquals("제가 내일 저녁 8시에 셀와 생일 파티에 갈거에.", KoreanClockRepair.select(sense, "제가 내일 전녁 여덟 시해 세화 생일파티에 갈 거예요"))
        assertEquals("제가 내일 저녁 8시에 셀와 생일 파티에 갈거에.", KoreanClockRepair.select(sense, "제가내일저녁 8시에쌔하생일파티에갈거예요."))
        // Not a dropped ten: 10 o'clock, a different evening hour, another day, or two clocks.
        assertEquals(sense, KoreanClockRepair.select(sense, "제가 내일 저녁 열 시에 세라 생일 파티에 갈 거예요."))
        assertEquals(sense, KoreanClockRepair.select(sense, "제가 내일 저녁 7시에 세라 생일 파티에 갈 거예요."))
        assertEquals(sense, KoreanClockRepair.select(sense, "제가 오늘 저녁 8시에 세라 생일 파티에 갈 거예요."))
        assertEquals(sense, KoreanClockRepair.select(sense, "제가 내일 아침 8시에 세라 생일 파티에 갈 거예요."))
        assertEquals(sense, KoreanClockRepair.select(sense, "8시나 9시에 세라 생일 파티에 갈 거예요."))
        assertFalse(KoreanClockRepair.needsRetry("제가 내일 저녁 8시에 세라 생일 파티에 갈 거예요."))
    }
}
