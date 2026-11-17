package com.sidekick.app.speaker

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
enum class VoiceSetting(val label: String, val suffix: String, val hint: String) {
    QUIET("Quiet room", "", "Alone in a quiet room, phone about 30–50 cm away."),
    NOISY("Some background noise", "-noisy", "With everyday background sound (TV, café, fan), phone at your usual distance."),
    TABLE("Phone on a table", "-table", "Phone lying on a table about an arm’s length away, as in a meeting."),
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
        consentNeededValue.value = preferences.getBoolean("legacy_needs_consent", false) && consentValue.value?.valid != true
    }
    private fun readConsent(): com.sidekick.core.VoiceprintConsent? = preferences.getString("consent_name", null)?.let {
        com.sidekick.core.VoiceprintConsent(it, preferences.getLong("consent_at", 0), preferences.getInt("consent_version", 0),
            preferences.getString("consent_method", "checkbox").orEmpty(), preferences.getBoolean("consent_guardian", false))
    }
    @Synchronized fun recordConsent(record: com.sidekick.core.VoiceprintConsent) {
        require(record.valid) { "Consent record is incomplete." }
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
        require(samples.all { it.size == modelValue.value.dimension }) { "Voice samples belong to a different speaker model." }
        val vectors = samples.map(VoiceMatch::normalized)
        require(vectors.indices.all { i -> vectors.indices.all { j -> VoiceMatch.similarity(vectors[i], vectors[j]) >= .6f } }) {
            "Voice samples differed too much. Try again, staying in the same place and keeping the phone still."
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
    /** Deletes every profile and the consent record ("Delete my voice setup"). */
    @Synchronized fun delete() {
        SpeakerModel.entries.forEach { model -> SpeechLanguage.entries.forEach { language -> VoiceSetting.entries.forEach { file(model, language, it).delete() } } }
        embeddings.forEach { it.fill(0f) }; embeddings = emptyList(); refresh()
        preferences.edit().remove("consent_name").remove("consent_at").remove("consent_version").remove("consent_method")
            .remove("consent_guardian").remove("legacy_needs_consent").remove("last_used").apply()
        consentValue.value = null; consentNeededValue.value = false
    }
    fun setEnabled(value: Boolean) { preferences.edit().putBoolean("enabled", value).apply(); enabledValue.value = value }
}
