package com.sidekick.core

/** Removes exact word overlap; revisions and uncertain matches remain visible for review. */
object LiveText {
    fun append(previous: String, next: String, utteranceBreak: Boolean = false): String {
        if (utteranceBreak) return listOf(previous.trimEnd(), next.trim()).filter(String::isNotEmpty).joinToString("\n")
        val prefix = previous.substringBeforeLast('\n', "")
        val tail = previous.substringAfterLast('\n')
        cjkOverlap(tail, next)?.let { merged -> return if (prefix.isEmpty()) merged else "$prefix\n$merged" }
        val before = tail.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        val after = next.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        fun key(word: String) = word.lowercase().trim('.', ',', '!', '?', ':', ';')
        var overlap = 0
        for (size in minOf(30, before.size, after.size) downTo 2) {
            if (before.takeLast(size).map(::key) == after.take(size).map(::key)) { overlap = size; break }
        }
        val result = (before + after.drop(overlap)).joinToString(" ")
        return if (prefix.isEmpty()) result else prefix + "\n" + result
    }

    /** Continuous Chinese/Korean output often has no spaces. Keep original spelling and punctuation. */
    private fun cjkOverlap(before: String, after: String): String? {
        fun cjk(code: Int) = code in 0x3400..0x9fff || code in 0xac00..0xd7af || code in 0x20000..0x323af
        data class Unit(val code: Int, val end: Int)
        fun units(value: String, start: Int, end: Int): List<Unit> {
            val result = mutableListOf<Unit>(); var index = start
            while (index < end) {
                val code = value.codePointAt(index); index += Character.charCount(code)
                if (Character.isLetterOrDigit(code)) result += Unit(Character.toLowerCase(code), index)
            }
            return result
        }
        val left = units(before, maxOf(0, before.length - 256), before.length)
        val right = units(after, 0, minOf(256, after.length))
        for (size in minOf(left.size, right.size) downTo 4) {
            val suffix = left.takeLast(size)
            if (suffix.count { cjk(it.code) } < 4) continue
            if (suffix.indices.all { suffix[it].code == right[it].code }) {
                var remainder = after.substring(right[size - 1].end)
                if (before.trimEnd().lastOrNull()?.let { it in "。！？、，：；,.!?;:" } == true) {
                    val leading = remainder.takeWhile { it.isWhitespace() || it in "。！？、，：；,.!?;:" }
                    remainder = (if (leading.any { it.isWhitespace() } && leading.length < remainder.length) " " else "") + remainder.drop(leading.length)
                }
                return before.trimEnd() + remainder
            }
        }
        return null
    }
}
