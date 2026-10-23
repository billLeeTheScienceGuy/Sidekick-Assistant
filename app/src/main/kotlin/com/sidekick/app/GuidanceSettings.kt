package com.sidekick.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GuidanceMode { PASSIVE, ASK }
enum class PlaceContext { CITY, PHONE }

class GuidanceSettings(context: Context) {
    private val preferences = context.getSharedPreferences("guidance", Context.MODE_PRIVATE)
    private val languageValue = MutableStateFlow(com.sidekick.core.SpeechLanguage.fromCode(preferences.getString("speech_language", "en")))
    val speechLanguage = languageValue.asStateFlow()
    fun setSpeechLanguage(value: com.sidekick.core.SpeechLanguage) { preferences.edit().putString("speech_language", value.code).apply(); languageValue.value = value }
    private val current = MutableStateFlow(if (preferences.getBoolean("ask_end_time", false)) GuidanceMode.ASK else GuidanceMode.PASSIVE)
    val mode = current.asStateFlow()
    private val includeEvents = MutableStateFlow(preferences.getBoolean("include_informational", true))
    val includeInformational = includeEvents.asStateFlow()
    private val automatic = MutableStateFlow(preferences.getBoolean("automatic_places", true))
    val automaticPlaces = automatic.asStateFlow()
    private val contextMode = MutableStateFlow(if (preferences.getBoolean("phone_place_context", false)) PlaceContext.PHONE else PlaceContext.CITY)
    val placeContext = contextMode.asStateFlow()
    private val cityValue = MutableStateFlow(preferences.getString("city", "").orEmpty())
    val city = cityValue.asStateFlow()
    private val messages = MutableStateFlow(preferences.getBoolean("message_notifications", false))
    val messageNotifications = messages.asStateFlow()
    private val packages = MutableStateFlow(preferences.getStringSet("message_apps", emptySet()).orEmpty().toSet())
    val messageApps = packages.asStateFlow()
    private val sharing = MutableStateFlow(preferences.getBoolean("shared_messages", true))
    val sharedMessages = sharing.asStateFlow()
    fun setSharedMessages(value: Boolean) { preferences.edit().putBoolean("shared_messages", value).apply(); sharing.value = value }
    fun setAutomaticPlaces(value: Boolean) { preferences.edit().putBoolean("automatic_places", value).apply(); automatic.value = value }
    fun setPlaceContext(value: PlaceContext) { preferences.edit().putBoolean("phone_place_context", value == PlaceContext.PHONE).apply(); contextMode.value = value }
    fun setCity(value: String) { if (value.length <= 100) { preferences.edit().putString("city", value).apply(); cityValue.value = value } }
    val messagesEnabledSince: Long get() = preferences.getLong("messages_enabled_since", Long.MAX_VALUE)
    fun setMessageNotifications(value: Boolean) {
        if (value && !messages.value) preferences.edit().putLong("messages_enabled_since", System.currentTimeMillis()).apply()
        preferences.edit().putBoolean("message_notifications", value).apply(); messages.value = value
    }
    fun setMessageApp(name: String, enabled: Boolean) {
        val updated = if (enabled) packages.value + name else packages.value - name
        preferences.edit().putStringSet("message_apps", updated).apply(); packages.value = updated
    }
    fun setIncludeInformational(include: Boolean) {
        preferences.edit().putBoolean("include_informational", include).apply()
        includeEvents.value = include
    }
    fun setMode(mode: GuidanceMode) {
        preferences.edit().putBoolean("ask_end_time", mode == GuidanceMode.ASK).apply()
        current.value = mode
    }
}
