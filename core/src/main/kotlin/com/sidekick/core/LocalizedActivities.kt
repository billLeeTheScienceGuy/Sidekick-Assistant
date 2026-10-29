package com.sidekick.core

import java.time.*
import java.time.temporal.TemporalAdjusters

/** Small grounded multilingual grammar. Original words remain evidence, never translated guesses. */
object LocalizedActivities {
    private data class Word(val key: String, val pattern: Regex)
    private fun rx(value: String) = Regex(value, RegexOption.IGNORE_CASE)
    private val events = listOf(
        Word("party", rx("파티|派对|派對|聚会|聚會|\\bfête\\b|\\bsoirée\\b|\\bparty\\b|вечеринк[а-я]*")),
        Word("meeting", rx("회[ \\t]*의|모임|会议|會議|\\bréunion\\b|\\btreffen\\b|\\bbesprechung\\b|встреч[а-я]*|совещани[а-я]*")),
        Word("appointment", rx("약속|진료|预约|預約|\\brendez-vous\\b|\\btermin\\b|при[её]м[а-я]*")),
        Word("concert", rx("콘서트|공연|音乐会|音樂會|\\bconcert\\b|\\bkonzert\\b|концерт[а-я]*")),
        Word("dinner", rx("저녁 식사|晚餐|\\bdîner\\b|\\babendessen\\b|ужин[а-я]*")),
        Word("class", rx("수업|课程|課程|\\bcours\\b|\\bunterricht\\b|урок[а-я]*")),
        Word("birthday", rx("생일|生日|\\banniversaire\\b|\\bgeburtstag\\b|день рождения")),
        Word("wedding", rx("결혼식|婚礼|婚禮|\\bmariage\\b|\\bhochzeit\\b|свадьб[а-я]*")),
    )
    // English-only sentences stay with ActivityTiming; a Party may occur in German.
    private val foreign = rx("[가-힣\u3400-\u9fffА-Яа-яЁё]|\\b(demain|aujourd'hui|heures?|minuit|fête|réunion|dîner|anniversaire|rendez-vous|mariage|cours|konzert|abendessen|termin|unterricht|geburtstag|hochzeit|morgen|heute|uhr|endet|treffen|besprechung)\\b")
    private val negative = rx("없(?:어|다|어요|습니다)|(?<![가-힣])안\\s*(?:가|갈|해|할)|않|아니|취소|어제|아마|만약|没|沒|不|取消|昨天|可能|如果|\\b(pas|jamais|annulé|annulée|hier|peut-être|si|nicht|kein\\w*|abgesagt|gestern|vielleicht|falls|wenn|не|нет|отмен[а-я]*|вчера|возможно|если)\\b")
    private val endMarker = rx("끝|종료|까지|结束|結束|到午夜|\\b(finit|finir|termine|terminer|jusqu['’]à|endet|enden|bis|законч[а-я]*|заверш[а-я]*|до|ends?|finishes|until|till)\\b")
    private val personal = rx("(?:저는|나는|제가|내가|전\\s|난\\s).*(?:갈|가요|갑니다|참석)|我(?:明天|今天|会|會|将|將|要|打算|计划|計劃|去|参加|參加)|\\b(?:je vais|j['’]irai|je participerai|ich werde|ich gehe|ich nehme|я пойду|я приду|я собираюсь|я буду)\\b")
    private val reported = rx("[\"“”«»「」『』]|말했|말하|라고|说|說|\\b(?:dit|disait|sagt|sagte|сказал[а-я]*|говорит)\\b")
    private val nextDay = rx("내일|明天|\\bdemain\\b|\\bmorgen\\b|\\bзавтра\\b|\\btomorrow\\b")
    private val sameDay = rx("오늘|今天|今晚|\\baujourd['’]hui\\b|\\bce soir\\b|\\bheute\\b|\\bсегодня\\b|\\b(?:today|tonight)\\b")
    private val weekdays = listOf(
        "월요일|星期一|周一|週一|\\blundi\\b|\\bmontag\\b|\\bпонедельник\\b|\\bmonday\\b",
        "화요일|星期二|周二|週二|\\bmardi\\b|\\bdienstag\\b|\\bвторник\\b|\\btuesday\\b",
        "수요일|星期三|周三|週三|\\bmercredi\\b|\\bmittwoch\\b|\\bсреду\\b|\\bwednesday\\b",
        "목요일|星期四|周四|週四|\\bjeudi\\b|\\bdonnerstag\\b|\\bчетверг\\b|\\bthursday\\b",
        "금요일|星期五|周五|週五|\\bvendredi\\b|\\bfreitag\\b|\\bпятницу\\b|\\bfriday\\b",
        "토요일|星期六|周六|週六|\\bsamedi\\b|\\bsamstag\\b|\\bсубботу\\b|\\bsaturday\\b",
        "일요일|星期日|星期天|周日|週日|\\bdimanche\\b|\\bsonntag\\b|\\bвоскресенье\\b|\\bsunday\\b",
    ).map(::rx)
    private val numberWords = mapOf("한" to 1, "두" to 2, "세" to 3, "네" to 4, "다섯" to 5, "여섯" to 6,
        "일곱" to 7, "여덟" to 8, "아홉" to 9, "열" to 10, "열한" to 11, "열두" to 12,
        "un" to 1, "deux" to 2, "trois" to 3, "quatre" to 4, "cinq" to 5, "six" to 6, "sept" to 7,
        "huit" to 8, "neuf" to 9, "dix" to 10, "onze" to 11, "douze" to 12, "vingt" to 20,
        "eins" to 1, "zwei" to 2, "drei" to 3, "vier" to 4, "fünf" to 5, "sechs" to 6, "sieben" to 7,
        "acht" to 8, "neun" to 9, "zehn" to 10, "elf" to 11, "zwölf" to 12, "zwanzig" to 20,
        "один" to 1, "два" to 2, "три" to 3, "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7,
        "восемь" to 8, "девять" to 9, "десять" to 10, "одиннадцать" to 11, "двенадцать" to 12, "двадцать" to 20,
        "treize" to 13, "quatorze" to 14, "quinze" to 15, "seize" to 16, "dix-sept" to 17, "dix-huit" to 18, "dix-neuf" to 19,
        "dix sept" to 17, "dix huit" to 18, "dix neuf" to 19, "vingt et un" to 21, "vingt-deux" to 22, "vingt-trois" to 23,
        "dreizehn" to 13, "vierzehn" to 14, "fünfzehn" to 15, "sechzehn" to 16, "siebzehn" to 17, "achtzehn" to 18, "neunzehn" to 19,
        "einundzwanzig" to 21, "zweiundzwanzig" to 22, "dreiundzwanzig" to 23,
        "тринадцать" to 13, "четырнадцать" to 14, "пятнадцать" to 15, "шестнадцать" to 16, "семнадцать" to 17, "восемнадцать" to 18, "девятнадцать" to 19,
        "двадцать один" to 21, "двадцать два" to 22, "двадцать три" to 23)
    private val numeral = "(?:\\d{1,2}|[零〇一二两兩三四五六七八九十]{1,3}|" + numberWords.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")"
    private val hourPattern = rx("(?:(오전|오후|아침|저녁|밤|새벽|上午|下午|晚上|早上)\\s*)?($numeral)\\s*(시(?!간)|点|點|heures?|h(?![a-z])|uhr|час[а-я]*)(?:\\s*(반|半)|\\s*(\\d{1,2})\\s*(?:분|分)?)?(?:\\s*(du soir|du matin|abends|morgens|вечера|утра))?")
    private val durationMarker = rx("동안|지속|持续|持續|\\b(?:dure|dauert|длится)\\b")
    private val duration = rx("($numeral)\\s*(시간|분|小时|小時|分钟|分鐘|heures?|minutes?|stunden?|minuten?|час[а-я]*|минут[а-я]*)")
    private val colon = rx("(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)")
    private val midnight = rx("자정|午夜|\\bminuit\\b|\\bmitternacht\\b|\\bполноч[а-я]*\\b|\\bmidnight\\b")
    private val noon = rx("정오|中午|\\bmidi\\b|\\bmittag\\b|\\bполдень\\b|\\bnoon\\b")
    private data class Clock(val time: LocalTime, val position: Int)

