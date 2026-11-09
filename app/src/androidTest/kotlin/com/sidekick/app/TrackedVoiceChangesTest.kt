package com.sidekick.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.data.MemoryDatabase
import com.sidekick.app.reminders.*
import com.sidekick.core.Speaker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TrackedVoiceChangesTest {
    @Test fun onlyTheSameAnonymousSpeakerCanCancelTheirInformationOnlyEvent() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".compacttest") || context.packageName.endsWith(".benchmark"))
        val settings = GuidanceSettings(context); val before = settings.handsOffMode.value
        val db = Room.inMemoryDatabaseBuilder(context, MemoryDatabase::class.java).build()
        val memory = TemporaryMemory()
        val reminders = ReminderCoordinator(context, db.commitments(), object : ReminderAlarms {
            override fun arm(item: com.sidekick.app.data.CommitmentEntity) { }
            override fun cancel(item: com.sidekick.app.data.CommitmentEntity) { }
        })
        val auto = AutomaticMemory(context, settings, memory, db.commitments(), reminders)
        val now = Instant.now(); val zone = ZoneId.systemDefault()
        try {
            settings.setHandsOff(true)
            memory.appendAttributedLive("There's a party tomorrow at 8pm.", now, zone, Speaker.OTHER, false, "spk_test_1", "Voice 1")
            auto.process()
            val initial = db.commitments().all().single()
            assertTrue(initial.informational)
            memory.appendAttributedLive("Cancel it.", now.plusSeconds(1), zone, Speaker.UNKNOWN, false, "spk_test_unassigned1", "Unassigned")
            auto.process(); assertEquals("ACCEPTED", db.commitments().get(initial.id)!!.state)
            memory.appendAttributedLive("Cancel it.", now.plusSeconds(2), zone, Speaker.OTHER, false, "spk_test_2", "Voice 2")
            auto.process(); assertEquals("ACCEPTED", db.commitments().get(initial.id)!!.state)
            memory.appendAttributedLive("Cancel it.", now.plusSeconds(3), zone, Speaker.OTHER, false, "spk_test_1", "Voice 1")
            auto.process(); assertEquals("CANCELLED", db.commitments().get(initial.id)!!.state)
            assertNull(db.commitments().get(initial.id)!!.dueAt)

            memory.appendAttributedLive("There's a concert tomorrow at 7pm.", now.plusSeconds(4), zone, Speaker.OTHER, false, "spk_test_1", "Voice 1")
            auto.process()
            val concert = db.commitments().all().single { it.state == "ACCEPTED" }
            memory.appendAttributedLive("There's a meeting tomorrow at 2pm.", now.plusSeconds(5), zone, Speaker.OTHER, false, "spk_test_2", "Voice 2")
            auto.process()
            val meeting = db.commitments().all().single { it.id != concert.id && it.state == "ACCEPTED" }
            memory.appendAttributedLive("Cancel it.", now.plusSeconds(6), zone, Speaker.OTHER, false, "spk_test_1", "Voice 1")
            auto.process()
            assertEquals("CANCELLED", db.commitments().get(concert.id)!!.state)
            assertEquals("ACCEPTED", db.commitments().get(meeting.id)!!.state)
            memory.appendAttributedLive("There's a party tomorrow at 8pm.", now.plusSeconds(7), zone, Speaker.OTHER, false, "spk_test_2", "Voice 2")
            auto.process()
            assertEquals(2, db.commitments().all().count { it.evidence.substringBefore('\n') == "There's a party tomorrow at 8pm." })
            assertEquals("CANCELLED", db.commitments().get(initial.id)!!.state)
        } finally { settings.setHandsOff(before); db.close() }
    }
}
