package com.sidekick.core

/** Removes exact word overlap; revisions and uncertain matches remain visible for review. */
object LiveText {
    fun append(previous: String, next: String): String {
        val before = previous.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        val after = next.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        fun key(word: String) = word.lowercase().trim('.', ',', '!', '?', ':', ';')
        var overlap = 0
        for (size in minOf(30, before.size, after.size) downTo 2) {
            if (before.takeLast(size).map(::key) == after.take(size).map(::key)) { overlap = size; break }
        }
        return (before + after.drop(overlap)).joinToString(" ")
    }
}
