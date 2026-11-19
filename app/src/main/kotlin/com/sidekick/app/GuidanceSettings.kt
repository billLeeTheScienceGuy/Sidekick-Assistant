package com.sidekick.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GuidanceMode { PASSIVE, ASK }
enum class PlaceContext { CITY, PHONE }

class GuidanceSettings(context: Context) {
    private val preferences = context.getSharedPreferences("guidance", Context.MODE_PRIVATE)
    private val appearanceValue = MutableStateFlow(runCatching {
        AppearanceMode.valueOf(preferences.getString("appearance", "SYSTEM") ?: "SYSTEM")
    }.getOrDefault(AppearanceMode.SYSTEM))
    val appearance = appearanceValue.asStateFlow()
    fun setAppearance(value: AppearanceMode) {
        preferences.edit().putString("appearance", value.name).apply(); appearanceValue.value = value
    }
    private inline fun <reified T : Enum<T>> preference(key: String, fallback: T): T =
        runCatching { enumValueOf<T>(preferences.getString(key, fallback.name) ?: fallback.name) }.getOrDefault(fallback)
    private val designValue = MutableStateFlow(preference("interface_design", InterfaceDesign.CALM))
    val interfaceDesign = designValue.asStateFlow()
    fun setInterfaceDesign(value: InterfaceDesign) { preferences.edit().putString("interface_design", value.name).apply(); designValue.value = value }
    private val textSizeValue = MutableStateFlow(preference("note_text_size", NoteTextSize.PHONE))
    val noteTextSize = textSizeValue.asStateFlow()
    fun setNoteTextSize(value: NoteTextSize) { preferences.edit().putString("note_text_size", value.name).apply(); textSizeValue.value = value }
    private val interfaceSizeValue = MutableStateFlow(preference("interface_size", InterfaceSize.PHONE))
    val interfaceSize = interfaceSizeValue.asStateFlow()
    fun setInterfaceSize(value: InterfaceSize) { preferences.edit().putString("interface_size", value.name).apply(); interfaceSizeValue.value = value }
    private val manualNotesValue = MutableStateFlow(preferences.getBoolean("manual_notes", true))
    val manualNotes = manualNotesValue.asStateFlow()
    fun setManualNotes(value: Boolean) { preferences.edit().putBoolean("manual_notes", value).apply(); manualNotesValue.value = value }
    /** Names the user often mentions, with Korean spellings ("Sarah 세라"): fixed in Korean speech (NameSplice). */
    private val spokenNamesValue = MutableStateFlow(preferences.getString("spoken_names", "").orEmpty())
    val spokenNames = spokenNamesValue.asStateFlow()
    fun setSpokenNames(value: String) { val v = value.take(2000); preferences.edit().putString("spoken_names", v).apply(); spokenNamesValue.value = v }
    /** Optional: pause listening (microphone off) after 30 quiet minutes with the phone untouched; resumes on use or carrying. */
    private val pauseRestingValue = MutableStateFlow(preferences.getBoolean("pause_when_resting", false))
    val pauseWhenResting = pauseRestingValue.asStateFlow()
    fun setPauseWhenResting(value: Boolean) { preferences.edit().putBoolean("pause_when_resting", value).apply(); pauseRestingValue.value = value }
    /** The one-time "pause when resting" suggestion was answered (Turn on or Not now). */
    private val restingTipValue = MutableStateFlow(preferences.getBoolean("resting_tip_done", false))
    val restingTipDone = restingTipValue.asStateFlow()
    fun setRestingTipDone() { preferences.edit().putBoolean("resting_tip_done", true).apply(); restingTipValue.value = true }
    /** Shows the suggestion again (phone tests). */
    internal fun resetRestingTip() { preferences.edit().putBoolean("resting_tip_done", false).apply(); restingTipValue.value = false }
    /** Optional mode: reminders are also read aloud when they arrive. */
    private val readAloudValue = MutableStateFlow(preferences.getBoolean("read_reminders_aloud", false))
    val readRemindersAloud = readAloudValue.asStateFlow()
    fun setReadRemindersAloud(value: Boolean) { preferences.edit().putBoolean("read_reminders_aloud", value).apply(); readAloudValue.value = value }
    /** Add Korean names from saved plans (not ones waiting in Review) to [learnedNames] automatically. Off: typed names only. */
    private val learnNamesValue = MutableStateFlow(preferences.getBoolean("learn_names", true))
    val learnNames = learnNamesValue.asStateFlow()
    fun setLearnNames(value: Boolean) { preferences.edit().putBoolean("learn_names", value).apply(); learnNamesValue.value = value }
    /** Keep learned names after their plan is past or deleted. Off: only names in plans that still exist. */
    private val keepNamesValue = MutableStateFlow(preferences.getBoolean("keep_learned_names", true))
    val keepLearnedNames = keepNamesValue.asStateFlow()
    fun setKeepLearnedNames(value: Boolean) { preferences.edit().putBoolean("keep_learned_names", value).apply(); keepNamesValue.value = value }
    /** Names learned from saved plans, on this phone only. Cleared by "Delete all". */
    private val learnedNamesValue = MutableStateFlow(preferences.getString("learned_names", "").orEmpty().split('\n').filter { it.isNotBlank() }.toSet())
    val learnedNames = learnedNamesValue.asStateFlow()
    @Synchronized fun setLearnedNames(value: Set<String>) {
        val v = value.take(100).toSet()
        if (v == learnedNamesValue.value) return
        preferences.edit().putString("learned_names", v.joinToString("\n")).apply(); learnedNamesValue.value = v
    }
    fun forgetLearnedName(name: String) = setLearnedNames(learnedNamesValue.value - name)
    fun clearLearnedNames() = setLearnedNames(emptySet())
    /** Simple home (large Start/Stop button, next plans; Today / Plans / More) is the default. Detailed stays in Settings. */
    private val oneScreenValue = MutableStateFlow(preferences.getBoolean("one_screen_home", true))
    val oneScreenHome = oneScreenValue.asStateFlow()
    fun setOneScreenHome(value: Boolean) { preferences.edit().putBoolean("one_screen_home", value).apply(); oneScreenValue.value = value }
    fun resetAppearance() {
        setAppearance(AppearanceMode.SYSTEM); setInterfaceDesign(InterfaceDesign.CALM)
        setNoteTextSize(NoteTextSize.PHONE); setInterfaceSize(InterfaceSize.PHONE)
    }

