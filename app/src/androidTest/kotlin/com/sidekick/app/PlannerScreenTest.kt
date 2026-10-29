package com.sidekick.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.sidekick.app.data.CommitmentEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class PlannerScreenTest {
    private var handsOff = false
    @org.junit.Before fun reviewMode() {
        val app = compose.activity.application as SidekickApplication
        assumeTrue(app.packageName.endsWith(".benchmark"))
        handsOff = app.guidance.handsOffMode.value; app.guidance.setHandsOff(false); app.temporary.discard()
    }
    @org.junit.After fun restoreMode() {
        val app = compose.activity.application as SidekickApplication
        app.temporary.discard(); app.guidance.setHandsOff(handsOff)
    }
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun speechLanguagePersistsAndShowsItsLocalTranscriptionScope() {
        val app = compose.activity.application as SidekickApplication
        assumeTrue(app.packageName.endsWith(".benchmark"))
        val previous = app.guidance.speechLanguage.value
        try {
            compose.onNodeWithTag("settings").performScrollTo().performClick()
            compose.onNodeWithTag("speech-language-ko").performScrollTo().performClick()
            assertEquals(com.sidekick.core.SpeechLanguage.KOREAN, GuidanceSettings(app).speechLanguage.value)
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithText("Speech detection · 한국어 · Korean · on-device transcription").performScrollTo().assertIsDisplayed()
        } finally { app.guidance.setSpeechLanguage(previous) }
    }
    @Test fun voiceSetupIsReachableWithoutOpeningMicrophone() {
        val app = compose.activity.application as SidekickApplication
        assumeTrue(app.packageName.endsWith(".benchmark"))
        compose.onNodeWithTag("settings").performScrollTo().performClick()
        compose.onNodeWithText("Set up my voice").performScrollTo().performClick()
        compose.onNodeWithText("Start voice setup").performScrollTo().assertIsDisplayed()
        assertFalse(com.sidekick.app.audio.ListeningService.state.value.recording)
        compose.onNodeWithText("Back to SideKick").performScrollTo().performClick()
    }
    @Test fun sharingRequiresConfirmationAndNeverSavesSenderPlansAutomatically() {
        val app = compose.activity.application as SidekickApplication
        assumeTrue(app.packageName.endsWith(".benchmark"))
        val previousSharing = app.guidance.sharedMessages.value
        val previousInclude = app.guidance.includeInformational.value
        val before = app.temporary.drafts.value.map { it.id }.toSet()
        val savedBefore = runBlocking { app.database.commitments().all().map { it.id }.toSet() }
        val text = "There's a party ${java.time.LocalDate.now().plusDays(1)} at 8pm at The James. It ends at midnight."
        fun share() = compose.activityRule.scenario.onActivity { activity ->
            val original = activity.intent
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(activity,
                android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text))
            activity.intent = original
        }
        try {
            app.guidance.setSharedMessages(false)
            share()
            compose.onNodeWithText("Find activity suggestions").assertIsNotEnabled()
            compose.onNodeWithText("Discard").performClick()
            app.guidance.setSharedMessages(true); app.guidance.setIncludeInformational(true)
            share()
            compose.onNodeWithText("Find activity suggestions").performClick()
            compose.waitUntil(5000) { app.temporary.drafts.value.any { it.id !in before && it.sourceKey == "shared" } }
            val suggestion = app.temporary.drafts.value.single { it.id !in before && it.sourceKey == "shared" }
            assertTrue(suggestion.informational); assertEquals("The James", suggestion.placeName)
            assertNotNull(suggestion.activityEndAt)
            assertEquals(savedBefore, runBlocking { app.database.commitments().all().map { it.id }.toSet() })
        } finally {
            app.guidance.setSharedMessages(previousSharing); app.guidance.setIncludeInformational(previousInclude)
            app.temporary.drafts.value.filter { it.id !in before }.forEach { app.temporary.remove(it.id) }
        }
    }

    @Test fun modesPersistAndUnknownEndNeverBlocksApproval() {
        val app = compose.activity.application as SidekickApplication
        assumeTrue(app.packageName.endsWith(".benchmark"))
        val previous = app.guidance.mode.value
        val fixture = CommitmentEntity("guidance-fixture", "Guidance fixture party", "Synthetic fixture", System.currentTimeMillis(), "America/Chicago", "Test")
        try {
            app.temporary.add(fixture)
            compose.onNodeWithTag("settings").performScrollTo().performClick()
            val allowed = compose.activity.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            compose.onNodeWithTag("location-permission-status").assertTextEquals("Location permission: ${if (allowed) "Allowed" else "Not allowed"}")
            compose.onNodeWithTag("mode-ask").performScrollTo().performClick()
            compose.onNodeWithText("Close").performClick()
            assertEquals(GuidanceMode.ASK, GuidanceSettings(app).mode.value)
            compose.onNodeWithTag("planner-tab").performScrollTo().performClick()
            compose.onNodeWithTag("planner-search").performScrollTo().performTextInput("Guidance fixture")
            compose.onNodeWithText("Review & accept").performScrollTo().performClick()
            compose.onNodeWithText("When do you think this will last till?").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("I don’t know").performScrollTo().performClick()
            compose.onNodeWithText("When do you think this will last till?").assertDoesNotExist()
            compose.onNodeWithText("Save & accept").performClick()
            compose.waitUntil(5000) { runBlocking { app.database.commitments().get(fixture.id) != null } }
            val saved = runBlocking { app.database.commitments().get(fixture.id)!! }
            assertNull(saved.activityStartAt); assertNull(saved.activityEndAt); assertNull(saved.dueAt)
            compose.onNodeWithTag("settings").performScrollTo().performClick()
            compose.onNodeWithTag("mode-passive").performScrollTo().performClick()
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithTag("filter-UNSCHEDULED").performScrollTo().performClick()
            compose.onNodeWithTag("main-list").performScrollToNode(hasTestTag("review-${fixture.id}"))
            compose.onNodeWithTag("review-${fixture.id}").performClick()
            compose.onNodeWithText("When do you think this will last till?").assertDoesNotExist()
            compose.onNodeWithTag("pick-activity-end").assertDoesNotExist()
            compose.onNodeWithTag("optional-activity").assertExists()
            compose.onNodeWithText("Cancel").performClick()
        } finally {
            app.guidance.setMode(previous); app.temporary.remove(fixture.id)
            runBlocking { app.database.commitments().delete(fixture.id) }
        }
    }

    @Test fun filtersSearchAndReminderEditorWorkWithoutTouchingPersonalData() {
        val app = compose.activity.application as SidekickApplication
        assumeTrue(app.packageName.endsWith(".benchmark"))
        val dao = app.database.commitments()
        val prefix = "planner-fixture-"
        val now = System.currentTimeMillis()
        val draft = CommitmentEntity(prefix + "review", "Planner fixture review", "Test fixture", now,
            "America/Chicago", "Temporary test proposal")
        try {
            runBlocking {
                dao.insertAll(listOf(
                    draft.copy(id = prefix + "scheduled", title = "Planner fixture scheduled", state = "ACCEPTED", dueAt = now + 3_600_000),
                    draft.copy(id = prefix + "undated", title = "Planner fixture undated", state = "ACCEPTED"),
                    draft.copy(id = prefix + "done", title = "Planner fixture done", state = "DONE"),
                ))
            }
            app.temporary.add(draft)
            compose.onNodeWithTag("planner-tab").performClick()
            compose.onNodeWithTag("planner-search").performScrollTo().performTextInput("Planner fixture")
            compose.onNodeWithText(draft.title).performScrollTo().assertExists()
            compose.onNodeWithTag("filter-REMINDERS").performScrollTo().performClick()
            compose.onNodeWithTag("main-list").performScrollToNode(hasText("Planner fixture scheduled"))
            compose.onNodeWithText("Planner fixture scheduled").assertExists()
            compose.onNodeWithText(draft.title).assertDoesNotExist()
            compose.onNodeWithTag("filter-UNSCHEDULED").performScrollTo().performClick()
            compose.onNodeWithTag("main-list").performScrollToNode(hasText("Planner fixture undated"))
            compose.onNodeWithText("Planner fixture undated").assertExists()
            compose.onNodeWithTag("filter-HISTORY").performScrollTo().performClick()
            compose.onNodeWithTag("main-list").performScrollToNode(hasText("Planner fixture done"))
            compose.onNodeWithText("Planner fixture done").assertExists()
            compose.onNodeWithTag("filter-REVIEW").performScrollTo().performClick()
            compose.onNodeWithText("Review & accept").performScrollTo().performClick()
            compose.onNodeWithTag("pick-reminder").assertExists()
            compose.onNodeWithText("In 1 hour").performScrollTo().performClick()
            compose.onNodeWithTag("edit-time").assertTextContains("20", substring = true)
            compose.onNodeWithText("No reminder").performScrollTo().performClick()
            compose.onNodeWithTag("edit-time").assertTextEquals("Not set")
            compose.onNodeWithTag("optional-activity").performScrollTo().performClick()
            compose.onNodeWithTag("pick-activity-start").performScrollTo().assertExists()
            compose.onNodeWithTag("pick-activity-end").performScrollTo().assertExists()
            compose.onNodeWithTag("check-calendar").performScrollTo().performClick()
            compose.onNodeWithText("Enter valid start and end times", substring = true).performScrollTo().assertExists()
            compose.onNodeWithText("Cancel").performClick()
            assertNotNull(app.temporary.drafts.value.find { it.id == draft.id })
        } finally {
            app.temporary.remove(draft.id)
            runBlocking { listOf("scheduled", "undated", "done").forEach { dao.delete(prefix + it) } }
        }
    }
}
