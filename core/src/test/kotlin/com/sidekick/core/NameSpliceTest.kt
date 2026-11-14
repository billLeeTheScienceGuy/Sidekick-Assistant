package com.sidekick.core

import kotlin.test.*

class NameSpliceTest {
    private val names = NameSplice.parse("Sarah 세라 사라\nJessica 제시카, Daniel 다니엘\nBrandon 브랜든; Justin 저스틴\nHannah 해나 한나, Ashley 애슐리")

    @Test fun `list parses English names with Korean spellings`() {
        assertEquals(NameSplice.Name("Sarah", listOf("세라", "사라")), names.first())
        assertEquals(7, names.size)
        assertTrue(NameSplice.parse("Sarah").isEmpty()) // no Korean spelling: nothing for the Korean model to look for
        assertEquals("세라\n사라\n제시카", NameSplice.hotwords(names).lines().take(3).joinToString("\n"))
    }
    @Test fun `names from the Korean model are spliced into SenseVoice's sentence`() {
        // Real pairs from the names benchmark: SenseVoice reading, Zipformer-with-list reading.
        assertEquals("Jessica 한테 오후 3 시에 전화 해야 돼요.", NameSplice.apply("제카 한테 오후 3 시에 전화 해야 돼요.", "제시카한테오후 3시에전화해야돼요.", names))
        assertEquals("아까 Daniel가 회의 시간을 바꿨어요.", NameSplice.apply("아까 다니일엘가 회의 시간을 바꿨어요.", "아까다니엘과회의시간을바꿨어요.", names))
        assertEquals("토요일 점심에 Brandon 내 집에 가요.", NameSplice.apply("토요일 점심에 브랜드 내 집에 가요.", "토요일점심에브랜든네집에가요.", names))
        assertEquals("아까 Justin과 회의 시간을 바꿨어요.", NameSplice.apply("아까 저스팅과 회의 시간을 바꿨어요.", "아까저스틴가회의시간을바꿨어요.", names))
        assertEquals("오늘 밤에Hannah한테 문자 보내야 해요.", NameSplice.apply("오늘 밤에혜나한테 문자 보내야 해요.", "오늘밤에해나한테문자보내야해요.", names))
        assertEquals("내일 저녁에 Ashley랑 같이 밥 먹기로 했어요.", NameSplice.apply("내일 저녁에 에슐리랑 같이 밥 먹기로 했어요.", "내일저녁에애슐리랑같이밥먹기로했어요.", names))
    }
    @Test fun `nothing changes without a listed name or when the sentence already has it`() {
        val plain = "내일 오후 6시에 잠실에서 회의가 있어요."
        assertEquals(plain, NameSplice.apply(plain, "내일오후6시에잠실에서회의가있어요.", names))
        assertEquals("I'm meeting Sarah for lunch", NameSplice.apply("I'm meeting Sarah for lunch", "세라", names))
        // A listed spelling that is part of another word is not a name: 사라졌어요 means "disappeared".
        assertEquals("열쇠가 사라졌어요.", NameSplice.apply("열쇠가 사라졌어요.", "열쇠가사라졌어요.", names))
        // The Korean model put the name somewhere else entirely: no splice.
        assertEquals("제가 내일 저녁 8시에 셀와 생일 파티에 갈거.", NameSplice.apply("제가 내일 저녁 8시에 셀와 생일 파티에 갈거.", "세라는 오늘 안 와요", names))
    }
    @Test fun `listed Korean spellings are shown as the typed name`() {
        assertEquals("Sarah랑 만나요. Sarah 생일이에요.", NameSplice.normalize("세라랑 만나요. 세라 생일이에요.", names))
        assertEquals("물건을 사라고 했어요.", NameSplice.normalize("물건을 사라고 했어요.", names))
    }
}
