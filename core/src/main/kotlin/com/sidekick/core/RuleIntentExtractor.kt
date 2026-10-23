package com.sidekick.core

/** Narrow development baseline; not a language model or a general promise detector. */
class RuleIntentExtractor : IntentExtractor {
    private val promise = Regex("^(?:I'll|I will|I promise to|I need to|I have to|I plan to|I intend to|I'm going to|I am going to|Remind me to)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val presentPlan = Regex("^I(?:'m| am) (attending|visiting|joining|meeting|travelling|traveling)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val futureTime = Regex("\\b(tomorrow|tonight|next|on (?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday))\\b", RegexOption.IGNORE_CASE)
    private val action = Regex("^(call|email|send|meet|finish|submit|bring|buy|book|pay|pick up|review|go|attend|visit|join|travel|drive|fly|leave|take|water|cook|study|exercise)\\b", RegexOption.IGNORE_CASE)
    private val progressive = mapOf("going" to "go", "attending" to "attend", "visiting" to "visit", "joining" to "join",
        "meeting" to "meet", "travelling" to "travel", "traveling" to "travel", "driving" to "drive", "flying" to "fly")
    private val uncertain = Regex("\\b(maybe|might|perhaps|if|unless|not|never|won't|would|could)\\b", RegexOption.IGNORE_CASE)

    override fun extract(transcript: Transcript): List<ProposedCommitment> {
        if (!transcript.processingAllowed || transcript.speaker != Speaker.USER) return emptyList()
        return transcript.text.split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map(String::trim).filter(String::isNotEmpty).mapNotNull { original ->
                if (original.endsWith("?")) return@mapNotNull null
                val evidence = original.trimEnd('.', '!')
                val normalized = evidence.replace('’', '\'')
                val planned = presentPlan.matchEntire(normalized)?.takeIf { futureTime.containsMatchIn(normalized) }
                var task = promise.matchEntire(normalized)?.groupValues?.get(1)
                    ?: planned?.let { "${it.groupValues[1]} ${it.groupValues[2]}" } ?: return@mapNotNull null
                task = task.replace(Regex("^be\\s+", RegexOption.IGNORE_CASE), "")
                val first = task.substringBefore(' ').lowercase()
                progressive[first]?.let { task = it + task.drop(first.length) }
                if (!action.containsMatchIn(task) || uncertain.containsMatchIn(task)) return@mapNotNull null
                ProposedCommitment(
                    title = task.replaceFirstChar { it.uppercase() }, evidence = evidence,
                    capturedAt = transcript.capturedAt, zoneId = transcript.zoneId,
                    needsReview = "Check the action and date/time. No reminder is scheduled.",
                )
            }.distinctBy { it.title.lowercase() }
    }
}
