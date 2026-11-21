package com.sidekick.core

/**
 * Keeps Auto-detect inside the languages the user turned on. After an automatic reading, [fallback] says which allowed
 * language to read the phrase again in, when the text is clearly in a language that is off (by its script, or by the
 * short-phrase hint for Latin-script text). Null: the reading stays.
 */
object DetectLanguages {
    private val all = SpeechLanguage.entries.filter { it != SpeechLanguage.AUTO }.toSet()

    /** One allowed language: no guessing at all, it is read as that language. */
    fun single(allowed: Set<SpeechLanguage>): SpeechLanguage? = allowed.singleOrNull()

    fun heard(text: String): SpeechLanguage? {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return null
        fun share(script: Character.UnicodeScript) = letters.count { Character.UnicodeScript.of(it.code) == script } * 2 > letters.length
        return when {
            share(Character.UnicodeScript.HANGUL) -> SpeechLanguage.KOREAN
            share(Character.UnicodeScript.HAN) -> SpeechLanguage.CHINESE
            share(Character.UnicodeScript.ARABIC) -> SpeechLanguage.ARABIC
            share(Character.UnicodeScript.CYRILLIC) -> SpeechLanguage.RUSSIAN
            else -> ShortLanguageHint.identify(text)?.takeIf { it in setOf(SpeechLanguage.ENGLISH, SpeechLanguage.FRENCH, SpeechLanguage.GERMAN) }
        }
    }

    fun fallback(text: String, allowed: Set<SpeechLanguage>): SpeechLanguage? {
        if (allowed.isEmpty() || allowed.containsAll(all)) return null
        val heard = heard(text) ?: return null
        if (heard in allowed) return null
        val latin = setOf(SpeechLanguage.ENGLISH, SpeechLanguage.FRENCH, SpeechLanguage.GERMAN)
        val order = listOf(SpeechLanguage.ENGLISH, SpeechLanguage.KOREAN, SpeechLanguage.CHINESE, SpeechLanguage.FRENCH,
            SpeechLanguage.GERMAN, SpeechLanguage.RUSSIAN, SpeechLanguage.ARABIC)
        return order.firstOrNull { it in allowed && heard in latin && it in latin } ?: order.first { it in allowed }
    }
}
