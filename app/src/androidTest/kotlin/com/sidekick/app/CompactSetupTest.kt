package com.sidekick.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.sidekick.core.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class CompactSetupTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun setupExplainsMissingPacksAndNeverStartsTheMicrophone() {
        val app = compose.activity.application as SidekickApplication
        assumeTrue(app.packageName.endsWith(".compacttest"))
        val oldLanguage = app.guidance.speechLanguage.value; val oldQuality = app.guidance.speechQuality.value
        try {
            app.guidance.setSpeechLanguage(SpeechLanguage.ENGLISH); app.guidance.setSpeechQuality(SpeechQuality.FAST)
            compose.onNodeWithTag("get-started").performClick()
            compose.onNodeWithTag("model-readiness").performScrollTo().assertTextEquals("Selected speech mode: ready")
            compose.onNodeWithTag("setup-language-choice").performScrollTo().performClick()
            compose.onNodeWithTag("setup-language-ar").performClick()
            compose.onNodeWithTag("setup-quality-choice").performScrollTo().performClick()
            compose.onNodeWithTag("setup-quality-ADAPTIVE").performClick()
            compose.onNodeWithTag("model-readiness").performScrollTo().assertTextContains("Needed", substring = true)
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithTag("main-list").performScrollToNode(hasText("Start live notes"))
            compose.onNodeWithText("Start live notes").assertIsNotEnabled()
            assertFalse(com.sidekick.app.audio.ListeningService.state.value.recording)
        } finally { app.guidance.setSpeechLanguage(oldLanguage); app.voiceProfile.selectLanguage(oldLanguage); app.guidance.setSpeechQuality(oldQuality) }
    }
}
