package com.sidekick.core

/** Normalize explicit spoken clocks for parsing only; keep original evidence unchanged. */
internal object EnglishClocks {
    private val hours = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")
    private val spoken = Regex("\\b(${hours.joinToString("|")})(?:\\s+o['’]clock)?\\s+(a\\.?\\s*m\\.?|p\\.?\\s*m\\.?|in the morning|in the afternoon|in the evening|at night)(?!\\w)", RegexOption.IGNORE_CASE)
    // "twelve thirty", "nine fifteen", "ten forty-five": spoken hour plus a common quarter-hour minute.
    private val spokenMinutes = Regex("\\b(${hours.joinToString("|")})\\s+(fifteen|thirty|forty[- ]five)(?!\\w)", RegexOption.IGNORE_CASE)
    // SenseVoice writes "at 1230" / "at 930"; only after a time preposition and only 1–12 o'clock, never a year like 2015.
    private val packedClock = Regex("\\b(at|by|until|till|from|around)\\s+(1[0-2]|[1-9])([0-5]\\d)(?!\\d)", RegexOption.IGNORE_CASE)
    /** "at 1230" and "twelve thirty" as (hour, minute, position) for parsers that keep original-text positions. */
    fun digitless(text: String): List<Triple<Int, Int, Int>> =
        packedClock.findAll(text).map { Triple(it.groupValues[2].toInt(), it.groupValues[3].toInt(), it.groups[2]!!.range.first) }.toList() +
        spokenMinutes.findAll(text).map { m -> Triple(hours.indexOf(m.groupValues[1].lowercase()) + 1,
            when (m.groupValues[2].lowercase().replace('-', ' ')) { "fifteen" -> 15; "thirty" -> 30; else -> 45 }, m.range.first) }.toList()
    fun normalize(text: String): String {
        val packed = packedClock.replace(text) { "${it.groupValues[1]} ${it.groupValues[2]}:${it.groupValues[3]}" }
        val minutes = spokenMinutes.replace(packed) { m ->
            val hour = hours.indexOf(m.groupValues[1].lowercase()) + 1
            "$hour:" + when (m.groupValues[2].lowercase().replace('-', ' ')) { "fifteen" -> "15"; "thirty" -> "30"; else -> "45" }
        }
        val joined = Regex("(?<=\\d)\\s*([ap])\\.?\\s+m\\.?(?!\\w)", RegexOption.IGNORE_CASE).replace(minutes) { "${it.groupValues[1]}m" }
        return spoken.replace(joined) {
        val hour = hours.indexOf(it.groupValues[1].lowercase()) + 1
        val suffix = it.groupValues[2].lowercase().let { value -> if (value.first() in "ap" && !value.startsWith("at")) value.replace(Regex("\\s+"), "") else value }
        if (suffix == "at night" && hour in 1..5) return@replace it.value
        "$hour${if (suffix.startsWith("a.") || suffix == "am" || suffix == "in the morning" || suffix == "at night" && hour == 12) "am" else "pm"}"
        }
    }
}
