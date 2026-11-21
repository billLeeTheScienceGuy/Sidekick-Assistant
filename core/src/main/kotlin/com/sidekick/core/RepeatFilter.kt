package com.sidekick.core

/**
 * Collapses recognizer loops: the same word or phrase repeated many times in a row ("I'll call you. I'll call you.
 * I'll call you. …", "谢谢谢谢谢谢"), a classic decoder failure. Keeps the first copy.
 * - A phrase of 4+ words: 3 or more copies in a row become one. 2–3 words: 4 or more. One word: 6 or more.
 *   So ordinary speech stays: "no, no", "very, very, very, very hot", "Let's get it. Let's get it. Let's get it.".
 * - Inside unspaced Chinese/Japanese/Korean text: a unit of 4+ different characters repeated 3+ times, or 2–3
 *   characters repeated 4+ times, becomes one; runs of one character (laughter, "ㅋㅋㅋㅋ", "哈哈哈哈") stay.
 * Checked against about 20,000 human transcripts (none changed) and the recognizer output saved with them.
 * Words compare by letters and digits only, so commas and periods between copies don't hide a loop.
 */
object RepeatFilter {
    private const val MAX_WORDS = 12
    private const val MAX_CHARS = 16

    /** Copies needed before a run counts as a loop. Real speech repeats short things more than long ones. */
    private fun wordCopies(words: Int) = when { words == 1 -> 6; words <= 3 -> 4; else -> 3 }
    private fun charCopies(chars: Int) = if (chars <= 3) 4 else 3

    fun collapse(text: String): String {
        if (text.isBlank()) return text
        val words = text.trim().split(Regex("\\s+"))
        val collapsedWords = collapseRuns(words, ::wordKey, MAX_WORDS, copies = ::wordCopies)
        val inside = collapsedWords.map(::collapseInsideWord)
        // Unchanged text keeps its original spacing exactly.
        return if (collapsedWords.size == words.size && inside == collapsedWords) text else inside.joinToString(" ")
    }

    /** True when [collapse] would change the text (for counting). */
    fun hasLoop(text: String): Boolean = collapse(text) != text

    private fun wordKey(word: String) = word.filter { it.isLetterOrDigit() }.lowercase()

    private fun cjk(code: Int) = Character.UnicodeScript.of(code).let {
        it == Character.UnicodeScript.HAN || it == Character.UnicodeScript.HANGUL || it == Character.UnicodeScript.HIRAGANA ||
            it == Character.UnicodeScript.KATAKANA
    }

    private fun collapseInsideWord(word: String): String {
        val codes = word.codePoints().toArray().toList()
        if (codes.size < 6 || codes.count(::cjk) * 2 < codes.size) return word
        val units = codes.map { String(Character.toChars(it)) }
        val kept = collapseRuns(units, { it.lowercase() }, MAX_CHARS, minimumUnit = 2, copies = ::charCopies)
        return kept.joinToString("")
    }

    /**
     * Left to right, finds the longest stretch made of one unit repeated [minimumCopies] times or more and keeps one
     * copy of that unit. Units with an empty key (punctuation only) never start a loop.
     */
    private fun <T> collapseRuns(items: List<T>, key: (T) -> String, maxUnit: Int, minimumUnit: Int = 1,
        copies: (Int) -> Int): List<T> {
        val minimumCopies = copies
        val keys = items.map(key)
        val out = ArrayList<T>(items.size)
        var i = 0
        while (i < items.size) {
            var bestSize = 0; var bestCopies = 0
            for (size in minimumUnit..minOf(maxUnit, (items.size - i) / 2)) {
                if ((0 until size).any { keys[i + it].isEmpty() }) continue
                // A unit of one repeated item ("ㅋㅋ", "the the") is just a shorter unit: judged at its own size.
                if (size > 1 && (1 until size).all { keys[i + it] == keys[i] }) continue
                var copies = 1
                while (i + (copies + 1) * size <= items.size && (0 until size).all { keys[i + copies * size + it] == keys[i + it] }) copies++
                if (copies >= minimumCopies(size) && copies * size > bestCopies * bestSize) { bestSize = size; bestCopies = copies }
            }
            if (bestSize == 0) { out += items[i]; i++ }
            else { out += items.subList(i, i + bestSize); i += bestSize * bestCopies }
        }
        return out
    }
}
