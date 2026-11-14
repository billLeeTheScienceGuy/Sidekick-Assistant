package com.sidekick.core

/**
 * Drops recognizer output that is not speech: music and noise come back as a scrap ("그.", "Angel.") from many seconds
 * of audio, or in a script none of the app's languages use ("Loぱ発キ", "bামnاخ"). Measured on 2,154 final readings of
 * real speech (FLEURS, YODAS, noisy/far scenarios, the owner's recordings): under 0.5 letters per second over 2+ seconds
 * happened 11 times, all junk ("I.", "а", "س"); a quarter or more letters in another script only in wrong-language guesses.
 */
object PhraseFilter {
    private val supported = setOf(Character.UnicodeScript.LATIN, Character.UnicodeScript.HANGUL, Character.UnicodeScript.HAN,
        Character.UnicodeScript.CYRILLIC, Character.UnicodeScript.ARABIC)

    fun isNoise(text: String, audioMs: Long): Boolean {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return false // blank text is already ignored
        val foreign = letters.count { Character.UnicodeScript.of(it.code) !in supported }
        if (foreign * 4 >= letters.length) return true
        return audioMs >= 2_000 && letters.length * 2_000L < audioMs
    }
}
