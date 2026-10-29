package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.core.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.text.Normalizer

/** Public and synthetic fixtures only; never opens the microphone. */
class SpeechComparisonTest {
    private fun normalized(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFKC).filter { it.isLetterOrDigit() }
    private fun distance(a: String, b: String): Int {
        var row = IntArray(b.length + 1) { it }
        a.forEachIndexed { i, x ->
            val next = IntArray(b.length + 1); next[0] = i + 1
            b.forEachIndexed { j, y -> next[j + 1] = minOf(next[j] + 1, row[j + 1] + 1, row[j] + if (x == y) 0 else 1) }
            row = next
        }
        return row.last()
    }
    @Test fun compareModelsAndShortPhraseContext() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(instrumentation.targetContext.packageName.endsWith(".benchmark"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifySpeechComparison") == "true")
        val app = instrumentation.targetContext.applicationContext as SidekickApplication
        val assets = instrumentation.context.assets
        val fixtures = assets.open("languages.json").bufferedReader().use { JSONArray(it.readText()) }
        try {
            for (quality in listOf(SpeechQuality.FAST, SpeechQuality.ACCURATE)) {
                for (i in 0 until fixtures.length()) {
                    val fixture = fixtures.getJSONObject(i)
                    val language = SpeechLanguage.fromCode(fixture.getString("code"))
                    val pcm = assets.open("${language.code}.wav").use { WavAudio.decode(it.readBytes()) }
                    val seconds = pcm.size / 16000.0
                    val start = android.os.SystemClock.elapsedRealtime()
                    val cpu = android.os.Process.getElapsedCpuTime()
                    val output = app.speech.transcribe(pcm, retainModel = true, selectedLanguage = language, selectedQuality = quality)
                    val elapsed = android.os.SystemClock.elapsedRealtime() - start
                    val cpuMs = android.os.Process.getElapsedCpuTime() - cpu
                    val memory = android.os.Debug.MemoryInfo().also { android.os.Debug.getMemoryInfo(it) }
                    val reference = normalized(fixture.getString("reference"))
                    assertTrue(output.isNotBlank())
                    println("QUALITY model=$quality; language=${language.code}; audioSec=$seconds; elapsedMs=$elapsed; cpuMs=$cpuMs; pssKb=${memory.totalPss}; CER=${distance(reference, normalized(output)).toDouble() / reference.length}; output=$output")
                }
                val party = assets.open("quality-party.wav").use { WavAudio.decode(it.readBytes()) }
                val short = assets.open("quality-short.wav").use { WavAudio.decode(it.readBytes()) }
                val context = SpeechContext()
                try {
                    val alone = app.speech.transcribe(short.copyOf(), retainModel = true, audioContext = 512, selectedLanguage = SpeechLanguage.ENGLISH, selectedQuality = quality)
                    context.prepare(party, 0, "fixture")
                    val combined = context.prepare(short, party.size.toLong() + 16000, "fixture")
                    val contextual = app.speech.transcribe(combined, retainModel = true, audioContext = 512, selectedLanguage = SpeechLanguage.ENGLISH, selectedQuality = quality)
                    println("QUALITY_SHORT model=$quality; alone=$alone; contextual=$contextual")
                    assertTrue(alone.isNotBlank() && contextual.isNotBlank())
                } finally { context.clear(); party.fill(0f); short.fill(0f) }
            }
        } finally { app.speech.release() }
    }
}
