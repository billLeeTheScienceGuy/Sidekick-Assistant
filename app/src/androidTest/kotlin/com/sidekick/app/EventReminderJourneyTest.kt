package com.sidekick.app

import android.Manifest
import android.app.*
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.reminders.*
import com.sidekick.core.Speaker
import java.time.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class EventReminderJourneyTest {
    @Test fun eventCorrectionCancelUndoAndRealReminderReceiverStayConsistent() = runBlocking {
        val i = InstrumentationRegistry.getInstrumentation()
        val app = i.targetContext.applicationContext as SidekickApplication
        assumeTrue(app.packageName.endsWith(".benchmark"))
        if (Build.VERSION.SDK_INT >= 33) i.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val previous = app.guidance.handsOffMode.value; var id: String? = null
        val manager = app.getSystemService(NotificationManager::class.java)
        try {
            app.guidance.setHandsOff(true); app.temporary.discard()
            val now = Instant.now(); val zone = ZoneId.systemDefault()
            app.temporary.appendAttributedLive("I'll attend a workshop tomorrow at 8pm at Receiver Journey Hall.", now, zone, Speaker.USER, false)
            app.automation.process()
            val original = app.database.commitments().all().single { it.placeName == "Receiver Journey Hall" }; id = original.id
            assertFalse(original.informational); assertEquals(original.activityStartAt, original.dueAt)
            app.temporary.appendAttributedLive("Move it to 9pm.", now.plusSeconds(1), zone, Speaker.USER, false); app.automation.process()
            val moved = app.database.commitments().get(id)!!
            assertEquals(original.dueAt!! + 3600000, moved.dueAt)
            val oldToken = app.database.commitments().change(id)!!.token
            app.temporary.appendAttributedLive("Cancel it.", now.plusSeconds(2), zone, Speaker.USER, false); app.automation.process()
            assertEquals("CANCELLED", app.database.commitments().get(id)!!.state)
            assertFalse(app.automation.undo(id, oldToken))
            assertTrue(app.automation.undo(id, app.database.commitments().change(id)!!.token))
            assertEquals(moved, app.database.commitments().get(id))
            val due = System.currentTimeMillis() + 2000
            app.reminders.save(id, moved.title, due, moved.zoneId, activityStartAt = moved.activityStartAt, placeName = moved.placeName)
            val pending = PendingIntent.getBroadcast(app, 0, Intent(app, ReminderReceiver::class.java).setAction(ReminderCoordinator.DELIVER)
                .setData(Uri.parse("sidekick://reminder/$id")), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            assertNotNull(pending)
            delay((due - System.currentTimeMillis() + 100).coerceAtLeast(0))
            pending!!.send()
            withTimeout(5000) { while (app.database.commitments().get(id)!!.deliveredAt == null || manager.activeNotifications.none { it.tag == id && it.id == 10 }) delay(30) }
            val delivered = app.database.commitments().get(id)!!.deliveredAt
            pending.send(); delay(200)
            assertEquals(delivered, app.database.commitments().get(id)!!.deliveredAt)
            app.reminders.mark(id, "DONE"); pending.send(); delay(200)
            assertEquals("DONE", app.database.commitments().get(id)!!.state)
            assertNull(app.database.commitments().get(id)!!.dueAt)
            assertTrue(manager.activeNotifications.none { it.tag == id && it.id == 10 })
        } finally {
            id?.let { app.reminders.delete(it) }; app.temporary.discard(); app.guidance.setHandsOff(previous)
        }
    }
}
