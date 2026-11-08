package com.sidekick.core

/** Grounded context assembly. No facts are generated or borrowed across another event. */
object ConversationActivities {
    private val noun = Regex("\\b(party|meeting|appointment|class|lecture|concert|dinner|lunch|wedding|birthday|practice|workshop|movie|trip|shift|call)\\b", RegexOption.IGNORE_CASE)
    private val attendance = Regex("^(?:I(?:['’]ll| will)|I(?:['’]m| am) going to) (be there|attend|go|join)(?: too)?[.!]*$", RegexOption.IGNORE_CASE)
    private val withdrawal = Regex("^I (?:won['’]t|will not|cannot|can['’]t) (?:be there|attend|go|join|make it)[.!]*$", RegexOption.IGNORE_CASE)
    private val namedAttendance = Regex("^I(?:['’]ll| will) (?:attend|go to|join) (.+?)[.!]*$", RegexOption.IGNORE_CASE)
    private val namedWithdrawal = Regex("^I (?:won['’]t|will not|cannot|can['’]t) (?:attend|go to|join) (.+?)[.!]*$", RegexOption.IGNORE_CASE)
    private val localAttendance = setOf("저도 갈게요", "저는 갈게요", "제가 갈게요", "저도 참석할게요", "저는 참석할게요",
        "我会去", "我也会去", "我会参加", "我也会参加", "j'y serai", "je serai là", "je viendrai", "je participerai",
        "ich komme", "ich werde dabei sein", "ich nehme teil", "я приду", "я тоже приду", "я буду там", "я буду участвовать",
        "ساحضر", "سوف احضر", "ساكون هناك")
    private val localWithdrawal = setOf("저는 못 가요", "저도 못 가요", "저는 안 갈게요", "저는 참석하지 않을게요",
        "我不去了", "我不会去", "我不参加了", "je ne viendrai pas", "je n'y serai pas", "je ne participerai pas",
        "ich komme nicht", "ich werde nicht dabei sein", "ich nehme nicht teil", "я не приду", "я не буду там", "я не буду участвовать",
        "لن احضر", "لن اكون هناك")
    private fun international(line: String): Boolean? {
        val text = ArabicActivities.normalized(line).lowercase(java.util.Locale.ROOT).replace('’', '\'').trim().trimEnd('.', '!', '。', '！')
        return when (text) { in localAttendance -> true; in localWithdrawal -> false; else -> null }
    }
    private fun named(line: String): Boolean? = when {
        ActivityTiming.hasDate(line) || ActivityTiming.mentionsClock(line) -> null
        namedAttendance.matchEntire(line.trim())?.groupValues?.get(1)?.let(ActivityTiming::looksLikeActivity) == true -> true
        namedWithdrawal.matchEntire(line.trim())?.groupValues?.get(1)?.let(ActivityTiming::looksLikeActivity) == true -> false
        else -> null
    }
    fun attendanceValue(line: String): Boolean? = when {
        attendance.matches(line.trim()) -> true
        withdrawal.matches(line.trim()) -> false
        else -> named(line) ?: international(line)
    }
    fun attendanceRevision(before: String, after: String): Boolean? {
        val old = before.lines().filter { attendanceValue(it) != null }
        val fresh = after.lines().filter { attendanceValue(it) != null }
        return if (fresh.size > old.size && fresh.take(old.size) == old) attendanceValue(fresh.last()) else null
    }
    private fun owned(source: Transcript, line: String, positive: Boolean): Boolean {
        val at = source.text.indexOf(line)
        if (at < 0) return false
        val before = source.text.take(at)
        if (Regex("\\b(?:said|says|told|quoted|pretend|imagine|if|unless|maybe|dit|si|gesagt|sagte|sagt|wenn|если|сказал[аи]?|говорит|говорят|قال|قالت|يقول|تقول|اذا)\\b|말했|말하|만약|如果|说|說", RegexOption.IGNORE_CASE).containsMatchIn(ArabicActivities.normalized(before.takeLast(120))) ||
            before.count { it == '"' } % 2 == 1 || before.lastIndexOf('“') > before.lastIndexOf('”')) return false
        return !positive || international(line) != null || IntentValidation.validate(source.copy(text = source.text.takeLast(4000)), listOf(IntentCandidate(line, line, ""))).isNotEmpty()
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
        var enriched = result.map { item ->
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
        if (source.speaker != Speaker.USER || informationalOnly) return enriched
        // Apply in source order, so a later withdrawal supersedes earlier attendance.
        for ((index, line) in lines.withIndex()) {
            val value = attendanceValue(line) ?: continue
            val pronoun = named(line) == null
            if (!pronoun && (ActivityTiming.hasDate(line) || ActivityTiming.mentionsClock(line))) continue
            if (!owned(source, line, value)) continue
            val target = namedAttendance.matchEntire(line.trim())?.groupValues?.get(1)
                ?: namedWithdrawal.matchEntire(line.trim())?.groupValues?.get(1)
            val subject = if (pronoun) null else target?.let(ActivityIdentity::subject) ?: continue
            val matches = enriched.withIndex().filter { (_, item) ->
                val openingAt = lines.indexOfFirst { opening(it) == opening(item.evidence) }
                openingAt in maxOf(0, index - 24) until index &&
                    (if (pronoun) enriched.size == 1 else ActivityIdentity.subject(item.evidence) == subject)
            }
            val match = matches.singleOrNull() ?: continue
            enriched = enriched.mapIndexed { i, item -> if (i != match.index) item else item.copy(informational = !value,
                evidence = item.evidence + "\n" + line) }
        }
        return enriched
    }
}
