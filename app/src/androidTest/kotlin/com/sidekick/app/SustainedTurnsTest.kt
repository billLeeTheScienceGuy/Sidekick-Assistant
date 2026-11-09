package com.sidekick.app

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.audio.LiveSpeechProcessor
import com.sidekick.app.speaker.*
import com.sidekick.app.speech.*
import com.sidekick.core.*
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.*

/** Explicit opt-in controlled playback; never opens AudioRecord or the personal app database. */
class SustainedTurnsTest {
    @Test fun pacedRecordedTurnsKeepTheirQueueAndMemoryBounded() = runBlocking<Unit> {
        val i = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("runSustained") == "true")
        val seconds = InstrumentationRegistry.getArguments().getString("sustainedSeconds")?.toIntOrNull() ?: 600
        require(seconds in 60..3600)
        val root = i.targetContext
        check(root.packageName.endsWith(".compacttest") || root.packageName.endsWith(".benchmark"))
        val id = java.util.UUID.randomUUID().toString()
        val folder = File(root.cacheDir, "sustained-turns-$id").apply { mkdirs() }
        val context = object : ContextWrapper(root) {
            override fun getNoBackupFilesDir() = folder
            override fun getSharedPreferences(name: String, mode: Int) = root.getSharedPreferences("sustained-$id-$name", mode)
        }
        fun load(name: String) = i.context.assets.open(name).use { WavAudio.decode(it.readBytes()) }
        val own = load("jfk.wav"); val other = load("quality-party.wav")
        val cycle = own.copyOfRange(0, 96_000) + other.copyOfRange(0, minOf(other.size, 64_000))
        val profile = VoiceProfile(context); profile.selectModel(SpeakerModel.CAMPPLUS)
        val speaker = SpeakerEngine(context, profile)
        val turns = SpeakerTurnEngine(context, profile, speaker)
        val speech = SpeechEngine(context)
        val memory = TemporaryMemory()
        val power = root.getSystemService(android.os.PowerManager::class.java)
        val wake = power.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "SideKick:SustainedFixture")
        var attribution = SpeakerTurnEngine.Attribution(Speaker.UNKNOWN, "spk_test_unassigned", "Unassigned", "")
        val batches = mutableListOf<Pair<Long, Int>>()
        val observations = JSONArray()
        var partyHits = 0; var falseOwnerParty = 0; var emitted = 0
        val begin = SystemClock.elapsedRealtime(); val captured = Instant.now(); val zone = ZoneId.systemDefault()
        var finished = false; var failure: String? = null; var feedFinishedAt = begin; var drainMs = 0L
        val pipeline = LiveSpeechProcessor(speech, contextKey = { if (attribution.speaker == Speaker.USER) attribution.key else null },
            partition = turns::partition, onPart = { samples, part -> attribution = turns.classify(samples, part) },
            onChunkProcessed = { elapsed, count ->
                batches += elapsed to count
                val thermal = if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else -1
                val battery = root.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val temperature = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
                val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
                val pss = Debug.getPss()
                observations.put(JSONObject().put("elapsedMs", SystemClock.elapsedRealtime() - begin).put("batchMs", elapsed)
                    .put("audioSamples", count).put("processPssKiB", pss).put("thermalStatus", thermal).put("batteryTemperatureTenthsC", temperature).put("plugged", plugged))
                check(plugged != 0) { "Controlled USB trial stopped after unplugging" }
                check(thermal < android.os.PowerManager.THERMAL_STATUS_SEVERE) { "Thermal safety limit reached" }
                check(pss < 1_500_000) { "Process memory safety limit reached" }
            }) { text, start, _ ->
            emitted++
            if (text.contains("party", true)) { partyHits++; if (attribution.speaker == Speaker.USER) falseOwnerParty++ }
            memory.appendAttributedLive(text, captured.plusMillis(start * 1000 / 16000), zone, attribution.speaker, false, attribution.key, attribution.label)
        }
        try {
            val references = listOf(0, 32_000, 64_000).map { start ->
                val window = own.copyOfRange(start, start + 80_000)
                try { speaker.embedding(window) } finally { window.fill(0f) }
            }
            try { profile.save(references) } finally { references.forEach { it.fill(0f) } }
            NativeVad.open(VadModel.install(context))
            wake.acquire((seconds + 120) * 1000L)
            coroutineScope {
                val worker = launch(Dispatchers.Default) { pipeline.consume() }
                var sent = 0L
                val target = seconds * 16000L
                while (sent < target) {
                    val offset = (sent % cycle.size).toInt()
                    val count = minOf(4096, cycle.size - offset, (target - sent).toInt())
                    val frame = cycle.copyOfRange(offset, offset + count)
                    try { pipeline.accept(frame) } finally { frame.fill(0f) }
                    sent += count
                    delay(count * 1000L / 16000L)
                }
                feedFinishedAt = SystemClock.elapsedRealtime()
                pipeline.finish(); withTimeout(60_000) { worker.join() }
                drainMs = SystemClock.elapsedRealtime() - feedFinishedAt
            }
            assertTrue(emitted > 0); assertTrue(partyHits > 0)
            assertEquals("Other source must never become the owner party", 0, falseOwnerParty)
            assertTrue(memory.speakerTurns.value.size <= 12)
            assertTrue(memory.text.value.length <= 6000)
            val workRatio = batches.sumOf { it.first } / (seconds * 1000.0)
            assertTrue("Total processing exceeded the real-time input budget: $workRatio", workRatio < 1.0)
            assertTrue("The final queue did not drain within one chunk", drainMs < 10_000)
            finished = true
        } catch (e: Throwable) { failure = e.javaClass.simpleName; throw e }
        finally {
            pipeline.close(); turns.close(); speaker.close(); profile.delete(); NativeVad.close()
            own.fill(0f); other.fill(0f); cycle.fill(0f); memory.discard()
            if (wake.isHeld) wake.release()
            folder.deleteRecursively(); root.deleteSharedPreferences("sustained-$id-voice")
            val report = JSONObject().put("device", Build.MODEL).put("sdk", Build.VERSION.SDK_INT)
                .put("requestedAudioSeconds", seconds).put("wallMs", SystemClock.elapsedRealtime() - begin)
                .put("completed", finished).put("failure", failure).put("emittedSegments", emitted)
                .put("processingWorkMs", batches.sumOf { it.first }).put("queueDrainMs", drainMs)
                .put("partyKeywordSegments", partyHits).put("falseOwnerPartySegments", falseOwnerParty)
                .put("conditions", "USB-connected controlled public/synthetic playback; no microphone, no battery-drain claim")
                .put("observations", observations)
            File(root.filesDir, "sustained-turns.json").writeText(report.toString(2))
            println("SUSTAINED_RESULT completed=$finished observations=${observations.length()} falseOwnerParty=$falseOwnerParty")
        }
    }
}
