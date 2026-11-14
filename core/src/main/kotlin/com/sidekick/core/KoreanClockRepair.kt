package com.sidekick.core

/** A second acoustic reading may repair one invalid 12-hour clock, never its context. */
object KoreanClockRepair {
    private val words = listOf("한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열", "열한", "열두", "열세", "열네", "열다섯", "열여섯", "열일곱", "열여덟", "열아홉").withIndex().associate { it.value to it.index + 1 }
    private val hourPattern = "열\\s*[0-9]|\\d{1,2}|" + words.keys.sortedByDescending { it.length }.joinToString("|")
    // A time of day before the hour means a 12-hour clock: "저녁 18시" is not something people say.
    private const val periods = "오전|오후|아침|새벽|낮|점심|저녁|밤"
    private val clock = Regex("($periods)\\s*($hourPattern)\\s*시(?:\\s*(\\d{1,2})\\s*분)?")
    private val bareClock = Regex("(?<![가-힣0-9])($hourPattern)\\s*시(?:\\s*(\\d{1,2})\\s*분)?")
    private val period = Regex(periods)
    private val days = Regex("그저께|어제|오늘|내일|모레|글피")
    private val negation = Regex("(?:^|\\s)(?:안|못)\\s")
    private fun hourOf(raw: String): Int? {
        val value = raw.replace(" ", "")
        return value.toIntOrNull() ?: words[value] ?: value.removePrefix("열").toIntOrNull()?.takeIf { value.startsWith("열") }?.plus(10)
    }
    private fun hour(m: MatchResult): Int? = hourOf(m.groupValues[2])
    fun clockText(text: String) = clock.findAll(text).joinToString(" / ") { it.value }.ifEmpty { "no supported clock" }
    fun needsRetry(text: String) = text.none { it in "?？\"“”«»「」『』" } && clock.findAll(text).singleOrNull()?.let { hour(it) !in 1..12 } == true
    /**
     * For an impossible clock no reading fixed: the original text with each plausible 12-hour reading, most likely first.
     * "저녁 18시" → 8시 (an extra "열" from linked speech), then 6시 (a 24-hour clock). Used only for confirmation, never saved as is.
     */
    fun likelyReadings(text: String): List<String> {
        if (!needsRetry(text)) return emptyList()
        val m = clock.findAll(text).single()
        val h = hour(m) ?: return emptyList()
        return listOf(h - 10, h - 12).filter { it in 1..12 }.distinct().map { text.replaceRange(m.groups[2]!!.range, it.toString()) }
    }
    /** The clock as written, e.g. "저녁 18시", for review notes. */
    fun clockOf(text: String): String? = clock.findAll(text).singleOrNull()?.value
    fun select(original: String, alternative: String): String {
        if (!needsRetry(original)) return original
        val before = clock.findAll(original).singleOrNull() ?: return original
        val minute = before.groupValues[3].ifEmpty { "0" }.toInt()
        clock.findAll(alternative).singleOrNull()?.let { after ->
            if (hour(after) in 1..12 && before.groupValues[1] == after.groupValues[1] && after.groupValues[3].ifEmpty { "0" }.toInt() == minute) {
                fun context(text: String, m: MatchResult) = text.removeRange(m.range).filter { it.isLetterOrDigit() }
                    .replace(Regex("(?:예요|에요){2,}$"), "예요")
                if (context(original, before) == context(alternative, after)) return original.replaceRange(before.range, after.value)
            }
        }
        return dropExtraTen(original, before, minute, alternative) ?: original
    }

    /**
     * "저녁 여덟 시" spoken quickly links into an "열" sound, so one model writes 18 (열+여덟) while another hears 8.
     * Only the hour digits change, and only when the readings agree on day, period, negation and minutes (owner's Pixel recording).
     */
    private fun dropExtraTen(original: String, before: MatchResult, minute: Int, alternative: String): String? {
        val from = hour(before) ?: return null
        val after = bareClock.findAll(alternative).singleOrNull() ?: return null
        val to = hourOf(after.groupValues[1]) ?: return null
        if (to !in 1..9 || from != to + 10 || after.groupValues[2].ifEmpty { "0" }.toInt() != minute) return null
        period.find(alternative)?.let { if (it.value != before.groupValues[1]) return null }
        if (days.findAll(original).map { it.value }.toSet() != days.findAll(alternative).map { it.value }.toSet()) return null
        if (negation.containsMatchIn(original) != negation.containsMatchIn(alternative)) return null
        return original.replaceRange(before.groups[2]!!.range, to.toString())
    }
}
