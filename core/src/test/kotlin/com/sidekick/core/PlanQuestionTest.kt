package com.sidekick.core

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanQuestionTest {
    private val zone = ZoneId.of("America/Chicago")
    private val now = ZonedDateTime.of(2026, 11, 18, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
    private val plans = listOf(
        PlanQuestion.Plan("Dentist appointment", at(18, 14)),
        PlanQuestion.Plan("Dinner with Sarah", at(18, 18)),
        PlanQuestion.Plan("Team meeting", at(19, 10, 30)),
        PlanQuestion.Plan("치과 예약", at(20, 15)),
        PlanQuestion.Plan("Mia's party", at(18, 20), informational = true))

    @Test fun everyExampleShownOnThePhoneIsUnderstood() {
        // The kinds in order: today, tomorrow, this week, what's next, when is a plan.
        for (code in listOf("en", "ko", "zh", "ar", "ru", "fr", "de")) {
            val kinds = PlanQuestion.examples(code).map { text ->
                for (allowed in listOf(setOf(code), setOf("en", "ko", "zh", "ar", "ru", "fr", "de"))) {
                    val q = PlanQuestion.parse(text, allowed)
                    assertTrue(q != null && q.language == code, "$code example not understood with $allowed: $text -> $q")
                }
                when (val q = PlanQuestion.parse(text, setOf(code))!!) {
                    is PlanQuestion.Day -> if (q.days == 7) "week" else if (q.offsetDays == 1) "tomorrow" else "today"
                    is PlanQuestion.Next -> "next"
                    is PlanQuestion.Find -> "find"
                }
            }
            assertEquals(listOf("today", "tomorrow", "week", "next", "find"), kinds, code)
        }
    }

    @Test fun onlyQuestionsAddressedToSideKickCount() {
        // The same words said to another person in a conversation are not questions to the app.
        assertNull(PlanQuestion.parse("What's the plan today?"))
        assertNull(PlanQuestion.parse("So what's my plan today, do you know?"))
        assertNull(PlanQuestion.parse("When is your dentist appointment?"))
        assertNull(PlanQuestion.parse("오늘 일정 뭐야?"))
        assertNull(PlanQuestion.parse("I told SideKick about it, what's the plan today?"))
        assertTrue(PlanQuestion.parse("SideKick, what's my plan today?") is PlanQuestion.Day)
        assertTrue(PlanQuestion.parse("Hey Sidekick what do I have tomorrow") is PlanQuestion.Day)
        assertTrue(PlanQuestion.parse("사이드킥, 오늘 일정 뭐야?") is PlanQuestion.Day)
        assertNull(PlanQuestion.parse("SideKick"))
    }

    @Test fun todayTomorrowAndNext() {
        assertEquals("Today you have Dentist appointment at 2 PM and Dinner with Sarah at 6 PM.",
            PlanQuestion.answer(PlanQuestion.parse("SideKick, what's my plan today?")!!, plans, now, zone))
        assertEquals("Tomorrow you have Team meeting at 10:30 AM.",
            PlanQuestion.answer(PlanQuestion.parse("SideKick, what do I have tomorrow?")!!, plans, now, zone))
        assertEquals("Next is Dentist appointment today at 2 PM.",
            PlanQuestion.answer(PlanQuestion.parse("SideKick, what's next?")!!, plans, now, zone))
        assertEquals("오늘은 오늘 오후 2시 Dentist appointment, 오늘 오후 6시 Dinner with Sarah 일정이 있어요.".replace("오늘 오후", "오후"),
            PlanQuestion.answer(PlanQuestion.parse("사이드킥, 오늘 일정 뭐야?")!!, plans, now, zone))
    }

    @Test fun allSevenLanguages() {
        fun ask(text: String, allowed: Set<String> = setOf("en", "ko", "zh", "ar", "ru", "fr", "de")) =
            PlanQuestion.parse(text, allowed)?.let { it.language to PlanQuestion.answer(it, plans, now, zone) }
        assertEquals("fr" to "Aujourd'hui: Dentist appointment à 14:00, Dinner with Sarah à 18:00.", ask("SideKick, qu'est-ce que j'ai aujourd'hui ?"))
        assertEquals("de" to "Morgen: Team meeting um 10:30.", ask("SideKick, was habe ich morgen vor?"))
        assertEquals("ru" to "Сегодня: Dentist appointment в 14:00, Dinner with Sarah в 18:00.", ask("Сайдкик, какие у меня планы на сегодня?"))
        assertEquals("zh" to "明天：Team meeting 10:30。", ask("SideKick，我明天有什么安排？"))
        assertEquals("ar" to "اليوم: Dentist appointment الساعة 14:00، Dinner with Sarah الساعة 18:00.", ask("سايد كيك، ما هي خطتي اليوم؟"))
        assertEquals("de" to "Team meeting: morgen um 10:30.", ask("SideKick, wann ist das Team meeting?"))
        assertEquals("fr" to "Dentist appointment: aujourd'hui à 14:00.", ask("SideKick, c'est quand le rendez-vous dentist ?"))
        // A manual language setting understands only that language.
        assertNull(ask("Сайдкик, что у меня сегодня?", setOf("en")))
        assertNull(ask("사이드킥, 오늘 일정 뭐야?", setOf("en")))
        assertEquals("en", ask("SideKick, what's my plan today?", setOf("en"))?.first)
        // Without the name, nothing in any language.
        assertNull(ask("Qu'est-ce que j'ai aujourd'hui ?"))
        assertNull(ask("Was habe ich heute vor?"))
        assertNull(ask("我今天有什么安排？"))
    }

    @Test fun findAPlanByName() {
        assertEquals("Dentist appointment is today at 2 PM.",
            PlanQuestion.answer(PlanQuestion.parse("SideKick, when is my dentist appointment?")!!, plans, now, zone))
        assertEquals("Team meeting is tomorrow at 10:30 AM.",
            PlanQuestion.answer(PlanQuestion.parse("SideKick, what time is the team meeting?")!!, plans, now, zone))
        assertEquals("치과 예약: 11월 20일 금요일 오후 3시.",
            PlanQuestion.answer(PlanQuestion.parse("사이드킥, 치과 언제야?")!!, plans, now, zone))
        assertEquals("I couldn't find that in your saved plans.",
            PlanQuestion.answer(PlanQuestion.parse("SideKick, when is my haircut?")!!, plans, now, zone))
        // Other people's plans (information only) are not read out as yours.
        assertEquals("I couldn't find that in your saved plans.",
            PlanQuestion.answer(PlanQuestion.parse("SideKick, when is Mia's party?")!!, plans, now, zone))
    }
}
