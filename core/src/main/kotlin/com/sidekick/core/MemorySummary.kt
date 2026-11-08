package com.sidekick.core

import java.time.*

data class SummaryFact(val id: String, val title: String, val state: String, val informational: Boolean,
    val start: Instant? = null, val end: Instant? = null, val reminder: Instant? = null,
    val place: String? = null, val address: String? = null)
data class DailyDigest(val plans: List<SummaryFact>, val information: List<SummaryFact>,
    val history: List<SummaryFact>, val undated: List<SummaryFact>)

/** Derived views of saved facts. No generated dates, attendance, completion times or transcript storage. */
object MemorySummary {
    fun daily(facts: List<SummaryFact>, day: LocalDate, zone: ZoneId): DailyDigest {
        val from = day.atStartOfDay(zone).toInstant()
        val until = day.plusDays(1).atStartOfDay(zone).toInstant()
        fun onDay(f: SummaryFact): Boolean {
            val activity = f.start?.let { start ->
                if (f.end != null && f.end > start) start < until && f.end > from else start >= from && start < until
            } == true
            return activity || f.reminder?.let { it >= from && it < until } == true
        }
        val saved = facts.filter { it.state in setOf("ACCEPTED", "DONE", "CANCELLED") }.distinctBy { it.id }
        val dated = saved.filter(::onDay).sortedWith(compareBy<SummaryFact> { it.start ?: it.reminder }.thenBy { it.title }.thenBy { it.id })
        return DailyDigest(dated.filter { it.state == "ACCEPTED" && !it.informational },
            dated.filter { it.state == "ACCEPTED" && it.informational },
            dated.filter { it.state != "ACCEPTED" },
            saved.filter { it.state == "ACCEPTED" && it.start == null && it.reminder == null }.sortedBy { it.title })
    }

    /** Exact source excerpts, rather than an inferred narrative about who attended or agreed. */
    fun conversation(text: String, limit: Int = 8): List<String> {
        require(limit in 1..20)
        return Sentences.split(text.takeLast(6000)).filter { line ->
            !AnalysisContext.reported(line) && (ActivityTiming.looksLikeActivity(line) ||
                EventChange.looksLikeChange(line) || ActivityTiming.timingFragment(line) != null ||
                LocalizedActivities.isContinuation(line) || ConversationActivities.attendanceValue(line) != null ||
                Regex("^(?:I'll|I will|I need to|I plan to)\\b", RegexOption.IGNORE_CASE).containsMatchIn(line))
        }.distinctBy { it.lowercase().trimEnd('.', '!', '。') }.takeLast(limit)
    }
}
