package com.sidekick.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.compose.setContent
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.audio.ListeningState
import org.junit.*
import org.junit.Assert.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** The one-time "pause when resting" suggestion after 3 hours of listening. */
class RestingTipTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SidekickApplication
    private var oldPause = false; private var oldDone = false

    @Before fun prepare() {
        check(app.packageName.endsWith(".compacttest") || app.packageName.endsWith(".benchmark"))
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish") || InstrumentationRegistry.getArguments().getString("allowPhysicalInterface") == "true")
        oldPause = app.guidance.pauseWhenResting.value; oldDone = app.guidance.restingTipDone.value
        app.guidance.setPauseWhenResting(false)
        app.guidance.resetRestingTip()
    }
    @After fun restore() {
        app.guidance.setPauseWhenResting(oldPause); if (oldDone) app.guidance.setRestingTipDone() else app.guidance.resetRestingTip()
    }

    @Test fun shownAfterThreeHoursAndTurnsTheOptionOn() {
        var listening by androidx.compose.runtime.mutableStateOf(ListeningState(recording = true, seconds = RESTING_TIP_AFTER_SECONDS - 60))
        compose.runOnUiThread { compose.activity.setContent { RestingTip(listening) } }
        compose.onNodeWithTag("resting-tip").assertDoesNotExist()
        listening = ListeningState(recording = true, seconds = RESTING_TIP_AFTER_SECONDS)
        compose.onNodeWithTag("resting-tip").assertIsDisplayed()
        compose.onNodeWithTag("resting-tip-on").performClick()
        compose.waitUntil(5_000) { app.guidance.pauseWhenResting.value && app.guidance.restingTipDone.value }
        compose.onNodeWithTag("resting-tip").assertDoesNotExist()
    }
}
