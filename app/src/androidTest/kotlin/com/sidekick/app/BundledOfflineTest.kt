package com.sidekick.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.data.MemoryDatabase
import com.sidekick.app.speech.SpeechEngine
import com.sidekick.core.WavAudio
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BundledOfflineTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MemoryDatabase::class.java)
    @Test fun oldestSavedMemorySurvivesTheEntireMigrationChain() {
        migration.createDatabase("capstone-upgrade", 1).use { db ->
            db.execSQL("INSERT INTO commitments (id,title,evidence,capturedAt,zoneId,reviewNote,state) VALUES ('capstone-old','Call Emma','Original excerpt',123,'America/Chicago','Review','ACCEPTED')")
        }
        migration.runMigrationsAndValidate("capstone-upgrade", 6, true, MemoryDatabase.MIGRATION_1_2,
            MemoryDatabase.MIGRATION_2_3, MemoryDatabase.MIGRATION_3_4, MemoryDatabase.MIGRATION_4_5, MemoryDatabase.MIGRATION_5_6).use { db ->
            db.query("SELECT evidence,dueAt,state,informational,placeName FROM commitments WHERE id='capstone-old'").use {
                assertTrue(it.moveToFirst()); assertEquals("Original excerpt", it.getString(0))
                assertTrue(it.isNull(1)); assertEquals("ACCEPTED", it.getString(2))
                assertEquals(0, it.getInt(3)); assertTrue(it.isNull(4))
            }
            db.query("SELECT count(*) FROM automatic_changes").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            db.query("SELECT count(*) FROM calendar_links").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun compactEnglishModelRunsLocallyAndClearsItsAudioBuffer() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".compacttest") || context.packageName.endsWith(".benchmark"))
        val engine = SpeechEngine(context)
        val bytes = instrumentation.context.assets.open("jfk.wav").use { it.readBytes() }
        val samples = WavAudio.decode(bytes)
        try {
            val result = withTimeout(60_000) { engine.transcribe(samples) }.lowercase()
            assertTrue(result, result.contains("country")); assertTrue(result, result.contains("ask"))
            assertTrue(samples.all { it == 0f })
        } finally { withContext(NonCancellable) { engine.release() }; bytes.fill(0); samples.fill(0f) }
    }
}
