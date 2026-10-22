package com.sidekick.app

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.core.WavAudio
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

object VadBenchmarkNative {
    init { System.loadLibrary("sidekick_speech") }
    external fun measure(path: String, pcm: FloatArray): DoubleArray?
}

class VadBenchmarkTest {
    @Test fun compareSilenceAndSpeech() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(instrumentation.targetContext.packageName.endsWith(".benchmark"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("vad") == "true")
        val model = File(instrumentation.targetContext.noBackupFilesDir, "benchmark-vad.bin")
        val bytes = instrumentation.context.assets.open("ggml-silero-v5.1.2.bin").use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("29940d98d42b91fbd05ce489f3ecf7c72f0a42f027e4875919a28fb4c04ea2cf", hash)
        model.writeBytes(bytes)
        val fixture = instrumentation.context.assets.open("jfk.wav").use { WavAudio.decode(it.readBytes()) }
        try {
            for (seconds in listOf(1, 5, 10)) for (speech in listOf(false, true)) {
                val samples = FloatArray(seconds * 16000) { if (speech) fixture[it % fixture.size] else 0f }
                val result = requireNotNull(VadBenchmarkNative.measure(model.absolutePath, samples))
                samples.fill(0f)
                Log.i("SidekickVadBenchmark", JSONObject().put("audioSeconds", seconds).put("speech", speech)
                    .put("loadMs", result[0]).put("meanMs", result[1]).put("maxMs", result[2])
                    .put("speechFrameFraction", result[3]).put("frames", result[4]).toString())
                if (!speech) assertEquals(0.0, result[3], .01)
                if (speech && seconds == 10) assertTrue(result[3] > .5)
            }
        } finally { fixture.fill(0f); model.delete() }
    }
}
