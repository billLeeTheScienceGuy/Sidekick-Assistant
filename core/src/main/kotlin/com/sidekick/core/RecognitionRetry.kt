package com.sidekick.core

import java.time.LocalTime
import java.util.Locale

/** Compare independent readings; never choose a time because it fits a saved plan. */
object RecognitionRetry {
    data class Selection(val text: String, val accepted: Boolean, val uncertain: Boolean = false, val reason: String? = null)
    private val negative = Regex("(?:\\b(?:not|never|no|without|don['’]?t|won['’]?t|cannot|can['’]?t|pas|jamais|sans|non|nicht|nie|ohne|kein\\p{L}*|не|нет|никогда|нельзя|без|لا|لن|لم|ليس)\\b|하지\\s*마|가지\\s*마|(?<![가-힣])안\\s*(?:가|갈|해|할|참석|취소)|않|없|不|没|沒|别|別|勿)", RegexOption.IGNORE_CASE)
    private fun clocks(text: String): Set<LocalTime> {
        val lower = EnglishClocks.normalize(text.lowercase(Locale.ROOT))
        val english = Regex("(?<!\\d)(\\d{1,2})(?::(\\d{2}))?\\s*([ap])\\.?m\\.?(?!\\p{L})").findAll(lower).mapNotNull { m ->
            val hour=m.groupValues[1].toInt(); val minute=m.groupValues[2].ifEmpty { "0" }.toInt()
            if(hour !in 1..12 || minute !in 0..59) null else LocalTime.of(hour%12+if(m.groupValues[3]=="p")12 else 0,minute)
        }.toList()
        return (english + LocalizedActivities.recognitionClocks(text) + ArabicActivities.recognitionClocks(text)).toSet()
    }
    private fun digits(text: String) = Regex("\\p{Nd}+").findAll(text).map { m -> m.value.map { Character.digit(it,10) }.joinToString("") }.toList()
    private val tomorrow = Regex("tomorrow|내일|明天|明晚|\\bdemain\\b|\\bmorgen\\b|завтра|غدا",RegexOption.IGNORE_CASE)
    private val today = Regex("\\b(?:today|tonight|heute|aujourd['’]hui)\\b|오늘|今天|今晚|ce soir|сегодня|اليوم",RegexOption.IGNORE_CASE)
    private fun relativeDay(text:String):Set<Int> = buildSet { if(tomorrow.containsMatchIn(ArabicActivities.normalized(text)))add(1);if(today.containsMatchIn(text))add(0) }
    private fun outsideClockDigits(text:String):List<String> {
        val english=Regex("(?<!\\d)(\\d{1,2})(?::(\\d{2}))?\\s*([ap])\\.?m\\.?(?!\\p{L})")
        val clean=english.replace(EnglishClocks.normalize(text.lowercase(Locale.ROOT))," ")
        return digits(ArabicActivities.recognitionNumberContext(LocalizedActivities.recognitionNumberContext(clean)))
    }
    fun select(primary: String, alternative: String?): Selection {
        if (alternative.isNullOrBlank()) return Selection(primary,false,reason="Second reading was empty")
        if (negative.containsMatchIn(primary) != negative.containsMatchIn(alternative))
            return Selection(primary,false,true,"Readings disagree about negation; automatic changes skipped")
        val oldDay=relativeDay(primary);val newDay=relativeDay(alternative)
        if(oldDay.isNotEmpty() && newDay.isNotEmpty() && oldDay!=newDay)
            return Selection(primary,false,true,"Readings disagree about the day; automatic changes skipped")
        val repaired = KoreanClockRepair.select(primary,alternative)
        if (repaired != primary) return Selection(repaired,true,reason="Invalid Korean clock independently corrected")
        val before=clocks(primary); val after=clocks(alternative)
        // A reading that loses an explicit time is never an improvement for planning.
        if (before.isNotEmpty() && after.isEmpty())
            return Selection(primary,false,reason="Second reading lost the time")
        if (before.isNotEmpty() && after.isNotEmpty() && before != after)
            return Selection(primary,false,true,"Readings disagree about times; automatic changes skipped")
        val oldDigits=digits(primary); val newDigits=digits(alternative)
        if (oldDigits.isNotEmpty() && newDigits.isNotEmpty() && oldDigits != newDigits &&
            !(before.isNotEmpty() && before==after && outsideClockDigits(primary)==outsideClockDigits(alternative)))
            return Selection(primary,false,true,"Readings disagree about numbers; automatic changes skipped")
        val oldLetters=primary.count { it.isLetter() }; val newLetters=alternative.count { it.isLetter() }
        if (oldLetters >= 12 && newLetters*2 < oldLetters)
            return Selection(primary,false,reason="Second reading dropped too much speech")
        return Selection(alternative,true)
    }
}
