package com.sidekick.core

/**
 * Names the user listed ("Sarah 세라") fixed inside Korean speech. SenseVoice cannot be given a word list; the Korean
 * Zipformer can (hotwords) but is weaker overall. So the Zipformer re-reads Korean phrases with the list, and only the name
 * is spliced into SenseVoice's sentence at the aligned position. Benchmark (48 synthetic Korean sentences with 16 English
 * names + the owner's recordings): names right 28/50 → 40/49 with the list at strength 1.5, and no invented names in
 * 135 name-free Korean clips.
 */
object NameSplice {
    data class Name(val display: String, val forms: List<String>)

    /** One name per line or comma: an English spelling and/or Korean spellings, e.g. "Sarah 세라 사라". */
    fun parse(text: String): List<Name> = text.split('\n', ',', ';').mapNotNull { entry ->
        val parts = entry.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val hangul = parts.filter { part -> part.all { it in '가'..'힣' } }.distinct()
        val latin = parts.filter { part -> part.any { it.isLetter() } && part.none { it in '가'..'힣' } }
        if (hangul.isEmpty()) null else Name((latin.joinToString(" ").ifEmpty { hangul.first() }).take(40), hangul.take(4))
    }.distinctBy { it.display }.take(50)

    /** The word list for the Korean model: every Korean spelling, one per line. */
    fun hotwords(names: List<Name>): String = names.flatMap { it.forms }.distinct().joinToString("\n")

    /** [sense] is SenseVoice's reading, [listed] the Zipformer's reading with the name list. */
    fun apply(sense: String, listed: String, names: List<Name>): String {
        var result = sense
        for (name in names) for (form in name.forms.sortedByDescending { it.length }) {
            if (form !in listed.replace(" ", "")) continue
            if (name.display in result || name.forms.any { standalone(it).containsMatchIn(result) }) break
            val span = alignedSpan(result, listed, form) ?: continue
            // Same letters in SenseVoice's reading: not a misheard name but a word containing it ("사라졌어요" = disappeared).
            if (result.substring(span).filter { !it.isWhitespace() } == form) continue
            result = result.replaceRange(span, name.display) // the splice already knows it's a name, even when glued to a word
            break
        }
        return normalize(result, names)
    }

    /** Listed Korean spellings, as whole words (optionally with a particle), are shown as the name the user typed. */
    fun normalize(text: String, names: List<Name>): String = names.fold(text) { acc, name ->
        name.forms.filter { it != name.display }.sortedByDescending { it.length }.fold(acc) { t, form -> standalone(form).replace(t, name.display) }
    }

    private const val particles = "이랑|랑|한테|에게|께서|께|가|이|는|은|를|을|의|도|와|과|네|씨|님|이가|이는|이를|이도|이한테"
    private fun standalone(form: String) = Regex("(?<![가-힣])${Regex.escape(form)}(?=(?:$particles)?(?![가-힣]))")

    /** Range in [sense] that the alignment of the space-free readings maps to [form] in [listed], if it is clean. */
    private fun alignedSpan(sense: String, listed: String, form: String): IntRange? {
        val senseIndex = sense.indices.filter { !sense[it].isWhitespace() }
        val a = senseIndex.map { sense[it] }.joinToString(""); val b = listed.filter { !it.isWhitespace() }
        val at = b.indexOf(form); if (at < 0 || a.isEmpty() || a.length > 400 || b.length > 400) return null
        val d = Array(a.length + 1) { i -> IntArray(b.length + 1) { j -> if (i == 0) j else if (j == 0) i else 0 } }
        for (i in 1..a.length) for (j in 1..b.length)
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        // Walk back: which characters of a sit opposite b[at until at+form.length]?
        val opposite = mutableListOf<Int>()
        var i = a.length; var j = b.length
        while (i > 0 && j > 0) {
            val inForm = j - 1 in at until at + form.length
            when {
                d[i][j] == d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1 -> { if (inForm) opposite += i - 1; i--; j-- }
                d[i][j] == d[i - 1][j] + 1 -> { if (j - 1 in at..at + form.length - 2) opposite += i - 1; i-- } // extra syllable inside the name
                else -> j--
            }
        }
        if (opposite.isEmpty()) return null
        val first = opposite.min(); val last = opposite.max()
        if (last - first + 1 !in form.length - 1..form.length + 1) return null
        // The name's neighbours must agree, so it lands in the same place (e.g. "점심에 | 브랜든 | 네").
        val before = a.getOrNull(first - 1); val after = a.getOrNull(last + 1)
        if (before != null && at > 0 && before != b[at - 1] && first > 0 && a.getOrNull(first - 2) != b.getOrNull(at - 2)) return null
        if (after != null && at + form.length < b.length && after != b[at + form.length] && a.getOrNull(last + 2) != b.getOrNull(at + form.length + 1)) return null
        return senseIndex[first]..senseIndex[last]
    }
}
