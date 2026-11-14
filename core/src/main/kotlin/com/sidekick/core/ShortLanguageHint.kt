package com.sidekick.core

/** Conservative text hint for a second acoustic read, never a source of event facts. */
object ShortLanguageHint {
    fun identify(text: String): SpeechLanguage? {
        val letters = text.codePoints().toArray().filter { Character.isLetter(it) }
        if (letters.isEmpty()) return null
        // A forced single-language retry can erase another language or a place name.
        val scripts = letters.groupingBy { Character.UnicodeScript.of(it) }.eachCount()
        val latin = scripts[Character.UnicodeScript.LATIN] ?: 0
        val nonLatin = scripts.filterKeys { it != Character.UnicodeScript.LATIN && it != Character.UnicodeScript.COMMON }
        if (latin >= 3 && nonLatin.values.any { it >= 2 }) return null
        if (nonLatin.values.count { it >= 2 } > 1) return null
        val lower = text.lowercase(java.util.Locale.ROOT)
        val counts = linkedMapOf(
            SpeechLanguage.KOREAN to letters.count { it in 0xac00..0xd7af },
            SpeechLanguage.CHINESE to letters.count { it in 0x3400..0x9fff },
            SpeechLanguage.RUSSIAN to letters.count { it in 0x0400..0x052f },
            SpeechLanguage.ARABIC to letters.count { it in 0x0600..0x06ff || it in 0x0750..0x077f },
        )
        if (counts.values.count { it >= 3 } > 1) return null
        counts.entries.firstOrNull { it.value >= 3 && it.value * 2 >= letters.size }?.let { candidate ->
            if (candidate.key == SpeechLanguage.RUSSIAN && listOf("перенеси", "встреч", "завтра", "отмени", "пойду", "собираюсь", "часов").count { lower.contains(it) } < 2) return null
            if (candidate.key == SpeechLanguage.CHINESE && (letters.any { it in 0x3040..0x30ff } ||
                listOf("明天", "会议", "會議", "下午", "点", "點", "聚会", "聚會", "取消", "不要", "结束", "結束").count { lower.contains(it) } < 2)) return null
            return candidate.key
        }
        if (counts.values.any { it > 0 }) return null
        val words = Regex("[\\p{L}]+(?:['’][\\p{L}]+)?").findAll(lower).map { it.value }.toSet()
        val vocab = mapOf(
            SpeechLanguage.ENGLISH to setOf("i", "i'll", "the", "don't", "meeting", "tomorrow", "morrow", "cancel", "move", "attend", "at", "appointment"),
            SpeechLanguage.FRENCH to setOf("je", "la", "le", "les", "réunion", "demain", "annule", "déplace", "pas", "une", "participerai"),
            SpeechLanguage.GERMAN to setOf("ich", "das", "treffen", "uhr", "morgen", "verschiebe", "nicht", "ab", "an", "einem", "nehme", "sag", "um"),
        )
        val ranked = vocab.map { (language, clues) -> language to words.count { it in clues } }.sortedByDescending { it.second }
        return ranked.first().takeIf { it.second >= 2 && ranked[1].second < 2 && it.second - ranked[1].second >= 2 }?.first
    }
    /** Very short CJK-looking speech only; do not erase another supported script. */
    fun cjkFallback(text: String, speechSamples: Int): Boolean {
        if(speechSamples !in 1600..48_000)return false
        val letters=text.codePoints().toArray().filter{Character.isLetter(it)}
        if(letters.any{Character.UnicodeScript.of(it) !in setOf(Character.UnicodeScript.HAN,Character.UnicodeScript.HANGUL,Character.UnicodeScript.LATIN)})return false
        return letters.count{Character.UnicodeScript.of(it) in setOf(Character.UnicodeScript.HAN,Character.UnicodeScript.HANGUL)}>=2
    }
    /** Korean, alone or mixed with English, at any length: Omnilingual garbles it, so SenseVoice must hear it too. */
    fun koreanSpeech(text: String): Boolean {
        val letters=text.codePoints().toArray().filter{Character.isLetter(it)}
        if(letters.any{Character.UnicodeScript.of(it) !in setOf(Character.UnicodeScript.HAN,Character.UnicodeScript.HANGUL,Character.UnicodeScript.LATIN)})return false
        return letters.count{Character.UnicodeScript.of(it)==Character.UnicodeScript.HANGUL}>=2
    }
    /**
     * SenseVoice reads Korean well but drops English spoken before or after it; Omnilingual keeps that English.
     * Restore only the primary reading's leading/trailing Latin runs. English inside the Korean is never spliced.
     */
    fun restoreLatinEdges(primary: String, candidate: String): String {
        val cjk = Regex("[\\p{IsHangul}\\p{IsHan}]")
        val first = cjk.find(primary)?.range?.first ?: return candidate
        val last = cjk.findAll(primary).last().range.last
        fun latinRun(text: String) = text.trim().takeIf { Regex("\\p{IsLatin}{3,}").containsMatchIn(it) && !cjk.containsMatchIn(it) }
        val spoken = candidate.lowercase(java.util.Locale.ROOT)
        // Restore a run only when the candidate dropped most of it; a few rewritten words ("twelve thirty" -> "1230") are not a drop.
        fun missing(run: String?) = run?.takeIf { r ->
            val words = Regex("\\p{IsLatin}{3,}").findAll(r).map { it.value.lowercase(java.util.Locale.ROOT) }.toList()
            words.count { spoken.contains(it) } * 2 < words.size
        }
        val lead = missing(latinRun(primary.substring(0, first)))
        val trail = missing(latinRun(primary.substring(last + 1)))
        return listOfNotNull(lead, candidate.trim(), trail).joinToString(" ")
    }
    /** Latin script only (English, French, German): Whisper may read it again. Digits and punctuation are ignored. */
    fun latinOnly(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        return letters.length >= 3 && letters.all { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.LATIN }
    }
    /**
     * At least as many Korean words as English words: a forced-Korean reading may write the English in Hangul.
     * Counted in words, not letters: "dentist appointment" has twice the letters of "금요일 오전 10시에 있어요".
     */
    fun koreanDominant(text: String): Boolean {
        val words = Regex("[\\p{L}\\p{N}]+").findAll(text).map { it.value }.toList()
        val hangul = words.count { w -> w.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HANGUL } }
        val latin = words.count { w -> w.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.LATIN } && w.none { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HANGUL } }
        return hangul >= 2 && hangul >= latin
    }
    /** SenseVoice's own language guess can render Korean speech as English-sounding words; such a reading is not a Korean retry. */
    fun keepsKorean(primary: String, candidate: String): Boolean {
        fun hangul(text: String) = text.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HANGUL }
        return hangul(candidate) * 2 >= hangul(primary)
    }
    fun preservesLatin(primary: String, candidate: String): Boolean {
        val spoken=candidate.filter{Character.UnicodeScript.of(it.code)==Character.UnicodeScript.LATIN}.lowercase(java.util.Locale.ROOT)
        return Regex("\\p{IsLatin}{3,}").findAll(primary).all{spoken.contains(it.value.lowercase(java.util.Locale.ROOT))}
    }
}
