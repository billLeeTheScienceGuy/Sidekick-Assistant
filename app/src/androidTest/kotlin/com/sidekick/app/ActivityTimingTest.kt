package com.sidekick.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.reminders.ReminderCoordinator
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ActivityTimingTest {
    @Test fun multilingualLiveAndIncomingTextRefineDraftsWithoutSavingThem() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, com.sidekick.app.data.MemoryDatabase::class.java).build()
        val now = Instant.parse("2026-10-23T17:00:00Z")
        val zone = ZoneId.of("America/Chicago")
        try {
            val memory = TemporaryMemory()
            memory.appendAttributedLive("저는 내일 오후 8시에 파티에 갈 거예요.", now, zone, com.sidekick.core.Speaker.USER)
            val initial = memory.drafts.value.single()
            assertFalse(initial.informational)
            memory.appendAttributedLive("자정에 끝나요.", now.plusSeconds(10), zone, com.sidekick.core.Speaker.USER)
            assertEquals(initial.id, memory.drafts.value.single().id)
            assertEquals(Instant.parse("2026-10-25T05:00:00Z").toEpochMilli(), memory.drafts.value.single().activityEndAt)
            assertTrue(database.commitments().all().isEmpty())
            val folder = java.io.File(context.cacheDir, "language-no-model-${java.util.UUID.randomUUID()}").apply { mkdirs() }
            try {
                val isolated = object : android.content.ContextWrapper(context) {
                    override fun getNoBackupFilesDir(): java.io.File = folder
                }
                val engine = com.sidekick.app.intent.IntentEngine(isolated)
                assertFalse(engine.ready.value)
                val typed = engine.extract(com.sidekick.core.Transcript("Morgen ist eine Party um 20 Uhr. Sie endet um Mitternacht.", now, zone, com.sidekick.core.Speaker.USER, true))
                assertEquals(Instant.parse("2026-10-25T05:00:00Z"), typed.single().activityEndAt)
            } finally { folder.delete() }
            val other = com.sidekick.core.Transcript("我明天晚上八点去参加派对。午夜结束。", now, zone, com.sidekick.core.Speaker.OTHER, true)
            val incoming = com.sidekick.core.ActivityTiming.extract(other, informationalOnly = true)
            assertTrue(incoming.single().informational)
            memory.addProposals(incoming.map { it.copy(sourceKey = "synthetic-message") })
            assertEquals(2, memory.drafts.value.size)
            assertTrue(database.commitments().all().isEmpty())
        } finally { database.close() }
    }
    @Test fun playbackScriptKeepsOneDraftWhileEndAndVenueAreCorrected() {
        val memory = TemporaryMemory()
        val zone = ZoneId.of("America/Chicago")
        val now = Instant.parse("2026-10-23T17:00:00Z")
        memory.beginSession()
        memory.appendLive("There's a party tomorrow at 8pm at The James.", now, zone)
        val initial = memory.drafts.value.single()
        assertTrue(initial.informational)
        assertEquals(Instant.parse("2026-10-25T01:00:00Z").toEpochMilli(), initial.activityStartAt)
        assertNull(initial.activityEndAt)
        memory.appendLive("It ends at 11pm.", now.plusSeconds(30), zone)
        assertEquals(Instant.parse("2026-10-25T04:00:00Z").toEpochMilli(), memory.drafts.value.single().activityEndAt)
        memory.appendLive("Actually it ends at midnight.", now.plusSeconds(60), zone)
        memory.appendLive("Actually it's at The Sylvee.", now.plusSeconds(90), zone)
        memory.appendLive("It's at 25 S Livingston Street.", now.plusSeconds(120), zone)
        val final = memory.drafts.value.single()
        assertEquals(initial.id, final.id)
        assertEquals(Instant.parse("2026-10-25T05:00:00Z").toEpochMilli(), final.activityEndAt)
        assertEquals("The Sylvee", final.placeName)
        assertEquals("25 S Livingston Street", final.placeAddress)
        println("PLAYBACK_SCRIPT checks=initialStart,unknownEnd,revisedEnd,revisedVenue,address,stableDraft; passed")
    }

    @Test fun informationalSettingDoesNotTurnOtherEventsIntoPersonalPlans() {
        val memory = TemporaryMemory(includeInformational = { false })
        val zone = ZoneId.of("America/Chicago")
        val now = Instant.parse("2026-10-23T17:00:00Z")
        memory.appendLive("There's a party tomorrow at 8pm.", now, zone)
        assertTrue(memory.drafts.value.isEmpty())
        memory.appendLive("I will bring snacks tomorrow.", now.plusSeconds(30), zone)
        assertEquals(1, memory.drafts.value.size)
        assertFalse(memory.drafts.value.single().informational)
    }

    @Test fun changingAnApprovedVenueRequiresReviewAndClearsItsPreviousAddress() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, com.sidekick.app.data.MemoryDatabase::class.java).build()
        val memory = TemporaryMemory()
        val coordinator = ReminderCoordinator(context, database.commitments())
        val zone = ZoneId.of("America/Chicago")
        val time = Instant.parse("2026-10-22T17:00:00Z")
        try {
            memory.appendLive("There's a party tomorrow at 8pm at The James. It's at 432 W Gorham Street.", time, zone)
            val initial = memory.drafts.value.single()
            coordinator.save(initial.id, initial.title, null, zone.id, initial, initial.activityStartAt, initial.activityEndAt)
            memory.approve(initial.id, database.commitments().get(initial.id)!!)
            memory.appendLive("Actually it's at The Sylvee.", time, zone)
            val revision = memory.drafts.value.single()
            assertEquals(initial.id, memory.updateTarget(revision.id))
            assertEquals("The Sylvee", revision.placeName); assertNull(revision.placeAddress)
            assertEquals("432 W Gorham Street", database.commitments().get(initial.id)!!.placeAddress)
            coordinator.save(initial.id, revision.title, null, zone.id, activityStartAt = revision.activityStartAt,
                activityEndAt = revision.activityEndAt, placeName = revision.placeName, placeAddress = revision.placeAddress.orEmpty())
            val saved = database.commitments().get(initial.id)!!
            assertEquals("The Sylvee", saved.placeName); assertNull(saved.placeAddress)
            assertEquals(1, database.commitments().all().size)
            memory.approve(revision.id, saved)
            memory.appendLive("It's at 25 S Livingston Street.", time, zone)
            assertEquals("25 S Livingston Street", memory.drafts.value.single().placeAddress)
            assertNull(database.commitments().get(initial.id)!!.placeAddress)
        } finally { database.close() }
    }
    @Test fun laterSpeechRefinesDraftThenOffersAnApprovedItemUpdateWithoutWritingIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, com.sidekick.app.data.MemoryDatabase::class.java).build()
        val memory = TemporaryMemory()
        val zone = ZoneId.of("America/Chicago")
        val now = Instant.parse("2026-10-22T17:00:00Z")
        val coordinator = ReminderCoordinator(context, database.commitments())
        try {
            memory.beginSession()
            memory.appendLive("Yeah there's a party at 8pm at The James.", now, zone)
            val initial = memory.drafts.value.single()
            assertNull(initial.activityEndAt)
            memory.appendLive("We can bring snacks. It will end around midnight.", now.plusSeconds(60), zone)
            val enriched = memory.drafts.value.single()
            assertEquals(initial.id, enriched.id)
            val midnight = Instant.parse("2026-10-23T05:00:00Z").toEpochMilli()
            assertEquals(midnight, enriched.activityEndAt)
            assertTrue(database.commitments().all().isEmpty())
            coordinator.save(enriched.id, "Party", null, zone.id, enriched, enriched.activityStartAt, enriched.activityEndAt)
            memory.approve(enriched.id, database.commitments().get(enriched.id)!!)
            memory.appendLive("We can bring drinks too.", now.plusSeconds(90), zone)
            assertTrue(memory.drafts.value.isEmpty())
            memory.appendLive("It's at 432 W Gorham Street.", now.plusSeconds(100), zone)
            assertNull(database.commitments().get(enriched.id)!!.placeAddress)
            memory.appendLive("Actually it ends at 1am.", now.plusSeconds(120), zone)
            val revision = memory.drafts.value.single()
            assertEquals(enriched.id, memory.updateTarget(revision.id))
            assertEquals(midnight + 3_600_000, revision.activityEndAt)
            assertEquals("432 W Gorham Street", revision.placeAddress)
            assertEquals(midnight, database.commitments().get(enriched.id)!!.activityEndAt)
            memory.dismiss(revision.id)
            assertEquals(midnight, database.commitments().get(enriched.id)!!.activityEndAt)
        } finally { database.close() }
    }

    @Test fun startOnlyAndUnknownTimesCanBeSavedAndEdited() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, com.sidekick.app.data.MemoryDatabase::class.java).build()
        val coordinator = ReminderCoordinator(context, db.commitments())
        val source = com.sidekick.app.data.CommitmentEntity("optional-time", "Party", "Test fixture", 0, "America/Chicago", "Test")
        try {
            coordinator.save(source.id, source.title, null, source.zoneId, source, 1000, null)
            assertNull(db.commitments().get(source.id)!!.activityEndAt)
            coordinator.save(source.id, source.title, null, source.zoneId, activityStartAt = 1000, activityEndAt = 2000)
            assertEquals(2000L, db.commitments().get(source.id)!!.activityEndAt)
            coordinator.save(source.id, source.title, null, source.zoneId)
            assertNull(db.commitments().get(source.id)!!.activityStartAt)
        } finally { db.close() }
    }
}
