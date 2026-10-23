package com.sidekick.core

data class IntentCandidate(val action: String, val evidence: String, val timePhrase: String)

/** Model fields are proposals. Evidence, ownership and dates still need code/user checks. */
object IntentValidation {
    private fun normalize(text: String) = text.replace('’', '\'').replace(Regex("\\s+"), " ").trim()
    private val owner = Regex("\\b(I|I'll|I'm|I've|my|me)\\b", RegexOption.IGNORE_CASE)
    private val unsafe = Regex("\\b(not|never|won't|maybe|might|if|unless|would|could|said|says|told|pretend|imagine)\\b", RegexOption.IGNORE_CASE)
    private val words = Regex("[\\p{L}\\p{N}]+")
    private val filler = setOf("a", "an", "the", "to", "for", "on", "by", "at", "with", "and", "my", "me", "i", "will")
    private val event = Regex("\\b(birthday|party|wedding|meeting|appointment|dinner|lunch|concert|class|conference|ceremony|reunion|graduation|funeral|show|game|event)\\b", RegexOption.IGNORE_CASE)
    private val forms = mapOf("going" to "go", "attending" to "attend", "visiting" to "visit",
        "travelling" to "travel", "traveling" to "travel", "driving" to "drive", "flying" to "fly",
        "joining" to "join", "meeting" to "meet", "leaving" to "leave", "taking" to "take",
        "bringing" to "bring", "sending" to "send", "calling" to "call", "watering" to "water")
    private fun canonical(word: String) = forms[word] ?: word

    fun validate(source: Transcript, candidates: List<IntentCandidate>): List<ProposedCommitment> {
        require(source.text.length <= 4000) { "Use at most 4,000 characters for smart analysis." }
        require(candidates.size <= 6) { "Too many model suggestions. Try a shorter note." }
        if (!source.processingAllowed || source.speaker != Speaker.USER) return emptyList()
        val original = normalize(source.text)
        return candidates.mapNotNull { candidate ->
            val evidence = normalize(candidate.evidence)
            val action = normalize(candidate.action)
            val time = normalize(candidate.timePhrase)
            if (evidence.length !in 5..1500 || action.length !in 3..200 || time.length > 160) return@mapNotNull null
            if (!original.contains(evidence) || !owner.containsMatchIn(evidence) || unsafe.containsMatchIn(evidence)) return@mapNotNull null
            val position = original.indexOf(evidence)
            val sentenceStart = original.take(position).indexOfLast { it in ".!?" } + 1
            val evidenceEnd = position + evidence.length
            val sentenceEnd = if (original[evidenceEnd - 1] in ".!?") evidenceEnd else {
                original.indexOfAny(charArrayOf('.', '!', '?'), evidenceEnd).let { if (it < 0) original.length else it + 1 }
            }
            if (unsafe.containsMatchIn(original.substring(sentenceStart, sentenceEnd))) return@mapNotNull null
            val prefix = original.substring(maxOf(0, position - 120), position)
            if (Regex("\\b(said|says|told|quoted|pretend|imagine)\\b", RegexOption.IGNORE_CASE).containsMatchIn(prefix)) return@mapNotNull null
            if (original.take(position).count { it == '"' } % 2 == 1 || prefix.lastIndexOf('“') > prefix.lastIndexOf('”')) return@mapNotNull null
            if (evidence.contains('"') || evidence.contains('“') || evidence.endsWith('?')) return@mapNotNull null
            if (time.isNotEmpty() && !evidence.contains(time)) return@mapNotNull null
            val evidenceWords = words.findAll(evidence.lowercase()).map { canonical(it.value) }.toMutableSet()
            if (event.containsMatchIn(evidence) && evidenceWords.any { it in setOf("go", "join", "attend") }) {
                evidenceWords.addAll(setOf("go", "join", "attend"))
            }
            if (words.findAll(action.lowercase()).any { canonical(it.value) !in evidenceWords && it.value !in filler }) return@mapNotNull null
            ProposedCommitment(action.replaceFirstChar { it.uppercase() }, evidence, source.capturedAt, source.zoneId,
                "Local model proposal. Time wording: ${time.ifEmpty { "unspecified" }}. Review the action and reminder time.")
        }.distinctBy { it.title.lowercase() to it.evidence }
    }
}
