package com.sidekick.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.audio.LiveSpeechProcessor
import com.sidekick.app.data.MemoryDatabase
import com.sidekick.app.reminders.ReminderCoordinator
import com.sidekick.app.speech.NativeVad
import com.sidekick.app.speech.VadModel
import com.sidekick.core.*
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LivePipelineTest {
    @Test fun unapprovedTextAndProposalsNeverEnterDatabase() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, MemoryDatabase::class.java).build()
        val temporary = TemporaryMemory()
        try {
            temporary.appendLive("I'll call Emma tomorrow at 5 pm.", Instant.now(), ZoneId.of("America/Chicago"))
            val draft = temporary.drafts.value.single()
            assertTrue(database.commitments().all().isEmpty())
            ReminderCoordinator(context, database.commitments()).save(draft.id, "Call Emma", null, draft.zoneId, draft)
            temporary.remove(draft.id)
            assertEquals("ACCEPTED", database.commitments().all().single().state)
            temporary.discard()
            assertTrue(temporary.text.value.isEmpty())
            assertTrue(TemporaryMemory().drafts.value.isEmpty())
            assertEquals(1, database.commitments().all().size)
        } finally { database.close() }
    }

    @Test fun realVadAndWhisperProcessPacedSpeechAndSkipSilence() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as SidekickApplication
        val fixture = instrumentation.context.assets.open("jfk.wav").use { WavAudio.decode(it.readBytes()) }
        val results = mutableListOf<String>()
        NativeVad.open(VadModel.install(app))
        val pipeline = LiveSpeechProcessor(app.speech) { text, _, _ -> results += text }
        try {
            coroutineScope {
                val worker = launch { pipeline.consume() }
                suspend fun feed(samples: FloatArray) {
                    for (start in samples.indices step 4096) {
                        val frame = samples.copyOfRange(start, minOf(start + 4096, samples.size))
                        try { pipeline.accept(frame) } finally { frame.fill(0f) }
                        delay(256)
                    }
                }
                feed(FloatArray(32_768))
                assertTrue(results.isEmpty())
                feed(fixture)
                feed(FloatArray(24_576))
                pipeline.finish()
                withTimeout(20_000) { worker.join() }
            }
            assertTrue("Expected progressive chunks", results.size >= 2)
            assertTrue(results.joinToString(" ").lowercase().contains("country"))
        } finally { pipeline.close(); NativeVad.close(); fixture.fill(0f) }
    }
}
