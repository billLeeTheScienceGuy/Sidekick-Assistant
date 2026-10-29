package com.sidekick.core

import java.time.*
import java.time.temporal.TemporalAdjusters

/** Grounded English timing suggestions. Missing or ambiguous times stay unknown. */
object ActivityTiming {
    private val event = Regex("\\b(party|meeting|appointment|class|lecture|concert|dinner|lunch|wedding|birthday|practice|workshop|movie|trip|shift|call)\\b", RegexOption.IGNORE_CASE)
    private val clock = "(?:\\b(?:1[0-2]|0?[1-9])(?::[0-5]\\d)?\\s*[ap]\\.?m\\.?(?!\\w)|\\b(?:[01]?\\d|2[0-3]):[0-5]\\d\\b|\\bmidnight\\b|\\bnoon\\b)"
    private val clocks = Regex(clock, RegexOption.IGNORE_CASE)
    private val ending = Regex("\\b(?:ends?|finish(?:es)?|until|till|over)\\s+(?:(?:at|around|about|by)\\s+)?($clock)", RegexOption.IGNORE_CASE)
    private val duration = Regex("\\b(?:lasts?|takes?)\\s+(?:(?:for|about|around)\\s+)?(\\d{1,2}(?:\\.\\d)?)\\s+(hours?|minutes?)\\b", RegexOption.IGNORE_CASE)
    private val excluded = Regex("\\b(yesterday|was|were|ended|finished|cancelled|canceled|not|never|won't|would|could|might|maybe|if|unless)\\b", RegexOption.IGNORE_CASE)
    private val namedDate = Regex("\\b(today|tonight|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|\\d{4}-\\d{2}-\\d{2})\\b", RegexOption.IGNORE_CASE)

    fun looksLikeActivity(text: String): Boolean = event.containsMatchIn(text) || LocalizedActivities.hasEvent(text)

    fun merge(source: Transcript, commitments: List<ProposedCommitment>): List<ProposedCommitment> {
        val activities = extract(source)
        fun matches(a: ProposedCommitment, b: ProposedCommitment): Boolean =
            b.evidence.lowercase().contains(a.evidence.substringBefore('\n').trimEnd('.', '!').lowercase())
        val enriched = commitments.map { item ->
            val activity = activities.singleOrNull { matches(it, item) }
            if (activity == null) {
                val place = PlaceHints.extract(item.evidence)
                item.copy(placeName = place.name, placeAddress = place.address)
            } else item.copy(evidence = activity.evidence,
                informational = if (activity.evidence.lines().any { ConversationActivities.attendanceValue(it) != null }) activity.informational else item.informational,
                activityStartAt = activity.activityStartAt, activityEndAt = activity.activityEndAt,
                placeName = activity.placeName, placeAddress = activity.placeAddress,
                needsReview = item.needsReview + " " + activity.needsReview.substringAfter("Attendance is unconfirmed. "))
        }
        return enriched + activities.filter { a -> commitments.none { matches(a, it) } }
    }

