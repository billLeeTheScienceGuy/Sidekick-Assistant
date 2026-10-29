package com.sidekick.core

object AnalysisContext {
    fun reported(sentence: String): Boolean = Regex("^(?:say|repeat|try saying|example|transcript|transcribed|the transcript|the recording|sidekick|keep|remove|fix|added|passed|past \\d+ core tests)\\b", RegexOption.IGNORE_CASE).containsMatchIn(sentence.trim())
    fun local(source: Transcript): List<ProposedCommitment> {
        val omitted = source.text.dropLast(4000)
        val quoted = omitted.count { it == '"' } % 2 == 1 || omitted.lastIndexOf('“') > omitted.lastIndexOf('”')
        val validationSource = source.copy(text = if (quoted) "\"" + source.text.takeLast(3999) else source.text.takeLast(4000))
        return ActivityTiming.merge(source, RuleIntentExtractor().extract(source)).filter {
        it.informational || LocalizedActivities.hasEvent(it.evidence) ||
            it.evidence.lines().any { line -> ConversationActivities.attendanceValue(line) == true &&
                IntentValidation.validate(validationSource, listOf(IntentCandidate(line, line, ""))).isNotEmpty() } ||
            IntentValidation.validate(validationSource, listOf(IntentCandidate(it.title, it.evidence.substringBefore('\n'), ""))).isNotEmpty()
        }
    }
    fun unresolved(source: Transcript, local: List<ProposedCommitment>): String = Sentences.split(source.text)
        .filter { sentence -> !reported(sentence) && local.none { it.evidence.contains(sentence.trimEnd('.', '!'), true) } }
        .filter { Regex("\\b(?:I|I'll|I'm|my|me)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        .joinToString("\n")
}