    private fun number(value: String): Int? {
        value.toIntOrNull()?.let { return it }
        numberWords[value.lowercase()]?.let { return it }
        val digits = mapOf('零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '兩' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        if (value == "十") return 10
        if ('十' in value) {
            val parts = value.split('十')
            if (parts.size != 2) return null
            val tens = if (parts[0].isEmpty()) 1 else digits[parts[0].singleOrNull()] ?: return null
            val units = if (parts[1].isEmpty()) 0 else digits[parts[1].singleOrNull()] ?: return null
            return tens * 10 + units
        }
        return value.singleOrNull()?.let(digits::get)
    }
    private fun clocks(text: String): List<Clock> {
        val values = mutableListOf<Clock>()
        colon.findAll(text).forEach { match ->
            if (!rx("^\\s*[ap]\\.?m").containsMatchIn(text.substring(match.range.last + 1))) {
                var hour = match.groupValues[1].toInt()
                val prefix = text.substring(maxOf(0, match.range.first - 5), match.range.first)
                if (hour in 1..12 && rx("(?:오후|저녁|밤|下午|晚上)\\s*$").containsMatchIn(prefix)) hour = hour % 12 + 12
                else if (hour in 1..12 && rx("(?:오전|아침|새벽|上午|早上)\\s*$").containsMatchIn(prefix)) hour %= 12
                values += Clock(LocalTime.of(hour, match.groupValues[2].toInt()), match.range.first)
            }
        }
        midnight.findAll(text).forEach { values += Clock(LocalTime.MIDNIGHT, it.range.first) }
        noon.findAll(text).forEach { values += Clock(LocalTime.NOON, it.range.first) }
        rx("\\b(1[0-2]|0?[1-9])(?::([0-5]\\d))?\\s*([ap])\\.?m\\.?(?!\\w)").findAll(text).forEach { match ->
            values += Clock(LocalTime.of(match.groupValues[1].toInt() % 12 + if (match.groupValues[3].equals("p", true)) 12 else 0,
                match.groupValues[2].toIntOrNull() ?: 0), match.range.first)
        }
        rx("($numeral)\\s+(вечера|утра)\\b").findAll(text).forEach { match ->
            val hour = number(match.groupValues[1]) ?: return@forEach
            if (hour in 1..12) values += Clock(LocalTime.of(hour % 12 + if (match.groupValues[2].equals("вечера", true)) 12 else 0, 0), match.range.first)
        }
        hourPattern.findAll(text).forEach { match ->
            var hour = number(match.groupValues[2]) ?: return@forEach
            val qualifier = match.groupValues[1] + match.groupValues[6]
            val pm = rx("오후|저녁|밤|下午|晚上|soir|abends|вечера").containsMatchIn(qualifier)
            val am = rx("오전|아침|새벽|上午|早上|matin|morgens|утра").containsMatchIn(qualifier)
            if (pm || am) { if (hour !in 1..12) return@forEach; hour = hour % 12 + if (pm) 12 else 0 }
            else if (hour in 1..12) return@forEach // Do not guess morning versus evening.
            val minute = if (match.groupValues[4].isNotEmpty()) 30 else match.groupValues[5].toIntOrNull() ?: 0
            runCatching { LocalTime.of(hour, minute) }.getOrNull()?.let { values += Clock(it, match.range.first) }
        }
        return values.sortedBy { it.position }
    }
    private fun date(text: String, today: LocalDate): LocalDate? {
        rx("\\b\\d{4}-\\d{2}-\\d{2}\\b").find(text)?.let { return runCatching { LocalDate.parse(it.value) }.getOrNull() }
        val candidates = mutableSetOf<LocalDate>()
        if (nextDay.containsMatchIn(text)) candidates += today.plusDays(1)
        if (sameDay.containsMatchIn(text)) candidates += today
        val nextWeek = rx("다음\\s*주|下周|下週|semaine prochaine|nächste\\w* woche|следующей неделе").containsMatchIn(text)
        weekdays.forEachIndexed { i, day -> if (day.containsMatchIn(text)) candidates +=
            if (nextWeek) today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).with(TemporalAdjusters.nextOrSame(DayOfWeek.of(i + 1)))
            else today.with(TemporalAdjusters.next(DayOfWeek.of(i + 1))) }
        return candidates.singleOrNull()
    }
    private fun hasDate(text: String) = nextDay.containsMatchIn(text) || sameDay.containsMatchIn(text) || weekdays.any { it.containsMatchIn(text) } || rx("\\d{4}-\\d{2}-\\d{2}").containsMatchIn(text)
    internal fun hasExplicitDate(text: String) = hasDate(text) || ArabicActivities.hasDate(text)
    internal fun timingFragment(text: String): Boolean {
        if (ArabicActivities.isArabic(text)) return ArabicActivities.timingFragment(text)
        var rest = text
        val patterns = listOf(nextDay, sameDay, hourPattern, colon, midnight, noon, rx("\\d{4}-\\d{2}-\\d{2}")) + weekdays
        val found = patterns.any { it.containsMatchIn(text) }
        patterns.forEach { rest = it.replace(rest, " ") }
        return found && rest.replace(rx("\\b(?:at|on|à|au|um|в|на)\\b|[\\s,.!。]"), "").isEmpty()
    }
    /** Every character must belong to a supported date, clock, or harmless connector. */
    fun changeStart(text: String, previous: Instant, capturedAt: Instant, zone: ZoneId): Instant? = runCatching {
        if (ArabicActivities.isArabic(text)) return ArabicActivities.changeStart(text, previous, capturedAt, zone)
        val times = clocks(text)
        require(times.size <= 1)
        val day = date(text, capturedAt.atZone(zone).toLocalDate())
        require(!hasDate(text) || day != null)
        val covered = BooleanArray(text.length)
        val datePatterns = listOf(nextDay, sameDay, rx("\\d{4}-\\d{2}-\\d{2}")) + weekdays
        val clockPatterns = listOf(hourPattern, colon, midnight, noon,
            rx("\\b(1[0-2]|0?[1-9])(?::([0-5]\\d))?\\s*([ap])\\.?m\\.?(?!\\w)"), rx("($numeral)\\s+(вечера|утра)\\b"))
        fun mark(pattern: Regex) { pattern.findAll(text).forEach { match -> match.range.forEach { covered[it] = true } } }
        datePatterns.forEach(::mark); clockPatterns.forEach(::mark)
        mark(rx("\\b(?:at|on|à|au|um|в|на)\\b|[\\s,.!。]"))
        require(covered.all { it })
        require(times.isNotEmpty() || (day != null && clockPatterns.none { it.containsMatchIn(text) }))
        at(day ?: previous.atZone(zone).toLocalDate(), times.singleOrNull()?.time ?: previous.atZone(zone).toLocalTime(), zone)
            .takeIf { it.isAfter(capturedAt) }
    }.getOrNull()
    private fun at(day: LocalDate, time: LocalTime, zone: ZoneId) = TimeResolver.parseEditor("$day ${time.toString().take(5)}", zone)
    fun hasEvent(text: String) = ArabicActivities.kind(text) != null || (foreign.containsMatchIn(text) && events.any { it.pattern.containsMatchIn(text) })
    fun bareEventKey(text: String): String? = ArabicActivities.bareKind(text) ?: events.firstOrNull { it.pattern.matches(text) }?.key
    fun eventKey(text: String): String? {
        ArabicActivities.kind(text)?.let { return it }
        val found = events.mapNotNull { word -> word.pattern.find(text)?.let { it.range.first to word.key } }.toMutableList()
        rx("\\b(" + events.joinToString("|") { it.key } + ")\\b").findAll(text).forEach { found += it.range.first to it.value.lowercase() }
        return found.minByOrNull { it.first }?.second
    }
    fun eventName(text: String): String? = ArabicActivities.name(text) ?: if (!hasEvent(text)) null else events.mapNotNull { it.pattern.find(text) }.minByOrNull { it.range.first }?.value
    fun referenceOpening(title: String, start: Instant, zone: ZoneId): String? = eventName(title)?.let {
        val local = start.atZone(zone)
        "$it ${local.toLocalDate()} ${local.toLocalTime().toString().take(5)}."
    }
    fun isContinuation(text: String) = ArabicActivities.isContinuation(text) || endMarker.containsMatchIn(text) || durationMarker.containsMatchIn(text) || place(text).let { it.name != null || it.address != null }

