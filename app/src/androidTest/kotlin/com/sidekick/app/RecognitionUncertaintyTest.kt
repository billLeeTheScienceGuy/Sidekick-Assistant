package com.sidekick.app

import com.sidekick.core.Speaker
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class RecognitionUncertaintyTest {
    @Test fun conflictingSpeechKeepsTextButCannotSaveOrReschedulePlans() {
        val memory=TemporaryMemory();val at=Instant.parse("2026-11-13T14:00:00Z");val zone=ZoneId.of("America/New_York")
        val text="I will attend a meeting tomorrow at 8pm."
        memory.appendAttributedLive(text,at,zone,Speaker.USER,recognitionReliable=false)
        assertTrue(memory.text.value.contains(text));assertTrue(memory.drafts.value.isEmpty())
        memory.appendAttributedLive("It ends at midnight.",at.plusSeconds(3),zone,Speaker.USER)
        assertTrue(memory.drafts.value.isEmpty())
        memory.appendAttributedLive("I will attend a meeting tomorrow at 6pm.",at.plusSeconds(6),zone,Speaker.USER)
        assertFalse(memory.drafts.value.isEmpty())
        val before=memory.drafts.value
        memory.appendAttributedLive("Move the meeting to tomorrow at 8pm.",at.plusSeconds(9),zone,Speaker.USER,recognitionReliable=false)
        assertEquals(before,memory.drafts.value)
    }
}
