package com.sidekick.core

/** Removes exact word overlap; revisions and uncertain matches remain visible for review. */
object LiveText {
    fun append(previous: String, next: String, utteranceBreak: Boolean = false): String {
        if (utteranceBreak) return listOf(previous.trimEnd(), next.trim()).filter(String::isNotEmpty).joinToString("\n")
        val prefix = previous.substringBeforeLast('\n', "")
        val tail = previous.substringAfterLast('\n')
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
}
