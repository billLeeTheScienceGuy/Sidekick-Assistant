package com.sidekick.app.speaker

import com.sidekick.app.R
import com.sidekick.app.tr

import android.content.Context
import android.util.AtomicFile
import com.sidekick.core.VoiceMatch
import com.sidekick.core.SpeechLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Where a setup recording was made. Each setting is its own profile; a turn counts as the owner when it matches any one
 * of them on its own (signatures from different settings are never averaged together).
 */
enum class VoiceSetting(val suffix: String) {
    QUIET(""), NOISY("-noisy"), TABLE("-table");
    val label: String get() = when (this) { QUIET -> tr(R.string.quiet_room); NOISY -> tr(R.string.some_background_noise); TABLE -> tr(R.string.phone_on_a_table) }
    val hint: String get() = when (this) { QUIET -> tr(R.string.alone_in_a_quiet_room_phone); NOISY -> tr(R.string.with_everyday_background_sound_tv_caf); TABLE -> tr(R.string.phone_lying_on_a_table_about) }
}

/**
 * Only normalized embeddings persist, inside no-backup private storage.
 * Each speaker model keeps one profile per language. English and Auto-detect share the general slot;
 * Auto-detect matching checks every enrolled language of the selected model.
 */
class VoiceProfile(context: Context, initialLanguage: SpeechLanguage = SpeechLanguage.ENGLISH) {
    private val directory = context.noBackupFilesDir
    private val preferences = context.getSharedPreferences("voice", Context.MODE_PRIVATE)
    private val languageValue = MutableStateFlow(initialLanguage)
    val language = languageValue.asStateFlow()
    private fun slot(selected: SpeechLanguage) = if (selected == SpeechLanguage.AUTO) SpeechLanguage.ENGLISH else selected
    private fun file(model: SpeakerModel, selected: SpeechLanguage = languageValue.value, setting: VoiceSetting = VoiceSetting.QUIET): AtomicFile {
        val target = slot(selected)
        val suffix = if (target == SpeechLanguage.ENGLISH) "" else "-${target.code}"
        return AtomicFile(File(directory, (if (model == SpeakerModel.RESNET) "voice-profile" else "voice-profile-campplus") + suffix + setting.suffix + ".json"))
    }
    /**
     * The benchmark package keeps ResNet for its older fixtures. The app uses only offered models: a saved ResNet choice
     * moves to CAM++ (strangers scored as high as the owner with ResNet in the evaluation), so voice setup is needed again.
     */
    private val benchmark = context.packageName.endsWith(".benchmark")
    private val defaultModel = if (benchmark) SpeakerModel.RESNET else SpeakerModel.CAMPPLUS
    private val modelValue = MutableStateFlow(SpeakerModel.entries.firstOrNull { it.name == preferences.getString("model", null) && (it.offered || benchmark) } ?: defaultModel)
    val model = modelValue.asStateFlow()
    /** Every saved setting for the selected language. */
    private var embeddings: List<FloatArray> = loadAll()
    /** Auto-detect references, read from disk once per model/language change instead of on every chunk. */
    private var autoReferences: List<FloatArray>? = null
    private val readyValue = MutableStateFlow(false)
    /** Selected language has a usable profile; in Auto-detect, any enrolled language counts. */
    val ready = readyValue.asStateFlow()
    private val enrolledValue = MutableStateFlow<Map<SpeechLanguage, Long>>(emptyMap())
    /** Enrolled language slots of the selected model and when each was last saved. */
    val enrolled = enrolledValue.asStateFlow()
    private val settingsValue = MutableStateFlow<Map<SpeechLanguage, Set<VoiceSetting>>>(emptyMap())
    /** Which settings (quiet, noise, table) each language has, for the selected model. */
    val settings = settingsValue.asStateFlow()
    private val enabledValue = MutableStateFlow(preferences.getBoolean("enabled", true))
    val enabled = enabledValue.asStateFlow()
    private val consentValue = MutableStateFlow(readConsent())
    /** The owner's written consent for their voice signature; deleted with the profile. */
    val consent = consentValue.asStateFlow()
    private val consentNeededValue = MutableStateFlow(false)
    /** A profile saved before consent records existed: matching stays off until the owner accepts the notice. */
    val consentNeeded = consentNeededValue.asStateFlow()
    init {
        refresh()
        if (enrolledValue.value.isNotEmpty() && consentValue.value?.valid != true && !preferences.getBoolean("consent_checked", false))
            preferences.edit().putBoolean("legacy_needs_consent", true).apply()
        preferences.edit().putBoolean("consent_checked", true).apply()
        // A profile saved before consent records existed, or a consent given to an older notice text: ask again.
        consentNeededValue.value = (preferences.getBoolean("legacy_needs_consent", false) || consentValue.value != null) && consentValue.value?.valid != true
    }
    private fun readConsent(): com.sidekick.core.VoiceprintConsent? = preferences.getString("consent_name", null)?.let {
        com.sidekick.core.VoiceprintConsent(it, preferences.getLong("consent_at", 0), preferences.getInt("consent_version", 0),
            preferences.getString("consent_method", "checkbox").orEmpty(), preferences.getBoolean("consent_guardian", false))
    }
    @Synchronized fun recordConsent(record: com.sidekick.core.VoiceprintConsent) {
        require(record.valid) { tr(R.string.consent_record_is_incomplete) }
        preferences.edit().putString("consent_name", record.name.take(80)).putLong("consent_at", record.atEpochMs)
            .putInt("consent_version", record.noticeVersion).putString("consent_method", record.method)
            .putBoolean("consent_guardian", record.guardian).putBoolean("legacy_needs_consent", false).apply()
        consentValue.value = record; consentNeededValue.value = false
    }
    private fun matchingAllowed() = enabledValue.value && !consentNeededValue.value
    /** Called when a listening session starts with a usable profile; at most one write per day. */
    fun markUsed(nowMs: Long = System.currentTimeMillis()) {
        if (nowMs - preferences.getLong("last_used", 0) > 86_400_000L) preferences.edit().putLong("last_used", nowMs).apply()
    }
    /** Retention: delete every profile and the consent record if unused for 12 months. Returns true if deleted. */
    @Synchronized fun purgeIfExpired(nowMs: Long = System.currentTimeMillis()): Boolean {
        val saved = enrolledValue.value.values.maxOrNull() ?: return false
        val lastUsed = preferences.getLong("last_used", 0).takeIf { it > 0 }
        if (!com.sidekick.core.VoiceprintPolicy.expired(saved, lastUsed, nowMs)) return false
        delete(); return true
    }
    private fun loadAll(selected: SpeechLanguage = languageValue.value): List<FloatArray> = VoiceSetting.entries.mapNotNull { load(selected, it) }
    private fun load(selected: SpeechLanguage, setting: VoiceSetting): FloatArray? = runCatching {
        val data = JSONObject(file(modelValue.value, selected, setting).openRead().bufferedReader().use { it.readText() })
        require(data.getString("model") == modelValue.value.hash)
        val values = data.getJSONArray("embedding")
        require(values.length() == modelValue.value.dimension)
        VoiceMatch.normalized(FloatArray(values.length()) { values.getDouble(it).toFloat() })
    }.getOrNull()
    private fun slots() = SpeechLanguage.entries.filter { it != SpeechLanguage.AUTO }
    private fun refresh() {
        autoReferences?.forEach { it.fill(0f) }; autoReferences = null
        val found = linkedMapOf<SpeechLanguage, Long>()
        val kinds = linkedMapOf<SpeechLanguage, Set<VoiceSetting>>()
        slots().forEach { language ->
            val saved = VoiceSetting.entries.filter { setting -> load(language, setting)?.also { it.fill(0f) } != null }
            if (saved.isNotEmpty()) {
                kinds[language] = saved.toSet()
                found[language] = saved.maxOf { file(modelValue.value, language, it).baseFile.lastModified() }
            }
        }
        enrolledValue.value = found; settingsValue.value = kinds
        readyValue.value = if (languageValue.value == SpeechLanguage.AUTO) found.isNotEmpty() else embeddings.isNotEmpty()
    }
    @Synchronized fun selectModel(model: SpeakerModel) {
        if (model == modelValue.value) return
        embeddings.forEach { it.fill(0f) }; modelValue.value = model
        embeddings = loadAll(); refresh()
        wakeSavedValue.value = wakeFile().baseFile.exists()
        preferences.edit().putString("model", model.name).apply()
    }
    @Synchronized fun selectLanguage(language: SpeechLanguage) {
        if (language == languageValue.value) return
        embeddings.forEach { it.fill(0f) }; languageValue.value = language
        embeddings = loadAll(); refresh()
    }
    /** The quiet-room profile of the selected language (the original single setup). */
    @Synchronized fun snapshot(): FloatArray? = if (matchingAllowed()) load(languageValue.value, VoiceSetting.QUIET) else null
    /** Same as [matchingSnapshots], with whether each one is a quiet-room profile. */
    @Synchronized fun matchingReferences(): List<Pair<VoiceSetting, FloatArray>> {
        if (!matchingAllowed()) return emptyList()
        val languages = if (languageValue.value == SpeechLanguage.AUTO) slots() else listOf(languageValue.value)
        return languages.flatMap { language -> VoiceSetting.entries.mapNotNull { setting -> load(language, setting)?.let { setting to it } } }
    }
    /** Every saved setting of the selected language; Auto-detect uses every enrolled language, same speaker model. */
    @Synchronized fun matchingSnapshots(): List<FloatArray> {
        if (!matchingAllowed()) return emptyList()
        if (languageValue.value != SpeechLanguage.AUTO) return embeddings.map { it.copyOf() }
        val references = autoReferences ?: slots().flatMap { loadAll(it) }.also { autoReferences = it }
        return references.map { it.copyOf() }
    }
    /** Saves to [target]'s [setting] slot; Auto-detect setup chooses the language explicitly. Other settings are kept. */
    @Synchronized fun save(samples: List<FloatArray>, target: SpeechLanguage = languageValue.value, setting: VoiceSetting = VoiceSetting.QUIET) {
        require(samples.size == 3)
        require(samples.all { it.size == modelValue.value.dimension }) { tr(R.string.voice_samples_belong_to_a_different) }
        val vectors = samples.map(VoiceMatch::normalized)
        require(vectors.indices.all { i -> vectors.indices.all { j -> VoiceMatch.similarity(vectors[i], vectors[j]) >= .6f } }) {
            tr(R.string.voice_samples_differed_too_much_try)
        }
        val result = VoiceMatch.normalized(FloatArray(modelValue.value.dimension) { i -> vectors.sumOf { it[i].toDouble() }.toFloat() / 3 })
        val data = JSONObject().put("model", modelValue.value.hash).put("embedding", JSONArray(result.toList())).toString().toByteArray()
        val destination = file(modelValue.value, target, setting)
        val stream = destination.startWrite()
        try { stream.write(data); destination.finishWrite(stream) } catch (e: Exception) { destination.failWrite(stream); throw e }
        result.fill(0f)
        if (slot(target) == slot(languageValue.value)) { embeddings.forEach { it.fill(0f) }; embeddings = loadAll() }
        refresh()
        setEnabled(true)
    }
    /**
     * Learning across sessions: moves the quiet-room signature of [target] a little toward [turns] (very confident owner
     * turns from one session), keeping the original setup signature to bound drift. Returns true when it changed.
     */
    @Synchronized fun learn(turns: List<FloatArray>, target: SpeechLanguage = languageValue.value): Boolean {
        if (!matchingAllowed() || consentValue.value?.valid != true) return false
        val destination = file(modelValue.value, target, VoiceSetting.QUIET)
        val data = runCatching { JSONObject(destination.openRead().bufferedReader().use { it.readText() }) }.getOrNull() ?: return false
        if (data.optString("model") != modelValue.value.hash) return false
        fun read(key: String) = data.optJSONArray(key)?.let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } }
        val current = read("embedding") ?: return false
        val setup = read("setup") ?: current.copyOf()
        val next = com.sidekick.core.VoiceprintPolicy.learned(VoiceMatch.normalized(current), VoiceMatch.normalized(setup), turns)
        try {
            if (next == null) return false
            val bytes = JSONObject().put("model", modelValue.value.hash).put("embedding", JSONArray(next.toList()))
                .put("setup", JSONArray(setup.toList())).put("learned", data.optInt("learned", 0) + 1).toString().toByteArray()
            val stream = destination.startWrite()
            try { stream.write(bytes); destination.finishWrite(stream) } catch (e: Exception) { destination.failWrite(stream); throw e }
            if (slot(target) == slot(languageValue.value)) { embeddings.forEach { it.fill(0f) }; embeddings = loadAll() }
            autoReferences?.forEach { it.fill(0f) }; autoReferences = null
            return true
        } finally { current.fill(0f); setup.fill(0f); next?.fill(0f) }
    }
    // Optional "SideKick voice check" (com.sidekick.core.WakeVoice): off unless the owner turns it on; its signature is
    // one averaged vector per speaker model, kept and deleted with the rest of the voice setup.
    private fun wakeFile(model: SpeakerModel = modelValue.value) = AtomicFile(File(directory, "wake-voice-${model.name.lowercase()}.json"))
    private val wakeOnValue = MutableStateFlow(preferences.getBoolean("wake_check", false))
    val wakeOn = wakeOnValue.asStateFlow()
    private val wakeSavedValue = MutableStateFlow(wakeFile().baseFile.exists())
    val wakeSaved = wakeSavedValue.asStateFlow()
    fun setWakeOn(value: Boolean) { preferences.edit().putBoolean("wake_check", value).apply(); wakeOnValue.value = value }
    /** The saved name signature, only while the check is on and voice matching is allowed (consent). */
    @Synchronized fun wakeTemplate(): FloatArray? {
        if (!wakeOnValue.value || !matchingAllowed() || consentValue.value?.valid != true) return null
        val data = runCatching { JSONObject(wakeFile().openRead().bufferedReader().use { it.readText() }) }.getOrNull() ?: return null
        if (data.optString("model") != modelValue.value.hash) return null
        return data.optJSONArray("embedding")?.let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } }
            ?.takeIf { it.size == modelValue.value.dimension }
    }
    @Synchronized fun saveWake(samples: List<FloatArray>) {
        require(samples.all { it.size == modelValue.value.dimension }) { tr(R.string.voice_samples_belong_to_a_different) }
        val template = com.sidekick.core.WakeVoice.template(samples)
            ?: throw IllegalArgumentException(tr(R.string.your_sidekick_samples_differed_too_much))
        try {
            val bytes = JSONObject().put("model", modelValue.value.hash).put("embedding", JSONArray(template.toList())).toString().toByteArray()
            val destination = wakeFile(); val stream = destination.startWrite()
            try { stream.write(bytes); destination.finishWrite(stream) } catch (e: Exception) { destination.failWrite(stream); throw e }
        } finally { template.fill(0f) }
        wakeSavedValue.value = true; setWakeOn(true)
    }
    @Synchronized fun deleteWake() {
        SpeakerModel.entries.forEach { wakeFile(it).delete() }
        wakeSavedValue.value = false; setWakeOn(false)
    }
    /** Deletes every profile and the consent record ("Delete my voice setup"). */
    @Synchronized fun delete() {
        deleteWake()
        SpeakerModel.entries.forEach { model -> SpeechLanguage.entries.forEach { language -> VoiceSetting.entries.forEach { file(model, language, it).delete() } } }
        embeddings.forEach { it.fill(0f) }; embeddings = emptyList(); refresh()
        preferences.edit().remove("consent_name").remove("consent_at").remove("consent_version").remove("consent_method")
            .remove("consent_guardian").remove("legacy_needs_consent").remove("last_used").apply()
        consentValue.value = null; consentNeededValue.value = false
    }
    fun setEnabled(value: Boolean) { preferences.edit().putBoolean("enabled", value).apply(); enabledValue.value = value }
}
