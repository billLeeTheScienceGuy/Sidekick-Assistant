package com.sidekick.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Questions to SideKick about saved plans, answered from those plans, in the app's seven languages:
 * "SideKick, what's my plan today?", "사이드킥, 오늘 일정 뭐야?", "SideKick, qu'est-ce que j'ai aujourd'hui ?",
 * "SideKick, was habe ich heute vor?", "Сайдкик, что у меня сегодня?", "SideKick，我今天有什么安排？", "سايدكيك، ما خطتي اليوم؟".
 * A question only counts when it starts with SideKick's name, so the same words said to another person are ignored.
 * [allowed] limits which languages are understood (a manual language setting understands only that language).
 */
object PlanQuestion {
    data class Plan(val title: String, val startMs: Long?, val informational: Boolean = false)

    sealed interface Question { val language: String; val korean: Boolean get() = language == "ko" }
    data class Day(val offsetDays: Int, val days: Int, override val language: String) : Question
    data class Next(override val language: String) : Question
    data class Find(val words: List<String>, override val language: String) : Question

    private class Lang(
        val code: String, val wake: Regex,
        val today: Regex, val tomorrow: Regex, val week: Regex, val about: Regex, val next: Regex,
        val find: Regex, val filler: Set<String>,
    )

    private const val LATIN_WAKE = "(?:hey|hi|ok|okay|dis|salut|hallo|he)?[\\s,]*side\\s?-?kick\\b"
    private val LANGS = listOf(
        Lang("ko", Regex("^\\s*(?:저기\\s*)?사이드\\s?킥(?:아|야)?[\\s,.!?]*(.*)$"),
            Regex("오늘"), Regex("내일"), Regex("이번\\s?주"), Regex("일정|약속|계획|뭐|있어|있나"), Regex("다음\\s?(일정|약속)"),
            Regex("언제|몇\\s?시"), setOf()),
        Lang("zh", Regex("^\\s*(?:嘿|喂)?\\s*(?:side\\s?kick|赛德克|塞德克|赛德可|赛得克)[\\s,，.!?。！？]*(.*)$", RegexOption.IGNORE_CASE),
            Regex("今天|今晚"), Regex("明天"), Regex("这周|本周|这个星期|这礼拜"), Regex("安排|计划|日程|什么事|干什么|有什么"), Regex("下一个|接下来"),
            Regex("什么时候|几点|哪天"), setOf("我的", "我", "的", "是", "什么时候", "几点", "哪天", "预约", "安排", "在", "吗", "呢", "那个", "这个")),
        Lang("ar", Regex("^\\s*(?:يا\\s*)?(?:سايد\\s?كيك|سايدكك|side\\s?kick)[\\s,،.!?؟]*(.*)$", RegexOption.IGNORE_CASE),
            Regex("اليوم|الليلة"), Regex("غدا|غدًا|بكرة"), Regex("هذا الأسبوع"), Regex("خط|برنامج|جدول|لدي|عندي|مواعيد"), Regex("التالي|القادم"),
            Regex("متى|في أي ساعة|الساعة كم"), setOf("متى", "موعد", "موعدي", "في", "أي", "ساعة", "الساعة", "كم", "هو", "هي", "ال", "مع")),
        Lang("ru", Regex("^\\s*(?:эй\\s*)?(?:сайд\\s?-?кик|сайдкик|side\\s?kick)[а-я]*[\\s,.!?]*(.*)$", setOf(RegexOption.IGNORE_CASE)),
            Regex("сегодня"), Regex("завтра"), Regex("на этой неделе|эта неделя|этой неделе"), Regex("план|дела|что у меня|расписан|запланирован"), Regex("что дальше|следующ"),
            Regex("когда|во сколько|в котором часу"), setOf("когда", "во", "сколько", "в", "котором", "часу", "у", "меня", "мой", "моя", "моё", "мои", "приём", "прием", "запись", "встреча", "будет", "к")),
        Lang("fr", Regex("^\\s*$LATIN_WAKE[\\s,.!?]*(.*)$", RegexOption.IGNORE_CASE),
            Regex("aujourd'?hui|ce soir"), Regex("demain"), Regex("cette semaine"), Regex("programme|prévu|j'ai|planning|agenda"), Regex("la suite|prochain"),
            Regex("^(quand|c'est quand|à quelle heure)"), setOf("quand", "c'est", "est", "mon", "ma", "mes", "le", "la", "les", "rendez-vous", "rdv", "à", "quelle", "heure", "chez", "avec", "du", "de", "des", "l", "d")),
        Lang("de", Regex("^\\s*$LATIN_WAKE[\\s,.!?]*(.*)$", RegexOption.IGNORE_CASE),
            Regex("heute"), Regex("morgen"), Regex("diese woche"), Regex("vor|an|geplant|termine|plan|habe ich"), Regex("als nächstes|nächste"),
            Regex("^(wann|um wie viel uhr)"), setOf("wann", "ist", "mein", "meine", "meinen", "der", "die", "das", "termin", "um", "wie", "viel", "uhr", "beim", "mit", "zum", "zur")),
        Lang("en", Regex("^\\s*$LATIN_WAKE[\\s,.!?]*(.*)$", RegexOption.IGNORE_CASE),
            Regex("\\b(today|tonight)\\b"), Regex("\\btomorrow\\b"), Regex("\\bthis week\\b"), Regex("\\b(plans?|schedule|have|doing|on|agenda|going on)\\b|^what"),
            Regex("\\bwhat'?s next\\b|\\bnext (plan|thing|appointment)\\b"), Regex("^(when|what time)\\b|^when's\\b"),
            setOf("my", "the", "a", "an", "is", "are", "when", "what", "time", "whens", "when's", "what's", "appointment", "appointments",
                "plan", "plans", "with", "do", "i", "have", "at", "s", "next", "for", "about", "tell", "me", "and")),
    )
    private val FILLER_KO = Regex("^(언제.*|몇|시.*|약속.*|일정.*|예약.*|내|제|나|저)$")
    private val PARTICLE_KO = Regex("(은|는|이|가|을|를|의|이랑|랑)$")

