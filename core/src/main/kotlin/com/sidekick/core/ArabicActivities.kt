package com.sidekick.core

import java.time.*
import java.time.temporal.TemporalAdjusters

/** Bounded Modern Standard Arabic grammar; original text is always kept as evidence. */
object ArabicActivities {
    fun isArabic(text: String) = Regex("[\\u0600-\\u06ff]").containsMatchIn(text)
    fun looksLikeChange(text: String) = Regex("(?<![\\p{L}])(?:الغي|الغ|تلغي|تلغ|انقل|غير موعد)(?![\\p{L}])").containsMatchIn(normalized(text))
    fun normalized(text: String): String = text.map { c -> when (c) {
        in '٠'..'٩' -> '0' + (c - '٠'); in '۰'..'۹' -> '0' + (c - '۰')
        'أ', 'إ', 'آ', 'ٱ' -> 'ا'; else -> c
    } }.joinToString("").replace(Regex("[\\u064B-\\u065F\\u0670ـ]"), "")
    private fun rx(s: String) = Regex(s)
    private val kinds = linkedMapOf("حفلة" to "party", "اجتماع" to "meeting", "موعد" to "appointment", "حفل موسيقي" to "concert", "عشاء" to "dinner", "درس" to "class", "عيد ميلاد" to "birthday", "زفاف" to "wedding")
    private val nouns = kinds.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
    private val event = rx("(?<![\\p{L}])(?:ال)?($nouns)(?:ا)?(?![\\p{L}])")
    private val negative = rx("(?<![\\p{L}])(?:لا|لن|لم|ليس|ليست|ربما|اذا|لو|هل|قد|امس|الغاء|الغيت|ملغي|ملغاة)(?![\\p{L}])")
    private val reported = rx("[\"“”«»]|(?<![\\p{L}])(?:قال|قالت|يقول|قالوا)(?![\\p{L}])")
    private val days = listOf("الاثنين", "الثلاثاء", "الاربعاء", "الخميس", "الجمعة", "السبت", "الاحد")
    private val datePattern = rx("(?<![\\p{L}\\p{N}])(?:غدا|غد|اليوم|\\d{4}-\\d{2}-\\d{2}|" + days.joinToString("|") + ")(?![\\p{L}\\p{N}])")
    internal fun hasDate(text: String) = datePattern.containsMatchIn(normalized(text))
    internal fun timingFragment(text: String): Boolean {
        val clean = normalized(text)
        val found = datePattern.containsMatchIn(clean) || clock.containsMatchIn(clean)
        return found && clock.replace(datePattern.replace(clean, " "), " ").replace(rx("(?<![\\p{L}])(?:في|عند)(?![\\p{L}])|[\\s،,.!]"), "").isEmpty()
    }
    private val hours = listOf("الواحدة", "الثانية", "الثالثة", "الرابعة", "الخامسة", "السادسة", "السابعة", "الثامنة", "التاسعة", "العاشرة", "الحادية عشرة", "الثانية عشرة")
    private val hourWords = (hours.withIndex().associate { it.value to it.index + 1 } + listOf("الاول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس", "السابع", "الثامن", "التاسع", "العاشر", "الحادي عشر", "الثاني عشر").withIndex().associate { it.value to it.index + 1 } + listOf("واحد", "اثنان", "ثلاثة", "اربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة", "عشرة", "احد عشر", "اثنا عشر").withIndex().associate { it.value to it.index + 1 })
    private val clock = rx("(?:الساعة\\s*)?(\\d{1,2}(?::[0-5]\\d)?|" + hourWords.keys.sortedByDescending { it.length }.joinToString("|") + ")(?:\\s*(صباحا|مساء|ظهرا|ليلا))?")
    private val end = rx("ينتهي|تنتهي|سينتهي|ستنتهي|حتى|منتصف الليل|لمدة")
    fun kind(text: String): String? = event.find(normalized(text))?.groupValues?.get(1)?.let(kinds::get)
    fun bareKind(text: String): String? = event.matchEntire(normalized(text).trim())?.groupValues?.get(1)?.let(kinds::get)
    fun name(text: String): String? = event.find(normalized(text))?.value
    private fun day(text: String, at: LocalDate): LocalDate? {
        val dates = datePattern.findAll(text).mapNotNull { match -> when (val value = match.value) {
            "غدا", "غد" -> at.plusDays(1); "اليوم" -> at
            else -> if (value in days) at.with(TemporalAdjusters.next(DayOfWeek.of(days.indexOf(value) + 1))) else runCatching { LocalDate.parse(value) }.getOrNull()
        } }.toSet()
        return dates.singleOrNull()
    }
    private data class Time(val value: LocalTime, val range: IntRange)
    private fun times(text: String): List<Time> {
        val withoutDates = datePattern.replace(text) { " ".repeat(it.value.length) }
        val values = clock.findAll(withoutDates).mapNotNull { m ->
            val word = m.groupValues[1]; val parts = word.split(':')
            var hour = parts[0].toIntOrNull() ?: hourWords[word] ?: return@mapNotNull null
            val qualifier = m.groupValues[2]
            if (qualifier.isNotEmpty()) { if (hour !in 1..12) return@mapNotNull null; hour = hour % 12 + if (qualifier == "صباحا") 0 else 12 }
            else if (':' !in word && hour in 1..12) return@mapNotNull null
            runCatching { Time(LocalTime.of(hour, parts.getOrNull(1)?.toInt() ?: 0), m.range) }.getOrNull()
        }.toMutableList()
        rx("منتصف الليل").findAll(text).forEach { values += Time(LocalTime.MIDNIGHT, it.range) }
        return values.sortedBy { it.range.first }
    }
    fun changeStart(text: String, previous: Instant, capturedAt: Instant, zone: ZoneId): Instant? = runCatching {
        val clean = normalized(text); val found = times(clean); require(found.size <= 1)
        val date = day(clean, capturedAt.atZone(zone).toLocalDate())
        require(!datePattern.containsMatchIn(clean) || date != null)
        var remaining = clean
        found.map { it.range }.sortedByDescending { it.first }.forEach { remaining = remaining.removeRange(it) }
        remaining = datePattern.replace(remaining, "").replace(rx("(?<![\\p{L}])(?:في|الى|عند)(?![\\p{L}])|[\\s،,.!؟]"), "")
        require(remaining.isEmpty() && (found.isNotEmpty() || date != null))
        TimeResolver.parseEditor("${date ?: previous.atZone(zone).toLocalDate()} ${found.singleOrNull()?.value ?: previous.atZone(zone).toLocalTime()}", zone).takeIf { it.isAfter(capturedAt) }
    }.getOrNull()
    fun resolveEnd(text: String, start: Instant, source: Transcript): Instant? = runCatching {
        val clean = normalized(text); require(!negative.containsMatchIn(clean) && end.containsMatchIn(clean))
        val duration = rx("لمدة\\s+(\\d+|ساعة|ساعتين)\\s*(ساعات|ساعة|دقائق|دقيقة)?").find(clean)
        if (duration != null) {
            val count = duration.groupValues[1].toLongOrNull() ?: if (duration.groupValues[1] == "ساعتين") 2L else 1L
            start.plusSeconds(count * if (duration.groupValues[2].startsWith("دق")) 60 else 3600).also { CalendarOverlap.validate(start.toEpochMilli(), it.toEpochMilli()) }
        } else {
            val time = times(clean).last().value
            val explicit = day(clean, source.capturedAt.atZone(source.zoneId).toLocalDate())
            val startDay = start.atZone(source.zoneId).toLocalDate()
            var date = explicit ?: startDay
            var value = TimeResolver.parseEditor("$date $time", source.zoneId)
            if (!value.isAfter(start) && (explicit == null || explicit == startDay)) { date = date.plusDays(1); value = TimeResolver.parseEditor("$date $time", source.zoneId) }
            CalendarOverlap.validate(start.toEpochMilli(), value.toEpochMilli()); value
        }
    }.getOrNull()
    fun isContinuation(text: String) = end.containsMatchIn(normalized(text))
    fun extract(source: Transcript, informationalOnly: Boolean): List<ProposedCommitment> {
        val sentences = Sentences.split(source.text)
        return sentences.mapIndexedNotNull { index, sentence ->
            var clean = normalized(sentence)
            if (kind(clean) == null || clean.startsWith("الغي ") || clean.startsWith("انقل ") || negative.containsMatchIn(clean) || sentence.endsWith('؟') || sentence.endsWith('?')) return@mapIndexedNotNull null
            val evidence = mutableListOf(sentence)
            // ASR can put a period between an activity/date and its standalone clock.
            if (times(clean).isEmpty() && !clock.containsMatchIn(datePattern.replace(clean) { " ".repeat(it.value.length) })) {
                val next = sentences.getOrNull(index + 1)
                val timeOnly = next?.let { normalized(it).trim().trimEnd('.', '!').removePrefix("عند ") }
                if (timeOnly != null && clock.matches(timeOnly) && times(timeOnly).size == 1) {
                    clean += " $timeOnly"; evidence += requireNotNull(next)
                }
            }
            val found = times(clean)
            val rawClocks = clock.findAll(datePattern.replace(clean) { " ".repeat(it.value.length) }).toList()
            if (found.isEmpty() || rawClocks.any { raw -> found.none { it.range == raw.range } }) return@mapIndexedNotNull null
            if (found.size > 1 && (found.size != 2 || !end.containsMatchIn(clean.substring(found.first().range.last + 1, found.last().range.first)))) return@mapIndexedNotNull null
            val today = source.capturedAt.atZone(source.zoneId).toLocalDate()
            val explicit = day(clean, today)
            if (datePattern.containsMatchIn(clean) && explicit == null) return@mapIndexedNotNull null
            val start = runCatching {
                var date = explicit ?: today; var value = TimeResolver.parseEditor("$date ${found.first().value}", source.zoneId)
                if (explicit == null && !value.isAfter(source.capturedAt)) { date = date.plusDays(1); value = TimeResolver.parseEditor("$date ${found.first().value}", source.zoneId) }
                value.takeIf { it.isAfter(source.capturedAt) }
            }.getOrNull() ?: return@mapIndexedNotNull null
            var ending: Instant? = resolveEnd(sentence, start, source)
            for (following in sentences.drop(index + 1).take(8)) {
                if (kind(following) != null) break
                val value = resolveEnd(following, start, source) ?: continue
                ending = value; evidence += following
            }
            val personal = !informationalOnly && source.speaker == Speaker.USER && rx("ساحضر|سوف احضر|ساذهب|سوف اذهب").containsMatchIn(clean) && !reported.containsMatchIn(clean)
            val place = rx("(?:في|بـ)\\s+((?:مطعم|مقهى|قاعة|فندق)\\s+.+?)(?=\\s+(?:غدا|اليوم|الساعة|عند)|[.!،]|$)").find(sentence)?.groupValues?.get(1)
            ProposedCommitment(sentence.trimEnd('.', '!'), evidence.joinToString("\n"), source.capturedAt, source.zoneId, "Basic Arabic rule; uncertain dates and places require review.", start, ending, informational = !personal, placeName = place)
        }
    }
    fun parseChange(text: String): EventChange? {
        val clean = normalized(text).trim().trimEnd('.', '!')
        if (negative.containsMatchIn(clean) || reported.containsMatchIn(clean) || clean.endsWith('؟') || clean.endsWith('?')) return null
        rx("^(?:الغي|الغ)\\s+(.+)$").matchEntire(clean)?.let { return EventChange(it.groupValues[1], true, "", text, localized = true) }
        rx("^(?:انقل|غير موعد)\\s+(.+?)\\s+الى\\s+(.+)$").matchEntire(clean)?.let { return EventChange(it.groupValues[1], false, it.groupValues[2], text, localized = true) }
        return null
    }
}
