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

/** Only normalized embeddings persist, inside no-backup private storage. */
class VoiceProfile(context: Context, initialLanguage: SpeechLanguage = SpeechLanguage.ENGLISH) {
    private val directory = context.noBackupFilesDir
    private val preferences = context.getSharedPreferences("voice", Context.MODE_PRIVATE)
    private val languageValue = MutableStateFlow(initialLanguage)
    val language = languageValue.asStateFlow()
    private fun file(model: SpeakerModel, selected: SpeechLanguage = languageValue.value): AtomicFile {
        val suffix = if (selected in setOf(SpeechLanguage.ENGLISH, SpeechLanguage.AUTO)) "" else "-${selected.code}"
        return AtomicFile(File(directory, (if (model == SpeakerModel.RESNET) "voice-profile" else "voice-profile-campplus") + suffix + ".json"))
    }
    private val legacyFile = file(SpeakerModel.RESNET, SpeechLanguage.ENGLISH).baseFile
    private val defaultModel = if (context.packageName.endsWith(".benchmark") || legacyFile.exists() || File(legacyFile.path + ".bak").exists()) SpeakerModel.RESNET else SpeakerModel.CAMPPLUS
    private val modelValue = MutableStateFlow(SpeakerModel.entries.firstOrNull { it.name == preferences.getString("model", null) } ?: defaultModel)
    val model = modelValue.asStateFlow()
    private var embedding: FloatArray? = load()
    private val readyValue = MutableStateFlow(embedding != null)
    val ready = readyValue.asStateFlow()
    private val enabledValue = MutableStateFlow(preferences.getBoolean("enabled", true))
    val enabled = enabledValue.asStateFlow()
    private fun load(): FloatArray? = runCatching {
        val data = JSONObject(file(modelValue.value).openRead().bufferedReader().use { it.readText() })
        require(data.getString("model") == modelValue.value.hash)
        val values = data.getJSONArray("embedding")
        require(values.length() == modelValue.value.dimension)
        VoiceMatch.normalized(FloatArray(values.length()) { values.getDouble(it).toFloat() })
    }.getOrNull()
    @Synchronized fun selectModel(model: SpeakerModel) {
        if (model == modelValue.value) return
        embedding?.fill(0f); modelValue.value = model
        embedding = load(); readyValue.value = embedding != null
        preferences.edit().putString("model", model.name).apply()
    }
    @Synchronized fun selectLanguage(language: SpeechLanguage) {
        if (language == languageValue.value) return
        embedding?.fill(0f); languageValue.value = language
        embedding = load(); readyValue.value = embedding != null
    }
    @Synchronized fun snapshot(): FloatArray? = if (enabledValue.value) embedding?.copyOf() else null
    @Synchronized fun save(samples: List<FloatArray>) {
        require(samples.size == 3)
        require(samples.all { it.size == modelValue.value.dimension }) { "Voice samples belong to a different speaker model." }
        val vectors = samples.map(VoiceMatch::normalized)
        require(vectors.indices.all { i -> vectors.indices.all { j -> VoiceMatch.similarity(vectors[i], vectors[j]) >= .6f } }) {
            "Voice samples differed too much. Try again alone in a quiet room."
        }
        val result = VoiceMatch.normalized(FloatArray(modelValue.value.dimension) { i -> vectors.sumOf { it[i].toDouble() }.toFloat() / 3 })
        val data = JSONObject().put("model", modelValue.value.hash).put("embedding", JSONArray(result.toList())).toString().toByteArray()
        val target = file(modelValue.value)
        val stream = target.startWrite()
        try { stream.write(data); target.finishWrite(stream) } catch (e: Exception) { target.failWrite(stream); throw e }
        embedding?.fill(0f); embedding = result; readyValue.value = true
        setEnabled(true)
    }
    @Synchronized fun delete() { SpeakerModel.entries.forEach { model -> SpeechLanguage.entries.forEach { file(model, it).delete() } }; embedding?.fill(0f); embedding = null; readyValue.value = false }
    fun setEnabled(value: Boolean) { preferences.edit().putBoolean("enabled", value).apply(); enabledValue.value = value }
}
