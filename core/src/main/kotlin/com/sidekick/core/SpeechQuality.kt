package com.sidekick.core

enum class SpeechQuality(val label: String) {
    ADAPTIVE("Adaptive · language-specific recognition"), FAST("Fast · Whisper tiny"), ACCURATE("Larger model · Whisper base");
    fun usesSenseVoice(language: SpeechLanguage): Boolean = this == ADAPTIVE && language in setOf(SpeechLanguage.KOREAN, SpeechLanguage.CHINESE)
    fun usesOmni(language: SpeechLanguage, shortPhrase: Boolean): Boolean = this == ADAPTIVE &&
        (language == SpeechLanguage.ARABIC || (!shortPhrase && language in setOf(SpeechLanguage.FRENCH, SpeechLanguage.GERMAN)))
    fun model(language: SpeechLanguage, shortPhrase: Boolean): SpeechQuality =
        if (this == ADAPTIVE) { if (language in setOf(SpeechLanguage.ENGLISH, SpeechLanguage.FRENCH, SpeechLanguage.RUSSIAN) && shortPhrase) ACCURATE else FAST } else this
    fun liveContext(language: SpeechLanguage, shortPhrase: Boolean): Int =
        if (language == SpeechLanguage.ENGLISH || (this == ADAPTIVE && shortPhrase && language in setOf(SpeechLanguage.FRENCH, SpeechLanguage.RUSSIAN))) 512 else 0
    companion object { fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: ADAPTIVE }
}
