package com.sidekick.core

/**
 * Which speech files each language needs, and what is ready given the files on the phone. Several languages share
 * files (Whisper serves every language in Faster mode; Omni serves Arabic, Auto-detect and Recommended
 * French/German/Russian; SenseVoice serves Recommended Korean and Chinese), so installing one file can make several
 * languages ready, and removing one can stop several. Everything here is pure so it can be shown before acting.
 */
object LanguageReadiness {
    /** Base files a language needs in a mode (the app's routing). */
    fun required(language: SpeechLanguage, quality: SpeechQuality): Set<String> = when {
        quality.usesSenseVoice(language) -> setOf("sensevoice")
        quality == SpeechQuality.ADAPTIVE && language in setOf(SpeechLanguage.ARABIC, SpeechLanguage.AUTO) -> setOf("omni")
        quality == SpeechQuality.ADAPTIVE && language in setOf(SpeechLanguage.FRENCH, SpeechLanguage.GERMAN, SpeechLanguage.RUSSIAN) -> setOf("whisper", "omni")
        language == SpeechLanguage.ENGLISH && quality == SpeechQuality.FAST -> emptySet()
        else -> setOf("whisper")
    }

    /** Optional files that make a language more accurate (never needed to work). */
    fun optional(language: SpeechLanguage): Set<String> = when (language) {
        SpeechLanguage.ENGLISH -> setOf("english")
        SpeechLanguage.RUSSIAN -> setOf("russian")
        SpeechLanguage.KOREAN -> setOf("korean")
        SpeechLanguage.AUTO -> setOf("english", "russian", "sensevoice")
        else -> emptySet()
    }

    /**
     * [installed]: packs whose files are on the phone; [inUse]: of those, the ones switched on.
     * [download]: packs to download (not on the phone); [switchOn]: on the phone but switched off (no download).
     */
    data class Status(val language: SpeechLanguage, val quality: SpeechQuality, val download: Set<String>, val switchOn: Set<String>,
        val downloadBytes: Long, val readyInFaster: Boolean) {
        val ready: Boolean get() = download.isEmpty() && switchOn.isEmpty()
    }

    fun status(language: SpeechLanguage, quality: SpeechQuality, installed: Set<String>, inUse: Set<String>, sizes: Map<String, Long>): Status {
        val need = required(language, quality)
        val download = need - installed
        val switchOn = need.intersect(installed) - inUse
        val faster = required(language, SpeechQuality.FAST).all { it in installed && it in inUse }
        return Status(language, quality, download, switchOn, download.sumOf { sizes[it] ?: 0L }, faster)
    }

    /** Languages that become ready in [quality] once [pack] is on and in use (and were not ready before). */
    fun unlocks(pack: String, quality: SpeechQuality, installed: Set<String>, inUse: Set<String>): List<SpeechLanguage> =
        SpeechLanguage.entries.filter { language ->
            !status(language, quality, installed, inUse, emptyMap()).ready &&
                status(language, quality, installed + pack, inUse + pack, emptyMap()).ready
        }

    /** What removing or switching off [pack] does: languages that stop working, and ones that still work in Faster mode. */
    data class Impact(val stops: List<SpeechLanguage>, val fasterOnly: List<SpeechLanguage>)

    fun impact(pack: String, quality: SpeechQuality, installed: Set<String>, inUse: Set<String>): Impact {
        val before = SpeechLanguage.entries.filter { status(it, quality, installed, inUse, emptyMap()).ready }
        val after = SpeechLanguage.entries.associateWith { status(it, quality, installed - pack, inUse - pack, emptyMap()) }
        val lost = before.filter { !after.getValue(it).ready }
        return Impact(lost.filter { !after.getValue(it).readyInFaster }, lost.filter { after.getValue(it).readyInFaster })
    }
}
