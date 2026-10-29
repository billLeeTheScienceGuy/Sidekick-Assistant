package com.sidekick.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.data.MemoryDatabase
import com.sidekick.app.reminders.ReminderCoordinator
import com.sidekick.core.Speaker
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class LocalizedChangesTest {
    @Test fun oldEvidenceCannotOverrideAManuallyCorrectedEventIdentity() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val db = Room.inMemoryDatabaseBuilder(context, MemoryDatabase::class.java).build()
        val settings = GuidanceSettings(context); val previous = settings.handsOffMode.value
        val memory = TemporaryMemory(); val reminders = ReminderCoordinator(context, db.commitments())
        val zone = ZoneId.of("America/Chicago"); val now = Instant.now()
        val proposal = com.sidekick.app.data.CommitmentEntity("manually-corrected", "Concert", "我明天晚上八点参加会议。", now.toEpochMilli(), zone.id, "Test", activityStartAt = now.plusSeconds(86_400).toEpochMilli(), sourceKey = "voice:1:USER")
        try {
            settings.setHandsOff(true)
            reminders.save(proposal.id, proposal.title, null, zone.id, proposal, proposal.activityStartAt)
            val original = db.commitments().get(proposal.id)!!
            memory.appendAttributedLive("取消会议。", now.plusSeconds(10), zone, Speaker.USER)
            AutomaticMemory(context, settings, memory, db.commitments(), reminders).process()
            assertEquals(original, db.commitments().get(proposal.id))
        } finally { reminders.deleteAll(); db.close(); settings.setHandsOff(previous) }
    }
    private val cases = listOf(
        listOf("سأحضر الاجتماع غدا الساعة 8 مساء.", "انقل الاجتماع إلى غدا الساعة 9 مساء.", "ينتهي عند منتصف الليل.", "ألغي الاجتماع."),
        listOf("저는 내일 오후 8시에 회의에 참석할 거예요.", "회의를 내일 오후 9시로 변경해줘.", "자정에 끝나요.", "회의를 취소해줘."),
        listOf("我明天晚上八点参加会议。", "把会议改到明天晚上9点。", "午夜结束。", "取消会议。"),
        listOf("Je vais à la réunion demain à 20h.", "Déplace la réunion à demain à 21h.", "Ça finit à minuit.", "Annule la réunion."),
        listOf("Ich werde morgen um 20 Uhr zum Treffen gehen.", "Verschiebe das Treffen auf morgen um 21 Uhr.", "Es endet um Mitternacht.", "Das Treffen ist abgesagt."),
        listOf("Я пойду на встречу завтра в 20:00.", "Перенеси встречу на завтра в 21:00.", "Закончится в полночь.", "Отмени встречу.")
    )
    @Test fun allFiveLanguagesCreateMoveRefineCancelAndUndoPersonalEvents() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val settings = GuidanceSettings(context); val previous = settings.handsOffMode.value
        val zone = ZoneId.of("America/Chicago")
        settings.setHandsOff(true)
        try {
            for (lines in cases) {
                val db = Room.inMemoryDatabaseBuilder(context, MemoryDatabase::class.java).build()
                val memory = TemporaryMemory(); val reminders = ReminderCoordinator(context, db.commitments())
                val auto = AutomaticMemory(context, settings, memory, db.commitments(), reminders)
                val now = Instant.now()
                try {
                    memory.appendAttributedLive(lines[0], now, zone, Speaker.USER); auto.process()
                    val original = db.commitments().all().single()
                    assertFalse(lines[0], original.informational); assertEquals(original.activityStartAt, original.dueAt)
                    memory.appendAttributedLive(lines[1], now.plusSeconds(10), zone, Speaker.USER); auto.process()
                    val moved = db.commitments().all().single()
                    assertEquals(lines[1], original.activityStartAt!! + 3_600_000L, moved.activityStartAt)
                    assertEquals(moved.activityStartAt, moved.dueAt)
                    memory.appendAttributedLive(lines[2], now.plusSeconds(20), zone, Speaker.USER); auto.process()
                    val refined = db.commitments().all().single()
                    assertEquals(lines[2], 3 * 3_600_000L, refined.activityEndAt!! - refined.activityStartAt!!)
                    memory.appendAttributedLive(lines[3], now.plusSeconds(30), zone, Speaker.USER); auto.process()
                    val cancelled = db.commitments().all().single()
                    assertEquals(lines[3], "CANCELLED", cancelled.state); assertNull(cancelled.dueAt)
                    assertTrue(auto.undo(cancelled.id, db.commitments().change(cancelled.id)!!.token))
                    assertEquals(refined, db.commitments().all().single())
                } finally { reminders.deleteAll(); db.close() }
            }
        } finally { settings.setHandsOff(previous) }
    }
    @Test fun chineseMessageChangesStayWithinTheirConversation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val settings = GuidanceSettings(context)
        val previous = settings.handsOffMode.value; val enabled = settings.messageNotifications.value; val apps = settings.messageApps.value
        val db = Room.inMemoryDatabaseBuilder(context, MemoryDatabase::class.java).build()
        val reminders = ReminderCoordinator(context, db.commitments()); val memory = TemporaryMemory()
        val auto = AutomaticMemory(context, settings, memory, db.commitments(), reminders)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            settings.setHandsOff(true); settings.setMessageNotifications(true); settings.setMessageApp("synthetic.language", true)
            val inbox = MessageInbox(settings, memory, scope) { auto.process() }; val now = System.currentTimeMillis()
            suspend fun send(conversation: String, text: String, offset: Long) = inbox.ingest(MessageEnvelope("synthetic.language", conversation, listOf(IncomingText(text, now + offset))))
            send("A", "明天晚上八点有会议。", 0)
            val original = db.commitments().all().single()
            send("B", "取消会议。", 1); assertEquals(original, db.commitments().all().single())
            send("A", "把会议改到明天晚上9点。", 2)
            assertEquals(original.activityStartAt!! + 3_600_000L, db.commitments().all().single().activityStartAt)
            send("A", "取消会议。", 3)
            assertEquals("CANCELLED", db.commitments().all().single().state)
            assertTrue(db.commitments().all().single().informational)
        } finally {
            scope.cancel(); reminders.deleteAll(); db.close(); settings.setHandsOff(previous)
            settings.setMessageNotifications(enabled); settings.setMessageApp("synthetic.language", "synthetic.language" in apps)
        }
    }
}
