package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.speaker.SpeakerEngine
import com.sidekick.app.speaker.VoiceProfile
import com.sidekick.core.*
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlinx.coroutines.*

class SpeakerIdentityTest {
    private val now = Instant.parse("2026-10-23T17:00:00Z")
    private val zone = ZoneId.of("America/Chicago")

    @Test fun shortOwnPhraseCanRefineAnEventButCannotInheritAnExpiredOrInterruptedIdentity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val profile = VoiceProfile(context)
        val engine = SpeakerEngine(context, profile)
        val pcm = instrumentation.context.assets.open("jfk.wav").use { WavAudio.decode(it.readBytes()) }
        val long = pcm.copyOfRange(0, 80_000)
        val short = pcm.copyOfRange(0, 33_600)
        try {
            val reference = engine.embedding(long)
            profile.save(listOf(reference, reference, reference))
            assertEquals(Speaker.UNKNOWN, engine.identify(short, 1000))
            assertEquals(Speaker.USER, engine.identify(long, 2000))
            val identified = engine.identify(short, 17_000)
            println("SHORT_SPEAKER ${engine.lastDiagnostic}")
            assertEquals(Speaker.USER, identified)
            val memory = TemporaryMemory()
            memory.appendAttributedLive("I'll go to the party tomorrow at 8pm.", now, zone, Speaker.USER)
            val id = memory.drafts.value.single().id
            memory.appendAttributedLive("It ends at midnight.", now.plusSeconds(15), zone, identified)
            assertEquals(id, memory.drafts.value.single().id)
            assertEquals(Instant.parse("2026-10-25T05:00:00Z").toEpochMilli(), memory.drafts.value.single().activityEndAt)
            // Short matches do not renew the long-phrase anchor.
            assertEquals(Speaker.UNKNOWN, engine.identify(short, 62_001))
            assertEquals(Speaker.USER, engine.identify(long, 70_000))
            assertEquals(Speaker.UNKNOWN, engine.identify(FloatArray(33_600), 71_000))
            assertEquals(Speaker.UNKNOWN, engine.identify(short, 72_000))
        } finally { profile.delete(); engine.close(); pcm.fill(0f); long.fill(0f); short.fill(0f) }
    }

    @Test fun speakerMatchingAndWhisperShareThePacedPipelineWithoutQueueOverflow() = pacedPipeline(SpeechQuality.ACCURATE, 1)
    @Test fun defaultModelHandlesSustainedPacedSpeech() = pacedPipeline(SpeechQuality.FAST, 4)

    private fun pacedPipeline(quality: SpeechQuality, repeats: Int) = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as SidekickApplication
        assumeTrue(app.packageName.endsWith(".benchmark"))
        val profile = VoiceProfile(app)
        val engine = SpeakerEngine(app, profile)
        val pcm = instrumentation.context.assets.open("jfk.wav").use { WavAudio.decode(it.readBytes()) }
        val latencies = mutableListOf<Long>()
        val speakers = mutableListOf<Speaker>()
        val transcripts = mutableListOf<String>()
        val start = android.os.SystemClock.elapsedRealtime()
        val cpu = android.os.Process.getElapsedCpuTime()
        val previousQuality = app.guidance.speechQuality.value
        app.guidance.setSpeechQuality(quality)
        var currentSpeaker = Speaker.UNKNOWN
        val enrollment = pcm.copyOfRange(0, 80_000)
        com.sidekick.app.speech.NativeVad.open(com.sidekick.app.speech.VadModel.install(app))
        val pipeline = com.sidekick.app.audio.LiveSpeechProcessor(app.speech, inspect = { samples ->
            currentSpeaker = engine.identify(samples); speakers += currentSpeaker; true
        }, contextKey = { if (currentSpeaker == Speaker.USER) "fixture" else null }) { text, _, latency -> transcripts += text; latencies += latency }
        try {
            val vector = engine.embedding(enrollment)
            profile.save(listOf(vector, vector, vector))
            coroutineScope {
                val worker = launch(Dispatchers.IO) { pipeline.consume() }
                repeat(repeats) { for (startSample in pcm.indices step 4096) {
                    val frame = pcm.copyOfRange(startSample, minOf(startSample + 4096, pcm.size))
                    try { pipeline.accept(frame) } finally { frame.fill(0f) }
                    delay(256)
                } }
                pipeline.finish()
                withTimeout(30_000) { worker.join() }
            }
            assertTrue(transcripts.joinToString(" ").contains("country", true))
            assertTrue(speakers.contains(Speaker.USER))
            val other = instrumentation.context.assets.open("quality-party.wav").use { WavAudio.decode(it.readBytes()) }
            try { assertNotEquals("Synthetic other voice must not inherit identity", Speaker.USER, engine.identify(other)) }
            finally { other.fill(0f) }
            val memory = android.os.Debug.MemoryInfo().also { android.os.Debug.getMemoryInfo(it) }
            println("SPEAKER_BASE_MEMORY pssKb=${memory.totalPss}")
            println("SPEAKER_PIPELINE model=$quality; audioMs=${pcm.size * 1000L / 16000 * repeats}; elapsedMs=${android.os.SystemClock.elapsedRealtime() - start}; processCpuMs=${android.os.Process.getElapsedCpuTime() - cpu}; chunks=${transcripts.size}; userChunks=${speakers.count { it == Speaker.USER }}; maxCombinedChunkMs=${latencies.maxOrNull()}")
        } finally {
            pipeline.close(); engine.close(); profile.delete(); pcm.fill(0f); enrollment.fill(0f)
            app.guidance.setSpeechQuality(previousQuality)
            com.sidekick.app.speech.NativeVad.close()
        }
    }

    @Test fun otherAndUnknownVoicesCannotCreatePersonalPromisesOrRefineUsersEvent() {
        for (speaker in listOf(Speaker.OTHER, Speaker.UNKNOWN)) {
            val memory = TemporaryMemory()
            memory.beginSession()
            memory.appendAttributedLive("I will bring snacks tomorrow.", now, zone, speaker)
            assertTrue(memory.drafts.value.isEmpty())
            memory.appendAttributedLive("There's a party tomorrow at 8pm at The James.", now, zone, speaker)
            assertTrue(memory.drafts.value.single().informational)
        }
        val memory = TemporaryMemory()
        memory.appendAttributedLive("I'll go to the party tomorrow at 8pm.", now, zone, Speaker.USER)
        assertFalse(memory.drafts.value.single().informational)
        memory.appendAttributedLive("It ends at midnight.", now, zone, Speaker.UNKNOWN)
        assertNull(memory.drafts.value.first().activityEndAt)
        assertEquals(2, memory.drafts.value.size)
        assertTrue(memory.drafts.value.last().reviewNote.contains("speaker uncertain"))
    }

    @Test fun knownUserCorrectionsStayInOneReviewDraft() {
        val memory = TemporaryMemory()
        memory.appendAttributedLive("I'll go to the party tomorrow at 8pm at The James.", now, zone, Speaker.USER)
        val id = memory.drafts.value.single().id
        memory.appendAttributedLive("It ends at 11pm. Actually it ends at midnight.", now.plusSeconds(10), zone, Speaker.USER)
        assertEquals(id, memory.drafts.value.single().id)
        assertEquals(Instant.parse("2026-10-25T05:00:00Z").toEpochMilli(), memory.drafts.value.single().activityEndAt)
    }

    @Test fun realModelEnrollmentReloadDisableAndDelete() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val profile = VoiceProfile(context)
        profile.delete()
        val engine = SpeakerEngine(context, profile)
        val pcm = instrumentation.context.assets.open("jfk.wav").use { WavAudio.decode(it.readBytes()) }
        val start = android.os.SystemClock.elapsedRealtime()
        try {
            val sample = pcm.copyOfRange(0, 80_000)
            val otherPhrase = pcm.copyOfRange(80_000, minOf(pcm.size, 160_000))
            try {
                val reference = engine.embedding(sample)
                val heldOut = engine.embedding(otherPhrase)
                val score = VoiceMatch.similarity(reference, heldOut)
                assertTrue("Same-speaker disjoint phrase should have positive similarity: $score", score > .35f)
                profile.save(listOf(reference, reference, reference))
                assertTrue(VoiceProfile(context).ready.value)
                val match = engine.identify(sample)
                assertEquals("Clean enrolled fixture", Speaker.USER, match)
                profile.setEnabled(false)
                assertEquals(Speaker.UNKNOWN, engine.identify(sample))
                profile.setEnabled(true)
                val previous = profile.snapshot()!!
                try {
                    profile.save(listOf(reference, reference, FloatArray(256)))
                    fail("Bad replacement should fail")
                } catch (_: IllegalArgumentException) { }
                assertArrayEquals(previous, profile.snapshot(), 0f)
                println("SPEAKER_MODEL disjointSameSpeakerCosine=$score; enrolledResult=$match; elapsedMs=${android.os.SystemClock.elapsedRealtime() - start}")
                profile.delete()
                assertFalse(VoiceProfile(context).ready.value)
                assertEquals(Speaker.UNKNOWN, engine.identify(sample))
            } finally { sample.fill(0f); otherPhrase.fill(0f) }
        } finally { pcm.fill(0f); engine.close(); profile.delete() }
    }
}
