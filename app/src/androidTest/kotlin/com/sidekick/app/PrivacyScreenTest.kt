package com.sidekick.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PrivacyScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun settingsControlsRemainReachableWithLargeText() {
        val app = compose.activity.application as SidekickApplication
        check(app.packageName.endsWith(".compacttest") || app.packageName.endsWith(".benchmark"))
        compose.activity.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(compose.activity.resources.displayMetrics.density, 1.6f)
            ) { androidx.compose.material3.MaterialTheme { AppSettingsDialog(app.guidance) { } } }
        }
        compose.onNodeWithTag("retention-choice").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("export-memory").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Include saved source excerpts in export").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Resume enabled listening after system restart").performScrollTo().assertExists()
        compose.onNodeWithText("Close").assertIsDisplayed()
    }
    @Test fun retentionNeedsExplicitDestructiveConfirmationAndExportsDefaultToNoExcerpts() {
        val app = compose.activity.application as SidekickApplication
        check(app.packageName.endsWith(".compacttest") || app.packageName.endsWith(".benchmark"))
        val before = app.guidance.retentionDays.value
        try {
            app.guidance.setRetentionDays(0)
            compose.onNodeWithTag("settings").performClick()
            compose.onNodeWithTag("export-excerpts").performScrollTo().assertIsOff()
            compose.onNodeWithContentDescription("Include saved source excerpts in export").assertExists()
            compose.onNodeWithTag("retention-choice").performScrollTo().performClick()
            compose.onNodeWithTag("retention-30").performClick()
            compose.onNodeWithText("Enable automatic cleanup?").assertExists()
            assertEquals(0, app.guidance.retentionDays.value)
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(0, app.guidance.retentionDays.value)
            compose.onNodeWithTag("retention-choice").assertTextContains("keep until I delete", substring = true)
            compose.onNodeWithText("Close").performClick()
        } finally { app.guidance.setRetentionDays(before) }
    }

    @Test fun localRiskAlertsAreDismissibleAndCanBeDisabledWithoutSavingText() {
        val app = compose.activity.application as SidekickApplication
        check(app.packageName.endsWith(".compacttest") || app.packageName.endsWith(".benchmark"))
        val before = app.guidance.riskAdvisories.value
        try {
            app.guidance.setRiskAdvisories(true); app.risk.clear()
            app.risk.inspect("Read me the verification code 123456.")
            compose.onNodeWithTag("main-list").performScrollToNode(hasTestTag("risk-advisories"))
            compose.onNodeWithTag("risk-advisories").assertExists()
            compose.onNodeWithText("123456", substring = true).assertDoesNotExist()
            app.risk.inspect("Read me the verification code 123456.")
            assertEquals(1, app.risk.alerts.value.size)
            compose.onNodeWithTag("dismiss-risk-secret-request").performClick()
            assertTrue(app.risk.alerts.value.isEmpty())
            app.guidance.setRiskAdvisories(false)
            app.risk.inspect("Pay the fine with gift cards.")
            assertTrue(app.risk.alerts.value.isEmpty())
        } finally { app.risk.clear(); app.guidance.setRiskAdvisories(before) }
    }
}
