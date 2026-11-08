package com.sidekick.core

import java.io.File
import java.security.MessageDigest
import java.time.*
import java.util.Locale
import kotlin.test.*

/** Reproducible, self-authored functional corpus; not a representative user study or ASR benchmark. */
class AccuracyEvaluationTest {
    data class Gold(val token: String, val start: Instant?, val end: Instant?, val personal: Boolean, val place: String?, val reminder: Boolean)
    data class Score(val id: String, val language: String, val bucket: String, val tp: Int, val fp: Int, val fn: Int,
        val timed: Int, val timesRight: Int, val ownersRight: Int, val places: Int, val placesRight: Int, val falseReminders: Int, val wrongReminderTimes: Int, val missingReminders: Int, val issues: List<String>)
    private val zone = ZoneId.of("America/New_York")
    private val captured = Instant.parse("2026-11-08T15:00:00Z")
    private fun instant(s: String) = if (s == "-") null else LocalDateTime.parse(s).atZone(zone).toInstant()
    private fun norm(s: String) = ArabicActivities.normalized(s).lowercase(Locale.ROOT)
    @Test fun evaluateCuratedCorpusAndWriteReport() {
        val bytes = javaClass.getResourceAsStream("/evaluation.tsv")!!.use { it.readBytes() }
        val rows = bytes.toString(Charsets.UTF_8).lines().filter { it.isNotBlank() && !it.startsWith('#') }
        val scores = rows.map { row ->
            val p = row.split('\t'); require(p.size == 6)
            val expected = if (p[5] == "-") emptyList() else p[5].split(';').map { item ->
                val v = item.split('@'); require(v.size == 6)
                Gold(v[0], instant(v[1]), instant(v[2]), v[3] == "P", v[4].takeUnless { it == "-" }, v[5] == "yes")
            }
            val source = Transcript(p[4], captured, zone, Speaker.valueOf(p[3]), true)
            val actual = if (source.speaker == Speaker.USER) AnalysisContext.local(source) else ActivityTiming.extract(source.copy(speaker = Speaker.OTHER), true)
            val remaining = actual.toMutableList(); val issues = mutableListOf<String>()
            var tp = 0; var timed = 0; var times = 0; var owners = 0; var places = 0; var placeRight = 0; var falseAlerts = 0; var wrongAlertTimes = 0; var missingAlerts = 0
            fun time(a: ProposedCommitment) = a.activityStartAt ?: TimeResolver.suggest(a.evidence, captured, zone)
            fun reminds(a: ProposedCommitment) = !a.informational && time(a)?.isAfter(captured) == true && !EnglishDates.hasNextWeekday(a.evidence)
            for (gold in expected) {
                val candidates = remaining.filter { norm(it.title).contains(norm(gold.token)) }
                val match = candidates.firstOrNull { time(it) == gold.start } ?: candidates.firstOrNull()
                if (match == null) { issues += "missed ${gold.token}"; if (gold.reminder) { missingAlerts++; issues += "missing reminder" }; continue }
                remaining.remove(match); tp++
                val rightTime = time(match) == gold.start && match.activityEndAt == gold.end
                timed++; if (rightTime) times++ else issues += "wrong/unknown time for ${gold.token}"
                if (!match.informational == gold.personal) owners++ else issues += "wrong attendance for ${gold.token}"
                if (gold.place != null) { places++; if (norm(match.placeName.orEmpty()).contains(norm(gold.place))) placeRight++ else issues += "missed place ${gold.place}" }
                if (reminds(match) && !gold.reminder) { falseAlerts++; issues += "false reminder" }
                if (reminds(match) && gold.reminder && time(match) != gold.start) { wrongAlertTimes++; issues += "mistimed reminder" }
                if (!reminds(match) && gold.reminder) { missingAlerts++; issues += "missing reminder" }
            }
            falseAlerts += remaining.count(::reminds)
            if (remaining.isNotEmpty()) issues += "${remaining.size} extra event(s)"
            Score(p[0], p[1], p[2], tp, remaining.size, expected.size - tp, timed, times, owners, places, placeRight, falseAlerts, wrongAlertTimes, missingAlerts, issues)
        }
        fun ratio(n: Int, d: Int) = if (d == 0) "n/a" else String.format(Locale.ROOT, "%.1f%%", 100.0 * n / d)
        val report = buildString {
            appendLine("# Text-understanding accuracy evaluation")
            appendLine(); appendLine("${scores.size} self-authored cases across seven languages. Fixed capture: $captured; timezone: ${zone.id}.")
            appendLine("Corpus SHA-256: `${MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }}`.")
            appendLine(); appendLine("This tests local text extraction with supplied speaker labels, not speech recognition, speaker ID, optional Qwen, live correction execution or a representative user population. Baseline cases exercise supported grammar; challenge cases test broader wording. Both are reported, including failures. No raw user conversations are used.")
            appendLine(); appendLine("Event matches use the labeled subject token. Time, attendance and place accuracy are on matched events; missed events are counted separately. Time accuracy includes unknown-time preservation and known end times. False reminders are reminders where the label says none; mistimed reminders have an incorrect start; missed reminders include missed events. This is not a calibrated confidence score.")
            appendLine(); appendLine("| Group | Cases | TP / FP / FN | Precision | Recall | Exact time | Attendance | Known place | False / mistimed / missed reminders |")
            appendLine("|---|---:|---|---:|---:|---:|---:|---:|---:|")
            val groups = linkedMapOf("All" to scores, "Baseline" to scores.filter { it.bucket == "baseline" }, "Challenge" to scores.filter { it.bucket == "challenge" })
            scores.map { it.language }.distinct().forEach { lang -> groups[lang] = scores.filter { it.language == lang } }
            groups.forEach { (name, s) ->
                val tp = s.sumOf { it.tp }; val fp = s.sumOf { it.fp }; val fn = s.sumOf { it.fn }
                appendLine("| $name | ${s.size} | $tp / $fp / $fn | ${ratio(tp, tp + fp)} | ${ratio(tp, tp + fn)} | ${ratio(s.sumOf { it.timesRight }, s.sumOf { it.timed })} | ${ratio(s.sumOf { it.ownersRight }, tp)} | ${ratio(s.sumOf { it.placesRight }, s.sumOf { it.places })} | ${s.sumOf { it.falseReminders }} / ${s.sumOf { it.wrongReminderTimes }} / ${s.sumOf { it.missingReminders }} |")
            }
            appendLine(); appendLine("## Cases needing work"); appendLine()
            scores.filter { it.issues.isNotEmpty() }.forEach { appendLine("- ${it.id} (${it.language}, ${it.bucket}): ${it.issues.joinToString("; ")}") }
            appendLine(); appendLine("Reproduce with `gradlew :core:test --tests '*AccuracyEvaluationTest'`. The full case text and labels are in `core/src/test/resources/evaluation.tsv`. The generated report is `core/build/reports/accuracy.md`. Do not present these curated results as real-world accuracy or hide the challenge results.")
        }
        File("build/reports/accuracy.md").apply { parentFile.mkdirs(); writeText(report) }
        assertEquals(7, scores.map { it.language }.toSet().size)
        // Keep evaluation reproducible; quality failures are recorded, not erased by relaxing labels.
        assertTrue(scores.size >= 70)
        assertEquals(0, scores.sumOf { it.falseReminders + it.wrongReminderTimes }, "Unsafe reminder guesses; see build/reports/accuracy.md")
        assertTrue(scores.filter { it.bucket == "baseline" }.all { it.fp == 0 && it.fn == 0 && it.timesRight == it.timed && it.ownersRight == it.tp && it.missingReminders == 0 },
            "Supported baseline regressed; see build/reports/accuracy.md")
    }
}
