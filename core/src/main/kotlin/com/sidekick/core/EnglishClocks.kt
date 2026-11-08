package com.sidekick.core

/** Normalize explicit spoken clocks for parsing only; keep original evidence unchanged. */
internal object EnglishClocks {
    private val hours = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")
    private val spoken = Regex("\\b(${hours.joinToString("|")})(?:\\s+o['’]clock)?\\s+(a\\.?m\\.?|p\\.?m\\.?|in the morning|in the afternoon|in the evening|at night)(?!\\w)", RegexOption.IGNORE_CASE)
    fun normalize(text: String): String = spoken.replace(text) {
        val hour = hours.indexOf(it.groupValues[1].lowercase()) + 1
        val suffix = it.groupValues[2].lowercase()
        if (suffix == "at night" && hour in 1..5) return@replace it.value
        "$hour${if (suffix.startsWith("a.") || suffix == "am" || suffix == "in the morning" || suffix == "at night" && hour == 12) "am" else "pm"}"
    }
}
