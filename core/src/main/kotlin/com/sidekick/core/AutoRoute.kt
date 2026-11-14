package com.sidekick.core

/**
 * Auto-detect routing, measured on natural speech (YODAS) and a real Korean/English speaker:
 * SenseVoice runs first (≈0.2× real time) and its language tag is reliable for Korean, Chinese and English.
 * It has no French, German, Russian or Arabic, labels that speech "en" and writes little or broken text,
 * so those clips also go to Omnilingual. Omnilingual alone misreads Korean as Latin-letter nonsense.
 */
object AutoRoute {
    private val cjkTags = setOf("ko", "zh", "yue")
    private val european = Regex("\\b(?:le|la|les|des|du|et|est|une|je|vous|nous|que|qui|pas|pour|dans|c['’]est|der|die|das|und|ist|nicht|ich|wir|sie|ein|eine|mit|auf|den|dem|zu|es)\\b", RegexOption.IGNORE_CASE)
    private fun letters(text: String) = text.count { it.isLetter() }
    private fun script(text: String, vararg scripts: Character.UnicodeScript) = text.count { it.isLetter() && Character.UnicodeScript.of(it.code) in scripts }

    /**
     * True when SenseVoice's own reading can be used without asking Omnilingual.
     * [speechMs] is the voiced span (SpeakerAudio.speechSpan), not the whole clip: pauses must not make English look sparse.
     */
    fun senseVoiceSuffices(text: String, tag: String, speechMs: Long): Boolean {
        // Arabic or Russian sometimes comes back tagged "zh" as one or two syllables ("哦.", "啦。") for 5–10 s of speech.
        // Real Korean/Chinese runs ~3–5 syllables per second; count all letters so Korean with English words still passes.
        if (tag in cjkTags) return speechMs > 0 && letters(text) >= 3 && letters(text) * 1000L >= speechMs
        if (tag != "en" || speechMs <= 0) return false
        // Fluent English has ≥ 8 letters per second of speech; non-English speech labeled "en" yields "The." or word salad.
        val latin = script(text, Character.UnicodeScript.LATIN)
        return latin * 1000L >= 8L * speechMs && european.findAll(text).count() < 2
    }
    /** Voiced milliseconds of 16 kHz audio, falling back to the full length when no clear span is found. */
    fun speechMs(samples: FloatArray): Long = (SpeakerAudio.speechSpan(samples)?.let { it.last - it.first + 1 } ?: samples.size) * 1000L / 16000

    /**
     * SenseVoice's chosen reading is plain English: worth re-reading with the English-only model (Parakeet).
     * Any Hangul/Han/other script means mixed speech, which the English model would drop, so it stays with SenseVoice.
     */
    fun englishOnly(text: String, tag: String): Boolean = tag == "en" && letters(text) > 0 && script(text, Character.UnicodeScript.LATIN) == letters(text)
    /**
     * Two English models agree on real English (median 2% apart, 90% of clips under 16%) but write unrelated English-like
     * guesses for German, French, Russian or Arabic speech that SenseVoice mislabels "en" (median 57–70% apart).
     * Below 25% apart the English model's reading is used; otherwise the speech is probably not English (overnight benchmark).
     */
    fun agreeOnEnglish(sense: String, english: String): Boolean {
        val a = comparable(sense); val b = comparable(english)
        if (a.isEmpty() || b.isEmpty()) return false
        return editDistance(a, b) * 100 < 25 * maxOf(a.length, b.length)
    }
    private val numberWords = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
        "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty")
    private val tens = mapOf("thirty" to "30", "forty" to "40", "fifty" to "50")
    /** Lowercase letters and digits only; spoken numbers as digits so "seven AM" matches "7 AM". */
    private fun comparable(text: String): String = Regex("[a-z]+").replace(text.lowercase()) { m ->
        numberWords.indexOf(m.value).takeIf { it >= 0 }?.toString() ?: tens[m.value] ?: m.value
    }.filter { it.isLetterOrDigit() }
    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1); current[0] = i
            for (j in 1..b.length) current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            previous = current
        }
        return previous[b.length]
    }

    /** Both readings exist: true keeps SenseVoice's, false takes Omnilingual's. */
    fun preferSenseVoice(sense: String, tag: String, omni: String): Boolean {
        val omniLetters = letters(omni)
        if (omniLetters == 0) return letters(sense) > 0
        // Cyrillic or Arabic means Russian or Arabic speech, which SenseVoice cannot read.
        if (script(omni, Character.UnicodeScript.CYRILLIC, Character.UnicodeScript.ARABIC) * 2 >= omniLetters) return false
        // Korean/Chinese speech SenseVoice did not tag: its reading is the better one when it has CJK text.
        if (script(sense, Character.UnicodeScript.HANGUL, Character.UnicodeScript.HAN) * 2 >= letters(sense) && letters(sense) > 0) return true
        // French or German function words: Omnilingual heard a language SenseVoice does not know.
        if (european.findAll(omni).count() >= 2) return false
        // English: SenseVoice is far more accurate unless it dropped most of the speech.
        return tag == "en" && letters(sense) * 10 >= omniLetters * 6
    }
}
