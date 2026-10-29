package com.sidekick.core

import java.time.Instant
import java.time.ZoneId
import kotlin.test.*

class ConversationActivitiesTest {
    private fun source(text: String) = Transcript(text, Instant.parse("2026-10-29T16:00:00Z"), ZoneId.of("America/New_York"), Speaker.USER, true)
    @Test fun `split date clock venue and ending stay grounded`() {
        val text = "There's a party. Tomorrow. At 8pm. It is at The James. It ends at midnight."
        val item = ActivityTiming.extract(source(text)).single()
        assertEquals(Instant.parse("2026-10-31T00:00:00Z"), item.activityStartAt)
        assertEquals(Instant.parse("2026-10-31T04:00:00Z"), item.activityEndAt)
        assertEquals("The James", item.placeName)
        assertTrue(item.informational)
        item.evidence.lines().forEach { assertTrue(text.contains(it)) }
    }
    @Test fun `split personal plan survives local evidence validation`() {
        val item = AnalysisContext.local(source("I'll attend the party. Tomorrow. At 8pm.")).single()
        assertFalse(item.informational)
        assertNotNull(item.activityStartAt)
    }
    @Test fun `a later date replaces an inferred date without guessing between conflicts`() {
        val item = ActivityTiming.extract(source("There's a party at 8pm. Tomorrow.")).single()
        assertEquals(30, item.activityStartAt!!.atZone(source("").zoneId).dayOfMonth)
        assertTrue(ActivityTiming.extract(source("There's a party at 8pm. Tomorrow or today.")).isEmpty())
    }
    @Test fun `attendance and withdrawal are separate from cancellation`() {
        val text = "There's a party tomorrow at 8pm. I'll be there."
        assertFalse(AnalysisContext.local(source(text)).single().informational)
        val withdrawn = AnalysisContext.local(source(text + " I won't be there.")).single()
        assertTrue(withdrawn.informational)
        assertNotNull(withdrawn.activityStartAt)
        assertTrue(ActivityTiming.extract(source(text).copy(speaker = Speaker.OTHER), true).single().informational)
        assertTrue(ActivityTiming.extract(source("I'll attend the party tomorrow at 8pm. I won't be there.")).single().informational)
    }
    @Test fun `multiple events cannot inherit pronoun attendance or a different events ending`() {
        val items = ActivityTiming.extract(source("There's a party tomorrow at 8pm. There's a concert tomorrow at 9pm. I'll be there. The party ends at midnight."))
        assertEquals(2, items.size)
        assertTrue(items.all { it.informational })
        assertNotNull(items.first().activityEndAt)
        assertNull(items.last().activityEndAt)
    }
    @Test fun `ambiguous timing and unrelated fragments stay unapplied`() {
        for (text in listOf("There's a party. Tomorrow. At 8pm or 9pm.", "There's a party. Tomorrow or today. At 8pm.",
            "Maybe there's a party. Tomorrow. At 8pm.", "There's a party. My train is tomorrow. At 8pm.",
            "There's a party. At 8pm.", "Say there's a party. Tomorrow. At 8pm.")) {
            assertTrue(ActivityTiming.extract(source(text)).isEmpty(), text)
        }
    }
    @Test fun `same kind events require a unique named detail and reported attendance stays informational`() {
        val text = "There's Alex's party tomorrow at 8pm. There's Emma's party tomorrow at 9pm. The party ends at midnight."
        assertTrue(ActivityTiming.extract(source(text)).all { it.activityEndAt == null })
        val named = ActivityTiming.extract(source(text + " Alex's party ends at 11pm."))
        assertNotNull(named.first().activityEndAt)
        assertNull(named.last().activityEndAt)
        assertTrue(ActivityTiming.extract(source("There's a party tomorrow at 8pm. Emma said. I'll be there.")).single().informational)
        assertFalse(ActivityTiming.merge(source("I'll attend the party tomorrow at 8pm. Emma said. I won't be there."),
            RuleIntentExtractor().extract(source("I'll attend the party tomorrow at 8pm. Emma said. I won't be there."))).single().informational)
    }
    @Test fun `split timing supports every configured language`() {
        for (text in listOf("파티가 있어요. 내일. 오후 8시.", "有聚会。明天。晚上八点。",
            "Il y a une fête. Demain. À 20 heures.", "Es gibt eine Party. Morgen. Um 20 Uhr.",
            "Есть вечеринка. Завтра. В 20 часов.", "هناك حفلة. غدا. الساعة الثامنة مساء.")) {
            val item = ActivityTiming.extract(source(text)).singleOrNull()
            assertNotNull(item, text)
            assertEquals(20, item.activityStartAt!!.atZone(source(text).zoneId).hour, text)
            assertEquals(30, item.activityStartAt!!.atZone(source(text).zoneId).dayOfMonth, text)
        }
    }
    @Test fun `bounded live context does not exceed the model validation limit`() {
        val text = "Background words ".repeat(260) + ". There's a party tomorrow at 8pm. I'll be there."
        assertTrue(text.length > 4000)
        assertFalse(ActivityTiming.extract(source(text)).single().informational)
    }
}
