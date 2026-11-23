package com.sidekick.app

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.data.CommitmentEntity
import com.sidekick.app.data.MemoryDatabase
import com.sidekick.app.data.MemoryExport
import com.sidekick.app.data.NoticeRecordEntity
import com.sidekick.app.speaker.SpeakerModel
import com.sidekick.app.speaker.VoiceProfile
import com.sidekick.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Privacy work package on a real device: notice-record migration, session-only voice names never reaching Room,
 * SharedPreferences or the JSON export, owner voiceprint consent gating and 12-month retention.
 * Uses isolated databases, preferences and no-backup folders; never the personal app data.
 */
class PrivacyConsentDeviceTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MemoryDatabase::class.java)
    private val root: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun noticeRecordTableMigratesFromVersion6() {
        migration.createDatabase("privacy-6-7", 6).use { db ->
            db.execSQL("INSERT INTO commitments (id,title,evidence,capturedAt,zoneId,reviewNote,state,informational) VALUES ('keep','Lunch','Lunch at noon',1,'UTC','r','ACCEPTED',0)")
        }
        migration.runMigrationsAndValidate("privacy-6-7", 8, true, MemoryDatabase.MIGRATION_6_7, MemoryDatabase.MIGRATION_7_8).use { db ->
            db.query("SELECT title FROM commitments WHERE id='keep'").use { assertTrue(it.moveToFirst()); assertEquals("Lunch", it.getString(0)) }
            db.query("SELECT COUNT(*) FROM notice_records").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            db.query("SELECT volumePercent, measuredLevel, audible FROM notice_records").use { assertEquals(3, it.columnCount) }
        }
    }

    @Test fun sessionVoiceNamesStayInMemory() = runBlocking {
        val id = java.util.UUID.randomUUID().toString()
        val name = "Zebulon"  // unusual on purpose: easy to find anywhere it should not be
        val voiceKey = "spk_${id.replace("-", "")}_1"
        val memory = TemporaryMemory()
        val zone = ZoneId.of("America/Chicago")
        val start = Instant.parse("2026-11-16T15:00:00Z")
        // Owner addresses Zebulon, a grouped voice answers within 5 s: a "<Name>?" chip, applied only on tap.
        memory.appendAttributedLive("Hey $name, are you free later?", start, zone, Speaker.USER, false, "spk_${id.replace("-", "")}_owner", "You")
        memory.appendAttributedLive("Yeah I think so.", start.plusSeconds(2), zone, Speaker.OTHER, false, voiceKey, "Voice 1", grouped = true)
        assertEquals(SessionNaming.Suggestion(voiceKey, name), memory.nameSuggestion.value)
        memory.voiceNamed(voiceKey, name, "Voice 1")
        assertNull(memory.nameSuggestion.value)
        // The named voice's dated invitation: information-only event "<Name>: meet at 5".
        memory.appendAttributedLive("Meet me at 5 tomorrow.", start.plusSeconds(10), zone, Speaker.OTHER, false, voiceKey, name, speakerName = name, grouped = true)
        val info = memory.drafts.value.single { it.title.startsWith("$name: meet at 5") }
        assertTrue(info.informational)
        assertNull(info.dueAt)                                    // never a reminder
        // Owner's short acceptance within 30 s: owner event saved to Review only (review: source).
        memory.appendAttributedLive("Sure.", start.plusSeconds(20), zone, Speaker.USER, false, "spk_${id.replace("-", "")}_owner", "You")
        val owner = memory.drafts.value.single { it.title.startsWith("Meet $name at 5") }
        assertFalse(owner.informational)
        assertTrue(owner.sourceKey!!.startsWith("review:"))

        // Persist the drafts and a notice record exactly as the app would, then look for voice data everywhere.
        val dbName = "privacy-voices-$id.db"
        val db = Room.databaseBuilder(root, MemoryDatabase::class.java, dbName).build()
        try {
            db.commitments().insertAll(memory.drafts.value.map { it.copy(state = "ACCEPTED") })
            db.noticeRecords().put(NoticeRecordEntity(sessionId = id, noticeAt = 1, stateCodes = "TX", recordingRule = "ONE_PARTY",
                biometricRule = "NOTICE", textVersion = ConsentNotice.VERSION, includesVoice = true, method = "ATTESTED"))
            val saved = db.commitments().all()
            // Only text carries the name; no column besides title/evidence/reviewNote mentions it.
            saved.forEach { row ->
                listOf(row.sourceKey, row.placeName, row.placeAddress, row.zoneId, row.state).forEach { assertFalse(it.orEmpty().contains(name)) }
            }
            db.noticeRecords().all().forEach { record ->
                assertFalse(record.toString().contains(name)); assertFalse(record.toString().contains("spk_"))
            }
            // The JSON export has text only: no source keys, no voice keys.
            val out = ByteArrayOutputStream()
            MemoryExport.write(out, saved, excerpts = true)
            val json = out.toString("UTF-8")
            assertFalse(json.contains("spk_")); assertFalse(json.contains("sourceKey")); assertFalse(json.contains("Voice 1"))
        } finally { db.close(); root.deleteDatabase(dbName) }
        // No preference file anywhere in this app holds the name or a voice key.
        val prefs = File(root.applicationInfo.dataDir, "shared_prefs")
        prefs.listFiles().orEmpty().forEach { file ->
            val text = file.readText()
            assertFalse("${file.name} holds a session voice name", text.contains(name))
            assertFalse("${file.name} holds a session voice key", text.contains(voiceKey))
        }
        // forgetOthers(): names, suggestions and nameable labels are gone at once.
        memory.forgetVoiceNames()
        assertTrue(memory.speakerTurns.value.none { it.voiceKey != null || it.label == name })
        memory.discard()
    }

    private fun isolated(id: String): Context {
        val folder = File(root.cacheDir, "privacy-$id").apply { mkdirs() }
        return object : ContextWrapper(root) {
            override fun getNoBackupFilesDir() = folder
            override fun getSharedPreferences(name: String, mode: Int) = root.getSharedPreferences("privacy-$id-$name", mode)
        }
    }

    @Test fun ownerVoiceprintNeedsConsentAndExpires() {
        val id = java.util.UUID.randomUUID().toString()
        val context = isolated(id)
        try {
            val profile = VoiceProfile(context); profile.selectModel(SpeakerModel.CAMPPLUS)
            val vector = FloatArray(SpeakerModel.CAMPPLUS.dimension) { if (it % 7 == 0) 1f else .01f }
            profile.save(listOf(vector, vector, vector))
            assertNull(profile.consent.value)
            // A profile saved before consent records existed (an app upgrade: the new version has never checked): matching off until accepted.
            context.getSharedPreferences("voice", Context.MODE_PRIVATE).edit().remove("consent_checked").commit()
            val upgraded = VoiceProfile(context); upgraded.selectModel(SpeakerModel.CAMPPLUS)
            assertTrue(upgraded.consentNeeded.value)
            assertTrue(upgraded.matchingSnapshots().isEmpty())
            upgraded.recordConsent(VoiceprintConsent("Test Owner", System.currentTimeMillis(), VoiceprintPolicy.NOTICE_VERSION, "checkbox"))
            assertFalse(upgraded.consentNeeded.value)
            assertEquals(1, upgraded.matchingSnapshots().size)
            // Retention: unused for more than 12 months -> profile and consent record deleted.
            assertFalse(upgraded.purgeIfExpired(System.currentTimeMillis()))
            assertTrue(upgraded.purgeIfExpired(System.currentTimeMillis() + VoiceprintPolicy.RETENTION_MS + 86_400_000L))
            assertTrue(upgraded.enrolled.value.isEmpty())
            assertNull(upgraded.consent.value)
            // "Delete my voice setup" removes the consent record too.
            upgraded.save(listOf(vector, vector, vector))
            upgraded.recordConsent(VoiceprintConsent("Test Owner", System.currentTimeMillis(), VoiceprintPolicy.NOTICE_VERSION, "checkbox"))
            upgraded.delete()
            assertNull(upgraded.consent.value)
            assertNull(VoiceProfile(context).consent.value)
        } finally {
            File(root.cacheDir, "privacy-$id").deleteRecursively(); root.deleteSharedPreferences("privacy-$id-voice")
        }
    }

    @Test fun jurisdictionRulesOnDevice() {
        // The bundled 1:500k borders load on the phone and resolve the handoff cases.
        val wisconsin = RecordingJurisdiction.locate(42.5131, -89.0, 50.0, 1_000)
        assertEquals(RecordingJurisdiction.BiometricRule.STRICT, wisconsin.biometric)
        assertEquals(RecordingJurisdiction.Rule.ALL_PARTY, wisconsin.recording)
        val texas = RecordingJurisdiction.locate(29.7604, -95.3698, 20.0, 1_000, precise = true)
        assertEquals(RecordingJurisdiction.BiometricRule.NOTICE, texas.biometric)
        assertEquals(RecordingJurisdiction.Rule.ONE_PARTY, texas.recording)
    }
}
