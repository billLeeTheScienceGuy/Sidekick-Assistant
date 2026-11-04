package com.sidekick.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Shared English date phrases. Two different dates in one text resolve to none; invalid dates fail. */
object EnglishDates {
    internal const val WEEKDAYS = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
    internal const val MONTHS = "january|february|march|april|may|june|july|august|september|october|november|december"
    private const val COUNT = "a|an|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|\\d{1,2}"
    private const val DAY = "(?:[12]\\d|3[01]|0?[1-9])"
    private const val YEAR = "(?:,?\\s+(\\d{4}))?"
    private val numbers = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")

    /** Longer phrases come first so "the day after tomorrow" is never read as "tomorrow". */
    val pattern = Regex("\\b(?:(?:the\\s+)?day\\s+after\\s+tomorrow|today|tonight|tomorrow|(?:(?:this|next)\\s+)?(?:$WEEKDAYS)" +
        "|(?:$MONTHS)\\s+$DAY(?:st|nd|rd|th)?\\b(?!\\s*(?:[ap]\\.?m\\b|:))(?:,?\\s+\\d{4})?" +
        "|(?:the\\s+)?$DAY(?:st|nd|rd|th)\\s+of\\s+(?:$MONTHS)(?:,?\\s+\\d{4})?" +
        "|in\\s+(?:$COUNT)\\s+(?:days?|weeks?)|\\d{4}-\\d{2}-\\d{2})\\b", RegexOption.IGNORE_CASE)
    private val relative = Regex("^in ($COUNT) (day|week)s?$")
    private val weekday = Regex("^(?:(this|next) )?($WEEKDAYS)$")
    private val monthFirst = Regex("^($MONTHS) ($DAY)(?:st|nd|rd|th)?$YEAR$")
    private val dayFirst = Regex("^($DAY)(?:st|nd|rd|th) of ($MONTHS)$YEAR$")
    private val nextWeekday = Regex("\\bnext\\s+(?:$WEEKDAYS)\\b", RegexOption.IGNORE_CASE)

    fun date(text: String, today: LocalDate): LocalDate? =
        pattern.findAll(text).map { resolve(it.value, today) }.toSet().singleOrNull()

    /** "Next Friday" is read as Friday of the following Monday-start week; callers should flag it for review. */
    fun hasNextWeekday(text: String) = nextWeekday.containsMatchIn(text)

    fun resolve(phrase: String, today: LocalDate): LocalDate {
        val value = phrase.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").removePrefix("the ")
        when (value) {
            "today", "tonight" -> return today
            "tomorrow" -> return today.plusDays(1)
            "day after tomorrow" -> return today.plusDays(2)
        }
        relative.matchEntire(value)?.let {
            val amount = amount(it.groupValues[1])
            return today.plusDays(if (it.groupValues[2] == "week") amount * 7 else amount)
        }
        weekday.matchEntire(value)?.let {
            val day = DayOfWeek.valueOf(it.groupValues[2].uppercase(Locale.ROOT))
            return when (it.groupValues[1]) {
                "this" -> today.with(TemporalAdjusters.nextOrSame(day))
                "next" -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).with(TemporalAdjusters.nextOrSame(day))
                else -> today.with(TemporalAdjusters.next(day))
            }
        }
        monthFirst.matchEntire(value)?.let { return calendar(it.groupValues[1], it.groupValues[2], it.groupValues[3], today) }
        dayFirst.matchEntire(value)?.let { return calendar(it.groupValues[2], it.groupValues[1], it.groupValues[3], today) }
        return LocalDate.parse(value)
    }

    /** Without a year, a month and day that already passed this year means next year. */
    private fun calendar(month: String, day: String, year: String, today: LocalDate): LocalDate {
        val monthValue = Month.valueOf(month.uppercase(Locale.ROOT))
        if (year.isNotEmpty()) return LocalDate.of(year.toInt(), monthValue, day.toInt())
        val thisYear = LocalDate.of(today.year, monthValue, day.toInt())
        return if (thisYear.isBefore(today)) LocalDate.of(today.year + 1, monthValue, day.toInt()) else thisYear
    }

    internal fun amount(word: String): Long = when (word) {
        "a", "an" -> 1
        in numbers -> numbers.indexOf(word) + 1L
        else -> word.toLong()
    }
}
