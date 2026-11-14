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
    private fun file(model: SpeakerModel, selected: SpeechLanguage = languageValue.value): AtomicFile {
        val target = slot(selected)
        val suffix = if (target == SpeechLanguage.ENGLISH) "" else "-${target.code}"
        return AtomicFile(File(directory, (if (model == SpeakerModel.RESNET) "voice-profile" else "voice-profile-campplus") + suffix + ".json"))
    }
    private val legacyFile = file(SpeakerModel.RESNET, SpeechLanguage.ENGLISH).baseFile
    private val defaultModel = if (context.packageName.endsWith(".benchmark") || legacyFile.exists() || File(legacyFile.path + ".bak").exists()) SpeakerModel.RESNET else SpeakerModel.CAMPPLUS
    private val modelValue = MutableStateFlow(SpeakerModel.entries.firstOrNull { it.name == preferences.getString("model", null) } ?: defaultModel)
    val model = modelValue.asStateFlow()
    private var embedding: FloatArray? = load()
    /** Auto-detect references, read from disk once per model/language change instead of on every chunk. */
    private var autoReferences: List<FloatArray>? = null
    private val readyValue = MutableStateFlow(false)
    /** Selected language has a usable profile; in Auto-detect, any enrolled language counts. */
    val ready = readyValue.asStateFlow()
    private val enrolledValue = MutableStateFlow<Map<SpeechLanguage, Long>>(emptyMap())
    /** Enrolled language slots of the selected model and when each was saved. */
    val enrolled = enrolledValue.asStateFlow()
    private val enabledValue = MutableStateFlow(preferences.getBoolean("enabled", true))
    val enabled = enabledValue.asStateFlow()
    init { refresh() }
    private fun load(selected: SpeechLanguage = languageValue.value): FloatArray? = runCatching {
        val data = JSONObject(file(modelValue.value, selected).openRead().bufferedReader().use { it.readText() })
        require(data.getString("model") == modelValue.value.hash)
        val values = data.getJSONArray("embedding")
        require(values.length() == modelValue.value.dimension)
        VoiceMatch.normalized(FloatArray(values.length()) { values.getDouble(it).toFloat() })
    }.getOrNull()
    private fun slots() = SpeechLanguage.entries.filter { it != SpeechLanguage.AUTO }
    private fun refresh() {
        autoReferences?.forEach { it.fill(0f) }; autoReferences = null
        val found = linkedMapOf<SpeechLanguage, Long>()
        slots().forEach { language -> load(language)?.let { vector -> vector.fill(0f); found[language] = file(modelValue.value, language).baseFile.lastModified() } }
        enrolledValue.value = found
        readyValue.value = if (languageValue.value == SpeechLanguage.AUTO) found.isNotEmpty() else embedding != null
    }
    @Synchronized fun selectModel(model: SpeakerModel) {
        if (model == modelValue.value) return
        embedding?.fill(0f); modelValue.value = model
        embedding = load(); refresh()
        preferences.edit().putString("model", model.name).apply()
    }
    @Synchronized fun selectLanguage(language: SpeechLanguage) {
        if (language == languageValue.value) return
        embedding?.fill(0f); languageValue.value = language
        embedding = load(); refresh()
    }
    @Synchronized fun snapshot(): FloatArray? = if (enabledValue.value) embedding?.copyOf() else null
    /** Auto-detect uses only explicitly enrolled profiles for this same speaker model. */
    @Synchronized fun matchingSnapshots(): List<FloatArray> {
        if (!enabledValue.value) return emptyList()
        if (languageValue.value != SpeechLanguage.AUTO) return listOfNotNull(embedding?.copyOf())
        val references = autoReferences ?: slots().mapNotNull { load(it) }.also { autoReferences = it }
        return references.map { it.copyOf() }
    }
    /** Saves to [target]'s slot; Auto-detect setup chooses the language explicitly. */
    @Synchronized fun save(samples: List<FloatArray>, target: SpeechLanguage = languageValue.value) {
        require(samples.size == 3)
        require(samples.all { it.size == modelValue.value.dimension }) { "Voice samples belong to a different speaker model." }
        val vectors = samples.map(VoiceMatch::normalized)
        require(vectors.indices.all { i -> vectors.indices.all { j -> VoiceMatch.similarity(vectors[i], vectors[j]) >= .6f } }) {
            "Voice samples differed too much. Try again alone in a quiet room."
        }
        val result = VoiceMatch.normalized(FloatArray(modelValue.value.dimension) { i -> vectors.sumOf { it[i].toDouble() }.toFloat() / 3 })
        val data = JSONObject().put("model", modelValue.value.hash).put("embedding", JSONArray(result.toList())).toString().toByteArray()
        val destination = file(modelValue.value, target)
        val stream = destination.startWrite()
        try { stream.write(data); destination.finishWrite(stream) } catch (e: Exception) { destination.failWrite(stream); throw e }
        if (slot(target) == slot(languageValue.value)) { embedding?.fill(0f); embedding = result } else result.fill(0f)
        refresh()
        setEnabled(true)
    }
    @Synchronized fun delete() { SpeakerModel.entries.forEach { model -> SpeechLanguage.entries.forEach { file(model, it).delete() } }; embedding?.fill(0f); embedding = null; refresh() }
    fun setEnabled(value: Boolean) { preferences.edit().putBoolean("enabled", value).apply(); enabledValue.value = value }
}
