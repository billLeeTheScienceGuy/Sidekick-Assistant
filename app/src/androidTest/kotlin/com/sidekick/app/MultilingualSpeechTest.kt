package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.core.SpeechLanguage
import com.sidekick.core.WavAudio
import kotlinx.coroutines.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.text.Normalizer

/** Opt-in public-fixture quality measurement, no microphone access. */
class MultilingualSpeechTest {
    @Test fun pacedAutomaticKoreanLivePipelineDoesNotOverflow() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(instrumentation.targetContext.packageName.endsWith(".benchmark"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyLanguages") == "true")
        val app = instrumentation.targetContext.applicationContext as SidekickApplication
        val previous = app.guidance.speechLanguage.value
        val pcm = instrumentation.context.assets.open("ko.wav").use { WavAudio.decode(it.readBytes()) }
        val results = mutableListOf<String>()
        val latencies = mutableListOf<Long>()
        com.sidekick.app.speech.NativeVad.open(com.sidekick.app.speech.VadModel.install(app))
        val pipeline = com.sidekick.app.audio.LiveSpeechProcessor(app.speech) { text, _, latency -> results += text; latencies += latency }
        try {
            app.guidance.setSpeechLanguage(SpeechLanguage.AUTO)
            coroutineScope {
                val worker = launch(Dispatchers.IO) { pipeline.consume() }
                for (start in pcm.indices step 4096) {
                    val frame = pcm.copyOfRange(start, minOf(start + 4096, pcm.size))
                    try { pipeline.accept(frame) } finally { frame.fill(0f) }
                    delay(256)
                }
                pipeline.finish()
                withTimeout(30_000) { worker.join() }
            }
            assertTrue(results.joinToString(" ").any { it in '\uAC00'..'\uD7AF' })
            println("MULTILINGUAL_LIVE mode=auto; source=ko; chunks=${results.size}; maxLatencyMs=${latencies.maxOrNull()}")
        } finally { pipeline.close(); pcm.fill(0f); app.guidance.setSpeechLanguage(previous); com.sidekick.app.speech.NativeVad.close() }
    }
    private fun normalized(text: String) = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKC)
        .filter { it.isLetterOrDigit() }
    private fun distance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        a.forEachIndexed { i, x ->
            val current = IntArray(b.length + 1); current[0] = i + 1
            b.forEachIndexed { j, y -> current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (x == y) 0 else 1) }
            previous = current
        }
        return previous.last()
    }
    @Test fun transcribesFiveLanguagesLocallyAndReportsReferenceError() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(instrumentation.targetContext.packageName.endsWith(".benchmark"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyLanguages") == "true")
        val app = instrumentation.targetContext.applicationContext as SidekickApplication
        val fixtures = instrumentation.context.assets.open("languages.json").bufferedReader().use { JSONArray(it.readText()) }
        try {
            for (i in 0 until fixtures.length()) {
                val fixture = fixtures.getJSONObject(i)
                val language = SpeechLanguage.fromCode(fixture.getString("code"))
                val pcm = instrumentation.context.assets.open("${language.code}.wav").use { WavAudio.decode(it.readBytes()) }
                val seconds = pcm.size / 16000.0
                val start = android.os.SystemClock.elapsedRealtime()
                val text = app.speech.transcribe(pcm, retainModel = true, selectedLanguage = language)
                val elapsed = android.os.SystemClock.elapsedRealtime() - start
                val reference = normalized(fixture.getString("reference"))
                val actual = normalized(text)
                assertTrue("No output for ${language.code}", actual.isNotEmpty())
                val error = distance(reference, actual).toDouble() / reference.length
                println("MULTILINGUAL code=${language.code}; seconds=$seconds; elapsedMs=$elapsed; normalizedCER=$error; output=$text")
            }
            val korean = instrumentation.context.assets.open("ko.wav").use { WavAudio.decode(it.readBytes()) }
            val autoKorean = app.speech.transcribe(korean, retainModel = true, selectedLanguage = SpeechLanguage.AUTO)
            println("MULTILINGUAL autoKorean=$autoKorean")
            assertTrue("Auto mode should retain Korean script", autoKorean.any { it in '\uAC00'..'\uD7AF' })
            val english = instrumentation.context.assets.open("jfk.wav").use { WavAudio.decode(it.readBytes()) }
            val auto = app.speech.transcribe(english, retainModel = true, selectedLanguage = SpeechLanguage.AUTO)
            assertTrue(auto.contains("country", true))
            val again = instrumentation.context.assets.open("jfk.wav").use { WavAudio.decode(it.readBytes()) }
            assertTrue(app.speech.transcribe(again, selectedLanguage = SpeechLanguage.ENGLISH).contains("country", true))
        } finally { app.speech.release() }
    }
}
