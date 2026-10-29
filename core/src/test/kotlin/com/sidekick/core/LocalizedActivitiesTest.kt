package com.sidekick.core

import java.time.Instant
import java.time.ZoneId
import kotlin.test.*

class LocalizedActivitiesTest {
    private val now = Instant.parse("2026-10-23T17:00:00Z")
    private fun source(text: String) = Transcript(text, now, ZoneId.of("America/Chicago"), Speaker.USER, true)
    private val examples = listOf(
        "내일 오후 8시에 The James에서 파티가 있어요. 자정에 끝나요.",
        "明天晚上八点在The James有派对。午夜结束。",
        "Il y a une fête demain à vingt heures au The James. Ça finit à minuit.",
        "Morgen ist eine Party um 20 Uhr im The James. Sie endet um Mitternacht.",
        "Завтра вечеринка в 20:00 в The James. Она закончится в полночь.",
    )
    @Test fun `all five languages retain source venue and resolve next-day midnight`() {
        examples.forEach { text ->
            val result = ActivityTiming.extract(source(text)).single()
            assertEquals(Instant.parse("2026-10-25T01:00:00Z"), result.activityStartAt, text)
            assertEquals(Instant.parse("2026-10-25T05:00:00Z"), result.activityEndAt, text)
            assertEquals("The James", result.placeName, text)
            assertTrue(result.informational)
            assertTrue(result.evidence.lines().all { text.contains(it) })
        }
    }
    @Test fun `negative conditional and ambiguous local statements are not invented`() {
        listOf("내일 오후 8시에 파티가 없어요.", "明天晚上八点没有派对。", "Il n'y a pas de fête demain à 20 heures.",
            "Morgen ist keine Party um 20 Uhr.", "Завтра не будет вечеринки в 20:00.",
            "아마 내일 오후 8시에 파티가 있어요.", "如果明天晚上八点有派对。", "Vielleicht ist morgen eine Party um 20 Uhr.",
            "내일 8시에 파티가 있어요.", "明天八点有派对。", "Il y a une fête demain à huit heures.")
            .forEach { assertTrue(ActivityTiming.extract(source(it)).isEmpty(), it) }
    }
    @Test fun `explicit self attendance is personal only for the enrolled user`() {
        listOf("저는 내일 오후 8시에 파티에 갈 거예요.", "我明天晚上八点去参加派对。", "Je vais à la fête demain à 20 heures.",
            "Ich werde morgen zur Party um 20 Uhr gehen.", "Я пойду на вечеринку завтра в 20:00.")
            .forEach { text ->
                assertFalse(ActivityTiming.extract(source(text)).single().informational, text)
                assertTrue(ActivityTiming.extract(source(text).copy(speaker = Speaker.OTHER)).isEmpty())
                assertTrue(ActivityTiming.extract(source(text).copy(speaker = Speaker.OTHER), informationalOnly = true).single().informational)
                assertTrue(ActivityTiming.extract(source(text).copy(processingAllowed = false)).isEmpty())
            }
    }
    @Test fun `cross-language end corrections retain original evidence`() {
        val text = "내일 오후 8시에 파티가 있어요. It ends at midnight."
        val activity = ActivityTiming.extract(source(text)).single()
        assertEquals(Instant.parse("2026-10-25T05:00:00Z"), activity.activityEndAt)
        val inverse = ActivityTiming.extract(source("There's a party tomorrow at 8pm. 자정에 끝나요.")).single()
        assertEquals(activity.activityEndAt, inverse.activityEndAt)
        assertEquals(Instant.parse("2026-10-25T01:30:00Z"), ActivityTiming.extract(source("내일 party at 8:30pm.")).single().activityStartAt)
        assertEquals(Instant.parse("2026-10-25T01:30:00Z"), ActivityTiming.extract(source("明天晚上8:30有派对。")).single().activityStartAt)
    }
    @Test fun `another activity breaks local pronoun association`() {
        val activity = ActivityTiming.extract(source("내일 오후 8시에 파티가 있어요. 내일 오후 9시에 회의가 있어요. 자정에 끝나요."))
        assertEquals(2, activity.size)
        assertNull(activity[0].activityEndAt)
        assertNotNull(activity[1].activityEndAt)
    }
    @Test fun `weekday and place corrections are grounded`() {
        val activity = ActivityTiming.extract(source("금요일 오후 8시에 파티가 있어요. 장소는 The James예요. 장소는 The Sylvee예요. 주소는 25 S Livingston Street예요.")).single()
        assertEquals(Instant.parse("2026-10-31T01:00:00Z"), activity.activityStartAt)
        assertEquals("The Sylvee", activity.placeName)
        assertEquals("25 S Livingston Street", activity.placeAddress)
    }
    @Test fun `explicit local durations and quoted ownership remain conservative`() {
        listOf("내일 오후 8시에 파티가 있어요. 2시간 동안 해요.", "明天晚上八点有派对。持续两小时。",
            "Une fête demain à 20 heures. Ça dure deux heures.", "Morgen ist eine Party um 20 Uhr. Sie dauert zwei Stunden.",
            "Завтра вечеринка в 20:00. Она длится два часа.").forEach {
            assertEquals(Instant.parse("2026-10-25T03:00:00Z"), ActivityTiming.extract(source(it)).single().activityEndAt, it)
        }
        assertTrue(ActivityTiming.extract(source("Il dit «Je vais à la fête demain à 20 heures».")).single().informational)
        assertTrue(ActivityTiming.extract(source("2026-02-30 오후 8시에 파티가 있어요.")).isEmpty())
        assertTrue(ActivityTiming.extract(source("Une réunion le 2026-11-01 à 1:30.")).isEmpty())
        assertTrue(ActivityTiming.extract(source("Une fête demain lundi à 20 heures.")).isEmpty())
        val monday = source("다음 주 금요일 오후 8시에 파티가 있어요.").copy(capturedAt = Instant.parse("2026-10-26T17:00:00Z"))
        assertEquals(Instant.parse("2026-11-07T02:00:00Z"), ActivityTiming.extract(monday).single().activityStartAt)
    }
}
