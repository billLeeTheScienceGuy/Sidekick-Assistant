package com.sidekick.core

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.TemporalAdjusters

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

    fun suggest(text: String, captured: Instant, zone: ZoneId): Instant? = runCatching {
        val lower = text.lowercase().replace("a.m.", "am").replace("p.m.", "pm")
        val local = captured.atZone(zone)
        val date = when {
            Regex("\\btomorrow\\b").containsMatchIn(lower) -> local.toLocalDate().plusDays(1)
            Regex("\\btoday\\b").containsMatchIn(lower) -> local.toLocalDate()
            Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b").containsMatchIn(lower) ->
                LocalDate.parse(Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b").find(lower)!!.value)
            else -> {
                val day = DayOfWeek.entries.firstOrNull { Regex("\\b${it.name.lowercase()}\\b").containsMatchIn(lower) } ?: return null
                local.toLocalDate().with(TemporalAdjusters.next(day))
            }
        }
        val match = Regex("\\b(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b").find(lower)
        val clock = if (match != null) {
            val hour = match.groupValues[1].toInt()
            require(hour in 1..12)
            LocalTime.of(hour % 12 + if (match.groupValues[3] == "pm") 12 else 0, match.groupValues[2].ifBlank { "0" }.toInt())
        } else {
            val military = Regex("\\bat\\s+(\\d{1,2}):(\\d{2})\\b").find(lower) ?: return null
            LocalTime.of(military.groupValues[1].toInt(), military.groupValues[2].toInt())
        }
        val result = parseEditor("$date ${clock.format(DateTimeFormatter.ofPattern("HH:mm"))}", zone)
        result.takeIf { it.isAfter(captured) }
    }.getOrNull()
}
