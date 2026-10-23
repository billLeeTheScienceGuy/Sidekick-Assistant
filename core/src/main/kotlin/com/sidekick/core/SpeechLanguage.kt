package com.sidekick.core

enum class SpeechLanguage(val code: String, val label: String) {
    ENGLISH("en", "English"), AUTO("auto", "Automatic · multilingual"),
    KOREAN("ko", "한국어 · Korean"), CHINESE("zh", "中文 · Chinese"),
    FRENCH("fr", "Français · French"), GERMAN("de", "Deutsch · German"), RUSSIAN("ru", "Русский · Russian");
    val multilingual: Boolean get() = this != ENGLISH
    companion object { fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: ENGLISH }
}
