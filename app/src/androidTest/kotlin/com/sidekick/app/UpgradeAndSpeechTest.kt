package com.sidekick.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.data.MemoryDatabase
import com.sidekick.core.WavAudio
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpgradeAndSpeechTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MemoryDatabase::class.java)

    @Test fun upgradesExistingMemoryWithoutDataLoss() {
        migration.createDatabase("upgrade-test", 1).apply {
            execSQL("INSERT INTO commitments (id,title,evidence,capturedAt,zoneId,reviewNote,state) VALUES ('old','Call Emma','Original words',123,'America/Chicago','Review','ACCEPTED')")
            close()
        }
        val database = migration.runMigrationsAndValidate("upgrade-test", 2, true, MemoryDatabase.MIGRATION_1_2)
        database.query("SELECT evidence,dueAt,state FROM commitments WHERE id='old'").use {
            assertTrue(it.moveToFirst()); assertEquals("Original words", it.getString(0)); assertTrue(it.isNull(1)); assertEquals("ACCEPTED", it.getString(2))
        }
        database.close()
    }

    @Test fun bundledSpeechModelTranscribesKnownAudioOffline() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as SidekickApplication
        val bytes = instrumentation.context.assets.open("jfk.wav").use { it.readBytes() }
        val text = app.speech.transcribe(WavAudio.decode(bytes)).lowercase()
        assertTrue("Expected the known speech to mention country; got: $text", text.contains("country"))
        assertTrue(text.contains("ask"))
    }

    @Test fun cancelledTranscriptionDoesNotBreakNextRecording() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as SidekickApplication
        val bytes = instrumentation.context.assets.open("jfk.wav").use { it.readBytes() }
        val job = launch { app.speech.transcribe(WavAudio.decode(bytes)) }
        delay(150)
        withTimeout(10_000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
        val result = withTimeout(30_000) { app.speech.transcribe(WavAudio.decode(bytes)) }
        assertTrue(result.lowercase().contains("country"))
    }

    @Test fun retainedModelSurvivesCancellationAndCanBeReleased() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as SidekickApplication
        val bytes = instrumentation.context.assets.open("jfk.wav").use { it.readBytes() }
        try {
            val first = WavAudio.decode(bytes)
            assertTrue(app.speech.transcribe(first, 4, true).lowercase().contains("country"))
            assertTrue(first.all { it == 0f })
            val cancelled = WavAudio.decode(bytes)
            val job = launch { app.speech.transcribe(cancelled, 4, true) }
            delay(150)
            withTimeout(10_000) { job.cancelAndJoin() }
            assertTrue(cancelled.all { it == 0f })
            assertTrue(app.speech.transcribe(WavAudio.decode(bytes), 4, true).lowercase().contains("country"))
            app.speech.release()
            assertTrue(app.speech.transcribe(WavAudio.decode(bytes)).lowercase().contains("country"))
        } finally { withContext(NonCancellable) { app.speech.release() }; bytes.fill(0) }
    }
}
