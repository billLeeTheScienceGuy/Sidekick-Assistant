package com.sidekick.core

/** Conservative text hint for a second acoustic read, never a source of event facts. */
object ShortLanguageHint {
    fun identify(text: String): SpeechLanguage? {
        val letters = text.codePoints().toArray().filter { Character.isLetter(it) }
        if (letters.isEmpty()) return null
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
        return ranked.first().takeIf { it.second >= 2 && it.second - ranked[1].second >= 2 }?.first
    }
}
