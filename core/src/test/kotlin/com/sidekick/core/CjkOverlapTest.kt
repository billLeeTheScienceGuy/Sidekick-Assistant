package com.sidekick.core

import kotlin.test.*

class CjkOverlapTest {
    @Test fun `Chinese continuous context removes exact overlap without losing the new end time`() {
        assertEquals("明天下午八点的会议在The James。晚上十点结束。", LiveText.append("明天下午八点的会议在The James。", "八点的会议在The James。晚上十点结束。"))
        assertEquals("明天晚上八点有聚会。", LiveText.append("明天晚上八点有聚会。", "晚上八点有聚会。"))
    }
    @Test fun `spaced or unspaced Korean can overlap while a new utterance keeps a deliberate repeat`() {
        assertEquals("내일오후여덟시에파티가있어요.열시에끝나요.", LiveText.append("내일오후여덟시에파티가있어요.", "파티가있어요.열시에끝나요."))
        assertEquals("내일 파티에 가요.\n내일 파티에 가요.", LiveText.append("내일 파티에 가요.", "내일 파티에 가요.", true))
    }
    @Test fun `short collisions revised numerals and Latin word fragments are not guessed away`() {
        assertEquals("明天开会。 开会地点未知。", LiveText.append("明天开会。", "开会地点未知。"))
        assertEquals("明天晚上八点有聚会。 晚上九点有聚会。", LiveText.append("明天晚上八点有聚会。", "晚上九点有聚会。"))
        assertEquals("Meet at the library. library tomorrow.", LiveText.append("Meet at the library.", "library tomorrow."))
    }
}
