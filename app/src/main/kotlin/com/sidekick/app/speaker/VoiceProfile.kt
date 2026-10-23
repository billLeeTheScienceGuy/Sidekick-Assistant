package com.sidekick.app.speaker

import android.content.Context
import android.util.AtomicFile
import com.sidekick.core.VoiceMatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Only normalized embeddings persist, inside no-backup private storage. */
class VoiceProfile(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "voice-profile.json"))
    private val preferences = context.getSharedPreferences("voice", Context.MODE_PRIVATE)
    private var embedding: FloatArray? = load()
    private val readyValue = MutableStateFlow(embedding != null)
    val ready = readyValue.asStateFlow()
    private val enabledValue = MutableStateFlow(preferences.getBoolean("enabled", true))
    val enabled = enabledValue.asStateFlow()
    private fun load(): FloatArray? = runCatching {
        val data = JSONObject(file.openRead().bufferedReader().use { it.readText() })
        require(data.getString("model") == SpeakerEngine.HASH)
        val values = data.getJSONArray("embedding")
        VoiceMatch.normalized(FloatArray(values.length()) { values.getDouble(it).toFloat() })
    }.getOrNull()
    @Synchronized fun snapshot(): FloatArray? = if (enabledValue.value) embedding?.copyOf() else null
    @Synchronized fun save(samples: List<FloatArray>) {
        require(samples.size == 3)
        val vectors = samples.map(VoiceMatch::normalized)
        require(vectors.indices.all { i -> vectors.indices.all { j -> VoiceMatch.similarity(vectors[i], vectors[j]) >= .6f } }) {
            "Voice samples differed too much. Try again alone in a quiet room."
        }
        val result = VoiceMatch.normalized(FloatArray(256) { i -> vectors.sumOf { it[i].toDouble() }.toFloat() / 3 })
        val data = JSONObject().put("model", SpeakerEngine.HASH).put("embedding", JSONArray(result.toList())).toString().toByteArray()
        val stream = file.startWrite()
        try { stream.write(data); file.finishWrite(stream) } catch (e: Exception) { file.failWrite(stream); throw e }
        embedding?.fill(0f); embedding = result; readyValue.value = true
        setEnabled(true)
    }
    @Synchronized fun delete() { file.delete(); embedding?.fill(0f); embedding = null; readyValue.value = false }
    fun setEnabled(value: Boolean) { preferences.edit().putBoolean("enabled", value).apply(); enabledValue.value = value }
}
