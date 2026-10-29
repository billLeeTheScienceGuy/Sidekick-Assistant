package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.*
import com.sidekick.app.speech.SpeechEngine
import com.sidekick.core.*
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assume.assumeTrue
import org.junit.Test

class KoreanAsrComparisonTest {
    private fun normal(text: String) = text.replace("6", "여섯").replace("7", "일곱").filter { it.isLetterOrDigit() }
    private fun distance(a: String, b: String): Int {
        var row = IntArray(b.length + 1) { it }
        for ((i, x) in a.withIndex()) {
            val next = IntArray(b.length + 1); next[0] = i + 1
            for ((j, y) in b.withIndex()) next[j + 1] = minOf(next[j] + 1, row[j + 1] + 1, row[j] + if (x == y) 0 else 1)
            row = next
        }
        return row.last()
    }
    @Test fun compareActualKoreanCommandsWithPublicSpeech() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("compareKoreanAsr") == "true")
        val assets = instrumentation.context.assets
        val cases = assets.open("korean-commands.json").bufferedReader().use { JSONArray(it.readText()) }
        val languageReference = assets.open("languages.json").bufferedReader().use { JSONArray(it.readText()).getJSONObject(0).getString("reference") }
        val modelFile = File(context.cacheDir, "sensevoice-eval.onnx")
        val tokensFile = File(context.cacheDir, "sensevoice-eval-tokens.txt")
        context.assets.open("models/sensevoice-ko.int8.onnx").use { input -> modelFile.outputStream().use(input::copyTo) }
        context.assets.open("models/sensevoice-tokens.txt").use { input -> tokensFile.outputStream().use(input::copyTo) }
        val whisper = SpeechEngine(context)
        var sense: OfflineRecognizer? = null
        val now = Instant.parse("2026-10-27T17:00:00Z"); val zone = ZoneId.of("America/Chicago")
        fun semantic(text: String, index: Int): Boolean = when (index % 4) {
            0 -> ActivityTiming.extract(Transcript(text, now, zone, Speaker.OTHER, true), true).singleOrNull()?.activityStartAt?.atZone(zone)?.hour == 18
            1 -> EventChange.parse(text)?.newStart(Instant.parse("2026-10-28T23:00:00Z"), now, zone)?.atZone(zone)?.hour == 19
            2 -> EventChange.parse(text)?.cancel == true
            else -> EventChange.parse(text) == null
        }
        try {
            for (backend in listOf("tiny", "base", "sensevoice")) {
                if (backend == "sensevoice") {
                    whisper.release()
                    sense = OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = OfflineModelConfig(
                        senseVoice = OfflineSenseVoiceModelConfig(model = modelFile.absolutePath, language = "ko", useInverseTextNormalization = true),
                        tokens = tokensFile.absolutePath, numThreads = 2)))
                }
                for (i in 0..cases.length()) {
                    val name = if (i == cases.length()) "ko.wav" else cases.getJSONObject(i).getString("file")
                    val reference = if (i == cases.length()) languageReference else cases.getJSONObject(i).getString("reference")
                    val pcm = assets.open(name).use { WavAudio.decode(it.readBytes()) }
                    val start = android.os.SystemClock.elapsedRealtime()
                    val text = try {
                        if (sense != null) {
                            val stream = sense!!.createStream()
                            try { stream.acceptWaveform(pcm, 16000); sense!!.decode(stream); sense!!.getResult(stream).text }
                            finally { stream.release() }
                        } else whisper.transcribe(pcm, retainModel = true, audioContext = if (pcm.size <= 163840) 512 else 0,
                            selectedLanguage = SpeechLanguage.KOREAN, selectedQuality = if (backend == "tiny") SpeechQuality.FAST else SpeechQuality.ACCURATE)
                    } finally { pcm.fill(0f) }
                    val elapsed = android.os.SystemClock.elapsedRealtime() - start
                    val norm = normal(reference)
                    val memory = android.os.Debug.MemoryInfo().also { android.os.Debug.getMemoryInfo(it) }
                    println("KOREAN_COMPARE backend=$backend; file=$name; ms=$elapsed; pssKb=${memory.totalPss}; CER=${distance(norm, normal(text)).toDouble()/norm.length}; semantic=${i == cases.length() || semantic(text, i)}; output=$text")
                }
            }
        } finally { whisper.release(); sense?.release(); modelFile.delete(); tokensFile.delete() }
    }
}
