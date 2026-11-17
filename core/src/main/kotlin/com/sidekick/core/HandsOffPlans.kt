package com.sidekick.core

import java.time.*
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Plan kinds with documented, conservative hands-off defaults. [lengthMinutes] is the assumed length when none was said. */
enum class PlanKind(val lengthMinutes: Int?) {
    BIRTHDAY(null), ANNIVERSARY(null), DAY_OFF(null), FLIGHT(null), TRAIN(null), BUS(null), FERRY(null),
    MEDICAL(60), APPOINTMENT(60), MEETING(60), CALL(30), DEADLINE(null), BILL(null), PICKUP(null),
    RESTAURANT(null), SERVICE_WINDOW(null), EVENT(null);
    val travel: Boolean get() = this == FLIGHT || this == TRAIN || this == BUS || this == FERRY
}

/** Visible hands-off notes on a plan. Only these sentences survive an automatic save, so every assumption stays visible. */
object PlanNotes {
    const val ALL_DAY = "all day (time assumed)"
    const val FOLLOW_UP = "Next reminder: 1 hour before."
    private val carried = listOf("Assumed:", "Repeats", "Reminder plan:", "Next reminder:", "May overlap with", "Lasts until")
    fun describe(assumptions: List<String>, notes: List<String>): String = (listOfNotNull(
        assumptions.distinct().takeIf { it.isNotEmpty() }?.joinToString("; ", "Assumed: ", ".")) + notes.distinct()).joinToString(" ")
    fun sentences(note: String): List<String> = note.split(Regex("(?<=\\.)\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
    /** The hands-off sentences of [note] (assumptions, repeats, reminder plan, overlaps), in order. */
    fun carry(note: String): String = sentences(note).filter { s -> carried.any { s.startsWith(it) } }.distinct().joinToString(" ")
    fun isAllDay(note: String) = note.contains(ALL_DAY)
    fun hasAssumption(note: String) = sentences(note).any { it.startsWith("Assumed:") }
    /** After a delivered reminder: the second default reminder (medical: 1 hour before) and the note without its marker. */
    fun followUp(note: String, start: Long?, now: Long): Pair<Long, String>? {
        if (start == null || !sentences(note).contains(FOLLOW_UP)) return null
        val at = start - 3_600_000L
        if (at <= now) return null
        return at to sentences(note).filter { it != FOLLOW_UP }.joinToString(" ")
    }
}

/**
 * Hands-off plan rules on top of the grounded extractors: all-day plans with a clear day, common plan kinds and
 * their documented defaults, visible assumptions, and conflicts that must wait in Review. Never invents people,
 * places or times; the only defaults are the documented lengths and reminder leads.
 */
object HandsOffPlans {
    private fun rx(value: String) = Regex(value, RegexOption.IGNORE_CASE)
    private const val WD = EnglishDates.WEEKDAYS
    private const val MO = EnglishDates.MONTHS
    private const val COUNT = "a|an|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|\\d{1,2}"

    private val kinds: List<Pair<PlanKind, Regex>> = listOf(
        PlanKind.BIRTHDAY to rx("\\b(?:birthday|b-?day)\\b"),
        PlanKind.ANNIVERSARY to rx("\\banniversary\\b"),
        PlanKind.PICKUP to rx("\\b(?:pick(?:ing)?\\s+(?:[\\p{L}'’]+\\s+){0,3}?up|drop(?:ping)?\\s+(?:[\\p{L}'’]+\\s+){0,3}?off)\\b"),
        PlanKind.FLIGHT to rx("\\b(?:flight|fly|flying|flies|plane|lands?|landing|airport)\\b"),
        PlanKind.TRAIN to rx("\\btrains?\\b"),
        PlanKind.FERRY to rx("\\bferry\\b"),
        PlanKind.BUS to rx("\\b(?:bus|coach)\\b"),
        PlanKind.MEDICAL to rx("\\b(?:dentist|dental|doctor|doctor['’]s|dr\\.?|physio|physiotherapist|therapist|therapy|check-?up|clinic|hospital|orthodontist|optometrist|eye exam|vet|vaccination|blood test|x-ray|surgery)(?![\\p{L}])"),
        PlanKind.BILL to rx("\\b(?:rent|bills?|mortgage|invoice|tuition|credit card|insurance)\\b(?=.*\\b(?:due|pay|paid|payment)\\b)|\\b(?:due|pay|payment)\\b(?=.*\\b(?:rent|bills?|mortgage|invoice|tuition|credit card|insurance)\\b)"),
        PlanKind.RESTAURANT to rx("\\b(?:reservation|reserved a table|booked a table|table for|restaurant)\\b"),
        PlanKind.SERVICE_WINDOW to rx("\\b(?:plumber|electrician|technician|repair(?:man|men|s)?|installer|handyman|delivery|deliveries|courier|movers|inspector|cleaners?)\\b"),
        PlanKind.DEADLINE to rx("\\b(?:due|deadline|submit|hand in|turn in)\\b|\\bby\\s+(?:the\\s+\\d|(?:this\\s+|next\\s+)?(?:$WD)\\b|(?:$MO)\\b|tomorrow\\b|tonight\\b|today\\b|end of\\b)"),
        PlanKind.DAY_OFF to rx("\\b(?:day off|days off|holiday|holidays|vacation|annual leave|pto|time off|off work|out of office)\\b|\\b(?:I['’]m|I am|we['’]re|we are|I['’]ll be|I will be|he['’]s|she['’]s|they['’]re)\\s+off\\b|^off\\b"),
        PlanKind.MEETING to rx("\\b(?:meeting|stand-?up|interview|one-on-one|conference|sync)\\b"),
        PlanKind.CALL to rx("\\b(?:call|zoom|facetime|video chat)\\b"),
        PlanKind.APPOINTMENT to rx("\\b(?:appointment|haircut)\\b"),
        PlanKind.EVENT to rx("\\b(?:party|class|lecture|concert|dinner|lunch|breakfast|brunch|wedding|practice|workshop|movie|trip|shift|game|show|recital|ceremony|graduation|funeral|reunion|exam|picnic|barbecue|bbq|festival|match|gym|yoga|rehearsal|playdate|sleepover)\\b"),
    )

    /** The plan kind named in [text], English first, then the multilingual event words. */
    fun kind(text: String): PlanKind? {
        kinds.firstOrNull { it.second.containsMatchIn(text) }?.let { return it.first }
        return when (LocalizedActivities.eventKey(text)) {
            null -> null
            "birthday" -> PlanKind.BIRTHDAY
            "meeting" -> PlanKind.MEETING
            "appointment" -> PlanKind.APPOINTMENT
            else -> PlanKind.EVENT
        }
    }

    // ---- wording filters -------------------------------------------------------------------------------------
    private val negation = rx("\\b(?:not|never|no|don['’]t|doesn['’]t|won['’]t|can['’]t|cannot|isn['’]t|aren['’]t|wasn['’]t|weren['’]t|didn['’]t|cancel(?:l)?ed|called off|postponed)\\b|n['’]t\\b")
    private val hedge = rx("\\b(?:maybe|might|perhaps|possibly|probably|not sure|i think|i guess|tentative(?:ly)?|pencil(?:led)? in|if|unless|could|should|would)\\b")
    private val past = rx("\\b(?:yesterday|last\\s+(?:night|week|month|year|$WD)|used to|ago|was|were|went|had|did)\\b")
    private val quoted = Regex("[\"“”«»]")
    private fun skip(sentence: String) = sentence.endsWith('?') || sentence.endsWith('？') || quoted.containsMatchIn(sentence) ||
        AnalysisContext.reported(sentence) || EventChange.looksLikeChange(sentence) ||
        rx("\\b(?:said|says|told|tells|heard|pretend|imagine)\\b").containsMatchIn(sentence)
    private fun negated(sentence: String) = negation.containsMatchIn(sentence.replace(rx("\\bnot sure\\b"), " "))
    private fun hedgeWord(sentence: String) = hedge.find(sentence)?.value

    // ---- ownership -------------------------------------------------------------------------------------------
    private val opener = rx("^(?:(?:so|and|oh|also|yeah|okay|ok|well|actually|remember|maybe|perhaps|probably|possibly|i think|i guess)[, ]+)+")
    private val thereIs = rx("^there(?:['’]s| is| will be| are)\\b")
    private val relation = rx("^(?:(?:my|our|the|your)\\s+)?(?:mom|mum|mother|dad|father|parents|sister|brother|wife|husband|partner|son|daughter|kids?|children|boss|friend|grandma|grandpa|grandmother|grandfather|aunt|uncle|cousin|colleague|coworker|neighbou?r|roommate)(?:['’]s)?\\b")
    private val pronoun = rx("^(?:he|she|they|his|her|their)\\b")
    private val ownerCue = rx("^(?:I(?:['’]ve| have)(?: got)?|I got|I['’]m (?:having|seeing|flying|taking|off)|my|we(?:['’]ve| have)(?: got)?|our)\\b")
    private val someoneTravels = rx("^(?!i\\b|we\\b)[\\p{L}'’]+\\s+(?:lands|arrives|flies|is landing|is arriving|is flying|gets in)\\b")
    private val afterName = rx("^(?:['’]s\\b|['’]ll\\b|\\s+(?:is|has|lands|arrives|flies|leaves|gets|will|comes|starts)\\b)")
    private val stopWords = setOf("i", "we", "my", "our", "the", "this", "that", "it", "there", "tomorrow", "today", "tonight", "next",
        "on", "at", "in", "from", "every", "pick", "drop", "call", "meet", "remind", "submit", "pay", "book", "take", "bring", "buy",
        "get", "go", "see", "visit", "send", "finish", "let", "let's", "it's", "that's", "please", "don't", "rent", "flight", "dinner",
        "lunch", "party", "meeting", "dentist", "doctor", "report", "table", "reservation", "plumber", "delivery", "holiday", "vacation")
    private fun stripped(text: String) = opener.replace(text.trim(), "")
    private fun thirdParty(text: String): Boolean {
        val t = stripped(text)
        if (relation.containsMatchIn(t) || pronoun.containsMatchIn(t) || someoneTravels.containsMatchIn(t)) return true
        val name = Regex("^(\\p{Lu}\\p{Ll}+)").find(t) ?: return false
        val word = name.groupValues[1]
        if (word.lowercase(Locale.ROOT) in stopWords || kinds.any { it.second.matches(word) } || EnglishDates.pattern.matches(word)) return false
        return afterName.containsMatchIn(t.substring(name.range.last + 1))
    }
    private fun personal(text: String, kind: PlanKind, allDay: Boolean): Boolean {
        val t = stripped(text)
        if (thereIs.containsMatchIn(t)) return false
        if (thirdParty(t)) return allDay && (kind == PlanKind.BIRTHDAY || kind == PlanKind.ANNIVERSARY)
        return true
    }

    // ---- dates -----------------------------------------------------------------------------------------------
    private val ordinal = rx("(?<![\\w-])(?:on\\s+)?the\\s+([12]\\d|3[01]|0?[1-9])(?:st|nd|rd|th)\\b(?!\\s+of\\b)")
    private val nth = rx("\\b(first|second|third|fourth|last|1st|2nd|3rd|4th)\\s+($WD)\\s+of\\s+(?:the|every|each|this)?\\s*month\\b")
    private val everyWeekday = rx("\\bevery\\s+(other\\s+)?($WD)\\b|\\bon\\s+($WD)s\\b")
    private val everyUnit = rx("\\b(?:every\\s+(?:other\\s+)?(?:day|week|month|year)|daily|weekly|monthly|yearly|annually|fortnightly)\\b")
    private val spanJoin = rx("^\\s*(?:-|–|to|until|till|through|thru)\\s*$")
    private val forSpan = rx("\\bfor\\s+($COUNT)\\s+(days?|weeks?)\\b")
    private data class Found(val range: IntRange, val phrase: String, val date: LocalDate, val ordinalDay: Int?)
    private data class Day(val first: LocalDate, val last: LocalDate, val assumptions: List<String>, val notes: List<String>, val weekly: Boolean)

    fun format(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " +
        date.dayOfMonth + " " + date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    private fun weekday(name: String) = DayOfWeek.valueOf(name.uppercase(Locale.ROOT))
    private fun capital(day: DayOfWeek) = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    private fun monthDay(day: Int, from: LocalDate): LocalDate? {
        var month = YearMonth.from(from)
        repeat(3) {
            if (day <= month.lengthOfMonth()) month.atDay(day).takeIf { !it.isBefore(from) }?.let { return it }
            month = month.plusMonths(1)
        }
        return null
    }
    private fun found(text: String, today: LocalDate): List<Found>? {
        val values = mutableListOf<Found>()
        for (m in EnglishDates.pattern.findAll(text))
            values += Found(m.range, m.value, runCatching { EnglishDates.resolve(m.value, today) }.getOrNull() ?: return null, null)
        for (m in ordinal.findAll(text)) {
            val day = m.groupValues[1].toInt()
            values += Found(m.range, m.value.trim(), monthDay(day, today) ?: return null, day)
        }
        return values.sortedBy { it.range.first }
    }

    /** (day, mentioned): mentioned with a null day means the day words conflict or cannot be resolved. */
    private fun day(text: String, today: LocalDate): Pair<Day?, Boolean> {
        nth.find(text)?.let { m ->
            val dow = weekday(m.groupValues[2]); val which = m.groupValues[1].lowercase(Locale.ROOT)
            val index = when (which) { "first", "1st" -> 1; "second", "2nd" -> 2; "third", "3rd" -> 3; "fourth", "4th" -> 4; else -> -1 }
            fun inMonth(month: YearMonth): LocalDate = if (index < 0) month.atEndOfMonth().with(TemporalAdjusters.previousOrSame(dow))
                else month.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(index, dow))
            var date = inMonth(YearMonth.from(today))
            if (date.isBefore(today)) date = inMonth(YearMonth.from(today).plusMonths(1))
            return Day(date, date, emptyList(), listOf("Repeats on the $which ${capital(dow)} of each month — only the first date is saved."), false) to true
        }
        everyWeekday.find(text)?.let { m ->
            val dow = weekday(m.groupValues[2].ifEmpty { m.groupValues[3] })
            val date = today.with(TemporalAdjusters.nextOrSame(dow))
            val other = if (m.groupValues[1].isNotEmpty()) "other " else ""
            return Day(date, date, emptyList(), listOf("Repeats every $other${capital(dow)} — only the first date is saved."), true) to true
        }
        val values = found(text, today) ?: return null to true
        val repeat = everyUnit.find(text)?.let { listOf("Repeats ${it.value.lowercase(Locale.ROOT)} — only the first date is saved.") }.orEmpty()
        if (values.isEmpty()) return null to false
        fun assumed(value: Found): List<String> = when {
            value.ordinalDay != null -> listOf("${format(value.date)} (month assumed from '${value.phrase.removePrefix("on ").trim()}')")
            EnglishDates.hasNextWeekday(value.phrase) -> listOf("${format(value.date)} (assumed from '${value.phrase.lowercase(Locale.ROOT)}')")
            else -> emptyList()
        }
        if (values.size == 2 && spanJoin.matches(text.substring(values[0].range.last + 1, values[1].range.first))) {
            val (a, b) = values
            val end = when {
                b.ordinalDay != null -> monthDay(b.ordinalDay, a.date)
                Regex("^(?:this\\s+)?($WD)$", RegexOption.IGNORE_CASE).matches(b.phrase.trim()) ->
                    a.date.with(TemporalAdjusters.nextOrSame(weekday(b.phrase.trim().lowercase(Locale.ROOT).removePrefix("this").trim())))
                else -> b.date
            } ?: return null to true
            if (end.isBefore(a.date) || ChronoUnit.DAYS.between(a.date, end) > 60) return null to true
            return Day(a.date, end, assumed(a), repeat, false) to true
        }
        if (values.map { it.date }.toSet().size != 1) return null to true
        val first = values.first()
        val last = forSpan.find(text)?.let { m ->
            val amount = EnglishDates.amount(m.groupValues[1].lowercase(Locale.ROOT))
            first.date.plusDays(if (m.groupValues[2].startsWith("week", true)) amount * 7 - 1 else amount - 1)
        }?.takeIf { !it.isBefore(first.date) && ChronoUnit.DAYS.between(first.date, it) <= 60 } ?: first.date
        return Day(first.date, last, assumed(first), repeat, false) to true
    }

    // ---- clocks ----------------------------------------------------------------------------------------------
    private const val T = "(?:(?:1[0-2]|0?[1-9])(?::[0-5]\\d)?(?:\\s*[ap]\\.?m\\.?(?!\\w))?|(?:[01]?\\d|2[0-3]):[0-5]\\d|noon|midnight)"
    private val window = rx("\\b(?:between|from)\\s+($T)\\s+(?:and|to|until|till|-|–)\\s+($T)(?![\\w:])")
    private val arrival = rx("\\b(?:lands?|landing|arrives?|arriving|gets?\\s+in|arrival(?:\\s+is)?)\\s+(?:at\\s+|around\\s+|about\\s+)?($T)(?![\\w:])")
    private val ending = rx("\\b(?:ends?|finish(?:es)?|until|till)\\s+(?:at\\s+|around\\s+|about\\s+|by\\s+)?($T)(?![\\w:])")
    private val explicit = rx("(?<![\\w:])(?:(?:1[0-2]|0?[1-9])(?::[0-5]\\d)?\\s*[ap]\\.?m\\.?(?!\\w)|(?:[01]?\\d|2[0-3]):[0-5]\\d(?!\\d)|noon\\b|midnight\\b)")
    private val bare = Regex("(?i:\\b(?:at|around)\\s+)(1[0-2]|0?[1-9])(?i:\\s*o['’]?clock)?(?![\\d:])(?!\\s*(?i:[ap]\\.?m|%|(?:st|nd|rd|th)\\b))" +
        "(?!\\s+(?:[A-Z]|(?i:street|st\\b|avenue|ave\\b|road|rd\\b|people|guests|persons|of\\b|days?\\b|weeks?\\b|hours?\\b|minutes?\\b|months?\\b)))")
    private val alternative = rx("\\b(?:\\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)(?::\\d{2})?\\s*(?:[ap]\\.?m\\.?)?\\s+or\\s+(?:\\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\\b")
    private val evening = rx("\\b(?:dinner|party|concert|movie|show|drinks|bar|pub|tonight|evening|night|game)\\b")
    private val morning = rx("\\b(?:breakfast|morning)\\b")

    /** (time, meridiem known). Hour-only and h:mm clocks from 1 to 12 have no known am/pm. */
    private fun parseClock(raw: String): Pair<LocalTime, Boolean>? {
        val v = raw.lowercase(Locale.ROOT).replace(" ", "").replace(".", "").replace("o'clock", "").replace("o’clock", "").replace("oclock", "")
        if (v == "noon") return LocalTime.NOON to true
        if (v == "midnight") return LocalTime.MIDNIGHT to true
        val m = Regex("(\\d{1,2})(?::(\\d{2}))?(am|pm)?").matchEntire(v) ?: return null
        val hour = m.groupValues[1].toInt(); val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
        if (m.groupValues[3].isNotEmpty()) {
            if (hour !in 1..12) return null
            return LocalTime.of(hour % 12 + if (m.groupValues[3] == "pm") 12 else 0, minute) to true
        }
        if (hour == 0 || hour in 13..23) return LocalTime.of(hour, minute) to true
        if (hour !in 1..12) return null
        return LocalTime.of(hour % 12, minute) to false
    }
    private fun guess(time: LocalTime, text: String): LocalTime {
        val hour = if (time.hour == 0) 12 else time.hour
        val pm = when { morning.containsMatchIn(text) -> false; evening.containsMatchIn(text) -> true; else -> hour == 12 || hour in 1..6 }
        return LocalTime.of(hour % 12 + if (pm) 12 else 0, time.minute)
    }
    private fun label(time: LocalTime): String {
        val hour = if (time.hour % 12 == 0) 12 else time.hour % 12
        return "$hour" + (if (time.minute != 0) ":%02d".format(time.minute) else "") + if (time.hour < 12) " am" else " pm"
    }
    private fun at(day: LocalDate, time: LocalTime, zone: ZoneId): Instant? =
        runCatching { TimeResolver.parseEditor("$day ${time.toString().take(5)}", zone) }.getOrNull()
    private fun hasClock(text: String): Boolean {
        val n = EnglishClocks.normalize(text)
        return explicit.containsMatchIn(n) || bare.containsMatchIn(n) || window.containsMatchIn(n) ||
            rx("\\bin\\s+(?:half an hour|(?:$COUNT|twenty|thirty|forty|fifty|sixty|ninety)\\s+(?:minutes?|hours?))\\b").containsMatchIn(n)
    }
    fun alternativeTimes(text: String): Boolean {
        val n = EnglishClocks.normalize(text)
        if (alternative.containsMatchIn(n)) return true
        val reserved = listOfNotNull(window.find(n)?.range, arrival.find(n)?.range, ending.find(n)?.range)
        return explicit.findAll(n).count { m -> reserved.none { m.range.first in it } } > 1
    }

    // ---- titles ----------------------------------------------------------------------------------------------
    private val flightCode = Regex("\\b([A-Z]{2}|[A-Z]\\d|\\d[A-Z])\\s?(\\d{1,4})\\b")
    private val flightDigits = rx("\\bflight\\s+(?:number\\s+)?(\\d{2,4})\\b")
    private val notAirlines = setOf("AM", "PM", "ET", "PT", "CT", "MT", "UK", "US", "TV", "OK", "ID", "NO", "AN", "OR", "TO", "AT", "IN", "ON", "BY", "IS", "MY")
    private const val PLACE = "((?:[A-Z][\\p{L}'’.-]*)(?:\\s+[A-Z][\\p{L}'’.-]*){0,2})"
    private val fromPlace = Regex("\\b(?i:from)\\s+$PLACE")
    private val toPlace = Regex("\\b(?i:to)\\s+$PLACE")
    private val partySize = rx("\\b(?:table|reservation|booking)\\s+for\\s+($COUNT)\\b|\\bfor\\s+($COUNT)\\s+(?:people|persons|guests|of us)\\b")
    fun flightNumber(text: String): String? = flightCode.findAll(text).firstOrNull { it.groupValues[1] !in notAirlines }
        ?.let { "${it.groupValues[1]} ${it.groupValues[2]}" } ?: flightDigits.find(text)?.groupValues?.get(1)
    private fun cleanPlace(value: String?): String? = value?.split(Regex("\\s+"))?.takeWhile { word ->
        !EnglishDates.pattern.matches(word) && word.lowercase(Locale.ROOT) !in setOf("the", "on", "at", "i", "and")
    }?.joinToString(" ")?.trimEnd('.', ',')?.takeIf { it.length >= 2 }
    private fun flightTitle(text: String): String? {
        val number = flightNumber(text)
        val from = cleanPlace(fromPlace.find(text)?.groupValues?.get(1))
        val to = cleanPlace(toPlace.find(text)?.groupValues?.get(1))
        if (number == null && from == null && to == null) return null
        return listOfNotNull("Flight", number, from?.let { "from $it" }, to?.let { "to $it" }).joinToString(" ")
    }
    private fun restaurantTitle(text: String, place: String?): String? {
        val size = partySize.find(text)?.let { m -> m.groupValues[1].ifEmpty { m.groupValues[2] } }
            ?.let { EnglishDates.amount(it.lowercase(Locale.ROOT)) }
        return when {
            size != null && place != null -> "Table for $size at $place"
            size != null -> "Table for $size"
            place != null -> "Reservation at $place"
            else -> null
        }
    }
    private fun sentenceTitle(text: String) = text.trim().trimEnd('.', '!', '。').replaceFirstChar { it.uppercase() }.take(500)

    // ---- detection -------------------------------------------------------------------------------------------
    private fun norm(text: String) = text.lowercase(Locale.ROOT).replace('’', '\'').trim().trimEnd('.', '!', '。', ' ')
    private fun covered(sentence: String, items: List<ProposedCommitment>): Boolean {
        val key = norm(sentence)
        return key.isEmpty() || items.any { item -> norm(item.evidence).contains(key) || item.evidence.lines().any { norm(it) == key } }
    }

    private fun detect(sentence: String, source: Transcript, owner: Boolean): ProposedCommitment? {
        val clean = sentence.trim()
        if (clean.isEmpty() || skip(clean)) return null
        if (LocalizedActivities.hasEvent(clean)) return localized(clean, source, owner)
        val kind = kind(clean) ?: return null
        if (negated(clean) || hedgeWord(clean) != null || past.containsMatchIn(clean)) return null
        val zone = source.zoneId; val captured = source.capturedAt
        val today = captured.atZone(zone).toLocalDate()
        val (day, mentioned) = day(clean, today)
        if (mentioned && day == null) return null
        val normalized = EnglishClocks.normalize(clean)
        val win = window.find(normalized)
        if (EnglishDates.unresolvedTiming(clean) && win == null) return null
        if (alternative.containsMatchIn(normalized)) return null
        val arrive = if (kind.travel || kind == PlanKind.PICKUP) arrival.find(normalized) else null
        val finish = ending.find(normalized)
        val reserved = listOfNotNull(win?.range, arrive?.range, finish?.range)
        val starts = explicit.findAll(normalized).filter { m -> reserved.none { m.range.first in it } }.toList()
        if (starts.size > 1 || (win != null && starts.isNotEmpty())) return null
        val assumptions = mutableListOf<String>()
        fun read(raw: String): LocalTime? {
            val (time, known) = parseClock(raw) ?: return null
            if (known) return time
            return guess(time, clean).also { assumptions += "${label(it)} (am/pm assumed)" }
        }
        var startTime: LocalTime? = null
        var endTime: LocalTime? = null
        if (win != null) {
            val first = parseClock(win.groupValues[1]) ?: return null
            val second = parseClock(win.groupValues[2]) ?: return null
            var a = first.first; var b = second.first
            if (!first.second || !second.second) {
                if (second.second && !first.second) {
                    a = LocalTime.of(a.hour % 12 + (if (b.hour >= 12 && (a.hour % 12) < (b.hour % 12 + if (b.hour % 12 == 0) 12 else 0)) 12 else 0), a.minute)
                } else if (!second.second && first.second) {
                    b = LocalTime.of(b.hour % 12 + if (a.hour >= 12 || b.hour % 12 <= a.hour % 12) 12 else 0, b.minute)
                } else {
                    a = guess(a, clean)
                    b = LocalTime.of(b.hour % 12, b.minute).let { raw -> if (raw.isAfter(a)) raw else raw.plusHours(12) }
                }
                assumptions += "${label(a)} to ${label(b)} (am/pm assumed)"
            }
            if (!b.isAfter(a)) return null
            startTime = a; endTime = b
        } else {
            startTime = starts.singleOrNull()?.let { read(it.value) ?: return null }
            if (startTime == null && arrive == null) bare.find(normalized)?.let { m -> startTime = read(m.groupValues[1]) ?: return null }
            if (arrive != null) {
                val time = read(arrive.groupValues[1]) ?: return null
                if (startTime == null) startTime = time else endTime = time
            }
            if (finish != null && startTime != null) endTime = read(finish.groupValues[1]) ?: return null
        }
        val notes = day?.notes.orEmpty().toMutableList()
        assumptions.addAll(0, day?.assumptions.orEmpty())
        val start: Instant
        var end: Instant?
        var allDay = false
        if (startTime != null) {
            var date = day?.first ?: today
            var value = at(date, startTime!!, zone) ?: return null
            if (!value.isAfter(captured)) {
                when {
                    day == null -> date = date.plusDays(1)
                    day.weekly -> date = date.plusDays(7)
                    else -> return null
                }
                value = at(date, startTime!!, zone) ?: return null
            }
            if (day == null) assumptions.add(0, "${format(date)} (day assumed)")
            start = value
            end = endTime?.let { at(date, it, zone) }?.let { if (it.isAfter(start)) it else it.plus(Duration.ofDays(1)) }
            if (end != null && Duration.between(start, end) > Duration.ofDays(7)) end = null
        } else {
            day ?: return null
            start = day.first.atStartOfDay(zone).toInstant()
            end = if (ChronoUnit.DAYS.between(day.first, day.last) >= 7) {
                notes += "Lasts until ${format(day.last)} — only the first day is blocked (longer than 7 days)."
                day.first.plusDays(1).atStartOfDay(zone).toInstant()
            } else day.last.plusDays(1).atStartOfDay(zone).toInstant()
            if (!end.isAfter(captured)) return null
            assumptions.add(0, PlanNotes.ALL_DAY)
            allDay = true
        }
        val ownPlan = owner && personal(clean, kind, allDay)
        val place = PlaceHints.extract(clean)
        val title = when (kind) {
            PlanKind.FLIGHT -> flightTitle(clean)
            PlanKind.RESTAURANT -> restaurantTitle(clean, place.name)
            else -> null
        } ?: sentenceTitle(clean)
        return ProposedCommitment(title, clean.trimEnd('.', '!'), captured, zone,
            if (ownPlan) "Detected plan; check the listed assumptions. " else "Information only; attendance unconfirmed. ",
            start, end, informational = !ownPlan, placeName = place.name, placeAddress = place.address,
            allDay = allDay, assumptions = assumptions, notes = notes)
    }

    private fun localized(sentence: String, source: Transcript, owner: Boolean): ProposedCommitment? {
        val today = source.capturedAt.atZone(source.zoneId).toLocalDate()
        val (_, date, own) = LocalizedActivities.undated(sentence, today, owner) ?: return null
        val start = date.atStartOfDay(source.zoneId).toInstant()
        val end = date.plusDays(1).atStartOfDay(source.zoneId).toInstant()
        if (!end.isAfter(source.capturedAt)) return null
        return ProposedCommitment(sentenceTitle(sentence), sentence.trimEnd('.', '!', '。'), source.capturedAt, source.zoneId,
            if (own) "Personal plan detected; check the listed assumptions. " else "Information only; attendance unconfirmed. ",
            start, end, informational = !own, allDay = true, assumptions = listOf(PlanNotes.ALL_DAY))
    }

    /** An owner's hedged plan ("maybe dinner Friday at 7"): not saved, waits in Review with the reason. */
    private fun hedgedPlan(sentence: String, source: Transcript): ProposedCommitment? {
        val clean = sentence.trim()
        if (clean.isEmpty() || skip(clean) || LocalizedActivities.hasEvent(clean)) return null
        kind(clean) ?: return null
        val word = hedgeWord(clean)
        val twoTimes = alternativeTimes(clean)
        if ((word == null && !twoTimes) || negated(clean) || past.containsMatchIn(clean)) return null
        val t = stripped(clean)
        if (thereIs.containsMatchIn(t) || thirdParty(t)) return null
        val today = source.capturedAt.atZone(source.zoneId).toLocalDate()
        val (day, mentioned) = day(clean, today)
        if (day == null && !mentioned && !hasClock(clean)) return null
        val reason = if (word != null) "it sounded uncertain (“${word.lowercase(Locale.ROOT)}”)" else "two different times were said"
        return ProposedCommitment(sentenceTitle(clean), clean.trimEnd('.', '!'), source.capturedAt, source.zoneId,
            "Not saved automatically: $reason. ", conflict = "Not saved automatically: $reason. Check before saving.")
    }

    private fun upgrade(item: ProposedCommitment, source: Transcript, owner: Boolean): ProposedCommitment {
        val first = item.evidence.substringBefore('\n')
        var result = item
        val kind = kind(first)
        if (owner && result.informational && kind != null && ownerCue.containsMatchIn(stripped(first)) && !thirdParty(first) &&
            !negated(first) && hedgeWord(first) == null && result.evidence.lines().none { ConversationActivities.attendanceValue(it) != null })
            result = result.copy(informational = false, needsReview = result.needsReview.replace("Attendance is unconfirmed. ", "Your plan (“${first.trim().split(Regex("\\s+")).take(2).joinToString(" ")}”). "))
        if (owner && !result.informational && result.activityStartAt == null && alternativeTimes(first))
            return result.copy(conflict = "Not saved automatically: two different times were said. Check before saving.")
        if (result.activityStartAt == null && !result.allDay && !EnglishDates.unresolvedTiming(first) && !hasClock(first) && !negated(first)) {
            val today = source.capturedAt.atZone(source.zoneId).toLocalDate()
            val day = day(first, today).first ?: return result
            val start = day.first.atStartOfDay(source.zoneId).toInstant()
            val end = (if (ChronoUnit.DAYS.between(day.first, day.last) >= 7) day.first else day.last).plusDays(1).atStartOfDay(source.zoneId).toInstant()
            if (!end.isAfter(source.capturedAt)) return result
            result = result.copy(activityStartAt = start, activityEndAt = end, allDay = true,
                assumptions = listOf(PlanNotes.ALL_DAY) + day.assumptions + result.assumptions, notes = result.notes + day.notes)
        }
        return result
    }

    private val specific = setOf(PlanKind.MEDICAL, PlanKind.FLIGHT, PlanKind.BIRTHDAY, PlanKind.ANNIVERSARY, PlanKind.RESTAURANT, PlanKind.TRAIN)
    private val possessive = Regex("([\\p{L}]+)['’]s\\b")
    private val notNames = setOf("it", "that", "there", "let", "what", "he", "she", "who", "today", "tomorrow", "tonight", "here", "where", "how", "i", "everyone")
    private fun names(text: String): Set<String> {
        val words = Regex("[\\p{L}'’]+").findAll(text).toList()
        val capitals = words.drop(1).map { it.value.replace(Regex("['’]s$"), "") }
            .filter { it.first().isUpperCase() && !EnglishDates.pattern.matches(it) && it.lowercase(Locale.ROOT) !in stopWords && kind(it) == null }
        return (possessive.findAll(text).map { it.groupValues[1] }.filter { it.lowercase(Locale.ROOT) !in notNames } + capitals)
            .map { it.lowercase(Locale.ROOT) }.filter { it !in notNames && it != "i" }.toSet()
    }
    /** A literal plan identity for merging and refinement: kind plus named people, a flight number, or the event subject. */
    fun identity(text: String): String? {
        val first = text.substringBefore('\n')
        val kind = kind(first) ?: return null
        flightNumber(first)?.takeIf { kind == PlanKind.FLIGHT }?.let { return "flight:$it" }
        if (kind != PlanKind.EVENT && kind != PlanKind.MEETING && kind != PlanKind.CALL) return kind.name + ":" + names(first).sorted().joinToString(",")
        return ActivityIdentity.subject(first)?.let { "event:$it" } ?: (kind.name + ":" + names(first).sorted().joinToString(","))
    }
    private fun specificIdentity(item: ProposedCommitment): String? {
        val first = item.evidence.substringBefore('\n')
        val kind = kind(first) ?: return null
        val id = identity(first) ?: return null
        val subjectWords = ActivityIdentity.subject(first)?.split(' ')
            ?.count { it !in setOf("have", "has", "got", "i've", "we", "we've", "our", "my", "a") } ?: 0
        return id.takeIf { kind in specific || names(first).isNotEmpty() || subjectWords >= 2 }
    }

    /**
     * Hands-off completion of detected plans: owner wording ("I have a dentist appointment") makes a plan personal,
     * a clear day without a clock becomes an all-day plan, plans the older rules miss are added, and conflicting
     * statements are marked [ProposedCommitment.conflict]. [includeReview] adds owner hedges as Review-only items.
     */
    fun complete(source: Transcript, proposals: List<ProposedCommitment>, includeReview: Boolean = true): List<ProposedCommitment> {
        if (!source.processingAllowed || source.speaker == Speaker.UNKNOWN) return proposals
        val owner = source.speaker == Speaker.USER
        val upgraded = proposals.map { upgrade(it, source, owner) }
        val sentences = Sentences.split(source.text)
        val added = mutableListOf<ProposedCommitment>()
        for (sentence in sentences) {
            if (covered(sentence, upgraded + added)) continue
            (detect(sentence, source, owner) ?: if (owner && includeReview) hedgedPlan(sentence, source) else null)?.let(added::add)
        }
        val all = (upgraded + added).toMutableList()
        // The same specific plan said with two different times: the later statement waits in Review.
        if (owner) {
            val text = source.text.lowercase(Locale.ROOT)
            val timed = all.withIndex().filter { (_, it) -> !it.informational && !it.allDay && it.activityStartAt != null && it.conflict == null }
            val groups = timed.groupBy { (_, it) -> specificIdentity(it)?.let { id -> id to it.activityStartAt!!.atZone(it.zoneId).toLocalDate() } }
            for ((key, members) in groups) {
                if (key == null || members.map { it.value.activityStartAt }.toSet().size < 2) continue
                val ordered = members.sortedBy { text.indexOf(norm(it.value.evidence.substringBefore('\n'))).let { at -> if (at < 0) Int.MAX_VALUE else at } }
                val firstTime = ordered.first().value.activityStartAt!!.atZone(source.zoneId).toLocalTime()
                ordered.drop(1).filter { it.value.activityStartAt != ordered.first().value.activityStartAt }.forEach { (index, item) ->
                    all[index] = item.copy(conflict = "Not saved automatically: an earlier statement gave ${label(firstTime)} for the same plan. Check before saving.")
                }
            }
        }
        return all
    }

    /** Documented defaults per kind: assumed length, reminder lead, repeat notes, and visible inferred days. */
    fun withDefaults(item: ProposedCommitment): ProposedCommitment {
        if (item.conflict != null) return item
        val first = item.evidence.substringBefore('\n')
        val kind = kind(first) ?: kind(item.title)
        val zone = item.zoneId
        val captured = item.capturedAt
        val start = item.activityStartAt ?: TimeResolver.suggest(first, captured, zone)
        val assumptions = item.assumptions.toMutableList()
        val notes = item.notes.toMutableList()
        if (start != null && !item.allDay) {
            val date = start.atZone(zone).toLocalDate()
            if (item.needsReview.contains("Date inferred") && assumptions.none { it.contains("(day assumed)") })
                assumptions += "${format(date)} (day assumed)"
            EnglishDates.pattern.findAll(first).firstOrNull { EnglishDates.hasNextWeekday(it.value) }?.let { m ->
                if (assumptions.none { it.contains("(assumed from") }) assumptions += "${format(date)} (assumed from '${m.value.lowercase(Locale.ROOT)}')"
            }
        }
        var end = item.activityEndAt
        val length = kind?.lengthMinutes
        if (!item.allDay && item.activityStartAt != null && end == null && length != null) {
            end = item.activityStartAt.plus(Duration.ofMinutes(length.toLong()))
            assumptions += (if (length == 60) "1 hour" else "$length minutes") + " (length assumed)"
        }
        val yearly = item.allDay && (kind == PlanKind.BIRTHDAY || kind == PlanKind.ANNIVERSARY)
        if (yearly && notes.none { it.startsWith("Repeats") }) notes += "Repeats yearly — only this year's date is saved."
        if (kind == PlanKind.BILL && Regex("\\brent\\b", RegexOption.IGNORE_CASE).containsMatchIn(first) && notes.none { it.startsWith("Repeats") })
            notes += "Repeats monthly (assumed for rent) — only this date is saved."
        var reminder: Instant? = null
        if (!item.informational && start != null) {
            val day = start.atZone(zone).toLocalDate()
            fun nine(date: LocalDate) = at(date, LocalTime.of(9, 0), zone)
            val (times, plan) = when {
                item.allDay && yearly -> listOfNotNull(nine(day.minusDays(1))) to "the day before at 9:00"
                item.allDay && kind == PlanKind.DAY_OFF -> emptyList<Instant>() to null
                item.allDay && kind == PlanKind.BILL -> listOfNotNull(nine(day.minusDays(2))) to "2 days before at 9:00"
                item.allDay && kind == PlanKind.MEDICAL -> listOfNotNull(nine(day.minusDays(1))) to "the day before at 9:00"
                item.allDay -> listOfNotNull(nine(day)) to "9:00 that morning"
                kind == PlanKind.FLIGHT -> listOf(start.minus(Duration.ofHours(3))) to "3 hours before (leave for the airport)"
                kind?.travel == true -> listOf(start.minus(Duration.ofHours(1))) to "1 hour before (time to leave)"
                kind == PlanKind.MEDICAL -> listOf(start.minus(Duration.ofDays(1)), start.minus(Duration.ofHours(1))) to "the day before and 1 hour before"
                kind == PlanKind.BILL -> listOfNotNull(nine(day.minusDays(2))) to "2 days before at 9:00"
                kind == PlanKind.PICKUP -> listOf(start.minus(Duration.ofMinutes(15))) to "15 minutes before"
                else -> listOf(start) to null
            }
            val future = times.filter { it.isAfter(captured) }
            reminder = future.firstOrNull() ?: start.takeIf { !item.allDay && it.isAfter(captured) }
            if (plan != null && reminder != null) notes += "Reminder plan: ${if (future.isEmpty()) "at the start (the usual lead time has passed)" else plan}."
            if (kind == PlanKind.MEDICAL && !item.allDay && future.size == 2) notes += PlanNotes.FOLLOW_UP
        }
        return item.copy(activityEndAt = end, assumptions = assumptions.distinct(), notes = notes.distinct(), reminderAt = reminder)
    }

    /** A plan reference for matching against saved items (times in epoch milliseconds). */
    data class PlanRef(val title: String, val evidence: String, val start: Long?, val end: Long?, val note: String, val informational: Boolean)
    private fun date(value: Long, zone: ZoneId) = Instant.ofEpochMilli(value).atZone(zone).toLocalDate()

    /** True when [candidate] states what [saved] only assumed (its time, day, am/pm or length): replace and drop the marker. */
    fun refines(saved: PlanRef, candidate: PlanRef, zone: ZoneId): Boolean {
        val savedStart = saved.start ?: return false
        val start = candidate.start ?: return false
        if (saved.informational != candidate.informational || !PlanNotes.hasAssumption(saved.note)) return false
        val id = identity(saved.evidence) ?: identity(saved.title) ?: return false
        if (id != (identity(candidate.evidence) ?: identity(candidate.title))) return false
        val savedDay = date(savedStart, zone); val day = date(start, zone)
        val days = kotlin.math.abs(ChronoUnit.DAYS.between(savedDay, day))
        val time = PlanNotes.isAllDay(saved.note) && !PlanNotes.isAllDay(candidate.note) && savedDay == day
        val next = saved.note.contains("(assumed from 'next") && !candidate.note.contains("(assumed from") && days == 7L
        val dayOnly = saved.note.contains("(day assumed)") && !candidate.note.contains("(day assumed)") && start != savedStart && days <= 7 &&
            Instant.ofEpochMilli(savedStart).atZone(zone).toLocalTime() == Instant.ofEpochMilli(start).atZone(zone).toLocalTime()
        val meridiem = saved.note.contains("(am/pm assumed)") && !candidate.note.contains("(am/pm assumed)") && kotlin.math.abs(start - savedStart) == 43_200_000L
        val length = saved.note.contains("(length assumed)") && !candidate.note.contains("(length assumed)") && start == savedStart &&
            candidate.end != null && candidate.end != saved.end
        return time || next || dayOnly || meridiem || length
    }

    /** The same plan said again: same identity and the same start (all-day plans: the same day). */
    fun samePlan(a: PlanRef, b: PlanRef, zone: ZoneId): Boolean {
        val id = identity(a.evidence) ?: identity(a.title) ?: return false
        if (id != (identity(b.evidence) ?: identity(b.title)) || a.informational != b.informational) return false
        val first = a.start ?: return false; val second = b.start ?: return false
        return first == second || (PlanNotes.isAllDay(a.note) && PlanNotes.isAllDay(b.note) && date(first, zone) == date(second, zone))
    }

    /** Saved personal timed plans overlapping [plan]; unknown ends count as one hour for this warning only. */
    fun clashes(plan: PlanRef, others: List<PlanRef>, zone: ZoneId): List<PlanRef> {
        val start = plan.start ?: return emptyList()
        if (plan.informational || PlanNotes.isAllDay(plan.note)) return emptyList()
        val end = plan.end ?: (start + 3_600_000L)
        return others.filter { other ->
            val otherStart = other.start ?: return@filter false
            !other.informational && !PlanNotes.isAllDay(other.note) && !samePlan(plan, other, zone) &&
                CalendarOverlap.overlaps(start, end, otherStart, other.end ?: (otherStart + 3_600_000L))
        }
    }
    fun clashNote(titles: List<String>): String = "May overlap with " + titles.distinct().take(2).joinToString(" and ") { "‘${it.take(60)}’" } + " (saved anyway)."

    /** A Review item whose plan has already ended (or a reminder-only item whose time passed). */
    fun stale(start: Long?, end: Long?, due: Long?, now: Long): Boolean {
        val last = end ?: start?.plus(3_600_000L) ?: due ?: return false
        return last < now
    }
}