    fun resolveEnd(text: String, start: Instant, source: Transcript): Instant? {
        if (ArabicActivities.isArabic(text)) return ArabicActivities.resolveEnd(text, start, source)
        if (negative.containsMatchIn(text)) return null
        if (durationMarker.containsMatchIn(text)) {
            val length = duration.find(text) ?: return null
            val value = number(length.groupValues[1]) ?: return null
            val minutes = rx("^(?:분|分钟|分鐘|minute|minuten|минут)").containsMatchIn(length.groupValues[2])
            return runCatching {
                val end = start.plusSeconds(value * if (minutes) 60L else 3600L)
                CalendarOverlap.validate(start.toEpochMilli(), end.toEpochMilli()); end
            }.getOrNull()
        }
        if (!endMarker.containsMatchIn(text)) return null
        val time = clocks(text).lastOrNull()?.time ?: return null
        return runCatching {
            val startDay = start.atZone(source.zoneId).toLocalDate()
            val explicit = date(text, source.capturedAt.atZone(source.zoneId).toLocalDate())
            if (hasDate(text) && explicit == null) return null
            var day = explicit ?: startDay
            var end = at(day, time, source.zoneId)
            if (!end.isAfter(start) && (explicit == null || explicit == startDay)) { day = day.plusDays(1); end = at(day, time, source.zoneId) }
            CalendarOverlap.validate(start.toEpochMilli(), end.toEpochMilli()); end
        }.getOrNull()
    }
    private fun place(text: String): PlaceHint {
        val explicit = listOf(
            rx("장소(?:는|가)\\s*(.+?)(?:입니다|이에요|예요|이다|[.!?。]|$)"),
            rx("(?:\\d{1,2}|여덟|일곱|아홉|열|한|두|세|네)\\s*시(?:에)?\\s+(.+?)에서"),
            rx("(?:地点|地點|场地|場地)(?:是|在)?\\s*(.+?)(?:[.!?。]|$)"),
            rx("(?:le lieu est|lieu est|der ort ist|место(?: встречи)?[: ]+)\\s*(.+?)(?:[.!?。]|$)"),
            rx("在\\s*(.+?)(?:举办|舉辦|举行|舉行|有|[.!?。]|$)"),
            rx("(?=(?:^|\\s)(?:à|au|chez|bei|im|в)\\s+([^\\d].+?)(?:[.!?。]|$))"),
        ).flatMap { it.findAll(text).map { found -> found.groupValues[1].trim() }.toList() }
            .firstOrNull { clocks(it).isEmpty() }
        val address = listOf(rx("주소(?:는|가)\\s*(.+?)(?:입니다|이에요|예요|[.!?。]|$)"),
            rx("地址(?:是|为|為)?\\s*(.+?)(?:[.!?。]|$)"),
            rx("(?:l['’]adresse est|die adresse ist|адрес[: ]+)\\s*(.+?)(?:[.!?。]|$)"))
            .firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1)?.trim() }
        val english = PlaceHints.extract(text)
        return PlaceHint(explicit?.takeIf { it.length in 2..120 } ?: english.name, address ?: english.address)
    }

    fun extract(source: Transcript, informationalOnly: Boolean): List<ProposedCommitment> {
        if (!source.processingAllowed || (source.speaker != Speaker.USER && !(informationalOnly && source.speaker == Speaker.OTHER))) return emptyList()
        val sentences = Sentences.split(source.text)
        return ArabicActivities.extract(source, informationalOnly) + sentences.mapIndexedNotNull { index, sentence ->
            if (!foreign.containsMatchIn(sentence) || negative.containsMatchIn(sentence) || sentence.endsWith('?') || sentence.endsWith('？')) return@mapIndexedNotNull null
            val event = events.firstOrNull { it.pattern.containsMatchIn(sentence) } ?: return@mapIndexedNotNull null
            val times = clocks(sentence)
            if (times.isEmpty() || (times.size == 1 && endMarker.containsMatchIn(sentence))) return@mapIndexedNotNull null
            // Never skip an earlier unresolved clock to borrow a later statement's clear time.
            if (hourPattern.find(sentence)?.range?.first?.let { it < times.first().position } == true) return@mapIndexedNotNull null
            val localDay = source.capturedAt.atZone(source.zoneId).toLocalDate()
            val explicit = date(sentence, localDay)
            if (hasDate(sentence) && explicit == null) return@mapIndexedNotNull null
            val start = runCatching {
                var value = at(explicit ?: localDay, times.first().time, source.zoneId)
                if (explicit == null && !value.isAfter(source.capturedAt)) value = at(localDay.plusDays(1), times.first().time, source.zoneId)
                value.takeIf { it.isAfter(source.capturedAt) }
            }.getOrNull() ?: return@mapIndexedNotNull null
            var end: Instant? = null
            var venue = place(sentence)
            val evidence = mutableListOf(sentence)
            for (following in sentences.drop(index).take(24)) {
                if (following != sentence) {
                    val other = events.filter { it.pattern.containsMatchIn(following) }
                    if (other.any { it.key != event.key } || (other.isNotEmpty() && clocks(following).isNotEmpty() && !endMarker.containsMatchIn(following))) break
                    if (negative.containsMatchIn(following) || following.endsWith('?') || following.endsWith('？') || !isContinuation(following)) continue
                }
                val foundEnd = resolveEnd(following, start, source)
                val foundPlace = place(following)
                if (foundEnd != null) end = foundEnd
                if (foundPlace.name != null || foundPlace.address != null) {
                    val changed = foundPlace.name != null && venue.name != null && !foundPlace.name.equals(venue.name, true)
                    venue = PlaceHint(foundPlace.name ?: venue.name, foundPlace.address ?: if (changed) null else venue.address)
                }
                if (foundEnd != null || foundPlace.name != null || foundPlace.address != null) evidence += following
            }
            val attending = !informationalOnly && source.speaker == Speaker.USER && personal.containsMatchIn(sentence) && !reported.containsMatchIn(sentence)
            ProposedCommitment(sentence.trimEnd('.', '!', '。').take(500), evidence.distinct().joinToString("\n"), source.capturedAt, source.zoneId,
                (if (attending) "Personal plan detected; verify ownership. " else "Attendance is unconfirmed. ") +
                    (if (explicit == null) "Date inferred from the next occurrence; check it. " else "") +
                    "Basic multilingual rule; check activity times and place. " + if (end == null) "End time is unknown." else "End may be approximate.",
                start, end, informational = !attending, placeName = venue.name, placeAddress = venue.address)
        }
    }
}