    /** Latin-script languages share the wake word; the words of the question pick the language. */
    private fun latinLanguage(rest: String, allowed: Set<String>): String? {
        val candidates = listOf("fr", "de", "en").filter { it in allowed }
        val score = candidates.associateWith { code ->
            val l = LANGS.first { it.code == code }
            listOf(l.today, l.tomorrow, l.week, l.next, l.find).count { it.containsMatchIn(rest) } +
                rest.split(Regex("\\s+")).count { it in l.filler }
        }
        return score.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: candidates.firstOrNull { it == "en" } ?: candidates.firstOrNull()
    }

    /**
     * Questions to read off the screen, per language ([code]): today, tomorrow, this week, what's next, when is a plan.
     * Each one is checked by PlanQuestionTest to be understood. Unknown codes get English.
     */
    fun examples(code: String): List<String> = EXAMPLES[code] ?: EXAMPLES.getValue("en")
    private val EXAMPLES = mapOf(
        "en" to listOf("SideKick, what's my plan today?", "SideKick, what do I have tomorrow?", "SideKick, what's on this week?",
            "SideKick, what's next?", "SideKick, when is my dentist appointment?"),
        "ko" to listOf("사이드킥, 오늘 일정 뭐야?", "사이드킥, 내일 약속 있어?", "사이드킥, 이번 주 일정 알려줘", "사이드킥, 다음 일정 뭐야?", "사이드킥, 치과 언제야?"),
        "zh" to listOf("SideKick，我今天有什么安排？", "SideKick，明天有什么安排？", "SideKick，这周有什么安排？", "SideKick，接下来是什么？", "SideKick，我的牙医预约是什么时候？"),
        "ar" to listOf("سايدكيك، ما خطتي اليوم؟", "سايدكيك، ماذا لدي غدًا؟", "سايدكيك، ما برنامجي هذا الأسبوع؟", "سايدكيك، ما الموعد التالي؟", "سايدكيك، متى موعد طبيب الأسنان؟"),
        "ru" to listOf("Сайдкик, что у меня сегодня?", "Сайдкик, что у меня завтра?", "Сайдкик, какие планы на этой неделе?", "Сайдкик, что дальше?", "Сайдкик, когда у меня стоматолог?"),
        "fr" to listOf("SideKick, qu'est-ce que j'ai aujourd'hui ?", "SideKick, qu'est-ce que j'ai demain ?", "SideKick, quel est mon programme cette semaine ?",
            "SideKick, c'est quoi la suite ?", "SideKick, quand est mon rendez-vous chez le dentiste ?"),
        "de" to listOf("SideKick, was habe ich heute vor?", "SideKick, was habe ich morgen vor?", "SideKick, welche Termine habe ich diese Woche?",
            "SideKick, was ist als Nächstes?", "SideKick, wann ist mein Zahnarzttermin?"),
    )

