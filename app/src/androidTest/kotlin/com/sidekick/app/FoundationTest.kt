package com.sidekick.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidekick.app.data.CommitmentEntity
import com.sidekick.app.data.MemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationTest {
    private var handsOff = false
    @org.junit.Before fun reviewMode() {
        val app = compose.activity.application as SidekickApplication
        org.junit.Assume.assumeTrue(app.packageName.endsWith(".benchmark"))
        handsOff = app.guidance.handsOffMode.value; app.guidance.setHandsOff(false); app.temporary.discard()
    }
    @org.junit.After fun restoreMode() {
        val app = compose.activity.application as SidekickApplication
        app.temporary.discard(); app.guidance.setHandsOff(handsOff)
    }
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun textRequiresConsentAndCreatesReviewableMemory() {
        compose.onNodeWithTag("main-list").performScrollToNode(hasText("Use an example"))
        compose.onNodeWithText("Use an example").performClick()
        compose.onNodeWithText("Find commitments").assertIsNotEnabled()
        compose.onNodeWithTag("consent").performScrollTo().performClick()
        compose.onNodeWithText("Find commitments").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("2 temporary proposal(s) ready for review. Approve to save.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        // The text flow works without asking for microphone access.
        assertFalse(com.sidekick.app.audio.ListeningService.state.value.recording)
    }

    @Test fun acceptedMemorySurvivesDatabaseReopenAndCanBeDeleted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, MemoryDatabase::class.java, name).build()
        var db = open()
        try {
            db.commitments().insertAll(listOf(CommitmentEntity("test", "Call Emma", "I'll call Emma", 123L, "America/Chicago", "Review date")))
            db.commitments().review("test", "ACCEPTED")
            db.close()
            db = open()
            assertEquals("ACCEPTED", db.commitments().observe().first().single().state)
            assertEquals("I'll call Emma", db.commitments().observe().first().single().evidence)
            db.commitments().deleteAll()
            assertTrue(db.commitments().observe().first().isEmpty())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
