package com.sidekick.core

/** Narrow development baseline; not a language model or a general promise detector. */
class RuleIntentExtractor : IntentExtractor {
    private val promise = Regex("^(?:I'll|I will|I promise to|I need to|I have to|I'm going to|I am going to|Remind me to)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val action = Regex("^(call|email|send|meet|finish|submit|bring|buy|book|pay|pick up|review)\\b", RegexOption.IGNORE_CASE)
    private val uncertain = Regex("\\b(maybe|might|perhaps|if|unless|not|never|won't|would|could)\\b", RegexOption.IGNORE_CASE)

    override fun extract(transcript: Transcript): List<ProposedCommitment> {
        if (!transcript.processingAllowed || transcript.speaker != Speaker.USER) return emptyList()
        return transcript.text.split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map(String::trim).filter(String::isNotEmpty).mapNotNull { original ->
                if (original.endsWith("?")) return@mapNotNull null
                val evidence = original.trimEnd('.', '!')
                val normalized = evidence.replace('’', '\'')
                val task = promise.matchEntire(normalized)?.groupValues?.get(1) ?: return@mapNotNull null
                if (!action.containsMatchIn(task) || uncertain.containsMatchIn(task)) return@mapNotNull null
                ProposedCommitment(
                    title = task.replaceFirstChar { it.uppercase() }, evidence = evidence,
                    capturedAt = transcript.capturedAt, zoneId = transcript.zoneId,
                    needsReview = "Check the action and date/time. No reminder is scheduled.",
                )
            }.distinctBy { it.title.lowercase() }
    }
}
