package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.speaker.*
import com.sidekick.core.WavAudio
import org.junit.Assert.*
import org.junit.Test

class NeuralDiarizationTest {
    @Test fun twoRecordedVoicesRunOfflineWithBoundedLatency() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".compacttest") || context.packageName.endsWith(".benchmark"))
        fun load(name: String) = instrumentation.context.assets.open(name).use { WavAudio.decode(it.readBytes()) }
        val first = load("jfk.wav"); val second = load("quality-party.wav")
        val samples = first.copyOfRange(0, 64000) + FloatArray(8000) + second.copyOfRange(0, minOf(second.size, 80000))
        val engine = NeuralDiarizer(context) { SpeakerModel.CAMPPLUS }
        try {
            val started = android.os.SystemClock.elapsedRealtime()
            val segments = engine.process(samples)
            val elapsed = android.os.SystemClock.elapsedRealtime() - started
            println("DIARIZATION_PROBE durationMs=${samples.size * 1000L / 16000} elapsedMs=$elapsed segments=$segments")
            assertTrue(segments.isNotEmpty())
            assertTrue(segments.all { it.start.isFinite() && it.end.isFinite() && it.start >= 0 && it.end > it.start && it.end <= samples.size / 16000f + .1f })
            assertTrue("Expected two distinct voices: $segments", segments.map { it.speaker }.toSet().size >= 2)
            assertTrue("Diarization probe exceeded 15 seconds: $elapsed", elapsed < 15_000)
        } finally { engine.close(); first.fill(0f); second.fill(0f); samples.fill(0f) }
    }
}
