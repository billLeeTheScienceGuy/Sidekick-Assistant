package com.sidekick.app

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.sidekick.core.SpeechLanguage
import com.sidekick.core.SpeechQuality
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ListeningSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as SidekickApplication
    private fun isolated() { check(app.packageName.endsWith(".compacttest") || app.packageName.endsWith(".benchmark")) }

    @Test fun settingsShowsCategoriesAndAutoDetectionRestoresManualLanguage() {
        isolated()
        val language = app.guidance.speechLanguage.value
        val quality = app.guidance.speechQuality.value
        try {
            app.guidance.setSpeechLanguage(SpeechLanguage.KOREAN)
            compose.onNodeWithTag("settings").performClick()
            compose.onNodeWithText("Recognition quality").assertDoesNotExist()
            compose.onNodeWithTag("settings-page-LISTENING").performScrollTo().performClick()
            compose.onNodeWithTag("auto-language").assertIsOff().performClick()
            assertEquals(SpeechLanguage.AUTO, GuidanceSettings(app).speechLanguage.value)
            assertEquals(SpeechQuality.ADAPTIVE, GuidanceSettings(app).speechQuality.value)
            compose.onNodeWithTag("language-choice").assertDoesNotExist()
            compose.onNodeWithTag("settings-back").performClick()
            compose.onNodeWithTag("settings-page-LISTENING").performScrollTo().performClick()
            compose.onNodeWithTag("auto-language").assertIsOn().performClick()
            assertEquals(SpeechLanguage.KOREAN, GuidanceSettings(app).speechLanguage.value)
            compose.onNodeWithTag("language-choice").assertIsDisplayed()
            assertFalse(com.sidekick.app.audio.ListeningService.state.value.recording)
        } finally { app.guidance.setSpeechLanguage(language); app.guidance.setSpeechQuality(quality); app.voiceProfile.selectLanguage(language) }
    }

    @Test fun startReminderPersistsOnlyOnStartAndCanBeRestoredInSettings() {
        isolated()
        val old = app.guidance.skipListeningIntro.value
        var starts = 0
        try {
            app.guidance.setSkipListeningIntro(false)
            compose.activity.setContent {
                var visible by remember { mutableStateOf(true) }
                MaterialTheme {
                    if (visible) ListeningStartDialog(app.guidance, true, false, { visible = false }, { starts++; visible = false })
                    else AppSettingsDialog(app.guidance) { }
                }
            }
            compose.onNodeWithText("Make sure everyone agrees.").assertIsDisplayed()
            compose.onNodeWithText("Voice matching does not verify consent.", substring = true).assertDoesNotExist()
            compose.onNodeWithTag("skip-listening-intro").performScrollTo().performClick()
            compose.onNodeWithText("Cancel").performClick()
            assertFalse(GuidanceSettings(app).skipListeningIntro.value)
            assertEquals(0, starts)
            compose.activity.setContent { MaterialTheme { ListeningStartDialog(app.guidance, true, false, {}, { starts++ }) } }
            compose.onNodeWithTag("skip-listening-intro").performScrollTo().performClick()
            compose.onNodeWithTag("confirm-listening").performClick()
            assertTrue(GuidanceSettings(app).skipListeningIntro.value)
            assertEquals(1, starts)
            // The test callback deliberately never opens a microphone.
            compose.activity.setContent { MaterialTheme { AppSettingsDialog(app.guidance) { } } }
            compose.onNodeWithTag("settings-page-LISTENING").performScrollTo().performClick()
            compose.onNodeWithTag("start-reminder").performScrollTo().assertIsOff().performClick()
            assertFalse(GuidanceSettings(app).skipListeningIntro.value)
            assertFalse(com.sidekick.app.audio.ListeningService.state.value.recording)
        } finally { app.guidance.setSkipListeningIntro(old) }
    }
}
