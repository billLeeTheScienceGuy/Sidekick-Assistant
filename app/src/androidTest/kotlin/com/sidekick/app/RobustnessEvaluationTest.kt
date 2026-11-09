package com.sidekick.app

import android.content.Context
import android.content.ContextWrapper
import android.os.Debug
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.speaker.*
import com.sidekick.app.speech.SpeechEngine
import com.sidekick.app.speech.OmniSpeechEngine
import com.sidekick.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.Random
import kotlin.math.*

/** Opt-in recorded public-speech evaluation. Never opens AudioRecord or the personal app. */
class RobustnessEvaluationTest {
    private val i get() = InstrumentationRegistry.getInstrumentation()
    private val root get() = i.targetContext
    private fun enabled() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runRobustness") == "true")
        check(root.packageName.endsWith(".benchmark"))
    }
    private fun manifest() = i.context.assets.open("robustness.json").bufferedReader().use { JSONObject(it.readText()) }
    private fun guard() {
        val power = root.getSystemService(android.os.PowerManager::class.java)
        check(android.os.Build.VERSION.SDK_INT < 29 || power.currentThermalStatus < android.os.PowerManager.THERMAL_STATUS_SEVERE) { "Thermal limit reached" }
    }
    private fun audio(clip: JSONObject): FloatArray {
        val data = i.context.assets.open(clip.getString("file")).use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
        check(hash == clip.getString("sha256")) { "Public fixture changed" }
        return WavAudio.decode(data).also { check(it.size in 48_000..160_000) }
    }
    private fun noisy(audio: FloatArray, seed: Long, db: Double = 10.0): FloatArray {
        val random = Random(seed)
        val noise = FloatArray(audio.size) { random.nextFloat() - .5f }
        val ratio = sqrt(audio.sumOf { it.toDouble() * it } / noise.sumOf { it.toDouble() * it }) / 10.0.pow(db / 20)
        return try { normalizePeak(FloatArray(audio.size) { (audio[it] + ratio * noise[it]).toFloat() }) }
        finally { noise.fill(0f) }
    }
    private fun normalizePeak(audio: FloatArray): FloatArray {
        val peak = audio.maxOf { abs(it) }
        if (peak > .95f) for (index in audio.indices) audio[index] *= .95f / peak
        return audio
    }
    private fun normalized(text: String) = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFKC)
        .codePoints().toArray().filter { Character.isLetterOrDigit(it) }.toIntArray()
    private fun edits(a: IntArray, b: IntArray): Int {
        var previous = IntArray(b.size + 1) { it }
        for (index in a.indices) {
            val current = IntArray(b.size + 1); current[0] = index + 1
            for (j in b.indices) current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (a[index] == b[j]) 0 else 1)
            previous = current
        }
        return previous.last()
    }

    @Test fun publicReadingsAndDeterministicNoiseMeasureProductionAdaptiveRecognition() = runBlocking<Unit> {
        enabled()
        var language = SpeechLanguage.ENGLISH
        val speech = SpeechEngine(root, { language }, { SpeechQuality.ADAPTIVE })
        val results = JSONArray(); val source = manifest()
        var completed = false
        try {
            val clips = source.getJSONArray("clips")
            for (index in 0 until clips.length()) {
                val clip = clips.getJSONObject(index)
                language = SpeechLanguage.fromCode(clip.getString("code"))
                val original = audio(clip)
                try {
                    for (condition in listOf("clean", "white-noise-10dB")) {
                        guard()
                        val samples = if (condition == "clean") original.copyOf() else noisy(original, index + 20261109L)
                        val short = samples.size < speech.shortBoostLimit()
                        val start = SystemClock.elapsedRealtime()
                        val text = speech.transcribe(samples, retainModel = true, audioContext = speech.liveContext(short), shortPhrase = short)
                        val elapsed = SystemClock.elapsedRealtime() - start
                        assertTrue("Native inputs must be wiped", samples.all { it == 0f })
                        val reference = normalized(clip.getString("reference")); val actual = normalized(text)
                        results.put(JSONObject().put("file", clip.getString("file")).put("language", language.code)
                            .put("backend", speech.lastBackend)
                            .put("condition", condition).put("reference", clip.getString("reference")).put("recognized", text)
                            .put("referenceCharacters", reference.size).put("characterEdits", edits(reference, actual))
                            .put("audioMs", original.size * 1000L / 16000).put("elapsedMs", elapsed).put("processPssKiB", Debug.getPss()))
                        println("ROBUST_ASR ${language.code} $condition file=${clip.getString("file")} elapsedMs=$elapsed")
                    }
                } finally { original.fill(0f) }
            }
            completed = true
        } finally {
            speech.release()
            File(root.filesDir, "robust-asr.json").writeText(JSONObject().put("device", android.os.Build.MODEL)
                .put("completed", completed).put("dataset", source).put("results", results)
                .put("conditions", "USB-connected public validation readings; deterministic white noise; no microphone or population accuracy claim").toString(2))
        }
    }

    @Test fun candidateOmnilingualForEuropeanReadings() {
        enabled()
        val engine = OmniSpeechEngine(root); val source = manifest(); val results = JSONArray()
        var completed = false
        try {
            val clips = source.getJSONArray("clips")
            for (index in 0 until clips.length()) {
                val clip = clips.getJSONObject(index)
                if (clip.getString("code") !in setOf("fr", "de", "ru")) continue
                val original = audio(clip)
                try {
                    for (condition in listOf("clean", "white-noise-10dB")) {
                        guard()
                        val samples = if (condition == "clean") original.copyOf() else noisy(original, index + 20261109L)
                        val start = SystemClock.elapsedRealtime()
                        val text = try { engine.transcribe(samples, 2, true) } finally { samples.fill(0f) }
                        val reference = normalized(clip.getString("reference")); val actual = normalized(text)
                        results.put(JSONObject().put("file", clip.getString("file")).put("language", clip.getString("code"))
                            .put("condition", condition).put("reference", clip.getString("reference")).put("recognized", text)
                            .put("referenceCharacters", reference.size).put("characterEdits", edits(reference, actual))
                            .put("audioMs", original.size * 1000L / 16000).put("elapsedMs", SystemClock.elapsedRealtime() - start).put("processPssKiB", Debug.getPss()))
                    }
                } finally { original.fill(0f) }
            }
            completed = true
        } finally {
            engine.release()
            File(root.filesDir, "robust-candidate.json").writeText(JSONObject().put("device", android.os.Build.MODEL)
                .put("completed", completed).put("dataset", source).put("results", results)
                .put("conditions", "Candidate model comparison on the same public clips and noise seeds; not an independent holdout").toString(2))
        }
    }

    @Test fun automaticWhisperAndOmnilingualComparison() = runBlocking<Unit> {
        enabled()
        val source = manifest(); val results = JSONArray(); val clips = source.getJSONArray("clips")
        var completed = false
        val whisper = SpeechEngine(root, { SpeechLanguage.AUTO }, { SpeechQuality.FAST })
        val omni = OmniSpeechEngine(root)
        try {
            for (backend in listOf("whisper-auto", "omni-auto")) {
                for (index in 0 until clips.length()) {
                    val clip = clips.getJSONObject(index); val original = audio(clip)
                    try {
                        for (condition in listOf("clean", "white-noise-10dB")) {
                            guard()
                            val samples = if (condition == "clean") original.copyOf() else noisy(original, index + 20261109L)
                            val start = SystemClock.elapsedRealtime()
                            val text = try {
                                if (backend == "whisper-auto") withTimeout(60_000) { whisper.transcribe(samples, retainModel = true, audioContext = 0) }
                                else omni.transcribe(samples, 2, true)
                            } finally { samples.fill(0f) }
                            val reference = normalized(clip.getString("reference"))
                            results.put(JSONObject().put("file", clip.getString("file")).put("language", clip.getString("code"))
                                .put("condition", condition).put("backend", backend).put("reference", clip.getString("reference")).put("recognized", text)
                                .put("referenceCharacters", reference.size).put("characterEdits", edits(reference, normalized(text)))
                                .put("audioMs", original.size * 1000L / 16000).put("elapsedMs", SystemClock.elapsedRealtime() - start).put("processPssKiB", Debug.getPss()))
                            println("AUTO_EVAL $backend ${clip.getString("code")} $condition ${clip.getString("file")}")
                        }
                    } finally { original.fill(0f) }
                }
                whisper.release(); omni.release()
            }
            completed = true
        } finally {
            whisper.release(); omni.release()
            File(root.filesDir, "robust-auto.json").writeText(JSONObject().put("device", android.os.Build.MODEL)
                .put("completed", completed).put("dataset", source).put("results", results)
                .put("conditions", "No forced language; public clean/noisy recordings, one language per clip; not continuous code switching").toString(2))
        }
    }

    @Test fun unfamiliarPublicVoicesAndBalancedOverlapDoNotBecomeTheEnrolledOwner() {
        enabled()
        val id = java.util.UUID.randomUUID().toString()
        val folder = File(root.cacheDir, "robust-voice-$id").apply { mkdirs() }
        val context = object : ContextWrapper(root) {
            override fun getNoBackupFilesDir() = folder
            override fun getSharedPreferences(name: String, mode: Int) = root.getSharedPreferences("robust-$id-$name", mode)
        }
        val profile = VoiceProfile(context); profile.selectModel(SpeakerModel.CAMPPLUS)
        val speaker = SpeakerEngine(context, profile)
        val engine = SpeakerTurnEngine(context, profile, speaker)
        val own = i.context.assets.open("jfk.wav").use { WavAudio.decode(it.readBytes()) }
        val results = JSONArray(); var completed = false
        fun evaluate(name: String, condition: String, samples: FloatArray, expectedOwner: Boolean) {
            guard()
            val start = SystemClock.elapsedRealtime()
            try {
                val parts = engine.partition(samples)
                for (part in parts) {
                    val window = samples.copyOfRange(part.start, part.end)
                    try {
                        val attribution = engine.classify(window, part)
                        results.put(JSONObject().put("file", name).put("condition", condition).put("expectedOwner", expectedOwner)
                            .put("speaker", attribution.speaker.name).put("ambiguous", part.ambiguous)
                            .put("start", part.start).put("end", part.end).put("diagnostic", attribution.diagnostic)
                            .put("elapsedMs", SystemClock.elapsedRealtime() - start))
                    } finally { window.fill(0f) }
                }
            } finally { samples.fill(0f) }
        }
        try {
            val references = listOf(0, 32_000, 64_000).map { start ->
                val window = own.copyOfRange(start, start + 80_000)
                try { speaker.embedding(window) } finally { window.fill(0f) }
            }
            try { profile.save(references) } finally { references.forEach { it.fill(0f) } }
            evaluate("jfk.wav", "clean-owner", own.copyOfRange(0, 96_000), true)
            evaluate("jfk.wav", "quiet-owner", FloatArray(96_000) { own[it] * .3f }, true)
            evaluate("jfk.wav", "noisy-owner-10dB", noisy(own.copyOfRange(0, 96_000), 41), true)
            val clips = manifest().getJSONArray("clips")
            for (index in 0 until clips.length()) {
                val clip = clips.getJSONObject(index); val other = audio(clip); val name = clip.getString("file")
                try {
                    evaluate(name, "unfamiliar-clean", other.copyOf(), false)
                    evaluate(name, "unfamiliar-noise-10dB", noisy(other, index + 51L), false)
                    val length = minOf(96_000, other.size)
                    val ownEnergy = (0 until length).sumOf { own[it].toDouble().pow(2) }
                    val otherEnergy = (0 until length).sumOf { other[it].toDouble().pow(2) }
                    val balance = sqrt(ownEnergy / otherEnergy)
                    evaluate(name, "simultaneous-equal-energy", normalizePeak(FloatArray(length) { (own[it] + balance * other[it]).toFloat() }), false)
                } finally { other.fill(0f) }
            }
            completed = true
            val falseOwners = (0 until results.length()).map { results.getJSONObject(it) }.filter { !it.getBoolean("expectedOwner") && it.getString("speaker") == "USER" }
            assertTrue("Unfamiliar/overlapping source was assigned to the owner: ${falseOwners.take(3)}", falseOwners.isEmpty())
        } finally {
            File(root.filesDir, "robust-speakers.json").writeText(JSONObject().put("device", android.os.Build.MODEL)
                .put("completed", completed).put("results", results).put("conditions", "Public JFK enrollment, unrelated modern FLEURS readings; one enrolled identity, no population accuracy claim").toString(2))
            engine.close(); speaker.close(); profile.delete(); own.fill(0f); folder.deleteRecursively()
            root.deleteSharedPreferences("robust-$id-voice")
        }
    }
}
