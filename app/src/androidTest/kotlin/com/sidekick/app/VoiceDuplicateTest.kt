package com.sidekick.app

import com.sidekick.core.*
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class VoiceDuplicateTest {
    private val now = Instant.parse("2026-10-23T17:00:00Z")
    private val zone = ZoneId.of("America/Chicago")
    private val party = "Tomorrow I'm going to the party at 8pm at the James."
    private fun event(memory: TemporaryMemory, voice: Speaker, offset: Long = 0, text: String = party) =
        memory.appendAttributedLive(text, now.plusSeconds(offset), zone, voice)

    @Test fun overlappingEventAcrossVoiceChangesKeepsOneDraftAndDoesNotPromoteOwnership() {
        val memory = TemporaryMemory()
        event(memory, Speaker.UNKNOWN)
        val original = memory.drafts.value.single()
        event(memory, Speaker.USER, 10)
        assertEquals(original, memory.drafts.value.single())
        assertTrue(memory.drafts.value.single().informational)
        event(memory, Speaker.UNKNOWN, 20)
        assertEquals(original.id, memory.drafts.value.single().id)
    }

    @Test fun changedDetailsAcrossVoicesNeedReviewAndStillAnchorLaterCorrections() {
        val memory = TemporaryMemory()
        event(memory, Speaker.USER)
        val original = memory.drafts.value.single()
        event(memory, Speaker.UNKNOWN, 10, party + " It ends at midnight.")
        assertEquals(2, memory.drafts.value.size)
        val revision = memory.drafts.value.single { it.id != original.id }
        assertEquals(original.id, memory.updateTarget(revision.id))
        assertNull(memory.drafts.value.first { it.id == original.id }.activityEndAt)
        assertTrue(revision.sourceKey!!.startsWith("uncertain:"))
        assertNotNull(revision.activityEndAt)
        memory.dismiss(revision.id)
        event(memory, Speaker.OTHER, 12, "Some unrelated words.")
        event(memory, Speaker.UNKNOWN, 15, party + " It ends at midnight.")
        assertEquals(1, memory.drafts.value.size)
        // A repeated event must preserve an anchor to the original item.
        event(memory, Speaker.USER, 20)
        event(memory, Speaker.UNKNOWN, 25, "It ends at 11pm.")
        assertEquals(original.id, memory.updateTarget(memory.drafts.value.last().id))
    }

    @Test fun acceptedAndDismissedEventsDoNotReappearWhenVoiceLabelsChange() {
        for (accept in listOf(false, true)) {
            val memory = TemporaryMemory()
            event(memory, Speaker.UNKNOWN)
            val original = memory.drafts.value.single()
            if (accept) memory.approve(original.id, original.copy(state = "ACCEPTED")) else memory.dismiss(original.id)
            event(memory, Speaker.USER, 10)
            assertTrue(memory.drafts.value.isEmpty())
            if (accept) {
                event(memory, Speaker.UNKNOWN, 20, party + " It ends at midnight.")
                assertEquals(original.id, memory.updateTarget(memory.drafts.value.single().id))
            }
        }
    }

    @Test fun punctuationCaseAndUnicodeCompatibilityDoNotCreateDuplicates() {
        val memory = TemporaryMemory()
        event(memory, Speaker.UNKNOWN)
        val original = memory.drafts.value.first()
        val proposal = ProposedCommitment(original.title, party.uppercase().replace("8", "８").replace('.', '!'), now.plusSeconds(15), zone,
            "Test", Instant.ofEpochMilli(original.activityStartAt!!), informational = true, sourceKey = "voice:99:UNKNOWN")
        memory.addProposals(listOf(proposal))
        assertEquals(1, memory.drafts.value.size)
    }

    @Test fun repeatedOldDetailsDoNotUndoManuallyEditedApprovedDetails() {
        val memory = TemporaryMemory()
        event(memory, Speaker.UNKNOWN, text = party + " It ends at midnight.")
        val original = memory.drafts.value.single()
        val edited = original.copy(state = "ACCEPTED", activityEndAt = original.activityEndAt!! + 3_600_000, placeName = "My chosen venue")
        memory.approve(original.id, edited)
        event(memory, Speaker.USER, 10, party + " It ends at midnight.")
        assertTrue(memory.drafts.value.isEmpty())
        event(memory, Speaker.UNKNOWN, 20, party + " It ends at midnight. It's at 25 S Livingston Street.")
        val revision = memory.drafts.value.single()
        assertEquals(edited.activityEndAt, revision.activityEndAt)
        assertEquals(edited.placeName, revision.placeName)
        assertEquals("25 S Livingston Street", revision.placeAddress)
        assertEquals(edited.id, memory.updateTarget(revision.id))
    }

    @Test fun differentDatesVenuesSentencesMessagesAndOldMentionsStaySeparate() {
        val samples = listOf(
            Triple(party.replace("tomorrow", "today", true), 10L, "voice:2:UNKNOWN"),
            Triple(party.replace("James", "Sylvee"), 10L, "voice:2:UNKNOWN"),
            Triple(party.replace("I'm going", "we're going"), 10L, "voice:2:UNKNOWN"),
            Triple(party, 121L, "voice:2:UNKNOWN"),
            Triple(party, 10L, "message:synthetic"),
        )
        for ((text, offset, source) in samples) {
            val memory = TemporaryMemory()
            event(memory, Speaker.UNKNOWN)
            val incoming = ActivityTiming.extract(Transcript(text, now.plusSeconds(offset), zone, Speaker.OTHER, true), informationalOnly = true)
            memory.addProposals(incoming.map { it.copy(sourceKey = source) })
            assertEquals("Must remain distinct: $text / $source / $offset", 2, memory.drafts.value.size)
        }
    }

    @Test fun repeatedActivitiesInAllFiveAddedLanguagesKeepOneDraft() {
        val examples = listOf("내일 오후 8시에 The James에서 파티가 있어요.", "明天晚上八点在The James有派对。",
            "Il y a une fête demain à vingt heures au The James.", "Morgen ist eine Party um 20 Uhr im The James.",
            "Завтра вечеринка в 20:00 в The James.")
        for (text in examples) {
            val memory = TemporaryMemory()
            event(memory, Speaker.UNKNOWN, text = text)
            val original = memory.drafts.value.single()
            event(memory, Speaker.OTHER, 10, text)
            assertEquals(original, memory.drafts.value.single())
        }
    }
}
