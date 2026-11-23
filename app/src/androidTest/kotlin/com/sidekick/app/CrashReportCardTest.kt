package com.sidekick.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The crash-report card: the writer is called directly (the test process never crashes). Isolated test copy only. */
class CrashReportCardTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as SidekickApplication
    private var oldTag = ""; private var oldOn = true; private var oldHome = true

    @Before fun isolated() {
        check(app.packageName.endsWith(".benchmark") || app.packageName.endsWith(".compacttest")) { "Run only in the test copy." }
        oldTag = AppLanguage.current(); oldOn = app.guidance.crashReports.value; oldHome = app.guidance.oneScreenHome.value
        AppLanguage.set(context, "en"); app.guidance.setCrashReports(true); app.guidance.setOneScreenHome(true); CrashReports.deleteAll(context)
    }
    @After fun restore() {
        CrashReports.deleteAll(context); app.guidance.setCrashReports(oldOn); app.guidance.setOneScreenHome(oldHome); AppLanguage.set(context, oldTag)
    }

    @Test fun cardAppearsAndDontSendDeletesTheReport() {
        val file = CrashReports.write(context, IllegalStateException("Call Emma tomorrow at 5 pm"), System.currentTimeMillis())!!
        val text = file.readText()
        assertFalse(text.contains("Emma")); assertTrue(text.contains("java.lang.IllegalStateException"))
        assertTrue(text.contains("App version:")); assertTrue(text.contains("Device:"))
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("crash-report-card").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("crash-send").assertExists()
            compose.onNodeWithTag("crash-dont-send").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("crash-report-card").fetchSemanticsNodes().isEmpty() }
        }
        assertTrue(CrashReports.list(context).isEmpty())
        assertFalse(file.exists())
    }

    @Test fun switchingOffSavesNothingAndDeletesExistingReports() {
        CrashReports.write(context, RuntimeException("x"), System.currentTimeMillis())
        assertEquals(1, CrashReports.list(context).size)
        app.guidance.setCrashReports(false)
        assertTrue(CrashReports.list(context).isEmpty())
        assertNull(CrashReports.write(context, RuntimeException("x"), System.currentTimeMillis()))
        assertTrue(CrashReports.list(context).isEmpty())
    }

    @Test fun keepsOnlyTheFiveNewestReports() {
        val now = System.currentTimeMillis()
        repeat(7) { CrashReports.write(context, RuntimeException("x"), now + it) }
        assertEquals(5, CrashReports.list(context).size)
        assertEquals(now + 6, CrashReports.list(context).first().timeMs)
    }
}
