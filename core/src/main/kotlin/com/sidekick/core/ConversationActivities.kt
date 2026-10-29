package com.sidekick.core

/** Grounded context assembly. No facts are generated or borrowed across another event. */
object ConversationActivities {
    private val noun = Regex("\\b(party|meeting|appointment|class|lecture|concert|dinner|lunch|wedding|birthday|practice|workshop|movie|trip|shift|call)\\b", RegexOption.IGNORE_CASE)
    private val attendance = Regex("^(?:I(?:['’]ll| will)|I(?:['’]m| am) going to) (be there|attend|go|join)(?: too)?[.!]*$", RegexOption.IGNORE_CASE)
    private val withdrawal = Regex("^I (?:won['’]t|will not|cannot|can['’]t) (?:be there|attend|go|join|make it)[.!]*$", RegexOption.IGNORE_CASE)
    fun attendanceValue(line: String): Boolean? = when {
        attendance.matches(line.trim()) -> true
        withdrawal.matches(line.trim()) -> false
        else -> null
    }
    private fun opening(text: String) = text.substringBefore('\n').trim().trimEnd('.', '!', '。')
    fun enrich(source: Transcript, raw: List<ProposedCommitment>, informationalOnly: Boolean): List<ProposedCommitment> {
        if (!source.processingAllowed || (source.speaker != Speaker.USER && !(informationalOnly && source.speaker == Speaker.OTHER))) return raw
        val lines = Sentences.split(source.text)
        val result = raw.toMutableList()
        for ((index, line) in lines.withIndex()) {
            if (!ActivityTiming.looksLikeActivity(line) || EventChange.looksLikeChange(line) || AnalysisContext.reported(line)) continue
            val existing = result.indexOfFirst { opening(it.evidence) == opening(line) }
            if (existing >= 0 && ActivityTiming.hasDate(line)) continue
            val fragments = mutableListOf<String>(); val phrases = mutableListOf<String>()
            for (next in lines.drop(index + 1).take(8)) {
                if (ActivityTiming.looksLikeActivity(next)) break
                val phrase = ActivityTiming.timingFragment(next) ?: break
                fragments += next; phrases += phrase
            }
            if (fragments.isEmpty()) continue
            val combined = opening(line) + " " + phrases.joinToString(" ")
            if (!ActivityTiming.hasDate(combined)) continue // Do not invent a date for a partial exchange.
            val following = lines.drop(index + fragments.size + 1).take(16).takeWhile { !ActivityTiming.looksLikeActivity(it) }
            val parsed = ActivityTiming.extractGrounded(source.copy(text = combined + "\n" + following.joinToString("\n")), informationalOnly).singleOrNull()
            if (parsed == null) { if (existing >= 0) result.removeAt(existing); continue }
            val extraEvidence = parsed.evidence.lines().drop(1)
            val personal = !informationalOnly && source.speaker == Speaker.USER && RuleIntentExtractor().extract(source.copy(text = line)).isNotEmpty()
            val assembled = parsed.copy(evidence = (listOf(line) + fragments + extraEvidence).distinct().joinToString("\n"), informational = if (personal) false else parsed.informational)
            if (existing >= 0) result[existing] = assembled else result += assembled
        }
        // Explicit named detail statements can refer back across a different activity.
        val enriched = result.map { item ->
            val first = opening(item.evidence)
            val at = lines.indexOfFirst { opening(it) == first }
            val kind = noun.find(first)?.value ?: return@map item
            var current = item
            for (line in lines.drop(at + 1).take(24)) {
                if (!ActivityTiming.looksLikeActivity(line) || !LocalizedActivities.isContinuation(line) && !Regex("\\b(?:ends?|finishes|lasts?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(line)) continue
                val target = ActivityIdentity.subject(line) ?: continue
                val matches = result.filter { ActivityIdentity.subject(it.evidence) == target || (target == kind.lowercase() && noun.find(it.evidence)?.value.equals(kind, true)) }
                if (matches.size != 1 || matches.single() != item) continue
                val local = item.activityStartAt?.atZone(source.zoneId) ?: continue
                val subject = ActivityIdentity.subject(item.evidence) ?: kind
                val reference = "There's a $subject on ${local.toLocalDate()} at ${local.toLocalTime().toString().take(5)}."
                val update = ActivityTiming.extractGrounded(source.copy(text = reference + "\n" + line), true).singleOrNull() ?: continue
                current = current.copy(activityEndAt = update.activityEndAt ?: current.activityEndAt,
                    placeName = update.placeName ?: current.placeName,
                    placeAddress = update.placeAddress ?: if (update.placeName != null && update.placeName != current.placeName) null else current.placeAddress,
                    evidence = (current.evidence.lines() + line).distinct().joinToString("\n"))
            }
            current
        }
        // Pronoun attendance needs exactly one event, and only the user's own speech.
        if (enriched.size != 1 || source.speaker != Speaker.USER || informationalOnly) return enriched
        val item = enriched.single(); val at = lines.indexOfFirst { opening(it) == opening(item.evidence) }
        val change = lines.drop(at + 1).take(24).mapNotNull { line -> attendanceValue(line)?.let { it to line } }.lastOrNull() ?: return enriched
        val prefix = source.text.substringBefore(change.second).takeLast(120)
        if (Regex("\\b(?:said|says|told|quoted|pretend|imagine)\\b", RegexOption.IGNORE_CASE).containsMatchIn(prefix) ||
            prefix.count { it == '"' } % 2 == 1 || prefix.lastIndexOf('“') > prefix.lastIndexOf('”')) return enriched
        if (change.first && IntentValidation.validate(source.copy(text = source.text.takeLast(4000)), listOf(IntentCandidate(change.second, change.second, ""))).isEmpty()) return enriched
        return listOf(item.copy(informational = !change.first, evidence = (item.evidence.lines() + change.second).distinct().joinToString("\n")))
    }
}
