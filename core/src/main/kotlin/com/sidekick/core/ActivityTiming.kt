package com.sidekick.core

import java.time.*

/** Grounded English timing suggestions. Missing or ambiguous times stay unknown. */
object ActivityTiming {
    private val event = Regex("\\b(party|meeting|appointment|class|lecture|concert|dinner|lunch|wedding|birthday|practice|workshop|movie|trip|shift|call)\\b", RegexOption.IGNORE_CASE)
    private val clock = "(?:\\b(?:1[0-2]|0?[1-9])(?::[0-5]\\d)?\\s*[ap]\\.?m\\.?(?!\\w)|\\b(?:[01]?\\d|2[0-3]):[0-5]\\d\\b|\\bmidnight\\b|\\bnoon\\b)"
    private val clocks = Regex(clock, RegexOption.IGNORE_CASE)
    private val ending = Regex("\\b(?:ends?|finish(?:es)?|until|till|over)\\s+(?:(?:at|around|about|by)\\s+)?($clock)", RegexOption.IGNORE_CASE)
    private val duration = Regex("\\b(?:lasts?|takes?)\\s+(?:(?:for|about|around)\\s+)?(\\d{1,2}(?:\\.\\d)?)\\s+(hours?|minutes?)\\b", RegexOption.IGNORE_CASE)
    private val excluded = Regex("\\b(yesterday|was|were|ended|finished|cancelled|canceled|not|never|won't|would|could|might|maybe|if|unless|used\\s+to)\\b", RegexOption.IGNORE_CASE)
    private val namedDate = EnglishDates.pattern
    private val separator = Regex("\\s*[,;]\\s*(?:and\\s+)?(?:then\\s+)?|\\s+(?:and\\s+then|and|then|after\\s+that|followed\\s+by)\\s+", RegexOption.IGNORE_CASE)
    // A clause with its own subject ("there's a meeting") does not inherit "I'll" from the first clause.
    private val ownSubject = Regex("^(?:there(?:['’]s|\\s+is|\\s+are)|it(?:['’]s|\\s+is)|(?:we|they|he|she|you)\\b|(?:my|our|his|her|their)\\s)", RegexOption.IGNORE_CASE)

    fun looksLikeActivity(text: String): Boolean = event.containsMatchIn(text) || LocalizedActivities.hasEvent(text)

    private fun startClocks(timing: String) =
        clocks.findAll(timing).filter { clock -> ending.findAll(timing).none { clock.range.first in it.range } }.toList()

    /**
     * "Lunch at noon and a meeting at 3pm" holds two plans. Split only when every part has its own
     * event word and exactly one start time; leading words and detail-only parts stay with their plan.
     */
    internal fun clauses(sentence: String): List<String> {
        val pieces = mutableListOf<IntRange>()
        var from = 0
        for (match in separator.findAll(sentence)) { pieces += from until match.range.first; from = match.range.last + 1 }
        pieces += from until sentence.length
        val merged = mutableListOf<IntRange>()
        for (piece in pieces.filter { sentence.substring(it).isNotBlank() }) {
            val last = merged.lastOrNull()
            if (last != null && !(event.containsMatchIn(sentence.substring(piece)) && event.containsMatchIn(sentence.substring(last))))
                merged[merged.lastIndex] = last.first..piece.last
            else merged += piece
        }
        if (merged.size < 2) return listOf(sentence)
        val parts = merged.map { sentence.substring(it).trim() }
        return parts.takeIf { it.all { part -> startClocks(EnglishClocks.normalize(part)).size == 1 } } ?: listOf(sentence)
    }

    fun merge(source: Transcript, commitments: List<ProposedCommitment>): List<ProposedCommitment> {
        val activities = extract(source)
        fun matches(a: ProposedCommitment, b: ProposedCommitment): Boolean =
            b.evidence.lowercase().contains(a.evidence.substringBefore('\n').trimEnd('.', '!').lowercase()) ||
                a.evidence.lines().drop(1).any { ConversationActivities.attendanceValue(it) != null &&
                    it.trimEnd('.', '!').equals(b.evidence.trimEnd('.', '!'), true) }
        val enriched = commitments.flatMap { item ->
            val matched = activities.filter { matches(it, item) }
            // "I'll go to lunch at noon and the meeting at 3pm": the promise covers each plan without its own subject.
            if (matched.size > 1) return@flatMap matched.map { activity ->
                if (ownSubject.containsMatchIn(activity.evidence.substringBefore('\n'))) activity
                else activity.copy(informational = item.informational,
                    needsReview = item.needsReview + " " + activity.needsReview.substringAfter("Attendance is unconfirmed. "))
            }
            val activity = matched.singleOrNull()
            listOf(if (activity == null) {
                val place = PlaceHints.extract(item.evidence)
                item.copy(placeName = place.name, placeAddress = place.address)
            } else item.copy(evidence = activity.evidence,
                informational = if (activity.evidence.lines().any { ConversationActivities.attendanceValue(it) != null }) activity.informational else item.informational,
                activityStartAt = activity.activityStartAt, activityEndAt = activity.activityEndAt,
                placeName = activity.placeName, placeAddress = activity.placeAddress,
                needsReview = item.needsReview + " " + activity.needsReview.substringAfter("Attendance is unconfirmed. ")))
        }
        return (enriched + activities.filter { a -> commitments.none { matches(a, it) } })
            .distinctBy { it.evidence.substringBefore('\n').lowercase() to it.activityStartAt }
    }

