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
    private val offset = Regex("\\bin\\s+(?:(half an hour)|(a|an|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|twenty|thirty|forty|fifty|sixty|ninety|\\d{1,3})\\s+(minute|hour)s?)\\b")
    private val duration = Regex("\\b(?:a|an|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|twenty|thirty|forty|fifty|sixty|ninety|\\d{1,3})\\s+(?:minutes?|hours?)\\b")

    fun suggest(text: String, captured: Instant, zone: ZoneId): Instant? = runCatching {
        val lower = text.lowercase().replace("a.m.", "am").replace("p.m.", "pm")
        if (EnglishDates.unresolvedTiming(lower)) return null
        val clockCount = clocks.findAll(lower).count()
        if (clockCount > 1) return null
        val hasClock = clockCount == 1
        // "In 30 minutes" is relative to capture time; combined with a date or clock it is ambiguous.
        offset.find(lower)?.let { match ->
            if (hasClock || EnglishDates.pattern.containsMatchIn(lower) || offset.findAll(lower).count() != 1 || duration.findAll(lower).count() != 1) return null
            val minutes = if (match.groupValues[1].isNotEmpty()) 30 else
                EnglishDates.amount(match.groupValues[2]) * if (match.groupValues[3] == "hour") 60 else 1
            return captured.plus(Duration.ofMinutes(minutes)).takeIf { it.isAfter(captured) }
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
}
