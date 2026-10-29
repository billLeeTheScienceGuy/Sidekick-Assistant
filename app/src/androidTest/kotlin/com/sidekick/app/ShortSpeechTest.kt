package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.speech.SpeechEngine
import com.sidekick.core.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Reproducible synthetic clips; does not open the microphone. */
class ShortSpeechTest {
    @Test fun adaptiveRecognizesShortCommandsAndPreservesNegation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(instrumentation.targetContext.packageName.endsWith(".benchmark"))
        val speech = SpeechEngine(instrumentation.targetContext)
        fun normalize(value: String) = value.lowercase().replace("nine", "9").filter { it.isLetterOrDigit() }
        val fixtures = instrumentation.context.assets.open("commands.json").bufferedReader().use { JSONArray(it.readText().removePrefix("\uFEFF")) }
        try {
            for (i in 0 until fixtures.length()) {
                val fixture = fixtures.getJSONObject(i)
                val pcm = instrumentation.context.assets.open(fixture.getString("file")).use { WavAudio.decode(it.readBytes()) }
                val start = android.os.SystemClock.elapsedRealtime()
                val output = speech.transcribe(pcm, retainModel = true, audioContext = 512, selectedQuality = SpeechQuality.ADAPTIVE)
                println("ADAPTIVE file=${fixture.getString("file")}; ms=${android.os.SystemClock.elapsedRealtime() - start}; output=$output")
                assertEquals(fixture.getString("file"), normalize(fixture.getString("reference")), normalize(output))
                if (fixture.getString("reference").startsWith("Do not")) assertNull(EventChange.parse(output))
            }
        } finally { speech.release() }
    }
    @Test fun compareShortPhraseRescue() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(instrumentation.targetContext.packageName.endsWith(".benchmark"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyShortRescue") == "true")
        val speech = SpeechEngine(instrumentation.targetContext)
        try {
            for (name in listOf("command-0-0.wav", "command-1-0.wav", "command-0-3.wav", "command-1-3.wav")) {
                val pcm = instrumentation.context.assets.open(name).use { WavAudio.decode(it.readBytes()) }
                try {
                    for ((quality, padded) in listOf(SpeechQuality.FAST to true, SpeechQuality.ACCURATE to false, SpeechQuality.ACCURATE to true)) {
                        val input = if (padded) pcm.copyOf(maxOf(pcm.size, 64_000)) else pcm.copyOf()
                        val start = android.os.SystemClock.elapsedRealtime()
                        val output = speech.transcribe(input, retainModel = true, audioContext = 512, selectedQuality = quality)
                        println("RESCUE file=$name; quality=$quality; pad=$padded; ms=${android.os.SystemClock.elapsedRealtime() - start}; output=$output")
                        assertTrue(output.isNotBlank())
                    }
                } finally { pcm.fill(0f) }
            }
        } finally { speech.release() }
    }
    @Test fun compareShortCommandDecoders() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(instrumentation.targetContext.packageName.endsWith(".benchmark"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyShortSpeech") == "true")
        val speech = SpeechEngine(instrumentation.targetContext)
        val fixtures = instrumentation.context.assets.open("commands.json").bufferedReader().use { JSONArray(it.readText().removePrefix("\uFEFF")) }
        try {
            for (i in 0 until fixtures.length()) {
                val fixture = fixtures.getJSONObject(i)
                val pcm = instrumentation.context.assets.open(fixture.getString("file")).use { WavAudio.decode(it.readBytes()) }
                try {
                    for ((beam, encoder) in listOf(1 to 512, 3 to 512, 3 to 0)) {
                        val start = android.os.SystemClock.elapsedRealtime()
                        val output = speech.transcribe(pcm.copyOf(), retainModel = true, audioContext = encoder, beamSize = beam)
                        println("SHORT file=${fixture.getString("file")}; ref=${fixture.getString("reference")}; beam=$beam; encoder=$encoder; ms=${android.os.SystemClock.elapsedRealtime() - start}; output=$output")
                        assertTrue(output.isNotBlank())
                    }
                } finally { pcm.fill(0f) }
            }
        } finally { speech.release() }
    }
}
