package com.sidekick.app

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.audio.ListeningService
import com.sidekick.core.SpeechLanguage
import com.sidekick.core.SpeechQuality
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Isolated UI checks; physical execution requires an explicit test argument. No microphone. */
class ResponsiveInterfaceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as SidekickApplication
    private lateinit var appearance: AppearanceMode
    private lateinit var language: SpeechLanguage
    private lateinit var quality: SpeechQuality
    private lateinit var design: InterfaceDesign
    private lateinit var textSize: NoteTextSize
    private lateinit var interfaceSize: InterfaceSize
    private var simple = false
    private var manual = true
    @Before fun isolated() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish") || InstrumentationRegistry.getArguments().getString("allowPhysicalInterface") == "true")
        check(app.packageName.endsWith(".benchmark") || app.packageName.endsWith(".compacttest"))
        appearance = app.guidance.appearance.value; language = app.guidance.speechLanguage.value; quality = app.guidance.speechQuality.value
        design = app.guidance.interfaceDesign.value; textSize = app.guidance.noteTextSize.value; interfaceSize = app.guidance.interfaceSize.value
        simple = app.guidance.oneScreenHome.value; manual = app.guidance.manualNotes.value
        app.guidance.setInterfaceDesign(InterfaceDesign.CALM); app.guidance.setNoteTextSize(NoteTextSize.PHONE); app.guidance.setInterfaceSize(InterfaceSize.PHONE)
        app.guidance.setOneScreenHome(false); app.guidance.setManualNotes(true)
        app.guidance.setAppearance(AppearanceMode.LIGHT)
        app.guidance.setSpeechLanguage(SpeechLanguage.ENGLISH); app.guidance.setSpeechQuality(SpeechQuality.FAST)
        app.temporary.discard()
    }
    @After fun restore() {
        app.guidance.setAppearance(appearance); app.guidance.setSpeechLanguage(language); app.guidance.setSpeechQuality(quality)
        app.guidance.setInterfaceDesign(design); app.guidance.setNoteTextSize(textSize); app.guidance.setInterfaceSize(interfaceSize)
        app.guidance.setOneScreenHome(simple); app.guidance.setManualNotes(manual)
        app.temporary.discard()
    }
    @Test fun navigationStaysReachableAndDraftSurvivesDestinations() {
        compose.onNodeWithTag("main-list").performScrollToNode(hasTestTag("transcript"))
        compose.onNodeWithTag("transcript").performTextInput("Interface draft, not an event.")
        compose.activity.runOnUiThread {
            androidx.core.view.WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
                .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("planner-tab").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("planner-tab").performClick()
        compose.onNodeWithTag("main-list").performScrollToNode(hasTestTag("planner-search"))
        compose.onNodeWithTag("planner-search").assertIsDisplayed()
        compose.onNodeWithTag("summary-tab").performClick()
        compose.onNodeWithTag("summary-content").assertExists()
        compose.onNodeWithTag("capture-tab").performClick()
        compose.onNodeWithTag("main-list").performScrollToNode(hasTestTag("transcript"))
        compose.onNodeWithTag("transcript").assertTextContains("Interface draft, not an event.")
        assertFalse(ListeningService.state.value.recording)
    }
    @Test fun appearancePersistsAndCanFollowTheSystemAgain() {
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("settings-page-DISPLAY").performScrollTo().performClick()
        compose.onNodeWithTag("appearance-DARK").performClick().assertIsSelected()
        assertEquals(AppearanceMode.DARK, GuidanceSettings(app).appearance.value)
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("settings-page-DISPLAY").performScrollTo().performClick()
        compose.onNodeWithTag("appearance-DARK").assertIsSelected()
        compose.onNodeWithTag("appearance-SYSTEM").performClick().assertIsSelected()
        assertEquals(AppearanceMode.SYSTEM, GuidanceSettings(app).appearance.value)
        compose.onNodeWithText("Close").performClick()
    }
    @Test fun sessionAndModelDetailsAreOptionalAndStillReachable() {
        compose.onNodeWithText("New times use phone timezone:", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("main-list").performScrollToNode(hasTestTag("session-details"))
        compose.onNodeWithTag("session-details").performClick()
        compose.onNodeWithText("New times use phone timezone:", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("main-list").performScrollToNode(hasTestTag("note-tools"))
        compose.onNodeWithTag("note-tools").performClick()
        compose.onNode(hasText("Import local intent model (484 MB)") or hasText("Replace local intent model")).performScrollTo().assertIsDisplayed()
        assertFalse(ListeningService.state.value.recording)
    }
    @Test fun actualWindowLayoutAndControlsRemainUsable() {
        val arguments = InstrumentationRegistry.getArguments()
        app.guidance.setAppearance(if (arguments.getString("interfaceDark") == "true") AppearanceMode.DARK else AppearanceMode.LIGHT)
        arguments.getString("interfaceDesign")?.let { app.guidance.setInterfaceDesign(InterfaceDesign.valueOf(it)) }
        arguments.getString("interfaceTextSize")?.let { app.guidance.setNoteTextSize(NoteTextSize.valueOf(it)) }
        arguments.getString("interfaceSize")?.let { app.guidance.setInterfaceSize(InterfaceSize.valueOf(it)) }
        val simpleHome = arguments.getString("interfaceHome") == "true"
        app.guidance.setOneScreenHome(simpleHome)
        compose.waitForIdle()
        val wide = compose.activity.resources.configuration.screenWidthDp / app.guidance.interfaceSize.value.factor >= 600
        if (arguments.getString("interfaceRtl") == "true") assertEquals(android.view.View.LAYOUT_DIRECTION_RTL, compose.activity.resources.configuration.layoutDirection)
        compose.onNodeWithTag(if (wide) "navigation-rail" else "bottom-navigation").assertIsDisplayed()
        compose.onNodeWithTag(if (wide) "bottom-navigation" else "navigation-rail").assertDoesNotExist()
        compose.onNodeWithTag("settings").assertHeightIsAtLeast(48.dp).assertIsDisplayed()
        compose.onNodeWithTag("planner-tab").assertIsDisplayed().performClick()
        compose.onNodeWithTag("summary-tab").assertIsDisplayed().performClick()
        compose.onNodeWithTag("capture-tab").assertIsDisplayed().performClick()
        if (simpleHome) {
            compose.onNodeWithTag("compact-listen").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("compact-details").assertIsDisplayed()
        } else {
            compose.onNodeWithTag("main-list").performScrollToNode(hasText("Start listening"))
            compose.onNodeWithText("Start listening").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        }
        assertFalse(ListeningService.state.value.recording)
        // Accessibility roots can be temporarily null on Android 17. Check the actual
        // focused system window instead, so an ANR overlay cannot earn a visual pass.
        var focused = false
        for (attempt in 0 until 20) {
            val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys window")
            focused = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { input ->
                input.lineSequence().filter { it.contains("mCurrentFocus=") }.any { it.contains(app.packageName + "/") }
            }
            if (focused) break
            android.os.SystemClock.sleep(100)
        }
        assertTrue("The isolated app must be focused, with no system error overlay", focused)
        val label = arguments.getString("interfaceScreenshot")?.takeIf { it.matches(Regex("[a-z0-9-]{1,40}")) }
        if (label != null) {
            val config = compose.activity.resources.configuration
            File(app.filesDir, "interface-$label.json").writeText(org.json.JSONObject()
                .put("widthDp", config.screenWidthDp).put("heightDp", config.screenHeightDp)
                .put("fontScale", config.fontScale.toDouble()).put("rtl", config.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL)
                .put("dark", app.guidance.appearance.value == AppearanceMode.DARK).put("navigation", if (wide) "rail" else "bar").toString(2))
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            checkNotNull(bitmap)
            try { File(app.filesDir, "interface-$label.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
        }
    }
}
