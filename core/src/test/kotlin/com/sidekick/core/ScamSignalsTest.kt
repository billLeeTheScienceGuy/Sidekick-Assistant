package com.sidekick.core

import kotlin.test.*

class ScamSignalsTest {
    private data class Case(val expected: ScamLevel, val language: String, val text: String)
    private val cases = javaClass.getResourceAsStream("/scam-eval.tsv")!!.bufferedReader(Charsets.UTF_8).readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line -> line.split('\t').let { Case(ScamLevel.valueOf(it[0]), it[1], it[2]) } }

    /** Every scam script reaches at least its level; every piece of everyday talk stays quiet. */
    @Test fun evaluationSet() {
        val misses = mutableListOf<String>(); val falseAlarms = mutableListOf<String>()
        for (c in cases) {
            val got = ScamMonitor().add(c.text, 0L)
            if (c.expected == ScamLevel.NONE) { if (got.level != ScamLevel.NONE) falseAlarms += "${c.language}: ${c.text} -> ${got.level} ${got.signals}" }
            else if (got.level < c.expected) misses += "${c.language} wanted ${c.expected}: ${c.text} -> ${got.level} ${got.signals}"
        }
        val scams = cases.count { it.expected != ScamLevel.NONE }; val normal = cases.size - scams
        println("scam evaluation: caught ${scams - misses.size} of $scams scam scripts; false alarms ${falseAlarms.size} of $normal everyday lines")
        assertTrue(misses.isEmpty() && falseAlarms.isEmpty(), (misses + falseAlarms).joinToString("\n"))
        assertTrue(cases.map { it.language }.toSet().containsAll(listOf("en", "ko", "zh", "ru", "ar", "fr", "de")))
    }

    /** Lines written after tuning and never tuned on: reports how the rules do on new phrasing (looser bar). */
    @Test fun heldOutSet() {
        val held = javaClass.getResourceAsStream("/scam-heldout.tsv")!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { line -> line.split('\t').let { Case(ScamLevel.valueOf(it[0]), it[1], it[2]) } }
        val scams = held.filter { it.expected != ScamLevel.NONE }; val normal = held.filter { it.expected == ScamLevel.NONE }
        val warned = scams.filter { ScamMonitor().add(it.text, 0).level != ScamLevel.NONE }
        val exact = scams.filter { ScamMonitor().add(it.text, 0).level >= it.expected }
        val alarms = normal.filter { ScamMonitor().add(it.text, 0).level != ScamLevel.NONE }
        println("held-out: warned on ${warned.size} of ${scams.size} scams (${exact.size} at the expected level); false alarms ${alarms.size} of ${normal.size}")
        (scams - warned.toSet()).forEach { println("  missed: ${it.language}: ${it.text} -> ${ScamMonitor().add(it.text, 0).signals}") }
        alarms.forEach { println("  false alarm: ${it.language}: ${it.text} -> ${ScamMonitor().add(it.text, 0).signals}") }
        assertTrue(warned.size * 10 >= scams.size * 7, "fewer than 70% of held-out scams warned")
        assertTrue(alarms.size <= 1, "more than one false alarm on held-out everyday talk")
    }

    @Test fun signsAddUpOverAConversation() {
        val monitor = ScamMonitor(windowMs = 600_000)
        assertEquals(ScamLevel.NONE, monitor.add("This is the fraud department at your bank.", 0).level)
        assertEquals(ScamLevel.STOP, monitor.add("Please read me the verification code.", 60_000).level)
        // Signs older than the window are forgotten.
        assertEquals(ScamLevel.NONE, monitor.assessment(60_000 + 600_001).level)
    }

    @Test fun callSummaryCoversOnlyTheCall() {
        val monitor = ScamMonitor()
        monitor.add("Install AnyDesk so I can help.", 1_000)
        assertEquals(ScamLevel.NONE, monitor.since(5_000).level)
        monitor.add("Why do I have to stay on the line? You want me to buy gift cards?", 6_000)
        assertEquals(listOf(ScamSignal.SECRECY, ScamSignal.GIFT_CARD), monitor.since(5_000).signals)
    }

    @Test fun stepFollowsWhatIsAskedAndNeverRepeatsTheCode() {
        val code = ScamCheck.message("Tell me the verification code 123456.")
        assertTrue(code.step.startsWith("Don't share the code"))
        assertFalse((code.title + code.step + code.reasons).contains("123456"))
        assertTrue(ScamCheck.message("Install AnyDesk so I can help.").step.startsWith("Don't install"))
        assertTrue(ScamCheck.message("Move your money to a safe account.").step.startsWith("Don't pay"))
    }

    @Test fun links() {
        for (bad in listOf("paypa1.com/login", "https://amaz0n-support.com", "paypal.com.secure-login.net/x", "bit.ly/3abc", "http://192.168.4.20/pay", "xn--pypal-4ve.com", "usps-redelivery.top"))
            assertTrue(LinkCheck.suspicious("Go to $bad now").isNotEmpty(), bad)
        for (good in listOf("paypal.com", "https://www.amazon.com/orders", "google.com/docs", "startups.com", "groups.google.com", "e.g. this", "zoom.us/j/1"))
            assertTrue(LinkCheck.suspicious("See $good please").isEmpty(), good)
    }

    @Test fun askingSideKick() {
        assertEquals("en", ScamQuestion.asked("is this a scam?"))
        assertEquals("ko", ScamQuestion.asked("이거 사기야?"))
        assertEquals("de", ScamQuestion.asked("ist das Betrug?"))
        assertNull(ScamQuestion.asked("what's my plan today?"))
        assertTrue(ScamQuestion.answer(ScamAssessment.of(emptyList()), "en").startsWith("I haven't heard"))
        assertTrue(ScamQuestion.answer(ScamAssessment.of(listOf(ScamSignal.AUTHORITY, ScamSignal.CODE)), "ko").startsWith("사기일"))
        assertTrue(ScamQuestion.answer(null, "fr").contains("désactivées"))
    }
}
