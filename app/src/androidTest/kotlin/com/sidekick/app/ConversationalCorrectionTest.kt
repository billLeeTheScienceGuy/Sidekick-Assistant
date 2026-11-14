package com.sidekick.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.data.MemoryDatabase
import com.sidekick.app.reminders.*
import com.sidekick.core.Speaker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.*

class ConversationalCorrectionTest {
    @Test fun aMatchedVoiceUpdatesOneSavedPartyAcrossLanguagesAndUnpunctuatedCorrections() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val settings = GuidanceSettings(context); val before = settings.handsOffMode.value
        val db = Room.inMemoryDatabaseBuilder(context, MemoryDatabase::class.java).build()
        val memory = TemporaryMemory()
        val reminders = ReminderCoordinator(context, db.commitments(), object : ReminderAlarms {
            override fun arm(item: com.sidekick.app.data.CommitmentEntity) { }
            override fun cancel(item: com.sidekick.app.data.CommitmentEntity) { }
        })
        val auto = AutomaticMemory(context, settings, memory, db.commitments(), reminders)
        val at = Instant.now(); val zone = ZoneId.of("America/New_York")
        val day = at.atZone(zone).toLocalDate().plusDays(1)
        try {
            settings.setHandsOff(true)
            for ((index, text) in listOf(
                "The party starts tomorrow at 8pm at The James.",
                "내일 있을 파 티는 자 정에 끝날 예정이에요.",
                "change tomorrow’s party to nine p m the party will be at memorial union instead of the james"
            ).withIndex()) {
                memory.appendAttributedLive(text, at.plusSeconds(index * 6L), zone, Speaker.USER, false, "spk_test_owner", "Voice 1")
                auto.process()
            }
            val saved = db.commitments().all().filter { it.state == "ACCEPTED" }.single()
            assertEquals(day.atTime(21, 0).atZone(zone).toInstant().toEpochMilli(), saved.activityStartAt)
            assertEquals(day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), saved.activityEndAt)
            assertEquals("memorial union", saved.placeName?.lowercase())
        } finally { settings.setHandsOff(before); db.close() }
    }
}
