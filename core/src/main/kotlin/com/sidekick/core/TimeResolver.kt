package com.sidekick.core

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Only resolve explicit dates and unambiguous clock times. Everything else stays for review. */
object TimeResolver {
    private val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)
    fun parseEditor(value: String, zone: ZoneId): Instant {
        val local = LocalDateTime.parse(value.trim(), formatter)
        val offsets = zone.rules.getValidOffsets(local)
        require(offsets.size == 1) { "This local time is skipped or occurs twice due to daylight saving. Choose an unambiguous time." }
        return local.toInstant(offsets.single())
    }
    fun format(instant: Instant, zone: ZoneId): String = formatter.format(instant.atZone(zone))

    private val meridiem = Regex("\\b(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b")
    private val military = Regex("\\bat\\s+(\\d{1,2}):(\\d{2})\\b")
    private val noon = Regex("\\bnoon\\b")
    private val midnight = Regex("\\bmidnight\\b")
    private val clocks = Regex("${meridiem.pattern}|\\b\\d{1,2}:\\d{2}\\b|${noon.pattern}|${midnight.pattern}")
    private const val UNITS = "one|two|three|four|five|six|seven|eight|nine"
    private const val NUMBER = "(?:(?:twenty|thirty|forty|fifty)[- ](?:$UNITS)|a|an|$UNITS|ten|eleven|twelve|twenty|thirty|forty|fifty|sixty|ninety|\\d{1,3}(?:\\.\\d+)?)"
    /** "in 1 hour 30 minutes", "in an hour and a half", "in two and a half hours", "in 1.5 hours". */
    private val offset = Regex("\\bin\\s+(?:(half\\s+an\\s+hour)|($NUMBER)(\\s+and\\s+a\\s+half)?\\s+(minute|hour)s?" +
        "(?:(\\s+and\\s+a\\s+half)|\\s+(?:and\\s+)?($NUMBER)\\s+minutes?)?)\\b")
    private val duration = Regex("\\b$NUMBER\\s+(?:and\\s+a\\s+half\\s+)?(?:minutes?|hours?)\\b|\\bhalf\\s+an\\s+hour\\b")

    fun suggest(text: String, captured: Instant, zone: ZoneId): Instant? = runCatching {
        if (EnglishDates.unresolvedTiming(text)) return null
        val lower = EnglishClocks.normalize(text.lowercase()).replace("a.m.", "am").replace("p.m.", "pm")
        if (EnglishDates.unresolvedTiming(lower)) return null
        val clockCount = clocks.findAll(lower).count()
        if (clockCount > 1) return null
        val hasClock = clockCount == 1
        // "In 30 minutes" is relative to capture time; combined with a date or clock it is ambiguous.
        offset.find(lower)?.let { match ->
            // A second duration ("in 30 minutes or in an hour") or a range ("in an hour or two") is not one delay.
            val extra = duration.findAll(lower).any { it.range.first !in match.range }
            if (hasClock || EnglishDates.pattern.containsMatchIn(lower) || offset.findAll(lower).count() != 1 || extra ||
                Regex("^\\s*or\\b").containsMatchIn(lower.substring(match.range.last + 1))) return null
            return captured.plus(delay(match) ?: return null).takeIf { it.isAfter(captured) }
        }
        if (EnglishDates.hasNextWeekday(lower)) return null // "Next Friday" may mean this week or the following one.
        val date = EnglishDates.date(lower, captured.atZone(zone).toLocalDate()) ?: return null
        val match = meridiem.find(lower)
        val clock = if (match != null) {
            val hour = match.groupValues[1].toInt()
            require(hour in 1..12)
            LocalTime.of(hour % 12 + if (match.groupValues[3] == "pm") 12 else 0, match.groupValues[2].ifBlank { "0" }.toInt())
        } else if (noon.containsMatchIn(lower)) LocalTime.NOON else if (midnight.containsMatchIn(lower)) LocalTime.MIDNIGHT else {
            val time = military.find(lower) ?: return null
            LocalTime.of(time.groupValues[1].toInt(), time.groupValues[2].toInt())
        }
        val result = parseEditor("$date ${clock.format(DateTimeFormatter.ofPattern("HH:mm"))}", zone)
        result.takeIf { it.isAfter(captured) }
    }.getOrNull()

    /** Elapsed time for one offset match; extra minutes only follow hours, and "a half" is said once. */
    private fun delay(match: MatchResult): Duration? {
        val groups = match.groupValues
        if (groups[1].isNotEmpty()) return Duration.ofMinutes(30)
        val unit = if (groups[4] == "hour") 3600.0 else 60.0
        val half = groups[3].isNotEmpty() || groups[5].isNotEmpty()
        if (groups[3].isNotEmpty() && groups[5].isNotEmpty() || groups[6].isNotEmpty() && unit != 3600.0) return null
        val seconds = (number(groups[2]) + if (half) 0.5 else 0.0) * unit + if (groups[6].isNotEmpty()) number(groups[6]) * 60 else 0.0
        return Duration.ofSeconds(Math.round(seconds))
    }

    private fun number(word: String): Double {
        val parts = word.split('-', ' ')
        return word.toDoubleOrNull() ?: if (parts.size == 2) (EnglishDates.amount(parts[0]) + EnglishDates.amount(parts[1])).toDouble()
            else EnglishDates.amount(word).toDouble()
    }
}
