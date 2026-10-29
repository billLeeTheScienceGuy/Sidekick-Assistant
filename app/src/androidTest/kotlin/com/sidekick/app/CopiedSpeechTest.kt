package com.sidekick.app

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.core.Speaker
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class CopiedSpeechTest {
    @Test fun copiedSuccessfulCorrectionsDoNotReplayOrReplaceSuccessWithAnAmbiguityWarning() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as SidekickApplication
        assumeTrue(app.packageName.endsWith(".benchmark"))
        val previous = app.guidance.handsOffMode.value
        val store = ViewModelStore(); var id: String? = null
        lateinit var model: MemoryViewModel
        try {
            app.guidance.setHandsOff(true); app.temporary.discard()
            val now = Instant.now(); val zone = ZoneId.systemDefault()
            app.temporary.appendAttributedLive("There's a workshop tomorrow at 8pm at Replay Status Test Hall.", now, zone, Speaker.UNKNOWN, false)
            app.automation.process()
            val original = app.database.commitments().all().single { it.placeName == "Replay Status Test Hall" }; id = original.id
            for ((index, text) in listOf("Move it to 9pm.", "Cancel it.").withIndex()) {
                app.temporary.appendAttributedLive(text, now.plusSeconds(index + 1L), zone, Speaker.UNKNOWN, false); app.automation.process()
            }
            val token = app.database.commitments().change(original.id)!!.token
            assertEquals("Event cancelled automatically", app.automation.status.value)
            repeat(2) {
                instrumentation.runOnMainSync {
                    model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[MemoryViewModel::class.java]
                    model.copyLiveText(); model.analyze()
                }
                withTimeout(30_000) { while (model.busy.value) delay(25) }
                assertEquals("Event cancelled automatically", app.automation.status.value)
                assertEquals(app.automation.status.value, model.message.value)
                assertEquals(token, app.database.commitments().change(original.id)!!.token)
                assertEquals("CANCELLED", app.database.commitments().get(original.id)!!.state)
            }
            // A genuinely new live command is not suppressed by copied-note deduplication.
            app.temporary.appendAttributedLive("Move it to 9pm.", now.plusSeconds(5), zone, Speaker.UNKNOWN, false)
            assertEquals(1, app.temporary.takeChanges().size)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            id?.let { app.reminders.delete(it) }; app.temporary.discard(); app.guidance.setHandsOff(previous)
        }
    }
    @Test fun copiedAndCorrectedLiveNotesUpdateTheirOriginalEventWithoutDuplicatingIt() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as SidekickApplication
        assumeTrue(app.packageName.endsWith(".benchmark"))
        val previous = app.guidance.handsOffMode.value
        val store = ViewModelStore()
        lateinit var model: MemoryViewModel
        var id: String? = null
        try {
            app.guidance.setHandsOff(true); app.temporary.discard()
            val now = Instant.now(); val zone = ZoneId.systemDefault()
            app.temporary.appendAttributedLive("There's a workshop tomorrow at 8pm at Copied Speech Test Hall.", now, zone, Speaker.UNKNOWN)
            app.automation.process()
            val original = app.database.commitments().all().single { it.placeName == "Copied Speech Test Hall" }
            id = original.id
            app.temporary.appendAttributedLive("Move it to 9pm.", now.plusSeconds(1), zone, Speaker.UNKNOWN)
            app.automation.process()
            app.temporary.appendAttributedLive("Cancellate.", now.plusSeconds(2), zone, Speaker.UNKNOWN)
            app.automation.process()
            instrumentation.runOnMainSync {
                model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[MemoryViewModel::class.java]
                model.copyLiveText(); model.analyze()
            }
            withTimeout(30_000) { while (model.busy.value) delay(25) }
            val replayed = app.database.commitments().all().filter { it.placeName == "Copied Speech Test Hall" }
            assertEquals(1, replayed.size); assertEquals(original.id, replayed.single().id)
            assertEquals(original.activityStartAt!! + 3_600_000L, replayed.single().activityStartAt)
            instrumentation.runOnMainSync {
                model.setTranscript(model.transcript.value.replace("Cancellate.", "Cancel it.")); model.analyze()
            }
            withTimeout(30_000) { while (model.busy.value) delay(25) }
            assertEquals("CANCELLED", app.database.commitments().get(original.id)!!.state)
            assertTrue(app.database.commitments().get(original.id)!!.informational)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            id?.let { app.reminders.delete(it) }; app.temporary.discard(); app.guidance.setHandsOff(previous)
        }
    }
}
