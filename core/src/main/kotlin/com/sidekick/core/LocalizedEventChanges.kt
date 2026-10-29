package com.sidekick.core

/** Explicit correction forms. Original target wording is retained for event association. */
object LocalizedEventChanges {
    private fun rx(text: String) = Regex(text, RegexOption.IGNORE_CASE)
    private val blocked = rx("않|아니|말고|만약|아마|하지\\s*마|(?<![가-힣])안\\s|不|别|別|如果|可能|说|說|\\b(?:pas|jamais|peut-être|si|dit|disait|nicht|kein\\w*|vielleicht|wenn|falls|sagte|не|если|возможно|сказал\\w*|говорит)\\b")
    private val cancellation = listOf(
        rx("^(.+?)(?:을|를)\\s*취소\\s*(?:해\\s*(?:줘|주세요)?|하세요)[.!。]*$"),
        rx("^(.+?)(?:이|가)\\s*취소\\s*(?:됐어|되었습니다|됐어요)[.!。]*$"),
        rx("^取消\\s*(.+?)[.!。]*$"), rx("^(.+?)(?:已取消|取消了)[.!。]*$"),
        rx("^annule(?:z)?\\s+(.+?)[.!]*$"), rx("^(.+?)\\s+est\\s+annul[ée]e?[.!]*$"),
        rx("^(?:sage|sag|sagt)\\s+(.+?)\\s+ab[.!]*$"), rx("^(.+?)\\s+(?:ist|wurde)\\s+abgesagt[.!]*$"),
        rx("^отмени\\s+(.+?)[.!]*$"), rx("^(.+?)\\s+отмен[её]н[ао]?[.!]*$")
    )
    private val movement = listOf(
        rx("^(.+?)(?:을|를)\\s*(.+?)(?:으로|로)\\s*(?:옮겨\\s*줘|변경\\s*해\\s*(?:줘|주세요)|바꿔\\s*줘)[.!。]*$"),
        rx("^把\\s*(.+?)(?:改到|移到|改为|改為)\\s*(.+?)[.!。]*$"),
        rx("^(?:déplacez?|reporte[rz]?|reprogramme[rz]?)\\s+(.+?)\\s+(?:à|pour)\\s+(.+?)[.!]*$"),
        rx("^verschiebe\\s+(.+?)\\s+(?:auf|nach)\\s+(.+?)[.!]*$"),
        rx("^перенеси\\s+(.+?)\\s+на\\s+(.+?)[.!]*$")
    )
    fun looksLike(text: String) = rx("취소|옮겨|변경|바꿔|取消|改到|移到|改为|改為|\\b(?:annul[ée]\\p{L}*|déplace\\p{L}*|reporte\\p{L}*|reprogramme\\p{L}*|verschiebe|abgesagt|отмени|отмен[её]н[ао]?|перенеси)\\b").containsMatchIn(text)
    private fun normalizeTarget(text: String): String {
        val clean = text.trim().lowercase(java.util.Locale.ROOT)
            .replace(rx("^(?:l['’]|(?:le|la|les|der|die|das|den|dem)\\s+)"), "")
            .replace(rx("\\bвстречу\\b"), "встреча").replace(rx("\\bвечеринку\\b"), "вечеринка")
        // ASR can duplicate the possessive particle before the command's object particle.
        val target = if (clean.endsWith("의") && LocalizedActivities.bareEventKey(clean.dropLast(1).trimEnd()) != null)
            clean.dropLast(1).trimEnd() else clean
        return if (target in setOf("그거", "그것", "그 행사", "它", "这个活动", "這個活動", "那个活动", "那個活動", "ça", "cela", "das", "es", "это")) "it" else target
    }
    fun parse(text: String): EventChange? {
        if (ArabicActivities.isArabic(text)) return ArabicActivities.parseChange(text)
        val clean = text.trim()
        if (clean.endsWith('?') || clean.endsWith('？') || clean.any { it in "\"“”«»「」『』" } || blocked.containsMatchIn(clean)) return null
        for (pattern in cancellation) pattern.matchEntire(clean)?.let {
            val subject = normalizeTarget(it.groupValues[1]); if (subject.length in 1..80) return EventChange(subject, true, "", text, localized = true)
        }
        for (pattern in movement) pattern.matchEntire(clean)?.let {
            val subject = normalizeTarget(it.groupValues[1]); if (subject.length in 1..80) return EventChange(subject, false, it.groupValues[2], text, localized = true)
        }
        return null
    }
    fun matches(target: String, title: String): Boolean {
        if (ArabicActivities.isArabic(target)) {
            val kind = ArabicActivities.bareKind(target)
            return if (kind != null) LocalizedActivities.eventKey(title) == kind else ArabicActivities.normalized(title).contains(ArabicActivities.normalized(target))
        }
        val literal = if (rx("[가-힣\\u3400-\\u9fff]").containsMatchIn(target)) title.contains(target, true)
            else Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(target) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(normalizeTarget(title))
        if (literal) return true
        val kind = LocalizedActivities.bareEventKey(target) ?: return false
        return LocalizedActivities.eventKey(title) == kind
    }
}
