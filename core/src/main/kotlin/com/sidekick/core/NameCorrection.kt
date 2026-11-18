package com.sidekick.core

/**
 * Corrects near-miss Korean spellings of names the app already knows ("에슐리랑" -> "애슐리랑" when 애슐리 is known).
 * Only a word directly followed by a person particle (한테, 랑, 씨, 네 …), attached or as the next word, is changed,
 * so ordinary words that sound like a name ("사랑 같이") stay as they are. Distance is counted in jamo (letters):
 * at most 1 for two-syllable names, at most 2 for longer ones, and the closest known name must be unique.
 */
object NameCorrection {
    private val PARTICLES = listOf("이랑", "한테", "에게", "하고", "랑", "씨", "님", "네", "내", "가", "과", "와", "의", "는", "를")
    private val PERSON = setOf("이랑", "한테", "에게", "하고", "랑", "씨", "님", "네", "내")
    private val TOKEN = Regex("[가-힣]+")

    fun correct(text: String, known: Collection<String>): String {
        val names = known.map { it.trim() }.filter { it.length >= 2 && it.all { c -> c in '가'..'힣' } }.distinct()
        if (names.isEmpty()) return text
        val out = StringBuilder(); var at = 0
        for (m in TOKEN.findAll(text)) {
            out.append(text, at, m.range.first); at = m.range.last + 1
            out.append(fix(m.value, text.substring(at), names))
        }
        return out.append(text, at, text.length).toString()
    }

    private fun fix(word: String, rest: String, names: List<String>): String {
        if (word in names) return word
        // Attached particle: stem + particle. Otherwise the next word must be exactly a particle.
        for (p in PARTICLES) if (word.length > p.length && word.endsWith(p)) {
            val stem = word.dropLast(p.length)
            closest(stem, names, p)?.let { return it + p }
        }
        val next = TOKEN.find(rest)?.takeIf { rest.substring(0, it.range.first).isBlank() }?.value ?: return word
        return if (next in PARTICLES) closest(word, names, next) ?: word else word
    }

    private fun closest(stem: String, names: List<String>, particle: String): String? {
        if (stem.length < 2 || stem in names) return if (stem in names) stem else null
        val s = jamo(stem)
        val scored = names.map { it to distance(s, jamo(it)) }.sortedBy { it.second }
        val (best, d) = scored.first()
        // Short names collide with common words ("사자는", "한다는"): only after particles used for people.
        if (best.length <= 2 && particle !in PERSON) return null
        val limit = if (best.length <= 2) 1 else 2
        if (d == 0 || d > limit) return null
        if (scored.size > 1 && scored[1].second == d) return null
        return best
    }

    private fun jamo(s: String): String = buildString {
        for (c in s) {
            val i = c - '가'
            if (i !in 0..11171) { append(c); continue }
            append('ᄀ' + i / 588); append('ᅡ' + (i % 588) / 28)
            if (i % 28 != 0) append('ᆧ' + i % 28)
        }
    }

    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1); cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }
}
