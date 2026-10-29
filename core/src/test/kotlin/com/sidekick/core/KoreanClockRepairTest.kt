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
        for (other in listOf(good.replace("내일", "오늘"), good.replace("파티", "회의"), good.replace("갈", "안 갈"), good.replace("오후", "오전"), good.replace("8시", "8시 30분"), bad))
            assertEquals(bad, KoreanClockRepair.select(bad, other))
    }
}
