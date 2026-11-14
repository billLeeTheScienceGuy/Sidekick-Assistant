package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.speech.*
import com.sidekick.app.audio.LiveSpeechProcessor
import com.sidekick.core.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.*

class AutomaticLanguageTest {
    private val i get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = i.targetContext
    private fun benchmarkOnly() { assumeTrue(context.packageName.endsWith(".benchmark")) }

    @Test fun automaticCommandsMeetTheRecordedBaselineWithoutInventingTimes() = runBlocking<Unit> {
        benchmarkOnly()
        val cases = i.context.assets.open("asr-commands.json").bufferedReader().use { JSONArray(it.readText()) }
        val engine = SpeechEngine(context, { SpeechLanguage.AUTO }, { SpeechQuality.ADAPTIVE })
        val results = JSONArray(); val failures = mutableListOf<String>()
        val knownArabicLimits = setOf("ar-ar-SA-ZariyahNeural-0.wav", "ar-ar-SA-ZariyahNeural-1.wav", "ar-ar-SA-HamedNeural-1.wav")
        var recognized = 0
        val captured = Instant.parse("2026-11-09T14:00:00Z"); val zone = ZoneId.of("America/New_York")
        try {
            for (index in 0 until cases.length()) {
                val case = cases.getJSONObject(index)
                val pcm = i.context.assets.open(case.getString("file")).use { WavAudio.decode(it.readBytes()) }
                val text = engine.transcribe(pcm, retainModel = true)
                val action = case.getString("action")
                val reliable = !engine.lastUncertain
                val change = if(reliable) EventChange.parse(text) else null
                val event = if(reliable) ActivityTiming.extract(Transcript(text, captured, zone, Speaker.OTHER, true), true).singleOrNull() else null
                val expectedDay = captured.atZone(zone).toLocalDate().plusDays(1)
                val previous = expectedDay.atTime(18, 0).atZone(zone).toInstant()
                val passed = when (action) {
                    "negative" -> change == null && event == null
                    "move" -> change?.cancel == false && change.newStart(previous, captured, zone) == expectedDay.atTime(19, 0).atZone(zone).toInstant()
                    "cancel" -> change?.cancel == true
                    else -> event?.activityStartAt == expectedDay.atTime(case.getInt("hour"), 0).atZone(zone).toInstant()
                }
                val safe = when (action) {
                    "negative" -> change == null && event == null
                    "cancel" -> change?.cancel == true || change == null && event?.activityStartAt == null
                    "move" -> change == null && event?.activityStartAt == null || change?.cancel == false && change.newStart(previous, captured, zone).let { it == null || it == expectedDay.atTime(19, 0).atZone(zone).toInstant() }
                    else -> change == null && event?.activityStartAt.let { it == null || it == expectedDay.atTime(case.getInt("hour"), 0).atZone(zone).toInstant() }
                }
                if (passed) recognized++
                results.put(JSONObject().put("file", case.getString("file")).put("language", case.getString("code"))
                    .put("reference", case.getString("reference")).put("recognized", text).put("action", action).put("passed", passed)
                    .put("safe", safe).put("automaticChangesBlocked", !reliable).put("knownLimit", case.getString("file") in knownArabicLimits))
                // Keep the three measured unresolved Arabic cases visible; never accept a wrong action/time.
                if (!safe || !passed && case.getString("file") !in knownArabicLimits) failures += "${case.getString("file")}: $text"
            }
        } finally {
            engine.release()
            File(context.filesDir, "automatic-commands.json").writeText(JSONObject().put("results", results)
                .put("conditions", "Synthetic commands, no forced language, no physical microphone; not natural-conversation accuracy").toString(2))
        }
        assertTrue("Command coverage regressed: $recognized/${cases.length()}", recognized >= 31)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun pacedPublicLanguageChangesStayWithinTheContinuousQueueBudget() = runBlocking<Unit> {
        benchmarkOnly()
        assumeTrue(InstrumentationRegistry.getArguments().getString("runRobustness") == "true")
        val manifest = i.context.assets.open("robustness.json").bufferedReader().use { JSONObject(it.readText()) }
        val all = manifest.getJSONArray("clips")
        val clips = (0 until all.length()).map { all.getJSONObject(it) }.distinctBy { it.getString("code") }
        val engine = SpeechEngine(context, { SpeechLanguage.AUTO }, { SpeechQuality.ADAPTIVE })
        val results = JSONArray(); var emitted = 0; var drain = 0L
        val folder = File(context.cacheDir, "auto-live-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : android.content.ContextWrapper(context) {
            override fun getNoBackupFilesDir() = folder
            override fun getSharedPreferences(name: String, mode: Int) = context.getSharedPreferences("${folder.name}-$name", mode)
        }
        val profile = com.sidekick.app.speaker.VoiceProfile(isolated)
        profile.selectModel(com.sidekick.app.speaker.SpeakerModel.CAMPPLUS)
        val speaker = com.sidekick.app.speaker.SpeakerEngine(isolated, profile)
        val turns = com.sidekick.app.speaker.SpeakerTurnEngine(isolated, profile, speaker)
        val pipeline = LiveSpeechProcessor(engine, partition = turns::partition, onPart = { audio, part -> turns.classify(audio, part); true }) { text, start, elapsed ->
            emitted++
            results.put(JSONObject().put("recognized", text).put("sourceStart", start).put("elapsedMs", elapsed))
        }
        NativeVad.open(VadModel.install(context))
        try {
            coroutineScope {
                val worker = launch(Dispatchers.Default) { pipeline.consume() }
                suspend fun feed(audio: FloatArray) {
                    for (offset in audio.indices step 4096) {
                        val frame = audio.copyOfRange(offset, minOf(offset + 4096, audio.size))
                        try { pipeline.accept(frame) } finally { frame.fill(0f) }
                        delay(frame.size * 1000L / 16000)
                    }
                }
                feed(FloatArray(16_000))
                for (clip in clips) {
                    val pcm = i.context.assets.open(clip.getString("file")).use { WavAudio.decode(it.readBytes()) }
                    try { feed(pcm) } finally { pcm.fill(0f) }
                    feed(FloatArray(24_000))
                }
                val finish = android.os.SystemClock.elapsedRealtime()
                pipeline.finish(); withTimeout(30_000) { worker.join() }
                drain = android.os.SystemClock.elapsedRealtime() - finish
            }
            assertTrue("Each language needs emitted speech", emitted >= 7)
            assertTrue("Final decode backlog exceeded one chunk: $drain", drain < 10_000)
            val text = (0 until results.length()).joinToString(" ") { results.getJSONObject(it).getString("recognized") }
            assertTrue(text.any { it in '\uac00'..'\ud7af' })
            assertTrue(text.any { it in '\u4e00'..'\u9fff' })
            assertTrue(text.any { it in '\u0600'..'\u06ff' })
        } finally {
            pipeline.close(); NativeVad.close(); turns.close(); speaker.close(); profile.delete()
            context.deleteSharedPreferences("${folder.name}-voice"); folder.deleteRecursively()
            File(context.filesDir, "automatic-live.json").writeText(JSONObject().put("results", results).put("queueDrainMs", drain)
                .put("conditions", "Real-time-paced public recordings, seven languages with quiet gaps, no microphone; not within-sentence code switching").toString(2))
        }
    }

    @Test fun compareShortArabicAlternativesWithoutGuessingMissingClockWords() = runBlocking<Unit> {
        benchmarkOnly()
        assumeTrue(InstrumentationRegistry.getArguments().getString("runRobustness") == "true")
        val source = i.context.assets.open("asr-commands.json").bufferedReader().use { JSONArray(it.readText()) }
        val engine = SpeechEngine(context)
        val results = JSONArray()
        try {
            for (quality in listOf(SpeechQuality.FAST, SpeechQuality.ACCURATE)) for (index in 0 until source.length()) {
                val case = source.getJSONObject(index)
                if (case.getString("code") != "ar") continue
                val pcm = i.context.assets.open(case.getString("file")).use { WavAudio.decode(it.readBytes()) }
                val text = engine.transcribe(pcm, retainModel = true, selectedLanguage = SpeechLanguage.ARABIC, selectedQuality = quality, audioContext = 0)
                results.put(JSONObject().put("file", case.getString("file")).put("quality", quality.name)
                    .put("reference", case.getString("reference")).put("recognized", text).put("action", case.getString("action")))
            }
        } finally {
            engine.release()
            File(context.filesDir, "arabic-short-alternatives.json").writeText(JSONObject().put("results", results).toString(2))
        }
    }
}
