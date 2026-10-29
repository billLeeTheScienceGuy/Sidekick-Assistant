package com.sidekick.core

import java.time.*
import java.time.temporal.TemporalAdjusters

/** Explicit grounded corrections; questions, possibilities and ambiguous clocks are ignored. */
data class EventChange(val target: String, val cancel: Boolean, val timing: String, val evidence: String, val localized: Boolean = false) {
    fun newStart(previous: Instant, capturedAt: Instant, zone: ZoneId): Instant? = if (localized) LocalizedActivities.changeStart(timing, previous, capturedAt, zone) else runCatching {
        val dates = Regex("\\b(today|tonight|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|\\d{4}-\\d{2}-\\d{2})\\b").findAll(timing).toList()
        require(dates.size <= 1)
        val today = capturedAt.atZone(zone).toLocalDate()
        val date = dates.singleOrNull()?.value?.let { value -> when (value) {
            "today", "tonight" -> today
            "tomorrow" -> today.plusDays(1)
            else -> DayOfWeek.entries.firstOrNull { it.name.equals(value, true) }?.let { today.with(TemporalAdjusters.next(it)) } ?: LocalDate.parse(value)
        } } ?: previous.atZone(zone).toLocalDate()
        val clocks = Regex("\\b(?:1[0-2]|0?[1-9])(?::[0-5]\\d)?\\s*[ap]\\.?m\\.?(?!\\w)|\\b(?:[01]?\\d|2[0-3]):[0-5]\\d\\b|\\b(?:noon|midnight)\\b").findAll(timing).toList()
        require(clocks.size <= 1 && (clocks.isNotEmpty() || dates.isNotEmpty()))
        // Reject unexplained text (including a second time, a range, or an ambiguous bare hour).
        var remainder = timing
        (dates.map { it.range } + clocks.map { it.range }).sortedByDescending { it.first }.forEach { remainder = remainder.removeRange(it) }
        require(remainder.replace(Regex("\\b(?:at|on)\\b|[\\s,.!]"), "").isEmpty())
        val clock = clocks.singleOrNull()?.value?.replace(" ", "")?.replace(".", "")?.let {
            when (it) {
                "noon" -> LocalTime.NOON
                "midnight" -> LocalTime.MIDNIGHT
                else -> {
                    val parts = Regex("(\\d{1,2})(?::(\\d{2}))?(am|pm)?").matchEntire(it) ?: error("Clock")
                    var hour = parts.groupValues[1].toInt()
                    if (parts.groupValues[3].isNotEmpty()) hour = hour % 12 + if (parts.groupValues[3] == "pm") 12 else 0
                    LocalTime.of(hour, parts.groupValues[2].ifEmpty { "0" }.toInt())
                }
            }
        } ?: previous.atZone(zone).toLocalTime().withSecond(0).withNano(0)
        TimeResolver.parseEditor("$date ${clock.toString().take(5)}", zone).takeIf { it.isAfter(capturedAt) }
    }.getOrNull()

    fun matches(title: String): Boolean {
        if (localized) return LocalizedEventChanges.matches(target, title)
        val words = target.removePrefix("the ").removePrefix("my ")
        return Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(words) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(title)
    }
    val pronoun: Boolean get() = target in setOf("it", "that", "that event")

    companion object {
        private val blocked = Regex("\\b(?:not|never|don['’]t|won['’]t|isn['’]t|wasn['’]t|if|unless|maybe|might|could|would|should|can|said|says|saying|heard|quote)\\b", RegexOption.IGNORE_CASE)
        private const val target = "(it|that|that event|(?:(?:the|my) )?[a-z][a-z '’\\-]{0,70}?)"
        private val cancelCommand = Regex("^(?:please )?cancel $target[.!]*$", RegexOption.IGNORE_CASE)
        private val cancelStatement = Regex("^$target (?:is|was|has been) cancel(?:l)?ed[.!]*$", RegexOption.IGNORE_CASE)
        private val moveCommand = Regex("^(?:please )?(?:move|reschedule) $target to (.+?)[!]*$", RegexOption.IGNORE_CASE)
        private val moveStatement = Regex("^$target (?:is|has been|was) (?:moved|rescheduled) to (.+?)[!]*$", RegexOption.IGNORE_CASE)
        private val makeTime = Regex("^make (it|that|that event) (.+?)[!]*$", RegexOption.IGNORE_CASE)
        fun looksLikeChange(text: String) = ArabicActivities.looksLikeChange(text) || LocalizedEventChanges.looksLike(text) || Regex("\\b(?:cancel(?:l)?ed|cancel|reschedule[ds]?|moved?|rescheduled)\\b|\\bmake (?:it|that)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        fun parse(text: String): EventChange? {
            LocalizedEventChanges.parse(text)?.let { return it }
            val clean = text.trim().lowercase(java.util.Locale.ROOT).replace(Regex("^(?:actually|okay|ok)[, ]+"), "")
                .replace(Regex("^let['’]s\\s+"), "").replace(Regex("\\bit['’]s\\b"), "it is")
            if (clean.endsWith('?') || blocked.containsMatchIn(clean) || clean.any { it in "\"“”" }) return null
            val cancellation = cancelCommand.matchEntire(clean) ?: cancelStatement.matchEntire(clean)
            if (cancellation != null) return EventChange(cancellation.groupValues[1], true, "", text)
            val movement = moveCommand.matchEntire(clean) ?: moveStatement.matchEntire(clean) ?: makeTime.matchEntire(clean) ?: return null
            val numbers = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")
            val timing = Regex("\\b(" + numbers.joinToString("|") + ")\\s+([ap])\\.?\\s*m\\.?(?!\\w)").replace(movement.groupValues[2]) {
                "${numbers.indexOf(it.groupValues[1]) + 1}${it.groupValues[2]}m"
            }
            return EventChange(movement.groupValues[1], false, timing, text)
        }
    }
}