    fun extract(source: Transcript, informationalOnly: Boolean = false): List<ProposedCommitment> {
        val raw = extractGrounded(source, informationalOnly)
        return ConversationActivities.enrich(source, raw, informationalOnly)
    }
    internal fun hasDate(text: String) = namedDate.containsMatchIn(text) || LocalizedActivities.hasExplicitDate(text)
    internal fun timingFragment(text: String): String? {
        val clean = text.trim().trimEnd('.', '!', '。').replace(Regex("^(?:(?:it|that)(?:['’]s| is)|(?:it )?starts?|(?:it )?will be)\\s+", RegexOption.IGNORE_CASE), "")
        if (clean.any { it in "?？؟\"“”«»" }) return null
        val remainder = namedDate.replace(clocks.replace(clean, ""), "").replace(Regex("\\b(?:at|on|or)\\b|[\\s,.!]", RegexOption.IGNORE_CASE), "")
        return clean.takeIf { ((namedDate.containsMatchIn(clean) || clocks.containsMatchIn(clean)) && remainder.isEmpty()) || LocalizedActivities.timingFragment(clean) }
    }
    internal fun extractGrounded(source: Transcript, informationalOnly: Boolean = false): List<ProposedCommitment> {
        if (!source.processingAllowed || (source.speaker != Speaker.USER && !(informationalOnly && source.speaker == Speaker.OTHER))) return emptyList()
        val sentences = Sentences.split(source.text)
        val english = sentences.mapIndexedNotNull { index, sentence ->
            if (AnalysisContext.reported(sentence)) return@mapIndexedNotNull null
            if (LocalizedActivities.hasEvent(sentence)) return@mapIndexedNotNull null
            val noun = event.find(sentence) ?: return@mapIndexedNotNull null
            if (excluded.containsMatchIn(sentence) || sentence.endsWith('?')) return@mapIndexedNotNull null
            val startClocks = clocks.findAll(sentence).filter { clock -> ending.findAll(sentence).none { clock.range.first in it.range } }.toList()
            val firstClock = startClocks.singleOrNull() ?: return@mapIndexedNotNull null
            // A statement about when an event ends is a continuation, not a second activity.
            if (ending.find(sentence)?.range?.first?.let { it < firstClock.range.first } == true) return@mapIndexedNotNull null
            val explicitDate = namedDate.containsMatchIn(sentence)
            val start = resolveStart(sentence, firstClock.value, source) ?: return@mapIndexedNotNull null
            var end: Instant? = null
            var endEvidence: String? = null
            var place = PlaceHints.extract(sentence)
            var placeEvidence: String? = null
            val nounName = noun.value.lowercase()
            for (following in sentences.drop(index).take(24)) {
                if (following != sentence) {
                    if (LocalizedActivities.hasEvent(following)) break
                    val otherNouns = event.findAll(following).map { it.value.lowercase() }.toList()
                    // Do not attach an ambiguous "it ends" across another activity/topic.
                    if (otherNouns.any { it != nounName }) break
                    if (otherNouns.isNotEmpty() && clocks.containsMatchIn(following) && !ending.containsMatchIn(following) && !duration.containsMatchIn(following)) break
                    if (otherNouns.isNotEmpty()) {
                        val target = ActivityIdentity.subject(following)
                        if (target != null && target != nounName && target != ActivityIdentity.subject(sentence)) continue
                        if (target == nounName && sentences.count { event.find(it)?.value.equals(nounName, true) &&
                            clocks.containsMatchIn(it) && !ending.containsMatchIn(it) && !duration.containsMatchIn(it) } > 1) continue
                    }
                    if (otherNouns.isEmpty() && place.name?.let { following.contains(it, true) } != true &&
                        !Regex("^(?:yeah[, ]+|actually[, ]+)?(?:it\\b|it['’]ll\\b|that\\b|the address\\b)", RegexOption.IGNORE_CASE).containsMatchIn(following) && !LocalizedActivities.isContinuation(following)) continue
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
            val evidence = listOfNotNull(sentence, endEvidence, placeEvidence).distinct().joinToString("\n")
            ProposedCommitment(sentence.substring(noun.range.first).trimEnd('.', '!').replaceFirstChar { it.uppercase() }.take(500),
                evidence, source.capturedAt, source.zoneId,
                "Attendance is unconfirmed. " + (if (!explicitDate) "Date inferred from the next occurrence; check it. " else "") +
                    "Activity times are suggestions from speech; review them. " + (if (end == null) "End time is unknown." else "End may be approximate."),
                start, end, informational = true, placeName = place.name, placeAddress = place.address)
        }.distinctBy { it.evidence.substringBefore('\n').lowercase() }
        return english + LocalizedActivities.extract(source, informationalOnly)
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
        val match = ending.find(sentence)
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

    private fun date(text: String, today: LocalDate): LocalDate? {
        return namedDate.findAll(text).map { match -> when (val value = match.value.lowercase()) {
            "today", "tonight" -> today
            "tomorrow" -> today.plusDays(1)
            else -> DayOfWeek.entries.firstOrNull { it.name.equals(value, true) }?.let { today.with(TemporalAdjusters.next(it)) }
                ?: LocalDate.parse(value)
        } }.toSet().singleOrNull()
    }

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
