package com.sidekick.app

import android.os.Debug
import android.os.Build
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.speech.SpeechEngine
import com.sidekick.core.WavAudio
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONObject

/** Opt-in, fixture-only benchmark. Never opens the microphone or writes PCM. */
class SpeechBenchmarkTest {
    @Test fun measure() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(instrumentation.targetContext.packageName.endsWith(".benchmark"))
        val context = instrumentation.targetContext
        val engine = SpeechEngine(context)
        val fixture = instrumentation.context.assets.open("jfk.wav").use { WavAudio.decode(it.readBytes()) }
        val power = context.getSystemService(PowerManager::class.java)
        val args = InstrumentationRegistry.getArguments()
        val rounds = args.getString("rounds")?.toInt() ?: 3
        val threads = args.getString("threads")?.toInt() ?: 2
        val audioContext = args.getString("audioContext")?.toInt() ?: 0
        val retain = args.getString("retain") == "true"
        val paced = args.getString("paced") == "true"
        val sizes = args.getString("sizes")?.split(",")?.map(String::toInt) ?: listOf(3, 5, 10)
        val runId = args.getString("runId") ?: "comparison"
        require(rounds in 1..100 && sizes.all { it in 1..30 })
        var due = SystemClock.elapsedRealtime()
        val peak = AtomicInteger(0)
        val monitor = launch(Dispatchers.IO) {
            while (isActive) {
                val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
                peak.updateAndGet { maxOf(it, memory.totalPss) }
                delay(100)
            }
        }
        try {
            repeat(rounds) { round ->
                for (seconds in sizes) {
                    if (paced) { due += seconds * 1000; delay(maxOf(0, due - SystemClock.elapsedRealtime())) }
                    val samples = FloatArray(seconds * 16000) { fixture[it % fixture.size] }
                    val before = SystemClock.elapsedRealtime()
                    val backlog = if (paced) maxOf(0, before - due) else 0
                    val text = engine.transcribe(samples, threads, retain, audioContext)
                    val elapsed = SystemClock.elapsedRealtime() - before
                    val info = Debug.MemoryInfo().also(Debug::getMemoryInfo)
                    val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    Log.i("SidekickBenchmark", JSONObject().put("runId", runId).put("threads", threads).put("audioContext", audioContext)
                        .put("retain", retain).put("paced", paced).put("backlogMs", backlog)
                        .put("round", round).put("audioSeconds", seconds)
                        .put("elapsedMs", elapsed).put("rtf", elapsed / (seconds * 1000.0))
                        .put("pssKb", info.totalPss).put("sampledPeakPssKb", peak.get())
                        .put("batteryTempTenthsC", battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1))
                        .put("plugged", battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1))
                        .put("thermalStatus", if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else -1)
                        .put("text", text).toString())
                }
            }
        } finally {
            withContext(NonCancellable) { monitor.cancelAndJoin(); engine.release() }
            fixture.fill(0f)
        }
    }
}
