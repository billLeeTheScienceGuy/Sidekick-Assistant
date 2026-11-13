package com.sidekick.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.sidekick.app.data.CommitmentEntity
import com.sidekick.core.Speaker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.time.*

class SummaryScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun savedFactsAndTemporaryHighlightsStaySeparateAndEditingUpdatesTheDigest() {
        val app = compose.activity.application as SidekickApplication
        assumeTrue(app.packageName.endsWith(".benchmark") || app.packageName.endsWith(".compacttest"))
        val previous = app.guidance.handsOffMode.value
        val prefix = "summary-fixture-"; val now = Instant.now(); val zone = ZoneId.systemDefault()
        val plan = CommitmentEntity(prefix + "plan", "Summary fixture plan", "Synthetic", now.toEpochMilli(), zone.id, "Fixture",
            state = "ACCEPTED", activityStartAt = now.toEpochMilli(), informational = false)
        val info = plan.copy(id = prefix + "info", title = "Summary fixture information", informational = true)
        try {
            app.guidance.setHandsOff(false); app.temporary.discard()
            runBlocking { app.database.commitments().insertAll(listOf(plan, info)) }
            app.temporary.appendAttributedLive("There's a workshop tomorrow at 8pm.", now, zone, Speaker.UNKNOWN, false)
            compose.onNodeWithTag("summary-tab").performClick()
            compose.onNodeWithTag("main-list").performScrollToNode(hasTestTag("summary-${plan.id}"))
            compose.onNodeWithTag("summary-${plan.id}").assertExists()
            compose.onNodeWithTag("main-list").performScrollToNode(hasTestTag("summary-${info.id}"))
            compose.onNodeWithTag("summary-${info.id}").assertExists()
            app.temporary.drafts.value.forEach { compose.onNodeWithTag("summary-${it.id}").assertDoesNotExist() }
            compose.onNodeWithTag("main-list").performScrollToNode(hasTestTag("summary-excerpt-0"))
            compose.onNodeWithTag("summary-excerpt-0").performScrollTo().assertTextContains("workshop", substring = true)
            compose.onNodeWithTag("clear-conversation").performScrollTo().performClick()
            compose.onNodeWithTag("summary-excerpt-0").assertDoesNotExist()
            assertNotNull(runBlocking { app.database.commitments().get(plan.id) })
            // Editing the derived card uses the existing editor and updates persisted memory.
            compose.onNodeWithTag("main-list").performScrollToNode(hasTestTag("summary-${plan.id}"))
            compose.onNodeWithTag("summary-edit-${plan.id}").performScrollTo().performClick()
            compose.onNodeWithTag("edit-title").performTextReplacement("Summary edited plan")
            compose.onNodeWithText("Save & accept").performClick()
            compose.waitUntil(5000) { runBlocking { app.database.commitments().get(plan.id)?.title == "Summary edited plan" } }
            compose.onNodeWithText("Summary edited plan").assertExists()
        } finally {
            app.temporary.discard(); app.guidance.setHandsOff(previous)
            runBlocking { listOf(plan.id, info.id).forEach { app.reminders.delete(it) } }
        }
    }
}
