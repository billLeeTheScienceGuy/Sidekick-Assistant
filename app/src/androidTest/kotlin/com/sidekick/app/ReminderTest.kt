package com.sidekick.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.data.CommitmentEntity
import com.sidekick.app.data.MemoryDatabase
import com.sidekick.app.reminders.ReminderCoordinator
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderTest {
    @Test fun reminderDeliveryIsIdempotentAndDeletionRemovesNotification() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val db = Room.inMemoryDatabaseBuilder(context, MemoryDatabase::class.java).build()
        val coordinator = ReminderCoordinator(context, db.commitments())
        val id = "test-${UUID.randomUUID()}"
        val now = System.currentTimeMillis() - 1000
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            db.commitments().insertAll(listOf(CommitmentEntity(id, "SideKick test reminder", "Test fixture", now,
                "America/Chicago", "Test fixture", "ACCEPTED", now)))
            coordinator.deliver(id, now - 1) // Stale alarm must not deliver.
            assertNull(db.commitments().get(id)!!.deliveredAt)
            coordinator.deliver(id, now)
            val delivered = db.commitments().get(id)!!.deliveredAt
            assertNotNull(delivered)
            coordinator.deliver(id, now)
            assertEquals(delivered, db.commitments().get(id)!!.deliveredAt)
            withTimeout(3000) { while (manager.activeNotifications.none { it.tag == id }) delay(30) }
            coordinator.delete(id)
            assertNull(db.commitments().get(id))
            withTimeout(3000) { while (manager.activeNotifications.any { it.tag == id }) delay(30) }
        } finally { coordinator.delete(id); db.close() }
    }

    @Test fun savingAndCompletingCancelsReminderState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val db = Room.inMemoryDatabaseBuilder(context, MemoryDatabase::class.java).build()
        val coordinator = ReminderCoordinator(context, db.commitments())
        val id = "test-${UUID.randomUUID()}"
        val due = System.currentTimeMillis() + 120_000
        try {
            db.commitments().insertAll(listOf(CommitmentEntity(id, "Initial", "Test fixture", 0, "America/Chicago", "Test")))
            coordinator.save(id, "Updated", due, "America/Chicago", activityStartAt = due + 3_600_000, activityEndAt = due + 7_200_000)
            assertEquals("Updated", db.commitments().get(id)!!.title)
            assertEquals(due, db.commitments().get(id)!!.dueAt)
            assertEquals(due + 3_600_000, db.commitments().get(id)!!.activityStartAt)
            assertEquals(due + 7_200_000, db.commitments().get(id)!!.activityEndAt)
            coordinator.mark(id, "DONE")
            assertNull(db.commitments().get(id)!!.dueAt)
            coordinator.deliver(id, due)
            assertNull(db.commitments().get(id)!!.deliveredAt)
        } finally { coordinator.delete(id); db.close() }
    }
}
