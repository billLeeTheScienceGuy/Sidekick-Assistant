package com.sidekick.core

/** Literal subject matching at an already identical timestamp, never fuzzy identity. */
object ActivityIdentity {
    private val noun = Regex("\\b(party|meeting|appointment|class|lecture|concert|dinner|lunch|wedding|birthday|practice|workshop|movie|trip|shift|call)\\b")
    private val filler = setOf("i", "i'll", "i'm", "will", "am", "there's", "there", "is", "are", "a", "an", "the", "to", "go", "going", "be", "attend", "attending", "join", "joining", "plan", "tomorrow", "today", "tonight", "yeah", "actually")
    fun subject(evidence: String): String? {
        val text = evidence.substringBefore('\n').lowercase(java.util.Locale.ROOT).replace('’', '\'')
        val event = noun.find(text) ?: return null
        fun words(value: String) = Regex("[\\p{L}\\p{N}']+").findAll(value).map { it.value }.filter { it !in filler }.toList()
        val suffix = text.substring(event.range.last + 1).split(Regex("\\b(?:tomorrow|today|tonight|at|on|starts?|ends?|monday|tuesday|wednesday|thursday|friday|saturday|sunday|\\d{4}-\\d{2}-\\d{2})\\b"), limit = 2).first()
        return (words(text.take(event.range.first)) + event.value + words(suffix)).joinToString(" ")
    }
}
