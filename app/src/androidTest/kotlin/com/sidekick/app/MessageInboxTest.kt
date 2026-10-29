package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MessageInboxTest {
    @Test fun fragmentedMessagesStayInTheirConversationAndNeverImplyUserAttendance() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeTrue(context.packageName.endsWith(".benchmark"))
        val settings = GuidanceSettings(context)
        val enabled = settings.messageNotifications.value; val apps = settings.messageApps.value
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val memory = TemporaryMemory()
        try {
            settings.setMessageNotifications(true); settings.setMessageApp("synthetic.fragments", true)
            val inbox = MessageInbox(settings, memory, scope)
            val now = System.currentTimeMillis()
            suspend fun send(thread: String, text: String, offset: Long) = inbox.ingest(MessageEnvelope("synthetic.fragments", thread, listOf(IncomingText(text, now + offset))))
            inbox.ingest(MessageEnvelope("synthetic.fragments", "party-thread", listOf(
                IncomingText("Tomorrow", now + 2), IncomingText("There's a party", now))))
            send("unrelated-thread", "Tomorrow. At 9pm.", 1)
            assertTrue(memory.drafts.value.isEmpty())
            send("party-thread", "Tomorrow", 2)
            send("party-thread", "At 8pm", 3)
            val first = memory.drafts.value.single()
            assertEquals(20, java.time.Instant.ofEpochMilli(first.activityStartAt!!).atZone(java.time.ZoneId.systemDefault()).hour)
            send("party-thread", "It ends at midnight. It's at Synthetic Hall. I'll be there.", 4)
            val updated = memory.drafts.value.single()
            assertEquals(first.id, updated.id); assertTrue(updated.informational)
            assertNotNull(updated.activityEndAt); assertEquals("Synthetic Hall", updated.placeName)
            send("party-thread", "At 8pm", 3) // Grouped notification replay.
            assertEquals(updated, memory.drafts.value.single())
            send("party-thread", "Actually it's at Old Hall.", 1) // Unseen but stale notification.
            assertEquals(updated, memory.drafts.value.single())
        } finally {
            scope.cancel(); settings.messageApps.value.forEach { settings.setMessageApp(it, false) }
            apps.forEach { settings.setMessageApp(it, true) }; settings.setMessageNotifications(enabled)
        }
    }
    @Test fun informationalSettingCanExcludeEventFactsWithoutLosingPersonalPlans() {
        val memory = TemporaryMemory { false }
        val time = java.time.Instant.now()
        val zone = java.time.ZoneId.systemDefault()
        memory.appendLive("There's a party tomorrow at 8pm.", time, zone)
        assertTrue(memory.drafts.value.isEmpty())
        memory.appendLive("I'll go to the party tomorrow at 8pm.", time, zone)
        assertEquals(1, memory.drafts.value.size)
        assertFalse(memory.drafts.value.single().informational)
    }
    @Test fun onlySelectedNewMessagesBecomeTemporaryInformationalSuggestions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = GuidanceSettings(context)
        val oldEnabled = settings.messageNotifications.value
        val oldApps = settings.messageApps.value
        val oldInclude = settings.includeInformational.value
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val memory = TemporaryMemory { settings.includeInformational.value }
        try {
            settings.setMessageNotifications(false); settings.setMessageNotifications(true)
            settings.setMessageApp("synthetic.messages", true); settings.setIncludeInformational(true)
            val inbox = MessageInbox(settings, memory, scope)
            val time = System.currentTimeMillis()
            val text = "There's a party ${LocalDate.now().plusDays(1)} at 8pm at The James."
            inbox.ingest(MessageEnvelope("unselected.app", "thread", listOf(IncomingText(text, time))))
            assertTrue(memory.drafts.value.isEmpty())
            inbox.ingest(MessageEnvelope("synthetic.messages", "thread", listOf(IncomingText(text, settings.messagesEnabledSince - 1))))
            assertTrue(memory.drafts.value.isEmpty())
            inbox.ingest(MessageEnvelope("synthetic.messages", "thread", listOf(IncomingText(text, time))))
            val first = memory.drafts.value.single()
            assertTrue(first.informational); assertEquals("The James", first.placeName); assertNull(first.placeAddress)
            inbox.ingest(MessageEnvelope("synthetic.messages", "thread", listOf(IncomingText(text, time))))
            assertEquals(1, memory.drafts.value.size)
            inbox.ingest(MessageEnvelope("synthetic.messages", "thread", listOf(IncomingText("It's at 432 W Gorham Street. It ends around midnight.", time + 1))))
            val updated = memory.drafts.value.single()
            assertEquals(first.id, updated.id)
            assertEquals("432 W Gorham Street", updated.placeAddress); assertNotNull(updated.activityEndAt)
            inbox.ingest(MessageEnvelope("synthetic.messages", "other-thread", listOf(IncomingText(text, time))))
            assertEquals(2, memory.drafts.value.size)
            settings.setMessageNotifications(false)
            inbox.ingest(MessageEnvelope("synthetic.messages", "disabled", listOf(IncomingText(text, time))))
            assertEquals(2, memory.drafts.value.size)
            assertEquals("The James, Madison, WI", PlaceLookup.mapsUri("The James", "Madison, WI").getQueryParameter("query"))
        } finally {
            scope.cancel()
            settings.messageApps.value.forEach { settings.setMessageApp(it, false) }
            oldApps.forEach { settings.setMessageApp(it, true) }
            settings.setMessageNotifications(oldEnabled); settings.setIncludeInformational(oldInclude)
        }
    }
}
