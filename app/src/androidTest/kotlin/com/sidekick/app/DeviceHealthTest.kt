package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.audio.LiveSpeechProcessor
import com.sidekick.app.speaker.SpeakerEngine
import com.sidekick.app.speech.NativeVad
import com.sidekick.app.speech.SpeechEngine
import com.sidekick.app.speech.VadModel
import com.sidekick.core.Speaker
import com.sidekick.core.SpeechLanguage
import com.sidekick.core.SpeechQuality
import com.sidekick.core.WavAudio
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Unattended device checks on the app's real recognition path. Opt-in: `-e deviceHealth <part>` with part =
 * energy | heat | memory | speaker | falsetrigger. Clips come from files/evaluation/<set>/ (manifest.tsv);
 * results go to files/evaluation/health-<part>.json. Optional: `-e language auto|en`, `-e minutes N`.
 * Screen may be off: a partial wake lock keeps the CPU running; no device setting is changed.
 */
class DeviceHealthTest {
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun clips(set: String) = File(File(context.filesDir, "evaluation"), set).let { dir ->
        File(dir, "manifest.tsv").readLines().filter { it.isNotBlank() }.map { it.split('\t', limit = 4) }.map { Triple(File(dir, it[0]), it[1], it[3]) }
    }
    private fun language() = SpeechLanguage.fromCode(arguments.getString("language") ?: SpeechLanguage.AUTO.code)
    private fun write(part: String, value: JSONObject) = File(File(context.filesDir, "evaluation"), "health-$part.json").writeText(value.toString(1))
    private fun <T> awake(block: () -> T): T {
        val wake = context.getSystemService(android.os.PowerManager::class.java)
            .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "SideKick:DeviceHealth").apply { setReferenceCounted(false); acquire(3 * 3_600_000L) }
        try { return block() } finally { if (wake.isHeld) wake.release() }
    }
    private fun thermal() = context.getSystemService(android.os.PowerManager::class.java).currentThermalStatus

    /** CPU seconds the app spends per minute of audio: the main driver of battery use while listening. */
    @Test fun energy() = runBlocking<Unit> {
        assumeTrue(arguments.getString("deviceHealth") == "energy")
        awake {
            runBlocking {
                val engine = SpeechEngine(context, ::language) { SpeechQuality.ADAPTIVE }
                val rows = clips(arguments.getString("set") ?: "timing")
                val cpu0 = android.os.Process.getElapsedCpuTime(); val wall0 = android.os.SystemClock.elapsedRealtime()
                var audioMs = 0L
                repeat((arguments.getString("rounds") ?: "3").toInt()) { for ((file, _, _) in rows) {
                    val samples = WavAudio.decode(file.readBytes()); audioMs += samples.size / 16
                    engine.transcribe(samples, 4, retainModel = true)
                } }
                engine.release()
                val cpuMs = android.os.Process.getElapsedCpuTime() - cpu0
                val battery = context.getSystemService(android.os.BatteryManager::class.java)
                write("energy", JSONObject().put("language", language().code).put("audioMinutes", audioMs / 60_000.0)
                    .put("cpuSecondsPerAudioMinute", cpuMs / 1000.0 / (audioMs / 60_000.0)).put("wallSeconds", (android.os.SystemClock.elapsedRealtime() - wall0) / 1000.0)
                    .put("charging", battery.isCharging).put("note", "CPU time is the battery driver; charge counters are unreliable while plugged in"))
            }
        }
    }

    /** Continuous replay: does speed fall as the phone warms up? */
    @Test fun heat() = runBlocking<Unit> {
        assumeTrue(arguments.getString("deviceHealth") == "heat")
        awake {
            runBlocking {
                val engine = SpeechEngine(context, ::language) { SpeechQuality.ADAPTIVE }
                val rows = clips(arguments.getString("set") ?: "timing")
                val began = android.os.SystemClock.elapsedRealtime()
                val until = began + (arguments.getString("minutes") ?: "60").toLong() * 60_000
                val samples = JSONArray()
                var i = 0
                while (android.os.SystemClock.elapsedRealtime() < until) {
                    val (file, _, _) = rows[i++ % rows.size]
                    val audio = WavAudio.decode(file.readBytes()); val audioMs = audio.size / 16
                    val start = android.os.SystemClock.elapsedRealtime()
                    engine.transcribe(audio, 4, retainModel = true)
                    samples.put(JSONObject().put("t", (start - began) / 1000)
                        .put("realTimeFactor", (android.os.SystemClock.elapsedRealtime() - start).toDouble() / audioMs).put("thermal", thermal()))
                }
                engine.release()
                write("heat", JSONObject().put("language", language().code).put("samples", samples))
            }
        }
    }

    /** Memory with the speech models loaded (the app's own process). */
    @Test fun memory() = runBlocking<Unit> {
        assumeTrue(arguments.getString("deviceHealth") == "memory")
        awake {
            runBlocking {
                val before = android.os.Debug.getPss()
                val engine = SpeechEngine(context, ::language) { SpeechQuality.ADAPTIVE }
                var peak = before
                for ((file, _, _) in clips(arguments.getString("set") ?: "timing")) {
                    engine.transcribe(WavAudio.decode(file.readBytes()), 4, retainModel = true)
                    peak = maxOf(peak, android.os.Debug.getPss())
                }
                engine.release(); System.gc()
                write("memory", JSONObject().put("language", language().code).put("pssBeforeMb", before / 1024.0).put("pssPeakMb", peak / 1024.0)
                    .put("pssAfterReleaseMb", android.os.Debug.getPss() / 1024.0))
            }
        }
    }

    /** Owner's recordings should match the enrolled profile; other people's voices should not. */
    @Test fun speaker() {
        assumeTrue(arguments.getString("deviceHealth") == "speaker")
        awake {
            val app = context.applicationContext as SidekickApplication
            val engine = SpeakerEngine(context, app.voiceProfile)
            val out = JSONArray()
            try {
                for ((set, owner) in listOf((arguments.getString("ownerSet") ?: "myvoice") to true, (arguments.getString("set") ?: "timing") to false))
                    for ((file, _, _) in clips(set)) {
                        if (!owner && file.name.startsWith("line")) continue
                        engine.newTurn()
                        val result = engine.identify(WavAudio.decode(file.readBytes()))
                        out.put(JSONObject().put("file", file.name).put("owner", owner).put("result", result.name).put("detail", engine.lastDiagnostic))
                    }
            } finally { engine.close() }
            val rows = (0 until out.length()).map { out.getJSONObject(it) }
            write("speaker", JSONObject().put("ownerAccepted", rows.count { it.getBoolean("owner") && it.getString("result") == Speaker.USER.name })
                .put("ownerClips", rows.count { it.getBoolean("owner") })
                .put("othersAccepted", rows.count { !it.getBoolean("owner") && it.getString("result") == Speaker.USER.name })
                .put("otherClips", rows.count { !it.getBoolean("owner") }).put("rows", out))
        }
    }

    /** Music, noise and silence through the live path (voice detection + recognition): phrases that should not exist. */
    @Test fun falsetrigger() = runBlocking<Unit> {
        assumeTrue(arguments.getString("deviceHealth") == "falsetrigger")
        awake {
            runBlocking {
                val engine = SpeechEngine(context, ::language) { SpeechQuality.ADAPTIVE }
                NativeVad.open(VadModel.install(context))
                val out = JSONArray(); var audioMs = 0L
                try {
                    for ((file, kind, _) in clips(arguments.getString("set") ?: "nonspeech")) {
                        val heard = mutableListOf<String>()
                        val processor = LiveSpeechProcessor(engine) { text, _, _ -> if (text.isNotBlank()) heard += text }
                        val worker = launch { processor.consume() }
                        val audio = WavAudio.decode(file.readBytes()); audioMs += audio.size / 16
                        for (i in audio.indices step 4096) processor.accept(audio.copyOfRange(i, minOf(i + 4096, audio.size)))
                        processor.finish(); worker.join(); processor.close()
                        out.put(JSONObject().put("file", file.name).put("kind", kind).put("phrases", JSONArray(heard)))
                    }
                } finally { NativeVad.close(); engine.release() }
                val phrases = (0 until out.length()).sumOf { out.getJSONObject(it).getJSONArray("phrases").length() }
                write("falsetrigger", JSONObject().put("audioMinutes", audioMs / 60_000.0).put("falsePhrases", phrases)
                    .put("per10Minutes", phrases / (audioMs / 600_000.0)).put("rows", out))
            }
        }
    }
}
