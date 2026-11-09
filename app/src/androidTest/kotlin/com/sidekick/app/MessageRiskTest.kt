package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class MessageRiskTest {
    @Test fun onlyNewMessagesFromAnEnabledSelectedAppReachRiskAnalysis() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".compacttest") || context.packageName.endsWith(".benchmark"))
        val settings = GuidanceSettings(context)
        val enabled = settings.messageNotifications.value
        val packages = settings.messageApps.value
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val inspected = mutableListOf<String>()
        try {
            settings.setMessageNotifications(true); settings.setMessageApp("synthetic.risk", true)
            val inbox = MessageInbox(settings, TemporaryMemory(), scope, onRisk = { inspected += it })
            val now = maxOf(System.currentTimeMillis(), settings.messagesEnabledSince)
            val text = IncomingText("Pay the fine with gift cards.", now)
            inbox.ingest(MessageEnvelope("unselected.app", "thread", listOf(text)))
            assertTrue(inspected.isEmpty())
            inbox.ingest(MessageEnvelope("synthetic.risk", "thread", listOf(text)))
            assertEquals(1, inspected.size)
            inbox.ingest(MessageEnvelope("synthetic.risk", "thread", listOf(text)))
            assertEquals(1, inspected.size)
            settings.setMessageNotifications(false)
            inbox.ingest(MessageEnvelope("synthetic.risk", "thread", listOf(text.copy(timestamp = now + 1))))
            assertEquals(1, inspected.size)
        } finally {
            scope.cancel()
            settings.setMessageApp("synthetic.risk", "synthetic.risk" in packages)
            settings.setMessageNotifications(enabled)
        }
    }
}
