package com.sidekick.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.data.*
import com.sidekick.app.reminders.ReminderCoordinator
import com.sidekick.core.*
import java.time.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class EventChangeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("America/Chicago")
    private val party = "I'll go to the party tomorrow at 8pm at The James. It ends at midnight."
    private suspend fun fixture(block: suspend (GuidanceSettings, TemporaryMemory, CommitmentDao, ReminderCoordinator, AutomaticMemory) -> Unit) {
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val db = Room.inMemoryDatabaseBuilder(context, MemoryDatabase::class.java).build()
        val settings = GuidanceSettings(context); val previous = settings.handsOffMode.value
        val memory = TemporaryMemory(); val reminders = ReminderCoordinator(context, db.commitments())
        try { settings.setHandsOff(true); block(settings, memory, db.commitments(), reminders, AutomaticMemory(context, settings, memory, db.commitments(), reminders)) }
        finally { db.commitments().all().forEach { reminders.delete(it.id) }; settings.setHandsOff(previous); db.close() }
    }
    @Test fun movesCancelsAndUndoesWithReminderDurationAndVenueIntact() = runBlocking { fixture { _, memory, dao, reminders, auto ->
        val now = Instant.now()
        memory.appendAttributedLive(party, now, zone, Speaker.USER); auto.process()
        val original = dao.all().single()
        memory.appendAttributedLive("Move the party to 9pm.", now.plusSeconds(10), zone, Speaker.USER); auto.process()
        val moved = dao.all().single()
        assertEquals(original.activityStartAt!! + 3_600_000L, moved.activityStartAt)
        assertEquals(original.activityEndAt!! + 3_600_000L, moved.activityEndAt)
        assertEquals(moved.activityStartAt, moved.dueAt); assertEquals("The James", moved.placeName)
        val moveToken = dao.change(moved.id)!!.token
        memory.appendAttributedLive("The party is cancelled.", now.plusSeconds(20), zone, Speaker.USER); auto.process()
        val cancelled = dao.all().single()
        assertEquals("CANCELLED", cancelled.state); assertNull(cancelled.dueAt)
        reminders.deliver(cancelled.id, moved.dueAt!!)
        assertNull(dao.get(cancelled.id)!!.deliveredAt)
        assertFalse(auto.undo(moved.id, moveToken))
        val token = dao.change(cancelled.id)!!.token
        assertTrue(auto.undo(cancelled.id, token)); assertEquals(moved, dao.all().single())
    } }
    @Test fun pronounsExpireAndUncertainVoiceCannotCancelPersonalPlan() = runBlocking { fixture { _, memory, dao, _, auto ->
        val now = Instant.now()
        memory.appendAttributedLive(party, now, zone, Speaker.USER); auto.process()
        val original = dao.all().single()
        memory.appendAttributedLive("Cancel it.", now.plusSeconds(200), zone, Speaker.USER); auto.process()
        assertEquals(original, dao.all().single())
        memory.appendAttributedLive("Cancel the party.", now.plusSeconds(210), zone, Speaker.UNKNOWN); auto.process()
        assertEquals(original, dao.all().single())
    } }
    @Test fun ambiguousNamedEventDoesNotChangeEitherEvent() = runBlocking { fixture { _, memory, dao, _, auto ->
        val now = Instant.now()
        memory.appendAttributedLive("There's a meeting tomorrow at 2pm. There's a meeting tomorrow at 4pm.", now, zone, Speaker.UNKNOWN); auto.process()
        val original = dao.all(); assertEquals(2, original.size)
        memory.appendAttributedLive("The meeting is cancelled.", now.plusSeconds(10), zone, Speaker.UNKNOWN); auto.process()
        assertEquals(original.toSet(), dao.all().toSet())
    } }
    @Test fun originalTranscriptReplayAfterRestartCannotRestoreOldTimeOrCancelledEvent() = runBlocking { fixture { settings, memory, dao, reminders, auto ->
        val now = Instant.now()
        memory.appendAttributedLive(party, now, zone, Speaker.UNKNOWN); auto.process()
        memory.appendAttributedLive("Move the party to 9pm.", now.plusSeconds(10), zone, Speaker.UNKNOWN); auto.process()
        val moved = dao.all().single()
        val next = TemporaryMemory(); val restarted = AutomaticMemory(context, settings, next, dao, reminders)
        next.appendAttributedLive(party, now.plusSeconds(20), zone, Speaker.UNKNOWN); restarted.process()
        assertEquals(moved, dao.all().single())
        next.appendAttributedLive("Cancel the party.", now.plusSeconds(30), zone, Speaker.UNKNOWN); restarted.process()
        val cancelled = dao.all().single()
        val third = TemporaryMemory()
        third.appendAttributedLive(party, now.plusSeconds(40), zone, Speaker.UNKNOWN)
        AutomaticMemory(context, settings, third, dao, reminders).process()
        assertEquals(cancelled, dao.all().single()); assertTrue(third.drafts.value.isEmpty())
    } }
    @Test fun messageChangesStayInTheirConversationAndRepeatedNotificationsDoNotReapply() = runBlocking { fixture { settings, memory, dao, _, auto ->
        val previous = settings.messageNotifications.value; val apps = settings.messageApps.value
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            settings.setMessageNotifications(true); settings.setMessageApp("synthetic.changes", true)
            val inbox = MessageInbox(settings, memory, scope) { auto.process() }
            val now = System.currentTimeMillis()
            suspend fun send(conversation: String, text: String, at: Long) = inbox.ingest(MessageEnvelope("synthetic.changes", conversation, listOf(IncomingText(text, at))))
            send("A", "There's a party tomorrow at 8pm.", now)
            val original = dao.all().single()
            send("B", "Cancel the party.", now + 1)
            assertEquals(original, dao.all().single())
            send("A", "Move the party to 9pm.", now + 2)
            val moved = dao.all().single(); assertNotEquals(original.activityStartAt, moved.activityStartAt)
            val token = dao.change(moved.id)!!.token
            send("A", "Move the party to 9pm.", now + 2)
            assertEquals(token, dao.change(moved.id)!!.token)
            send("A", "Cancel it.", now + 3)
            assertEquals("CANCELLED", dao.all().single().state)
            assertNull(dao.all().single().dueAt)
        } finally { scope.cancel(); settings.setMessageNotifications(previous); settings.setMessageApp("synthetic.changes", "synthetic.changes" in apps) }
    } }
    @Test fun staleResolvedChangeCannotOverwriteManualEditAndReminderLeadTimeMoves() = runBlocking { fixture { _, memory, dao, reminders, auto ->
        memory.appendAttributedLive(party, Instant.now(), zone, Speaker.USER); auto.process()
        val original = dao.all().single()
        reminders.save(original.id, original.title, original.activityStartAt!! - 600_000, zone.id,
            activityStartAt = original.activityStartAt, activityEndAt = original.activityEndAt)
        assertFalse(reminders.changeEvent(original, original.activityStartAt + 3_600_000, false))
        val edited = dao.get(original.id)!!
        assertTrue(reminders.changeEvent(edited, edited.activityStartAt!! + 3_600_000, false))
        assertEquals(600_000L, dao.get(original.id)!!.activityStartAt!! - dao.get(original.id)!!.dueAt!!)
    } }
    @Test fun aFreshEndCorrectionAfterReschedulingUsesTheNewEventDate() = runBlocking { fixture { _, memory, dao, _, auto ->
        val now = Instant.now()
        memory.appendAttributedLive(party, now, zone, Speaker.USER); auto.process()
        memory.appendAttributedLive("Move the party to 9pm.", now.plusSeconds(10), zone, Speaker.USER); auto.process()
        memory.appendAttributedLive("It ends at midnight.", now.plusSeconds(20), zone, Speaker.USER); auto.process()
        val item = dao.all().single()
        assertEquals(3 * 3_600_000L, item.activityEndAt!! - item.activityStartAt!!)
        assertEquals("The James", item.placeName)
    } }
    @Test fun aLaterExplicitMoveCanReturnToAnEarlierRequestedTime() = runBlocking { fixture { _, memory, dao, _, auto ->
        val now = Instant.now()
        memory.appendAttributedLive(party, now, zone, Speaker.USER); auto.process()
        for ((index, clock) in listOf("9pm", "10pm", "9pm").withIndex()) {
            memory.appendAttributedLive("Move the party to $clock.", now.plusSeconds((index + 1) * 10L), zone, Speaker.USER); auto.process()
        }
        assertEquals(21, Instant.ofEpochMilli(dao.all().single().activityStartAt!!).atZone(zone).hour)
    } }
    @Test fun pacedRealVadAndAdaptiveSpeechMoveAndCancelWithoutLosingNegation() = runBlocking { fixture { _, memory, dao, _, auto ->
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val clips = listOf("quality-party.wav", "command-0-1.wav", "command-0-3.wav", "command-0-0.wav")
        val speech = com.sidekick.app.speech.SpeechEngine(context, quality = { SpeechQuality.ADAPTIVE })
        val now = Instant.now()
        val outputs = mutableListOf<String>()
        com.sidekick.app.speech.NativeVad.open(com.sidekick.app.speech.VadModel.install(context))
        val processor = com.sidekick.app.audio.LiveSpeechProcessor(speech) { text, sample, latency ->
            outputs += text
            println("ADAPTIVE_STREAM latency=$latency; output=$text")
            memory.appendAttributedLive(text, now.plusMillis(sample * 1000 / 16000), zone, Speaker.UNKNOWN); auto.process()
            if (text.contains("not cancel", true)) assertEquals("ACCEPTED", dao.all().single().state)
        }
        try {
            coroutineScope {
                val worker = launch(Dispatchers.IO) { processor.consume() }
                for (clip in clips) {
                    val pcm = assets.open(clip).use { WavAudio.decode(it.readBytes()) } + FloatArray(24_000)
                    try {
                        for (start in pcm.indices step 4096) {
                            val frame = pcm.copyOfRange(start, minOf(start + 4096, pcm.size))
                            try { processor.accept(frame) } finally { frame.fill(0f) }
                            delay(256)
                        }
                    } finally { pcm.fill(0f) }
                }
                processor.finish(); withTimeout(30_000) { worker.join() }
            }
            val event = dao.all().single()
            assertEquals(outputs.joinToString(" | "), "CANCELLED", event.state)
            assertTrue(event.informational); assertNull(event.dueAt)
            assertEquals(21, Instant.ofEpochMilli(event.activityStartAt!!).atZone(zone).hour)
            assertTrue(outputs.any { it.contains("not cancel", true) })
        } finally { processor.close(); com.sidekick.app.speech.NativeVad.close() }
    } }
}
