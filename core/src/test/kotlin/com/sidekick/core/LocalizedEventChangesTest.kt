package com.sidekick.core

import java.time.Instant
import java.time.ZoneId
import kotlin.test.*

class LocalizedEventChangesTest {
    @Test fun `spoken compound evening hours stay grounded across languages`() {
        for (text in listOf("Déplace la réunion à demain à dix-huit heures.", "Verschiebe das Treffen auf morgen um achtzehn Uhr.", "Перенеси встречу на завтра в восемнадцать часов."))
            assertEquals(18, EventChange.parse(text)!!.newStart(previous, now, zone)!!.atZone(zone).hour, text)
    }
    @Test fun `recognized spacing inside meeting preserves evidence and clock`() {
        val text = "내일 오후 6 시에 회 의가 있 어요."
        val event = ActivityTiming.extract(Transcript(text, now, zone, Speaker.OTHER, true), true).single()
        assertEquals(text, event.evidence)
        assertEquals(18, event.activityStartAt!!.atZone(zone).hour)
        assertEquals("meeting", LocalizedActivities.bareEventKey("회 의"))
        assertNull(LocalizedActivities.bareEventKey("회\n의"))
    }
    @Test fun `garbled earlier clock is not replaced by a later command clock`() {
        val text = "네 우 여섯 시에 회의가 있어요 화일을 내일 오후 7시 를 벽에 대해서"
        assertTrue(ActivityTiming.extract(Transcript(text, now, zone, Speaker.OTHER, true), true).isEmpty())
    }
    private val zone = ZoneId.of("America/Chicago")
    private val now = Instant.parse("2026-10-27T17:00:00Z")
    private val previous = Instant.parse("2026-10-29T01:00:00Z")
    @Test fun `five languages resolve explicit moves without translating their targets`() {
        val cases = listOf(
            "회의를 내일 오후 9시로 변경해줘." to "회의",
            "把会议改到明天晚上9点。" to "会议",
            "Déplace la réunion à demain à 21h." to "réunion",
            "Verschiebe das Treffen auf morgen um 21 Uhr." to "treffen",
            "Перенеси встречу на завтра в 21:00." to "встреча")
        for ((text, target) in cases) {
            assertTrue(EventChange.looksLikeChange(text), text)
            val change = EventChange.parse(text)!!
            assertEquals(target, change.target); assertFalse(change.cancel)
            assertEquals(Instant.parse("2026-10-29T02:00:00Z"), change.newStart(previous, now, zone), text)
            assertTrue(change.matches("$target tomorrow"))
        }
    }
    @Test fun `five cancellation forms and pronouns are explicit`() {
        for (text in listOf("회의를 취소해줘.", "取消会议。", "Annule la réunion.", "Das Treffen ist abgesagt.", "Отмени встречу.")) {
            assertTrue(EventChange.looksLikeChange(text), text)
            assertTrue(EventChange.parse(text)!!.cancel, text)
        }
        for (text in listOf("그것을 취소해줘.", "取消它。", "Annule ça.", "Sag das ab.", "Отмени это."))
            assertTrue(EventChange.parse(text)!!.pronoun, text)
        assertTrue(EventChange.looksLikeChange("La réunion est annulée."))
        assertTrue(EventChange.parse("La réunion est annulée.")!!.cancel)
        assertTrue(EventChange.parse("회의를 취소해 줘.")!!.cancel)
        assertNotNull(EventChange.parse("회의를 내일 오후 9시로 변경해 주세요."))
        assertTrue(EventChange.parse("회의 를 취소 해 줘.")!!.cancel)
        assertNotNull(EventChange.parse("회의 를 내일 오후 7 시로 변경 해줘."))
    }
    @Test fun `native negation questions and reported corrections cannot mutate an event`() {
        assertEquals("회의", EventChange.parse("회의의 를 취소해줘.")!!.target)
        assertEquals("앨리스의 회의", EventChange.parse("앨리스의 회의를 취소해줘.")!!.target)
        for (text in listOf("회의를 취소하지 마.", "회의를 취소해줘?", "不要取消会议。", "如果取消会议。", "N'annule pas la réunion.", "Annule la réunion?", "Das Treffen ist nicht abgesagt.", "Отмени встречу?", "Не отмени встречу.", "Он сказал: отмени встречу."))
            assertNull(EventChange.parse(text), text)
    }
    @Test fun `ambiguous clocks alternatives unexplained text and DST stay unresolved`() {
        for (timing in listOf("내일 9시", "明天9点", "demain à 9h", "morgen um 9 Uhr", "завтра в 9 часов", "demain à 21h ou 22h", "내일 오후 9시 아마", "2026-11-01 01:30", "2027-03-14 02:30"))
            assertNull(LocalizedActivities.changeStart(timing, previous, now, zone), timing)
    }
    @Test fun `date only keeps clock and time only keeps date`() {
        assertEquals(previous, EventChange.parse("Перенеси встречу на завтра.")!!.newStart(previous, now, zone))
        assertEquals(Instant.parse("2026-10-29T02:00:00Z"), EventChange.parse("把会议改到晚上9点。")!!.newStart(previous, now, zone))
        assertFalse(EventChange.parse("Annule la réunion.")!!.matches("concert"))
    }
    @Test fun `generic event types can cross languages but named targets are not translated guesses`() {
        assertTrue(EventChange.parse("取消会议。")!!.matches("Meeting with Alice tomorrow at 8pm"))
        assertTrue(EventChange.parse("회의를 취소해줘.")!!.matches("Réunion demain à 20h"))
        assertFalse(EventChange.parse("取消艾丽丝会议。")!!.matches("Meeting with Bob tomorrow at 8pm"))
        assertTrue(EventChange.parse("取消会议。")!!.matches("Meeting about a party tomorrow at 8pm"))
        assertFalse(EventChange.parse("取消派对。")!!.matches("Meeting about a party tomorrow at 8pm"))
        val mixed = EventChange.parse("把meeting改到tomorrow at 9pm。")!!
        assertEquals(Instant.parse("2026-10-29T02:00:00Z"), mixed.newStart(previous, now, zone))
    }
}
