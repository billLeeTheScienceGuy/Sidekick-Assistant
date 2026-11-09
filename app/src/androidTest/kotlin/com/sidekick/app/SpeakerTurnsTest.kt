package com.sidekick.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.speaker.*
import com.sidekick.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.*

class SpeakerTurnsTest {
    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName.endsWith(".compacttest") || it.packageName.endsWith(".benchmark"))
    }
    @Test fun recordedOwnerAndSecondVoiceSeparateWithoutGivingTheSecondVoiceOwnership() {
        val root = context(); val id = java.util.UUID.randomUUID().toString()
        val folder = File(root.cacheDir, "speaker-turns-$id").apply { mkdirs() }
        val isolated = object : ContextWrapper(root) {
            override fun getNoBackupFilesDir() = folder
            override fun getSharedPreferences(name: String, mode: Int) = root.getSharedPreferences("turns-$id-$name", mode)
        }
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val recording = assets.open("jfk.wav").use { WavAudio.decode(it.readBytes()) }
        val own = recording.copyOfRange(0, 96_000)
        val other = assets.open("quality-party.wav").use { WavAudio.decode(it.readBytes()) }
        val profile = VoiceProfile(isolated); profile.selectModel(SpeakerModel.CAMPPLUS)
        val speaker = SpeakerEngine(isolated, profile)
        val engine = SpeakerTurnEngine(isolated, profile, speaker)
        try {
            val references = listOf(0, 32_000, 64_000).map { start ->
                val window = recording.copyOfRange(start, start + 80_000)
                try { speaker.embedding(window) } finally { window.fill(0f) }
            }
            try { profile.save(references) } finally { references.forEach { it.fill(0f) } }
            val single = engine.partition(own)
            println("DIARIZATION_SINGLE $single")
            assertEquals("One recorded voice should remain one region", 1, single.size)
            val owner = engine.classify(own, single.single())
            println("DIARIZATION_OWNER $owner")
            assertEquals(owner.diagnostic, Speaker.USER, owner.speaker)
            assertTrue(owner.key.endsWith("_owner"))
            val mixed = own + other.copyOfRange(0, minOf(64_000, other.size))
            try {
                val parts = engine.partition(mixed)
                println("DIARIZATION_TURNS separationMs=${engine.lastSeparationMs} $parts")
                assertTrue(parts.size >= 2)
                var ownFound = false; var secondFound = false
                for (part in parts) {
                    val samples = mixed.copyOfRange(part.start, part.end)
                    try {
                        val actor = engine.classify(samples, part)
                        if (part.end <= own.size && actor.speaker == Speaker.USER) ownFound = true
                        if (part.start >= own.size - 4000) { secondFound = true; assertNotEquals("Second voice: $actor", Speaker.USER, actor.speaker) }
                    } finally { samples.fill(0f) }
                }
                assertTrue("Owner turn was not independently recognized", ownFound)
                assertTrue("Second voice not separated", secondFound)
            } finally { mixed.fill(0f) }
            val overlap = FloatArray(minOf(own.size, other.size)) { (own[it] + other[it]) * .45f }
            try {
                val parts = engine.partition(overlap)
                println("DIARIZATION_OVERLAP $parts")
                for (part in parts) {
                    val samples = overlap.copyOfRange(part.start, part.end)
                    try { assertNotEquals("Overlapping voices must not establish ownership", Speaker.USER, engine.classify(samples, part).speaker) }
                    finally { samples.fill(0f) }
                }
            } finally { overlap.fill(0f) }
            val returning = engine.classify(own, engine.partition(own).single())
            assertEquals(Speaker.USER, returning.speaker)
            assertEquals("The independently matched owner keeps one context across other turns", owner.key, returning.key)
        } finally {
            engine.close(); speaker.close(); profile.delete(); recording.fill(0f); own.fill(0f); other.fill(0f)
            folder.deleteRecursively(); root.deleteSharedPreferences("turns-$id-voice")
        }
    }

    @Test fun separateAnonymousVoicesCannotCompleteEachOthersActivityAndTranscriptTurnsAreTemporary() {
        val now = Instant.now(); val zone = ZoneId.systemDefault()
        val memory = TemporaryMemory()
        memory.appendAttributedLive("There's a party tomorrow at 8pm.", now, zone, Speaker.OTHER, false, "spk_test_1", "Voice 1")
        val initial = memory.drafts.value.single()
        memory.appendAttributedLive("It ends at midnight.", now.plusSeconds(5), zone, Speaker.OTHER, false, "spk_test_2", "Voice 2")
        assertNull(memory.drafts.value.single().activityEndAt)
        assertEquals(listOf("Voice 1", "Voice 2"), memory.speakerTurns.value.map { it.label })
        memory.appendAttributedLive("It ends at midnight.", now.plusSeconds(10), zone, Speaker.OTHER, false, "spk_test_1", "Voice 1")
        assertNotNull(memory.drafts.value.first { it.id == initial.id }.activityEndAt)
        repeat(20) { memory.appendAttributedLive("A temporary utterance.", now.plusSeconds(it + 20L), zone, Speaker.OTHER, false, "spk_test_1", "Voice 1") }
        assertEquals(12, memory.speakerTurns.value.size)
        memory.clearText(); assertTrue(memory.speakerTurns.value.isEmpty())
        assertTrue(memory.text.value.isEmpty())
    }
}
