package com.sidekick.app

import android.app.NotificationManager
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.core.Speaker
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class UndoPlannerTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun oneTapReminderCheckSchedulesOneLabelledEventAndPlannerCanUndoIt() {
        val i = InstrumentationRegistry.getInstrumentation()
        val app = i.targetContext.applicationContext as SidekickApplication
        assumeTrue(app.packageName.endsWith(".benchmark"))
        if (android.os.Build.VERSION.SDK_INT >= 33) i.uiAutomation.grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        val id = com.sidekick.app.reminders.ReminderCoordinator.CHECK_ID
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            runBlocking { app.reminders.delete(id) }
            scenario = ActivityScenario.launch(Intent(app, MainActivity::class.java))
            compose.onNodeWithTag("settings").performClick()
            compose.onNodeWithTag("reminder-alert-details").assertExists()
            compose.onNodeWithTag("test-reminder").performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { app.database.commitments().get(id) != null } }
            val item = runBlocking { app.database.commitments().get(id)!! }
            assertEquals("SideKick reminder check", item.title)
            assertEquals("ACCEPTED", item.state)
            assertTrue(item.dueAt!! > System.currentTimeMillis() + 60_000)
            assertNotNull(runBlocking { app.database.commitments().change(id) })
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithTag("planner-tab").performClick()
            compose.onNodeWithTag("main-list").performScrollToNode(hasTestTag("undo-$id"))
            compose.onNodeWithTag("undo-$id").performClick()
            compose.waitUntil(5000) { runBlocking { app.database.commitments().get(id) == null } }
        } finally { scenario?.close(); runBlocking { app.reminders.delete(id) } }
    }
    @Test fun dismissedNotificationDoesNotRemovePlannerUndo() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SidekickApplication
        assumeTrue(app.packageName.endsWith(".benchmark"))
        val previous = app.guidance.handsOffMode.value; var id: String? = null
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            app.guidance.setHandsOff(true); app.temporary.discard()
            runBlocking {
                app.temporary.appendAttributedLive("There's a workshop tomorrow at 8pm at Planner Undo Test Hall.", Instant.now(), ZoneId.systemDefault(), Speaker.UNKNOWN)
                app.automation.process()
                id = app.database.commitments().all().single { it.placeName == "Planner Undo Test Hall" }.id
            }
            app.getSystemService(NotificationManager::class.java).cancel(id, AutomaticMemory.NOTIFICATION)
            scenario = ActivityScenario.launch(Intent(app, MainActivity::class.java))
            compose.onNodeWithTag("planner-tab").performClick()
            compose.onNodeWithTag("main-list").performScrollToNode(hasTestTag("undo-$id"))
            compose.onNodeWithTag("undo-$id").performClick()
            compose.waitUntil(5000) { runBlocking { app.database.commitments().get(id!!) == null } }
            compose.onNodeWithTag("undo-$id").assertDoesNotExist()
        } finally {
            scenario?.close(); id?.let { runBlocking { app.reminders.delete(it) } }
            app.temporary.discard(); app.guidance.setHandsOff(previous)
        }
    }
}