    fun extract(source: Transcript, informationalOnly: Boolean = false): List<ProposedCommitment> {
        val raw = extractGrounded(source, informationalOnly)
        return ConversationActivities.enrich(source, raw, informationalOnly)
    }
    internal fun hasDate(text: String) = namedDate.containsMatchIn(text) || LocalizedActivities.hasExplicitDate(text)
    internal fun mentionsClock(text: String) = clocks.containsMatchIn(EnglishClocks.normalize(text))
    internal fun timingFragment(text: String): String? {
        val clean = text.trim().trimEnd('.', '!', '。').replace(Regex("^(?:(?:it|that)(?:['’]s| is)|(?:it )?starts?|(?:it )?will be)\\s+", RegexOption.IGNORE_CASE), "")
        if (clean.any { it in "?？؟\"“”«»" }) return null
        if (EnglishDates.unresolvedTiming(clean)) return null
        val timing = EnglishClocks.normalize(clean)
        val remainder = namedDate.replace(clocks.replace(timing, ""), "").replace(Regex("\\b(?:at|on|or)\\b|[\\s,.!]", RegexOption.IGNORE_CASE), "")
        return clean.takeIf { ((namedDate.containsMatchIn(timing) || clocks.containsMatchIn(timing)) && remainder.isEmpty()) || LocalizedActivities.timingFragment(clean) }
    }
    internal fun extractGrounded(source: Transcript, informationalOnly: Boolean = false): List<ProposedCommitment> {
        if (!source.processingAllowed || (source.speaker != Speaker.USER && !(informationalOnly && source.speaker == Speaker.OTHER))) return emptyList()
        val sentences = Sentences.split(source.text)
        val english = sentences.withIndex().flatMap { (index, sentence) ->
            if (AnalysisContext.reported(sentence) || EnglishDates.unresolvedTiming(sentence) || LocalizedActivities.hasEvent(sentence) ||
                // Hedges and negation anywhere in the sentence block every plan in it.
                !event.containsMatchIn(sentence) || excluded.containsMatchIn(sentence) || sentence.endsWith('?')) return@flatMap emptyList()
            val parts = clauses(sentence)
            val split = parts.size > 1
            // A part without its own date uses the sentence's date ("tomorrow, lunch at noon and a meeting at 3pm").
            val sentenceDates = namedDate.findAll(sentence).joinToString(" ") { it.value }
            parts.mapNotNull { clause -> grounded(source, sentences, index, sentence, clause, split,
                if (split && !namedDate.containsMatchIn(clause)) "$clause $sentenceDates".trim() else clause) }
        }.distinctBy { it.evidence.substringBefore('\n').lowercase() }
        return english + LocalizedActivities.extract(source, informationalOnly)
    }

