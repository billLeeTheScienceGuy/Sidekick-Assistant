package com.sidekick.app

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.speaker.VoiceEnrollmentActivity
import com.sidekick.core.SpeechLanguage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** For each app-language choice: the main screens open without a crash and their key nodes exist. Isolated test copy only; no microphone. */
class AppLanguageScreensTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as SidekickApplication
    private var oldTag = ""
    private var oldHome = true
    private var oldSpeech = SpeechLanguage.ENGLISH

    @Before fun isolated() {
        check(app.packageName.endsWith(".benchmark") || app.packageName.endsWith(".compacttest")) { "Run only in the test copy." }
        oldTag = AppLanguage.current(); oldHome = app.guidance.oneScreenHome.value; oldSpeech = app.guidance.speechLanguage.value
        app.guidance.setOneScreenHome(true); app.guidance.setSpeechLanguage(SpeechLanguage.ENGLISH)
    }
    @After fun restore() {
        AppLanguage.set(context, oldTag)
        app.guidance.setOneScreenHome(oldHome); app.guidance.setSpeechLanguage(oldSpeech)
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun scrollTo(tag: String) { waitFor(tag); compose.onNodeWithTag(tag).performScrollTo().assertExists() }

    @Test fun everyLanguageOpensTheMainScreens() {
        for (tag in AppLanguage.tags) {
            AppLanguage.set(context, tag)
            assertEquals(tag, AppLanguage.current())
            // The choice is read back from the system (Android 13+) or the private preference.
            AppLanguage.refresh(context)
            assertEquals(tag, AppLanguage.current())
            ActivityScenario.launch(MainActivity::class.java).use {
                waitFor("one-screen-home"); compose.onNodeWithTag("compact-listen").assertExists()
                // Plans
                compose.onNodeWithTag("planner-tab").performClick()
                compose.onNodeWithTag("main-list").performScrollToNode(hasTestTag("planner-search"))
                compose.onNodeWithTag("planner-search").assertExists()
                // More, quick start
                compose.onNodeWithTag("more-tab").performClick()
                waitFor("more-content")
                scrollTo("get-started"); compose.onNodeWithTag("get-started").performClick()
                scrollTo("model-readiness"); scrollTo("setup-language-choice")
                compose.onAllNodesWithText(tr(R.string.done)).onLast().performClick()
                compose.waitForIdle()
                waitFor("more-tab"); compose.onNodeWithTag("more-tab").performClick()
                scrollTo("more-settings"); compose.onNodeWithTag("more-settings").performClick()
                for (page in listOf("SCREEN", "LISTENING", "VOICE", "PLANS", "PRIVACY")) {
                    scrollTo("settings-page-$page"); compose.onNodeWithTag("settings-page-$page").performClick()
                    waitFor("settings-back")
                    when (page) {
                        "SCREEN" -> { scrollTo("app-language-system"); scrollTo("app-language-de"); scrollTo("home-simple") }
                        "LISTENING" -> scrollTo("auto-language")
                        "PRIVACY" -> scrollTo("crash-reports-toggle")
                    }
                    compose.onNodeWithTag("settings-back").performClick()
                }
            }
            for (action in listOf(VoiceEnrollmentActivity.CONSENT_ONLY, VoiceEnrollmentActivity.WAKE_ONLY)) {
                ActivityScenario.launch<VoiceEnrollmentActivity>(Intent(context, VoiceEnrollmentActivity::class.java).setAction(action)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use {
                    compose.waitUntil(10_000) {
                        listOf("voice-consent", "consent-name", "wake-setup", "wake-check").any { t -> compose.onAllNodesWithTag(t).fetchSemanticsNodes().isNotEmpty() }
                    }
                }
            }
        }
    }
}
