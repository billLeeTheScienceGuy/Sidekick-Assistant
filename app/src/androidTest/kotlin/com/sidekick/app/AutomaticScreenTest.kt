package com.sidekick.app

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class AutomaticScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun sharedActivitySavesWithoutAnApprovalDialogInHandsOffMode() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SidekickApplication
        assumeTrue(app.packageName.endsWith(".benchmark"))
        val previous = app.guidance.handsOffMode.value
        val sharing = app.guidance.sharedMessages.value
        var savedId: String? = null
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            app.guidance.setSharedMessages(true); app.guidance.setHandsOff(true)
            assertTrue(GuidanceSettings(app).handsOffMode.value)
            scenario = ActivityScenario.launch(Intent(app, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "There's a workshop tomorrow at 9pm at Automatic Share Test Hall."))
            compose.waitUntil(5000) { runBlocking {
                savedId = app.database.commitments().all().singleOrNull { it.placeName == "Automatic Share Test Hall" }?.id
                savedId != null
            } }
            val saved = runBlocking { app.database.commitments().get(savedId!!)!! }
            assertTrue(saved.informational); assertNull(saved.dueAt)
            compose.onNodeWithText("Shared message").assertDoesNotExist()
            compose.onNodeWithTag("automation-status").performScrollTo().assertTextContains("Hands-off mode", substring = true)
        } finally {
            savedId?.let { runBlocking { app.reminders.delete(it) }; app.temporary.retire(it) }
            app.guidance.setHandsOff(previous); app.guidance.setSharedMessages(sharing)
            scenario?.close()
        }
    }
}
