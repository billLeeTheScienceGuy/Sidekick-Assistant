package com.sidekick.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.sidekick.core.Speaker
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class UncertainUpdateScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun editorRequiresConfirmationAndReplacesOneDraft() {
        val app = compose.activity.application as SidekickApplication
        assumeTrue(app.packageName.endsWith(".benchmark"))
        val previousMode = app.guidance.handsOffMode.value
        app.guidance.setHandsOff(false)
        val before = app.temporary.drafts.value.map { it.id }.toSet()
        val now = Instant.now()
        var target: String? = null
        try {
            app.temporary.appendAttributedLive("I'll go to the party tomorrow at 8pm.", now, ZoneId.of("America/Chicago"), Speaker.USER)
            val original = app.temporary.drafts.value.single { it.id !in before }
            target = original.id
            app.temporary.appendAttributedLive("It ends at midnight.", now.plusSeconds(10), ZoneId.of("America/Chicago"), Speaker.UNKNOWN)
            val revision = app.temporary.drafts.value.single { app.temporary.updateTarget(it.id) == original.id }
            compose.onNodeWithTag("planner-tab").performScrollTo().performClick()
            compose.onNodeWithTag("main-list").performScrollToNode(hasTestTag("review-${revision.id}"))
            compose.onNodeWithTag("review-${revision.id}").performScrollTo().performClick()
            compose.onNode(hasText(revision.reviewNote) and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed()
            assertNull(runBlocking { app.database.commitments().get(original.id) })
            compose.onNodeWithText("Confirm & apply update").performClick()
            compose.waitUntil(5000) { runBlocking { app.database.commitments().get(original.id) != null } }
            assertEquals(revision.activityEndAt, runBlocking { app.database.commitments().get(original.id)!!.activityEndAt })
            assertTrue(app.temporary.drafts.value.none { it.id == original.id || it.id == revision.id })
            assertNull(runBlocking { app.database.commitments().get(revision.id) })
        } finally {
            app.temporary.drafts.value.filter { it.id !in before }.forEach { app.temporary.remove(it.id) }
            target?.let { runBlocking { app.database.commitments().delete(it) } }
            app.temporary.clearText()
            app.guidance.setHandsOff(previousMode)
        }
    }
}
