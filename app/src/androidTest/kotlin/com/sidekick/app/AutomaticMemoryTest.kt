package com.sidekick.app

import android.app.NotificationManager
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.data.*
import com.sidekick.app.reminders.ReminderCoordinator
import com.sidekick.core.Speaker
import com.sidekick.core.WavAudio
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class AutomaticMemoryTest {
    @Test fun laterExplicitDateRefinesInferredDateOnSameSavedEvent() = runBlocking { fixture { _, memory, db, _, auto ->
        val now = Instant.now().atZone(zone).toLocalDate().atTime(12, 0).atZone(zone).toInstant()
        memory.appendAttributedLive("There's a party at 8pm.", now, zone, Speaker.UNKNOWN, false); auto.process()
        val original = db.commitments().all().single()
        memory.appendAttributedLive("Tomorrow.", now.plusSeconds(5), zone, Speaker.UNKNOWN, false); auto.process()
        val updated = db.commitments().all().single()
        assertEquals(original.id, updated.id)
        assertEquals(original.activityStartAt!! + 86_400_000, updated.activityStartAt)
        assertNull(updated.dueAt)
    } }
    @Test fun splitPersonalPlanUpgradesOneSavedItemAndSupportsUndo() = runBlocking { fixture { _, memory, db, _, auto ->
        val now = Instant.now()
        memory.appendAttributedLive("I'll attend the party.", now, zone, Speaker.USER, false); auto.process()
        val original = db.commitments().all().single()
        assertNull(original.activityStartAt)
        memory.appendAttributedLive("Tomorrow.", now.plusSeconds(5), zone, Speaker.USER, false); auto.process()
        memory.appendAttributedLive("At 8pm.", now.plusSeconds(10), zone, Speaker.USER, false); auto.process()
        val timed = db.commitments().all().single()
        assertEquals(original.id, timed.id); assertNotNull(timed.activityStartAt)
        assertEquals(timed.activityStartAt, timed.dueAt)
        assertTrue(auto.undo(timed.id, db.commitments().change(timed.id)!!.token))
        assertEquals(original, db.commitments().all().single())
    } }
    @Test fun attendanceThenWithdrawalKeepsOneInformationalEventAndRemovesReminder() = runBlocking { fixture { _, memory, db, _, auto ->
        val now = Instant.now()
        memory.appendAttributedLive("There's a party tomorrow at 8pm.", now, zone, Speaker.USER, false); auto.process()
        val original = db.commitments().all().single()
        assertTrue(original.informational)
        memory.appendAttributedLive("I'll be there.", now.plusSeconds(5), zone, Speaker.USER, false); auto.process()
        val personal = db.commitments().all().single()
        assertEquals(original.id, personal.id); assertFalse(personal.informational)
        assertEquals(personal.activityStartAt, personal.dueAt)
        memory.appendAttributedLive("I won't be there.", now.plusSeconds(10), zone, Speaker.USER, false); auto.process()
        val withdrawn = db.commitments().all().single()
        assertEquals(original.id, withdrawn.id); assertTrue(withdrawn.informational)
        assertEquals("ACCEPTED", withdrawn.state); assertNull(withdrawn.dueAt)
        assertTrue(auto.undo(withdrawn.id, db.commitments().change(withdrawn.id)!!.token))
        assertEquals(personal, db.commitments().all().single())
    } }
    @Test fun splitReviewDraftKeepsIdAndOldSpeechCannotDonateTiming() {
        val memory = TemporaryMemory(); val now = Instant.now()
        memory.appendAttributedLive("I'll attend the party.", now, zone, Speaker.USER, false)
        val id = memory.drafts.value.single().id
        memory.appendAttributedLive("Tomorrow. At 8pm.", now.plusSeconds(10), zone, Speaker.USER, false)
        assertEquals(id, memory.drafts.value.single().id); assertNotNull(memory.drafts.value.single().activityStartAt)
        val stale = TemporaryMemory()
        stale.appendAttributedLive("There's a party.", now, zone, Speaker.UNKNOWN, false)
        stale.appendAttributedLive("Tomorrow. At 8pm.", now.plusSeconds(180), zone, Speaker.UNKNOWN, false)
        assertTrue(stale.drafts.value.isEmpty())
    }
    @Test fun koreanCorrectionsPreferTheJustDiscussedMeetingOverOldTrials() = runBlocking { fixture { _, memory, db, _, auto ->
        val now = Instant.now()
        val old = (1..2).map { index -> CommitmentEntity("old-ko-$index", "회의 내일 오후 7시", "회의 내일 오후 7시", now.minusSeconds(3600).toEpochMilli(), zone.id, "Test", state = "ACCEPTED", activityStartAt = now.plusSeconds(86400).toEpochMilli(), informational = true, sourceKey = "voice:1:UNKNOWN") }
        db.commitments().insertAll(old)
        val earlier = old.first().copy(id = "previous-clean-meeting", title = "내일 오후 6시에 회의가 있어요", evidence = "내일 오후 6시에 회의가 있어요.", activityStartAt = now.atZone(zone).toLocalDate().plusDays(1).atTime(18, 0).atZone(zone).toInstant().toEpochMilli())
        db.commitments().insertAll(listOf(earlier))
        memory.appendAttributedLive("내일 오후 6 시에 회 의가 있 어요.", now, zone, Speaker.UNKNOWN, false); auto.process()
        val current = db.commitments().all().single { it.id !in old.map { item -> item.id } }
        assertEquals(earlier.id, current.id)
        memory.appendAttributedLive("회의 를 내일 오후 7 시로 변경 해줘.", now.plusSeconds(10), zone, Speaker.UNKNOWN, false); auto.process()
        assertEquals(19, Instant.ofEpochMilli(db.commitments().get(current.id)!!.activityStartAt!!).atZone(zone).hour)
        memory.appendAttributedLive("회의의 를 취소 해줘.", now.plusSeconds(20), zone, Speaker.UNKNOWN, false); auto.process()
        assertEquals("CANCELLED", db.commitments().get(current.id)!!.state)
        old.forEach { assertEquals(it, db.commitments().get(it.id)) }
    } }
    @Test fun koreanAmbiguityWithoutRecentContextStillLeavesEventsAlone() = runBlocking { fixture { _, memory, db, _, auto ->
        val now = Instant.now()
        val old = (1..2).map { index -> CommitmentEntity("ambiguous-ko-$index", "회의", "회의", now.toEpochMilli(), zone.id, "Test", state = "ACCEPTED", activityStartAt = now.plusSeconds(86400).toEpochMilli(), informational = true, sourceKey = "voice:1:UNKNOWN") }
        db.commitments().insertAll(old)
        memory.appendAttributedLive("회의를 취소해줘.", now, zone, Speaker.UNKNOWN, false); auto.process()
        old.forEach { assertEquals(it, db.commitments().get(it.id)) }
    } }
    @Test fun announcementAndLaterAttendanceBecomeOnePersonalEventWithUndo() = runBlocking { fixture { _, memory, db, _, auto ->
        val now = Instant.now()
        memory.appendAttributedLive("There's a workshop tomorrow at 8pm.", now, zone, Speaker.UNKNOWN); auto.process()
        val informational = db.commitments().all().single()
        memory.appendAttributedLive("I'll attend the workshop tomorrow at 8 p.m.", now.plusSeconds(10), zone, Speaker.USER); auto.process()
        val personal = db.commitments().all().single()
        assertEquals(informational.id, personal.id); assertFalse(personal.informational)
        assertEquals(personal.activityStartAt, personal.dueAt)
        val token = db.commitments().change(personal.id)!!.token
        assertTrue(auto.undo(personal.id, token)); assertEquals(informational, db.commitments().all().single())
    } }
    @Test fun namedPeopleVenuesAndConversationsAreNotCollapsedByEventKind() = runBlocking { fixture { _, memory, db, _, auto ->
        val now = Instant.now()
        memory.appendAttributedLive("There's a meeting with Alex tomorrow at 8pm. There's a meeting with Emma tomorrow at 8pm.", now, zone, Speaker.UNKNOWN); auto.process()
        assertEquals(2, db.commitments().all().size)
        memory.appendAttributedLive("There's a workshop tomorrow at 8pm at Test Hall A. There's a workshop tomorrow at 8pm at Test Hall B.", now.plusSeconds(10), zone, Speaker.UNKNOWN); auto.process()
        assertEquals(4, db.commitments().all().size)
    } }
    @Test fun smartAnalysisSkipsKnownPlans() = runBlocking {
        val engine = com.sidekick.app.intent.IntentEngine(context)
        val now = Instant.now()
        val known = com.sidekick.core.Transcript("I'll attend a workshop tomorrow at 8pm.", now, zone, Speaker.USER, true)
        assertTrue(withTimeout(5000) { engine.extract(known) }.any { !it.informational })
        assertNull(engine.notice.value)
    }
    @Test fun tokenLimitFailureKeepsKnownPlansAndCancellationStillPropagates() = runBlocking {
        val source = com.sidekick.core.Transcript("I'll attend the workshop tomorrow at 8pm. I intend to organize my bookshelf tomorrow.", Instant.now(), zone, Speaker.USER, true)
        val failing = com.sidekick.app.intent.IntentEngine(context) { throw IllegalStateException("Analysis exceeded its token limit") }
        val proposals = failing.extract(source)
        assertTrue(proposals.any { !it.informational && it.title.contains("workshop", true) })
        assertTrue(failing.notice.value!!.contains("local rules"))
        val cancelled = com.sidekick.app.intent.IntentEngine(context) { throw CancellationException("test cancellation") }
        try { cancelled.extract(source); fail("Cancellation must propagate") } catch (_: CancellationException) { }
    }
    @Test fun smartSubsetIsStillValidatedAgainstFullOriginalContext() = runBlocking {
        val source = com.sidekick.core.Transcript("Emma said. I'll organize my bookshelf tomorrow.", Instant.now(), zone, Speaker.USER, true)
        val engine = com.sidekick.app.intent.IntentEngine(context) {
            listOf(com.sidekick.core.IntentCandidate("Organize my bookshelf", "I'll organize my bookshelf tomorrow.", "tomorrow"))
        }
        assertTrue(engine.extract(source).isEmpty())
    }
    @Test fun oldAutomaticAnnouncementIsArchivedWithUndoWhenOnePersonalEventAlreadyExists() = runBlocking { fixture { _, _, db, reminders, auto ->
        val now = Instant.now().toEpochMilli()
        val start = now + 86_400_000
        val info = CommitmentEntity("old-announcement", "Workshop", "There's a workshop tomorrow at 8pm.", now, zone.id, "Test", activityStartAt = start, informational = true, sourceKey = "voice:1:UNKNOWN")
        val personal = info.copy(id = "personal-workshop", evidence = "I'll attend the workshop tomorrow at 8pm.", informational = false, sourceKey = "voice:2:USER")
        reminders.save(info.id, info.title, null, zone.id, info, start, informational = true, automatic = true)
        reminders.save(personal.id, personal.title, start, zone.id, personal, start, informational = false, automatic = true)
        auto.process()
        assertEquals("DISMISSED", db.commitments().get(info.id)!!.state)
        assertEquals(start, db.commitments().get(personal.id)!!.dueAt)
        val token = db.commitments().change(info.id)!!.token
        assertTrue(auto.undo(info.id, token))
        auto.process()
        assertEquals("ACCEPTED", db.commitments().get(info.id)!!.state)
    } }
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("America/Chicago")
    private val party = "I'll go to the party tomorrow at 8pm at The James."
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MemoryDatabase::class.java)
    private suspend fun fixture(block: suspend (GuidanceSettings, TemporaryMemory, MemoryDatabase, ReminderCoordinator, AutomaticMemory) -> Unit) {
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val settings = GuidanceSettings(context); val previous = settings.handsOffMode.value
        val db = Room.inMemoryDatabaseBuilder(context, MemoryDatabase::class.java).build()
        val memory = TemporaryMemory(); val reminders = ReminderCoordinator(context, db.commitments())
        try { settings.setHandsOff(true); block(settings, memory, db, reminders, AutomaticMemory(context, settings, memory, db.commitments(), reminders)) }
        finally {
            db.commitments().all().forEach { reminders.delete(it.id); context.getSystemService(NotificationManager::class.java).cancel(it.id, AutomaticMemory.NOTIFICATION) }
            settings.setHandsOff(previous); db.close()
        }
    }
    @Test fun savesAndCorrectsWithoutAnyEditorOrApproval() = runBlocking { fixture { _, memory, db, _, auto ->
        val now = Instant.now()
        memory.appendAttributedLive(party, now, zone, Speaker.USER)
        auto.process()
        val original = db.commitments().all().single()
        assertEquals("ACCEPTED", original.state); assertFalse(original.informational)
        assertEquals(original.activityStartAt, original.dueAt); assertTrue(memory.drafts.value.isEmpty())
        memory.appendAttributedLive("It ends at midnight.", now.plusSeconds(10), zone, Speaker.UNKNOWN)
        auto.process()
        val updated = db.commitments().all().single()
        assertEquals(original.id, updated.id); assertNotNull(updated.activityEndAt)
        assertTrue(memory.drafts.value.isEmpty())
        val change = db.commitments().change(original.id)!!
        assertTrue(auto.undo(original.id, change.token))
        assertEquals(original, db.commitments().get(original.id))
        assertFalse(auto.undo(original.id, change.token))
        memory.appendAttributedLive("It ends at midnight.", now.plusSeconds(15), zone, Speaker.UNKNOWN)
        auto.process()
        assertEquals(original, db.commitments().get(original.id))
    } }
    @Test fun unknownVoicesSaveInformationOnlyAndManualEditsInvalidateUndo() = runBlocking { fixture { _, memory, db, reminders, auto ->
        memory.appendAttributedLive(party, Instant.now(), zone, Speaker.UNKNOWN)
        auto.process()
        val item = db.commitments().all().single()
        assertTrue(item.informational); assertNull(item.dueAt)
        val token = db.commitments().change(item.id)!!.token
        reminders.save(item.id, "My edited title", null, zone.id, activityStartAt = item.activityStartAt)
        assertFalse(auto.undo(item.id, token))
        assertEquals("My edited title", db.commitments().get(item.id)!!.title)
    } }
    @Test fun reviewModeAndInvalidItemsDoNotSilentlySave() = runBlocking { fixture { settings, memory, db, _, auto ->
        settings.setHandsOff(false)
        memory.appendAttributedLive(party, Instant.now(), zone, Speaker.USER); auto.process()
        assertTrue(db.commitments().all().isEmpty()); assertEquals(1, memory.drafts.value.size)
        settings.setHandsOff(true); auto.process(); assertEquals(1, db.commitments().all().size)
        memory.add(CommitmentEntity("bad-auto-fixture", "", "Synthetic invalid fixture", 0, zone.id, "Test"))
        auto.process(); assertNull(db.commitments().get("bad-auto-fixture"))
        assertTrue(auto.status.value.contains("failed")); assertEquals(1, memory.drafts.value.size)
    } }
    @Test fun newSessionMatchesSavedEventWithoutDuplicatingOrPromotingAttendance() = runBlocking { fixture { settings, memory, db, reminders, auto ->
        val now = Instant.now()
        memory.appendAttributedLive(party, now, zone, Speaker.UNKNOWN); auto.process()
        val original = db.commitments().all().single()
        val next = TemporaryMemory()
        val restarted = AutomaticMemory(context, settings, next, db.commitments(), reminders)
        next.appendAttributedLive(party, now.plusSeconds(20), zone, Speaker.USER); restarted.process()
        assertEquals(listOf(original), db.commitments().all()); assertTrue(next.drafts.value.isEmpty())
        next.appendAttributedLive("It ends at midnight.", now.plusSeconds(30), zone, Speaker.USER); restarted.process()
        val updated = db.commitments().all().single()
        assertEquals(original.id, updated.id); assertTrue(updated.informational); assertNull(updated.dueAt)
        assertNotNull(updated.activityEndAt)
    } }
    @Test fun incomingMessageCallbackSavesInBackground() = runBlocking { fixture { settings, memory, db, _, auto ->
        val enabled = settings.messageNotifications.value; val apps = settings.messageApps.value
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            settings.setMessageNotifications(true); settings.setMessageApp("synthetic.auto.messages", true)
            val inbox = MessageInbox(settings, memory, scope) { auto.process() }
            inbox.ingest(MessageEnvelope("synthetic.auto.messages", "synthetic conversation", listOf(IncomingText("There's a party tomorrow at 8pm. It ends at midnight.", System.currentTimeMillis()))))
            assertTrue(db.commitments().all().single().informational)
            assertNotNull(db.commitments().all().single().activityEndAt)
        } finally {
            scope.cancel(); settings.setMessageNotifications(enabled)
            settings.setMessageApp("synthetic.auto.messages", "synthetic.auto.messages" in apps)
        }
    } }
    @Test fun undoJournalSurvivesDatabaseReopen() = runBlocking {
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val name = "automatic-reopen-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, MemoryDatabase::class.java, name).build()
        var db = open()
        val settings = GuidanceSettings(context); val previous = settings.handsOffMode.value
        var id: String? = null
        try {
            settings.setHandsOff(true)
            val memory = TemporaryMemory(); val reminders = ReminderCoordinator(context, db.commitments())
            memory.appendAttributedLive(party, Instant.now(), zone, Speaker.UNKNOWN)
            AutomaticMemory(context, settings, memory, db.commitments(), reminders).process()
            id = db.commitments().all().single().id
            val token = db.commitments().change(id)!!.token
            db.close(); db = open()
            val restarted = AutomaticMemory(context, settings, TemporaryMemory(), db.commitments(), ReminderCoordinator(context, db.commitments()))
            assertTrue(restarted.undo(id, token)); assertTrue(db.commitments().all().isEmpty())
        } finally {
            id?.let { context.getSystemService(NotificationManager::class.java).cancel(it, AutomaticMemory.NOTIFICATION) }
            settings.setHandsOff(previous); db.close(); context.deleteDatabase(name)
        }
    }
    @Test fun versionFiveMigrationPreservesExistingItems() {
        migration.createDatabase("automatic-upgrade-test", 4).apply {
            execSQL("INSERT INTO commitments (id,title,evidence,capturedAt,zoneId,reviewNote,state,informational) VALUES ('kept','Party','Original words',123,'America/Chicago','Saved','ACCEPTED',1)")
            close()
        }
        migration.runMigrationsAndValidate("automatic-upgrade-test", 5, true, MemoryDatabase.MIGRATION_4_5).use { db ->
            db.query("SELECT evidence,informational FROM commitments WHERE id='kept'").use { assertTrue(it.moveToFirst()); assertEquals("Original words", it.getString(0)); assertEquals(1, it.getInt(1)) }
            db.query("SELECT * FROM automatic_changes").use { assertEquals(0, it.count) }
        }
    }
    @Test fun notificationUndoWorksWithoutOpeningAnActivity() = runBlocking {
        val app = context.applicationContext as SidekickApplication
        assumeTrue(app.packageName.endsWith(".benchmark"))
        val previous = app.guidance.handsOffMode.value
        var id: String? = null
        try {
            app.guidance.setHandsOff(true)
            app.temporary.appendAttributedLive("There's a workshop tomorrow at 9pm at Synthetic Test Hall.", Instant.now(), zone, Speaker.UNKNOWN)
            app.automation.process()
            val item = app.database.commitments().all().single { it.placeName == "Synthetic Test Hall" }
            id = item.id
            val notification = context.getSystemService(NotificationManager::class.java).activeNotifications.single { it.tag == item.id && it.id == AutomaticMemory.NOTIFICATION }
            notification.notification.actions.single { it.title.toString() == "Undo" }.actionIntent.send()
            withTimeout(5000) { while (app.database.commitments().get(item.id) != null) delay(25) }
            assertNull(app.database.commitments().change(item.id))
        } finally {
            app.guidance.setHandsOff(previous)
            id?.let { app.reminders.delete(it); app.temporary.retire(it); context.getSystemService(NotificationManager::class.java).cancel(it, AutomaticMemory.NOTIFICATION) }
        }
    }
    @Test fun realVadAndWhisperSaveAnEventAndEndCorrectionWithoutReview() = runBlocking { fixture { _, memory, db, _, auto ->
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val partyPcm = assets.open("quality-party.wav").use { WavAudio.decode(it.readBytes()) }
        val endPcm = assets.open("quality-short.wav").use { WavAudio.decode(it.readBytes()) }
        val pcm = partyPcm + FloatArray(24_000) + endPcm + FloatArray(24_000)
        partyPcm.fill(0f); endPcm.fill(0f)
        val speech = com.sidekick.app.speech.SpeechEngine(context)
        val now = Instant.now()
        com.sidekick.app.speech.NativeVad.open(com.sidekick.app.speech.VadModel.install(context))
        val processor = com.sidekick.app.audio.LiveSpeechProcessor(speech) { text, start, _ ->
            memory.appendAttributedLive(text, now.plusMillis(start * 1000 / 16000), zone, Speaker.UNKNOWN)
            auto.process()
        }
        try {
            coroutineScope {
                val worker = launch(Dispatchers.IO) { processor.consume() }
                for (start in pcm.indices step 4096) {
                    val frame = pcm.copyOfRange(start, minOf(start + 4096, pcm.size))
                    try { processor.accept(frame) } finally { frame.fill(0f) }
                }
                processor.finish(); withTimeout(60_000) { worker.join() }
            }
            val saved = db.commitments().all().single()
            assertTrue(saved.informational); assertNotNull(saved.activityEndAt)
            assertTrue(memory.drafts.value.isEmpty())
        } finally { processor.close(); pcm.fill(0f); com.sidekick.app.speech.NativeVad.close() }
    } }
    @Test fun restartedReplayDoesNotUndoACorrectedVenueAndNewVenueClearsAddress() = runBlocking { fixture { settings, memory, db, reminders, auto ->
        val now = Instant.now()
        memory.appendAttributedLive(party, now, zone, Speaker.UNKNOWN); auto.process()
        memory.appendAttributedLive("Actually it's at The Sylvee. It's at 25 S Livingston Street.", now.plusSeconds(10), zone, Speaker.UNKNOWN); auto.process()
        val corrected = db.commitments().all().single()
        assertEquals("The Sylvee", corrected.placeName); assertEquals("25 S Livingston Street", corrected.placeAddress)
        val next = TemporaryMemory()
        val restarted = AutomaticMemory(context, settings, next, db.commitments(), reminders)
        next.appendAttributedLive(party, now.plusSeconds(20), zone, Speaker.UNKNOWN); restarted.process()
        assertEquals(corrected, db.commitments().all().single())
        next.appendAttributedLive("Actually it's at New Test Hall.", now.plusSeconds(30), zone, Speaker.UNKNOWN); restarted.process()
        assertEquals("New Test Hall", db.commitments().all().single().placeName)
        assertNull(db.commitments().all().single().placeAddress)
    } }
}