    private fun grounded(source: Transcript, sentences: List<String>, index: Int, sentence: String, clause: String, split: Boolean, dateText: String): ProposedCommitment? {
        val noun = event.find(clause) ?: return null
        val timing = EnglishClocks.normalize(clause)
        val firstClock = startClocks(timing).singleOrNull() ?: return null
        // A statement about when an event ends is a continuation, not a second activity.
        if (ending.find(timing)?.range?.first?.let { it < firstClock.range.first } == true) return null
        val explicitDate = namedDate.containsMatchIn(dateText)
        val start = resolveStart(dateText, firstClock.value, source) ?: return null
        var end: Instant? = null
        var endEvidence: String? = null
        var place = PlaceHints.extract(clause)
        var placeEvidence: String? = null
        val nounName = noun.value.lowercase()
        for ((offset, following) in (listOf(clause) + sentences.drop(index + 1).take(23)).withIndex()) {
            if (offset > 0 && (split || following != sentence)) {
                if (LocalizedActivities.hasEvent(following) && (LocalizedActivities.eventKey(following) != nounName || !LocalizedActivities.isContinuation(following))) break
                val otherNouns = event.findAll(following).map { it.value.lowercase() }.toList()
                // Do not attach an ambiguous "it ends" across another activity/topic.
                if (otherNouns.any { it != nounName }) break
                if (otherNouns.isNotEmpty() && mentionsClock(following) && !ending.containsMatchIn(EnglishClocks.normalize(following)) && !duration.containsMatchIn(following)) break
                if (otherNouns.isNotEmpty()) {
                    val target = ActivityIdentity.subject(following)
                    if (target != null && target != nounName && target != ActivityIdentity.subject(clause)) continue
                    if (target == nounName && sentences.count { event.find(it)?.value.equals(nounName, true) &&
                        clocks.containsMatchIn(it) && !ending.containsMatchIn(it) && !duration.containsMatchIn(it) } > 1) continue
                }
                // After a sentence with several plans, "it ends at 4pm" could mean any of them; only named follow-ups attach.
                if (otherNouns.isEmpty() && (split || place.name?.let { following.contains(it, true) } != true &&
                    !Regex("^(?:yeah[, ]+|actually[, ]+)?(?:it\\b|it['’]ll\\b|that\\b|the address\\b)", RegexOption.IGNORE_CASE).containsMatchIn(following) && !LocalizedActivities.isContinuation(following))) continue
            }
            if (excluded.containsMatchIn(following) || following.endsWith('?')) continue
            val foundEnd = resolveEnd(following, start, source)
            if (foundEnd != null) { end = foundEnd; endEvidence = following }
            val foundPlace = PlaceHints.extract(following)
            if (foundPlace.name != null || foundPlace.address != null) {
                val changedVenue = foundPlace.name != null && place.name != null && !foundPlace.name.equals(place.name, true)
                place = PlaceHint(foundPlace.name ?: place.name,
                    foundPlace.address ?: if (changedVenue) null else place.address)
                placeEvidence = following
            }
        }
        val evidence = listOfNotNull(clause, endEvidence, placeEvidence).distinct().joinToString("\n")
        return ProposedCommitment(clause.trimEnd('.', '!').replaceFirstChar { it.uppercase() }.take(500),
            evidence, source.capturedAt, source.zoneId,
            "Attendance is unconfirmed. " + (if (!explicitDate) "Date inferred from the next occurrence; check it. " else "") +
                (if (EnglishDates.hasNextWeekday(dateText)) "\"Next\" weekday read as next week's; check it. " else "") +
                (if (split) "One of several plans in one sentence; check its details. " else "") +
                "Activity times are suggestions from speech; review them. " + (if (end == null) "End time is unknown." else "End may be approximate."),
            start, end, informational = true, placeName = place.name, placeAddress = place.address)
    }

    private fun resolveStart(sentence: String, clockText: String, source: Transcript): Instant? = runCatching {
        val local = source.capturedAt.atZone(source.zoneId)
        val explicit = namedDate.containsMatchIn(sentence)
        val explicitDay = date(sentence, local.toLocalDate())
        if (explicit && explicitDay == null) return null
        var date = explicitDay ?: local.toLocalDate()
        var result = at(date, clockText, source.zoneId)
        if (!explicit && !result.isAfter(source.capturedAt)) { date = date.plusDays(1); result = at(date, clockText, source.zoneId) }
        result.takeIf { it.isAfter(source.capturedAt) }
    }.getOrNull()

    private fun resolveEnd(sentence: String, start: Instant, source: Transcript): Instant? = runCatching {
        val match = ending.find(EnglishClocks.normalize(sentence))
        val end = if (match != null) {
            val startDay = start.atZone(source.zoneId).toLocalDate()
            val explicitDay = date(sentence, source.capturedAt.atZone(source.zoneId).toLocalDate())
            var value = at(explicitDay ?: startDay, match.groupValues[1], source.zoneId)
            // Midnight following an evening event belongs to the next calendar day.
            if (!value.isAfter(start) && (explicitDay == null || explicitDay == startDay))
                value = at((explicitDay ?: startDay).plusDays(1), match.groupValues[1], source.zoneId)
            value
        } else {
            val length = duration.find(sentence) ?: return LocalizedActivities.resolveEnd(sentence, start, source)
            val seconds = (length.groupValues[1].toDouble() * if (length.groupValues[2].startsWith("hour", true)) 3600 else 60).toLong()
            start.plusSeconds(seconds)
        }
        CalendarOverlap.validate(start.toEpochMilli(), end.toEpochMilli())
        end
    }.getOrNull()

    private fun date(text: String, today: LocalDate): LocalDate? = EnglishDates.date(text, today)

    private fun at(day: LocalDate, text: String, zone: ZoneId): Instant {
        val normalized = text.lowercase().replace(" ", "").replace(".", "")
        val clock = when (normalized) {
            "midnight" -> LocalTime.MIDNIGHT
            "noon" -> LocalTime.NOON
            else -> {
                val parts = Regex("(\\d{1,2})(?::(\\d{2}))?(am|pm)?").matchEntire(normalized) ?: error("Unknown clock")
                var hour = parts.groupValues[1].toInt()
                val meridiem = parts.groupValues[3]
                if (meridiem.isNotEmpty()) hour = hour % 12 + if (meridiem == "pm") 12 else 0
                LocalTime.of(hour, parts.groupValues[2].ifBlank { "0" }.toInt())
            }
        }
        return TimeResolver.parseEditor("$day ${clock.toString().take(5)}", zone)
    }
}