    fun parse(text: String, allowed: Set<String> = setOf("en", "ko", "zh", "ar", "ru", "fr", "de")): Question? {
        // The name may be written in any script ("SideKick，我…"); the words after it decide the language.
        val rest = LANGS.firstNotNullOfOrNull { l -> l.wake.find(text)?.groupValues?.get(1)?.trim() } ?: return null
        if (rest.isEmpty()) return null
        val code = when {
            rest.any { it in '가'..'힣' } -> "ko"
            rest.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN } -> "zh"
            rest.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC } -> "ar"
            rest.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.CYRILLIC } -> "ru"
            else -> null
        }
        if (code != null) return if (code in allowed) question(LANGS.first { it.code == code }, rest) else null
        val latin = rest.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}0-9' -]"), " ").replace(Regex("\\s+"), " ").trim()
        val chosen = latinLanguage(latin, allowed) ?: return null
        return question(LANGS.first { it.code == chosen }, latin)
    }

    private fun question(l: Lang, rest: String): Question? {
        val code = l.code
        when {
            l.next.containsMatchIn(rest) -> return Next(code)
            l.about.containsMatchIn(rest) && l.today.containsMatchIn(rest) -> return Day(0, 1, code)
            l.about.containsMatchIn(rest) && l.tomorrow.containsMatchIn(rest) -> return Day(1, 1, code)
            l.about.containsMatchIn(rest) && l.week.containsMatchIn(rest) -> return Day(0, 7, code)
        }
        if (!l.find.containsMatchIn(rest)) return null
        val subject = when (code) {
            "ko" -> rest.replace(Regex("[?.!,]"), " ").split(Regex("\\s+")).filter { !FILLER_KO.matches(it) }
                .map { if (it.length > 2) PARTICLE_KO.replace(it, "") else it }.filter { it.length >= 2 }
            "zh" -> l.filler.sortedByDescending { it.length }.fold(rest.replace(Regex("[？?。，,！!\\s]"), " ")) { t, f -> t.replace(f, " ") }
                .split(Regex("\\s+")).filter { it.length >= 2 }
            else -> rest.replace(Regex("[?.!,،؟]"), " ").split(Regex("[\\s']+")).map { it.lowercase(Locale.ROOT).removeSuffix("'s") }
                .map { if (code == "ar") it.removePrefix("ال").let { s -> if (s.length >= 2) s else it } else it }
                .filter { it !in l.filler && it.length > 1 }
        }
        return if (subject.isEmpty()) null else Find(subject, code)
    }

    // ---- Answers ----
    private class Words(val today: String, val tomorrow: String, val week: String, val noneToday: String, val noneTomorrow: String,
        val noneWeek: String, val noNext: String, val notFound: String, val past: String, val at: String)
    private val WORDS = mapOf(
        "fr" to Words("Aujourd'hui", "Demain", "Cette semaine", "Rien d'enregistré pour le reste de la journée.", "Rien d'enregistré pour demain.",
            "Rien d'enregistré cette semaine.", "Aucun plan à venir enregistré.", "Je ne trouve pas cela dans vos plans.", " (passé)", "à"),
        "de" to Words("Heute", "Morgen", "Diese Woche", "Für den Rest des Tages ist nichts gespeichert.", "Für morgen ist nichts gespeichert.",
            "Diese Woche ist nichts gespeichert.", "Keine kommenden Pläne gespeichert.", "Das habe ich in Ihren Plänen nicht gefunden.", " (vorbei)", "um"),
        "ru" to Words("Сегодня", "Завтра", "На этой неделе", "На сегодня больше ничего не сохранено.", "На завтра ничего не сохранено.",
            "На этой неделе ничего не сохранено.", "Нет сохранённых планов на будущее.", "Не нашёл такого в ваших планах.", " (уже прошло)", "в"),
        "zh" to Words("今天", "明天", "这周", "今天接下来没有保存的安排。", "明天没有保存的安排。", "这周没有保存的安排。", "没有即将到来的安排。",
            "在你的安排里没有找到。", "（已过）", ""),
        "ar" to Words("اليوم", "غدًا", "هذا الأسبوع", "لا توجد خطط محفوظة لبقية اليوم.", "لا توجد خطط محفوظة لغد.", "لا توجد خطط محفوظة هذا الأسبوع.",
            "لا توجد خطط قادمة محفوظة.", "لم أجد ذلك في خططك.", " (انتهى)", "الساعة"),
    )
    private val LOCALES = mapOf("fr" to Locale.FRENCH, "de" to Locale.GERMAN, "ru" to Locale("ru"), "zh" to Locale.CHINESE, "ar" to Locale("ar"))

    fun answer(question: Question, plans: List<Plan>, nowMs: Long, zone: ZoneId): String {
        val own = plans.filter { !it.informational && it.startMs != null }.sortedBy { it.startMs }
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val lang = question.language
        return when (question) {
            is Day -> {
                val from = today.plusDays(question.offsetDays.toLong())
                val to = from.plusDays(question.days.toLong())
                val found = own.filter { date(it, zone) >= from && date(it, zone) < to && (question.offsetDays > 0 || it.startMs!! >= nowMs - 3_600_000) }.take(5)
                val week = question.days == 7
                when (lang) {
                    "en" -> if (found.isEmpty()) "You have nothing saved ${if (week) "this week" else if (question.offsetDays == 1) "tomorrow" else "for the rest of today"}."
                        else "${if (week) "This week you have" else if (question.offsetDays == 1) "Tomorrow you have" else "Today you have"} " +
                            list(found.map { "${it.title} ${when_(it, today, zone, lang, week)}" }, " and ") + "."
                    "ko" -> {
                        val label = if (week) "이번 주에는" else if (question.offsetDays == 1) "내일은" else "오늘은"
                        if (found.isEmpty()) "$label 저장된 일정이 없어요." else "$label " + found.joinToString(", ") { "${when_(it, today, zone, lang, week)} ${it.title}" } + " 일정이 있어요."
                    }
                    else -> {
                        val w = WORDS.getValue(lang)
                        if (found.isEmpty()) if (week) w.noneWeek else if (question.offsetDays == 1) w.noneTomorrow else w.noneToday
                        else (if (week) w.week else if (question.offsetDays == 1) w.tomorrow else w.today) + (if (lang == "zh") "：" else ": ") +
                            found.joinToString(if (lang == "zh") "，" else if (lang == "ar") "، " else ", ") { "${it.title} ${when_(it, today, zone, lang, week)}".trim() } +
                            if (lang == "zh") "。" else "."
                    }
                }
            }
            is Next -> {
                val pick = own.firstOrNull { it.startMs!! >= nowMs }
                when {
                    lang == "en" -> pick?.let { "Next is ${it.title} ${when_(it, today, zone, lang, true)}." } ?: "You have no upcoming plans saved."
                    lang == "ko" -> pick?.let { "다음 일정은 ${when_(it, today, zone, lang, true)} ${it.title}이에요." } ?: "앞으로 저장된 일정이 없어요."
                    pick == null -> WORDS.getValue(lang).noNext
                    else -> "${it(lang)}${pick.title}${if (lang == "zh") "：" else ": "}${when_(pick, today, zone, lang, true)}${if (lang == "zh") "。" else "."}"
                }
            }
            is Find -> {
                val matches = own.filter { p -> question.words.any { w -> p.title.contains(w, ignoreCase = true) } }
                val pick = matches.firstOrNull { it.startMs!! >= nowMs } ?: matches.lastOrNull()
                val past = pick != null && pick.startMs!! < nowMs
                when {
                    pick == null -> when (lang) { "en" -> "I couldn't find that in your saved plans."; "ko" -> "그런 일정은 저장되어 있지 않아요."; else -> WORDS.getValue(lang).notFound }
                    lang == "en" -> "${pick.title} is ${when_(pick, today, zone, lang, true)}${if (past) " (already past)" else ""}."
                    lang == "ko" -> "${pick.title}: ${when_(pick, today, zone, lang, true)}${if (past) " (지났어요)" else ""}."
                    else -> "${pick.title}${if (lang == "zh") "：" else ": "}${when_(pick, today, zone, lang, true)}${if (past) WORDS.getValue(lang).past else ""}${if (lang == "zh") "。" else "."}"
                }
            }
        }
    }

    private fun it(lang: String) = when (lang) { "fr" -> "Ensuite : "; "de" -> "Als Nächstes: "; "ru" -> "Дальше: "; "zh" -> "下一个："; "ar" -> "التالي: "; else -> "" }

    private fun date(plan: Plan, zone: ZoneId): LocalDate = Instant.ofEpochMilli(plan.startMs!!).atZone(zone).toLocalDate()

    private fun when_(plan: Plan, today: LocalDate, zone: ZoneId, lang: String, withDay: Boolean): String {
        val at = Instant.ofEpochMilli(plan.startMs!!).atZone(zone)
        val allDay = at.hour == 0 && at.minute == 0
        when (lang) {
            "ko" -> {
                val day = when (at.toLocalDate()) { today -> "오늘"; today.plusDays(1) -> "내일"
                    else -> "${at.monthValue}월 ${at.dayOfMonth}일 ${at.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)}요일" }
                val time = if (allDay) "" else "${if (at.hour < 12) "오전" else "오후"} ${(at.hour + 11) % 12 + 1}시${if (at.minute != 0) " ${at.minute}분" else ""}"
                return listOf(if (withDay) day else "", time).filter { it.isNotBlank() }.joinToString(" ")
            }
            "en" -> {
                val day = when (at.toLocalDate()) { today -> "today"; today.plusDays(1) -> "tomorrow"
                    else -> "on " + at.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)) }
                val time = if (allDay) "" else "at " + at.format(DateTimeFormatter.ofPattern(if (at.minute == 0) "h a" else "h:mm a", Locale.US))
                return listOf(if (withDay) day else "", time).filter { it.isNotBlank() }.joinToString(" ")
            }
            else -> {
                val w = WORDS.getValue(lang); val locale = LOCALES.getValue(lang)
                val day = when (at.toLocalDate()) { today -> w.today.lowercase(locale); today.plusDays(1) -> w.tomorrow.lowercase(locale)
                    else -> at.format(DateTimeFormatter.ofPattern(if (lang == "zh") "M月d日 EEEE" else "EEEE d MMMM", locale)) }
                val time = if (allDay) "" else (if (w.at.isEmpty()) "" else w.at + " ") + at.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))
                return listOf(if (withDay) day else "", time).filter { it.isNotBlank() }.joinToString(" ")
            }
        }
    }

    private fun list(items: List<String>, and: String) = if (items.size <= 1) items.joinToString("") else items.dropLast(1).joinToString(", ") + and + items.last()
}
