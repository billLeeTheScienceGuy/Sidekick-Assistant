package com.sidekick.core

/** A second acoustic reading may repair one invalid 12-hour clock, never its context. */
object KoreanClockRepair {
    private val words = listOf("한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열", "열한", "열두", "열세", "열네", "열다섯", "열여섯", "열일곱", "열여덟", "열아홉").withIndex().associate { it.value to it.index + 1 }
    private val clock = Regex("(오전|오후)\\s*(열\\s*[0-9]|\\d{1,2}|" + words.keys.sortedByDescending { it.length }.joinToString("|") + ")\\s*시(?:\\s*(\\d{1,2})\\s*분)?")
    private fun hour(m: MatchResult): Int? {
        val value = m.groupValues[2].replace(" ", "")
        return value.toIntOrNull() ?: words[value] ?: value.removePrefix("열").toIntOrNull()?.takeIf { value.startsWith("열") }?.plus(10)
    }
    fun clockText(text: String) = clock.findAll(text).joinToString(" / ") { it.value }.ifEmpty { "no supported clock" }
    fun needsRetry(text: String) = text.none { it in "?？\"“”«»「」『』" } && clock.findAll(text).singleOrNull()?.let { hour(it) !in 1..12 } == true
    fun select(original: String, alternative: String): String {
        if (!needsRetry(original)) return original
        val before = clock.findAll(original).singleOrNull() ?: return original
        val after = clock.findAll(alternative).singleOrNull() ?: return original
        if (hour(after) !in 1..12 || before.groupValues[1] != after.groupValues[1]) return original
        val minute = after.groupValues[3].ifEmpty { "0" }.toInt()
        if (minute !in 0..59 || minute != before.groupValues[3].ifEmpty { "0" }.toInt()) return original
        fun context(text: String, m: MatchResult) = text.removeRange(m.range).filter { it.isLetterOrDigit() }
            .replace(Regex("(?:예요|에요){2,}$"), "예요")
        return if (context(original, before) == context(alternative, after))
            original.replaceRange(before.range, after.value) else original
    }
}
