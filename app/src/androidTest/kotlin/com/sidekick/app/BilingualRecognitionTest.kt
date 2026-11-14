package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.speech.*
import com.sidekick.core.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** Same fixtures/test APK can run against the previous and new isolated app APKs. No microphone. */
class BilingualRecognitionTest {
    @Test fun shortLanguageSwitchesPreserveBothLanguagesAndMeasureErrors() = runBlocking<Unit> {
        val i = InstrumentationRegistry.getInstrumentation()
        val context = i.targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("runRobustness") == "true")
        val source = i.context.assets.open("bilingual.json").bufferedReader().use { JSONObject(it.readText()) }
        val clips = source.getJSONArray("clips"); val results = JSONArray()
        val engine = SpeechEngine(context, { SpeechLanguage.AUTO }, { SpeechQuality.ADAPTIVE })
        fun normalized(text: String) = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFKC)
            .codePoints().toArray().filter { Character.isLetterOrDigit(it) }.toIntArray()
        fun edits(a: IntArray, b: IntArray): Int {
            var previous = IntArray(b.size + 1) { it }
            for (index in a.indices) {
                val current = IntArray(b.size + 1); current[0] = index + 1
                for (j in b.indices) current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (a[index] == b[j]) 0 else 1)
                previous = current
            }
            return previous.last()
        }
        var complete = false
        try {
            for (index in 0 until clips.length()) {
                val clip = clips.getJSONObject(index)
                val data = i.context.assets.open(clip.getString("file")).use { it.readBytes() }
                val hash = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
                check(hash == clip.getString("sha256"))
                val samples = WavAudio.decode(data)
                val power = context.getSystemService(android.os.PowerManager::class.java)
                check(android.os.Build.VERSION.SDK_INT < 29 || power.currentThermalStatus < android.os.PowerManager.THERMAL_STATUS_SEVERE)
                val start = android.os.SystemClock.elapsedRealtime()
                val text = engine.transcribe(samples, retainModel = true)
                assertTrue(samples.all { it == 0f }); assertTrue(text.isNotBlank())
                val reference = normalized(clip.getString("reference"))
                results.put(JSONObject().put("file", clip.getString("file")).put("reference", clip.getString("reference"))
                    .put("recognized", text).put("backend", engine.lastBackend).put("referenceCharacters", reference.size)
                    .put("characterEdits", edits(reference, normalized(text))).put("venueKept", text.contains("james", true))
                    .put("audioMs", clip.getLong("audioMs")).put("elapsedMs", android.os.SystemClock.elapsedRealtime() - start))
            }
            complete = true
        } finally {
            engine.release()
            File(context.filesDir, "bilingual-recognition.json").writeText(JSONObject().put("completed", complete)
                .put("conditions", source.getString("conditions")).put("results", results).toString(2))
        }
        if (InstrumentationRegistry.getArguments().getString("runSenseCandidate") == "true") {
            val candidateResults = JSONArray()
            val model = ModelPacks.ensure(context, SenseVoiceEngine.MODEL, SenseVoiceEngine.MODEL_HASH)
            val tokens = ModelPacks.ensure(context, SenseVoiceEngine.TOKENS, SenseVoiceEngine.TOKENS_HASH)
            val candidate = com.k2fsa.sherpa.onnx.OfflineRecognizer(config = com.k2fsa.sherpa.onnx.OfflineRecognizerConfig(
                modelConfig = com.k2fsa.sherpa.onnx.OfflineModelConfig(
                    senseVoice = com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig(model = model.absolutePath, language = "auto", useInverseTextNormalization = true),
                    tokens = tokens.absolutePath, numThreads = 2)))
            try {
                for (index in 0 until clips.length()) {
                    val clip = clips.getJSONObject(index)
                    if (clip.getJSONArray("languages").getString(1) !in setOf("ko", "zh")) continue
                    val samples = i.context.assets.open(clip.getString("file")).use { WavAudio.decode(it.readBytes()) }
                    val stream = candidate.createStream(); val start = android.os.SystemClock.elapsedRealtime()
                    val text = try {
                        stream.acceptWaveform(samples, 16000); candidate.decode(stream)
                        candidate.getResult(stream).text.replace(Regex("<\\|[^|>]+\\|>"), "").trim()
                    } finally { stream.release(); samples.fill(0f) }
                    val reference = normalized(clip.getString("reference"))
                    candidateResults.put(JSONObject().put("file", clip.getString("file")).put("reference", clip.getString("reference"))
                        .put("recognized", text).put("referenceCharacters", reference.size).put("characterEdits", edits(reference, normalized(text)))
                        .put("venueKept", text.contains("james", true)).put("elapsedMs", android.os.SystemClock.elapsedRealtime() - start))
                }
            } finally {
                candidate.release()
                File(context.filesDir, "bilingual-candidate.json").writeText(JSONObject().put("results", candidateResults)
                    .put("conditions", "SenseVoice auto candidate; four concatenated synthetic English/Korean or Chinese clips, not natural accuracy").toString(2))
            }
        }
    }
}
