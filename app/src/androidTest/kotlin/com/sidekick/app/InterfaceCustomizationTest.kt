package com.sidekick.app

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import com.sidekick.app.audio.ListeningService

class InterfaceCustomizationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as SidekickApplication
    private lateinit var oldDesign: InterfaceDesign
    private lateinit var oldText: NoteTextSize
    private lateinit var oldSize: InterfaceSize
    private lateinit var oldAppearance: AppearanceMode
    private var oldManual = true
    private var oldHome = false
    @Before fun prepare() {
        check(app.packageName.endsWith(".compacttest") || app.packageName.endsWith(".benchmark"))
        check(Build.HARDWARE in setOf("ranchu", "goldfish") || InstrumentationRegistry.getArguments().getString("allowPhysicalInterface") == "true")
        val s = app.guidance
        oldDesign = s.interfaceDesign.value; oldText = s.noteTextSize.value; oldSize = s.interfaceSize.value
        oldAppearance = s.appearance.value; oldManual = s.manualNotes.value; oldHome = s.oneScreenHome.value
        s.resetAppearance(); s.setManualNotes(true); s.setOneScreenHome(false)
    }
    @After fun restore() {
        val s = app.guidance
        s.setInterfaceDesign(oldDesign); s.setNoteTextSize(oldText); s.setInterfaceSize(oldSize)
        s.setAppearance(oldAppearance); s.setManualNotes(oldManual); s.setOneScreenHome(oldHome)
    }
    @Test fun designAndSizeChoicesPersistAndResetWithoutChangingFeatures() {
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("settings-page-DISPLAY").performScrollTo().performClick()
        compose.onNodeWithTag("design-WARM").performScrollTo().performClick()
        compose.onNodeWithTag("text-size-LARGE").performScrollTo().performClick()
        compose.onNodeWithTag("interface-size-LARGE").performScrollTo().performClick()
        val saved = GuidanceSettings(app)
        assertEquals(InterfaceDesign.WARM, saved.interfaceDesign.value)
        assertEquals(NoteTextSize.LARGE, saved.noteTextSize.value)
        assertEquals(InterfaceSize.LARGE, saved.interfaceSize.value)
        compose.onNodeWithTag("manual-notes").performScrollTo().performClick()
        compose.onNodeWithTag("reset-appearance").performScrollTo().performClick()
        assertEquals(InterfaceDesign.CALM, app.guidance.interfaceDesign.value)
        assertEquals(NoteTextSize.PHONE, app.guidance.noteTextSize.value)
        assertEquals(InterfaceSize.PHONE, app.guidance.interfaceSize.value)
        assertFalse("Appearance reset preserves feature choices", app.guidance.manualNotes.value)
        compose.onNodeWithText("Close").performClick()
    }
    @Test fun manualNotesCanBeHiddenWithoutDisablingListeningOrPlanner() {
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("settings-page-DISPLAY").performScrollTo().performClick()
        compose.onNodeWithTag("manual-notes").performScrollTo().performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("transcript").assertDoesNotExist()
        compose.onNodeWithTag("manual-add").assertDoesNotExist()
        compose.onNodeWithTag("main-list").performScrollToNode(hasText("Start listening"))
        compose.onNodeWithText("Start listening").assertIsDisplayed()
        compose.onNodeWithTag("planner-tab").performClick()
        compose.onNodeWithTag("manual-add").assertDoesNotExist()
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("settings-page-DISPLAY").performScrollTo().performClick()
        compose.onNodeWithTag("manual-notes").performScrollTo().performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("main-list").performScrollToNode(hasTestTag("manual-add"))
        compose.onNodeWithTag("manual-add").assertIsDisplayed()
        assertFalse(ListeningService.state.value.recording)
    }
    @Test fun interfaceScaleChangesTargetsIndependentlyOfTextScale() {
        fun button() = compose.onNodeWithTag("main-list").performScrollToNode(hasText("Start listening"))
        fun textPixels(): Float {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText("Start listening", useUnmergedTree = true)
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val input = layouts.single().layoutInput
            return with(input.density) { input.style.fontSize.toPx() }
        }
        button()
        val before = compose.onNodeWithText("Start listening").fetchSemanticsNode().boundsInRoot.height
        val textBefore = textPixels()
        app.guidance.setInterfaceSize(InterfaceSize.EXTRA_LARGE)
        compose.waitForIdle(); button()
        val after = compose.onNodeWithText("Start listening").fetchSemanticsNode().boundsInRoot.height
        val textAfter = textPixels()
        assertTrue("Controls should grow", after > before * 1.15f)
        // Layout-box padding/rounding may change with density; compare resolved font pixels.
        assertEquals("UI scaling alone preserves font size", textBefore, textAfter, 1f)
        app.guidance.setNoteTextSize(NoteTextSize.EXTRA_LARGE)
        compose.waitForIdle(); button()
        assertTrue(textPixels() > textBefore)
    }
    @Test fun oneScreenHomeKeepsEssentialsVisibleAndDetailsAvailable() {
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("settings-page-DISPLAY").performScrollTo().performClick()
        compose.onNodeWithTag("home-simple").performScrollTo().performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("one-screen-home").assertIsDisplayed()
        compose.onNodeWithTag("compact-listen").assertIsDisplayed()
        compose.onNodeWithTag("compact-details").assertIsDisplayed().performClick()
        compose.onNodeWithTag("main-list").assertIsDisplayed()
        compose.onNodeWithText("Back to simple home").performScrollTo().performClick()
        compose.onNodeWithTag("planner-tab").performClick()
        compose.onNodeWithTag("main-list").assertIsDisplayed()
        compose.onNodeWithTag("capture-tab").performClick()
        compose.onNodeWithTag("compact-listen").assertIsDisplayed()
        assertTrue(GuidanceSettings(app).oneScreenHome.value)
        assertFalse(ListeningService.state.value.recording)
    }
}
