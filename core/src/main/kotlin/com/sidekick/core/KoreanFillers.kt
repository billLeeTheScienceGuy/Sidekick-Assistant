package com.sidekick.core

/**
 * Korean recognizers sometimes write a hesitation sound as its own word ("회의가 어 있어요"). A lone filler between two
 * Hangul words is dropped. A filler that starts the text or follows punctuation ("어, 알았어" = "yeah, got it") stays,
 * and nothing inside a word is touched.
 */
object KoreanFillers {
    private val fillers = setOf("어", "음", "으", "으음", "어어", "음음", "흠")
    private fun hangul(word: String) = word.isNotEmpty() && word.all { it in '가'..'힣' || it.isDigit() }

    fun strip(text: String): String {
        val words = text.split(' ')
        if (words.size < 3) return text
        val kept = words.filterIndexed { i, word ->
            !(word in fillers && i > 0 && i < words.size - 1 && hangul(words[i - 1]) && words[i + 1].isNotEmpty() &&
                (words[i + 1][0] in '가'..'힣'))
        }
        return if (kept.size == words.size) text else kept.joinToString(" ")
    }
}
