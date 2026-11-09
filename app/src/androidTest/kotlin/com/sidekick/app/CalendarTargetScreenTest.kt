package com.sidekick.app

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.data.CommitmentEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CalendarTargetScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun calendarSourceLinkOpensOnlyTheSavedEventWithoutChangingIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as SidekickApplication
        check(app.packageName.endsWith(".compacttest") || app.packageName.endsWith(".benchmark"))
        val id = "link-${java.util.UUID.randomUUID()}"
        val item = CommitmentEntity(id, "Synthetic source-link event", "Synthetic source", System.currentTimeMillis(), "UTC", "", state = "ACCEPTED")
        val dao = app.database.commitments()
        try {
            runBlocking { dao.insertAll(listOf(item)) }
            val uri = Uri.parse(CalendarTarget.uri(app.packageName, id))
            val launch = Intent(Intent.ACTION_VIEW, uri, app, MainActivity::class.java)
            ActivityScenario.launch<MainActivity>(launch).use { scenario ->
                var original: Intent? = null
                scenario.onActivity { original = it.intent }
                try {
                    compose.waitUntil(5_000) { compose.onAllNodesWithTag("edit-title").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithTag("edit-title").assertTextContains(item.title)
                    compose.onNodeWithText("Cancel").performClick()
                    assertEquals(item, runBlocking { dao.get(id) })
                    scenario.onActivity { activity ->
                        @Suppress("DEPRECATION")
                        instrumentation.callActivityOnNewIntent(activity, Intent(Intent.ACTION_VIEW, uri.buildUpon().appendQueryParameter("cancel", "true").build()))
                    }
                    compose.waitForIdle()
                    compose.onNodeWithTag("edit-title").assertDoesNotExist()
                    assertEquals(item, runBlocking { dao.get(id) })
                } finally {
                    // setIntent changes ActivityScenario's launch identity; restore it for teardown.
                    scenario.onActivity { activity -> original?.let(activity::setIntent) }
                }
            }
        } finally { runBlocking { dao.delete(id) } }
    }
}