    private val phoneRegionValue = MutableStateFlow(preferences.getBoolean("privacy_phone_region", false))
    val privacyPhoneRegion = phoneRegionValue.asStateFlow()
    private val regionValue = MutableStateFlow(runCatching {
        if (!phoneRegionValue.value) null else com.sidekick.core.CachedPrivacyRegion(
            com.sidekick.core.PrivacyRegion(preferences.getString("privacy_country", "").orEmpty(), preferences.getString("privacy_area", "").orEmpty()),
            preferences.getLong("privacy_region_at", 0))
    }.getOrNull())
    val privacyRegion = regionValue.asStateFlow()
    fun setPrivacyPhoneRegion(enabled: Boolean) {
        preferences.edit().putBoolean("privacy_phone_region", enabled).apply(); phoneRegionValue.value = enabled
        if (!enabled) setPrivacyRegion(null)
    }
    fun setPrivacyRegion(region: com.sidekick.core.PrivacyRegion?) {
        val value = region?.takeIf { phoneRegionValue.value }?.let { com.sidekick.core.CachedPrivacyRegion(it, System.currentTimeMillis()) }
        preferences.edit().apply {
            if (value == null) { remove("privacy_country"); remove("privacy_area"); remove("privacy_region_at") }
            else { putString("privacy_country", value.region.country); putString("privacy_area", value.region.area); putLong("privacy_region_at", value.checkedAt) }
        }.apply()
        regionValue.value = value
    }
    private val syncValue = MutableStateFlow(CalendarSyncConfig(preferences.getBoolean("calendar_sync", false),
        preferences.getLong("calendar_sync_id", -1).takeIf { it > 0 }, preferences.getBoolean("calendar_sync_information", false)))
    val calendarSync = syncValue.asStateFlow()
    fun setCalendarSync(config: CalendarSyncConfig) {
        require(!config.enabled || config.calendarId != null && config.calendarId > 0)
        preferences.edit().putBoolean("calendar_sync", config.enabled).putLong("calendar_sync_id", config.calendarId ?: -1)
            .putBoolean("calendar_sync_information", config.includeInformational).apply()
        syncValue.value = config
    }
    private val diarizationValue = MutableStateFlow(preferences.getBoolean("diarization", true))
    val diarization = diarizationValue.asStateFlow()
    fun setDiarization(value: Boolean) { preferences.edit().putBoolean("diarization", value).apply(); diarizationValue.value = value }
    /** "Treat every place as the strictest state": no location requests; all-party + no voice grouping everywhere. */
    private val strictestValue = MutableStateFlow(preferences.getBoolean("strictest_everywhere", false))
    val strictestEverywhere = strictestValue.asStateFlow()
    fun setStrictestEverywhere(value: Boolean) { preferences.edit().putBoolean("strictest_everywhere", value).apply(); strictestValue.value = value }
    /** "Include the voice-analysis line in every state": notice required before voice grouping everywhere. */
    private val voiceLineValue = MutableStateFlow(preferences.getBoolean("voice_line_everywhere", false))
    val voiceLineEverywhere = voiceLineValue.asStateFlow()
    fun setVoiceLineEverywhere(value: Boolean) { preferences.edit().putBoolean("voice_line_everywhere", value).apply(); voiceLineValue.value = value }
    /** Show Announce / "I told everyone" during every session, not only when a notice is needed. */
    private val alwaysAnnounceValue = MutableStateFlow(preferences.getBoolean("always_show_announce", true))
    val alwaysShowAnnounce = alwaysAnnounceValue.asStateFlow()
    fun setAlwaysShowAnnounce(value: Boolean) { preferences.edit().putBoolean("always_show_announce", value).apply(); alwaysAnnounceValue.value = value }
    /** Play the notice automatically when a new conversation starts (off by default). */
    private val autoAnnounceValue = MutableStateFlow(preferences.getBoolean("auto_announce", false))
    val autoAnnounce = autoAnnounceValue.asStateFlow()
    fun setAutoAnnounce(value: Boolean) { preferences.edit().putBoolean("auto_announce", value).apply(); autoAnnounceValue.value = value }
    /** The precise-location request is shown once. */
    val preciseLocationAsked: Boolean get() = preferences.getBoolean("precise_location_asked", false)
    fun markPreciseLocationAsked() { preferences.edit().putBoolean("precise_location_asked", true).apply() }
    private val retentionValue = MutableStateFlow(preferences.getInt("retention_days", 0).takeIf { it in com.sidekick.core.MemoryRetention.choices } ?: 0)
    val retentionDays = retentionValue.asStateFlow()
    private val riskValue = MutableStateFlow(preferences.getBoolean("risk_advisories", true))
    val riskAdvisories = riskValue.asStateFlow()
    fun setRiskAdvisories(value: Boolean) { preferences.edit().putBoolean("risk_advisories", value).apply(); riskValue.value = value }
    fun setRetentionDays(days: Int) {
        require(days in com.sidekick.core.MemoryRetention.choices)
        preferences.edit().putInt("retention_days", days).apply(); retentionValue.value = days
    }
    private val handsOff = MutableStateFlow(preferences.getBoolean("hands_off", !context.packageName.endsWith(".benchmark")))
    val handsOffMode = handsOff.asStateFlow()
    fun setHandsOff(value: Boolean) { preferences.edit().putBoolean("hands_off", value).apply(); handsOff.value = value }
    /** Languages Auto-detect may choose (all by default). Fewer languages: fewer wrong-language guesses on short phrases. */
    private val detectValue = MutableStateFlow(preferences.getString("detect_languages", null)?.split(',')
        ?.mapNotNull { code -> com.sidekick.core.SpeechLanguage.entries.firstOrNull { it.code == code && it != com.sidekick.core.SpeechLanguage.AUTO } }
        ?.toSet()?.takeIf { it.isNotEmpty() } ?: com.sidekick.core.SpeechLanguage.entries.filter { it != com.sidekick.core.SpeechLanguage.AUTO }.toSet())
    val detectLanguages = detectValue.asStateFlow()
    fun setDetectLanguages(value: Set<com.sidekick.core.SpeechLanguage>) {
        val v = value.filter { it != com.sidekick.core.SpeechLanguage.AUTO }.toSet()
        if (v.isEmpty()) return // at least one language stays on
        preferences.edit().putString("detect_languages", v.joinToString(",") { it.code }).apply(); detectValue.value = v
    }
    private val languageValue = MutableStateFlow(com.sidekick.core.SpeechLanguage.fromCode(preferences.getString("speech_language", "en")))
    val speechLanguage = languageValue.asStateFlow()
    private val qualityValue = MutableStateFlow(preferences.getString("speech_quality", null)?.let(com.sidekick.core.SpeechQuality::fromName)
        ?: if (com.sidekick.app.speech.ModelPacks.available(context, "ggml-base.bin")) com.sidekick.core.SpeechQuality.ADAPTIVE else com.sidekick.core.SpeechQuality.FAST)
    val speechQuality = qualityValue.asStateFlow()
    init { if (!preferences.contains("speech_quality")) preferences.edit().putString("speech_quality", qualityValue.value.name).apply() }
    fun setSpeechQuality(value: com.sidekick.core.SpeechQuality) { preferences.edit().putString("speech_quality", value.name).apply(); qualityValue.value = value }
    fun setSpeechLanguage(value: com.sidekick.core.SpeechLanguage) {
        preferences.edit().apply {
            putString("speech_language", value.code)
            if (value != com.sidekick.core.SpeechLanguage.AUTO) putString("last_manual_language", value.code)
        }.apply()
        languageValue.value = value
    }
    fun setAutomaticLanguage(enabled: Boolean) {
        if (enabled) {
            if (languageValue.value != com.sidekick.core.SpeechLanguage.AUTO) setSpeechLanguage(languageValue.value)
            setSpeechQuality(com.sidekick.core.SpeechQuality.ADAPTIVE)
            setSpeechLanguage(com.sidekick.core.SpeechLanguage.AUTO)
        } else {
            setSpeechLanguage(com.sidekick.core.SpeechLanguage.fromCode(preferences.getString("last_manual_language", "en"))
                .takeUnless { it == com.sidekick.core.SpeechLanguage.AUTO } ?: com.sidekick.core.SpeechLanguage.fromCode("en"))
        }
    }
    private val skipIntroValue = MutableStateFlow(preferences.getBoolean("skip_listening_intro", false))
    val skipListeningIntro = skipIntroValue.asStateFlow()
    fun setSkipListeningIntro(value: Boolean) { preferences.edit().putBoolean("skip_listening_intro", value).apply(); skipIntroValue.value = value }
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
