package com.sidekick.app

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.data.CommitmentEntity
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Names learned from plans, the read-aloud reminder option and the home-screen widget (November 18 additions). */
class NewOptionsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as SidekickApplication
    private var oldLearn = true; private var oldKeep = true; private var oldRead = false
    private var oldNames = emptySet<String>()
    private val id = "new-options-test-plan"

    @Before fun prepare() {
        check(app.packageName.endsWith(".compacttest") || app.packageName.endsWith(".benchmark"))
        check(Build.HARDWARE in setOf("ranchu", "goldfish") || InstrumentationRegistry.getArguments().getString("allowPhysicalInterface") == "true")
        val s = app.guidance
        oldLearn = s.learnNames.value; oldKeep = s.keepLearnedNames.value; oldRead = s.readRemindersAloud.value; oldNames = s.learnedNames.value
        s.setLearnNames(true); s.setKeepLearnedNames(true); s.clearLearnedNames()
    }
    @After fun restore() = runBlocking {
        app.database.commitments().delete(id)
        val s = app.guidance
        s.setLearnNames(oldLearn); s.setKeepLearnedNames(oldKeep); s.setReadRemindersAloud(oldRead); s.setLearnedNames(oldNames)
    }

    private fun waitFor(condition: () -> Boolean) { compose.waitUntil(10_000, condition) }

    @Test fun namesFromASavedPlanStayAfterItIsDeleted() = runBlocking {
        val now = System.currentTimeMillis()
        app.database.commitments().insertAll(listOf(CommitmentEntity(id, "세라랑 저녁", "내일 저녁에 세라랑 같이 밥 먹기로 했어요", now, "UTC", "",
            state = "ACCEPTED", activityStartAt = now + 86_400_000)))
        waitFor { "세라" in app.guidance.learnedNames.value }
        app.database.commitments().delete(id)
        Thread.sleep(1_000)
        assertTrue("kept after the plan is deleted", "세라" in app.guidance.learnedNames.value)
        app.guidance.setKeepLearnedNames(false)
        waitFor { "세라" !in app.guidance.learnedNames.value }
    }

    @Test fun plansInReviewTeachNoNames() = runBlocking {
        app.database.commitments().insertAll(listOf(CommitmentEntity(id, "제시카한테 전화", "제시카한테 전화해야 돼요", System.currentTimeMillis(), "UTC", "",
            state = "PROPOSED")))
        Thread.sleep(1_500)
        assertFalse("제시카" in app.guidance.learnedNames.value)
    }

    @Test fun settingsShowTheNewOptions() {
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("settings-page-PLANS").performScrollTo().performClick()
        compose.onNodeWithTag("read-reminders-aloud").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("read-reminders-aloud").performScrollTo().performClick()
        waitFor { app.guidance.readRemindersAloud.value }
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("settings-page-PRIVACY").performScrollTo().performClick()
        compose.onNodeWithTag("learn-names").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("keep-learned-names").performScrollTo().assertIsDisplayed()
    }

    @Test fun widgetDrawsWithoutError() = runBlocking {
        PlanWidget.render(app) // no widget placed: returns quietly; placed: redraws
        com.sidekick.app.reminders.ReminderVoice.speak(app, "Reminder: test")
    }
}
