package com.sidekick.core

import java.time.Instant
import kotlin.test.*

/** Voice thresholds, session-only names, invitations and owner voiceprint retention. */
class PrivacyWorkPackageTest {
    private val strict = VoiceMatch.Thresholds.BEFORE_NOTICE

    @Test fun `standard thresholds are unchanged`() {
        assertEquals(Speaker.USER, VoiceMatch.classifyScores(.70f, .70f, .70f))
        assertEquals(Speaker.USER, VoiceMatch.classifyScores(.66f, .79f, .70f))            // balanced
        assertEquals(Speaker.UNKNOWN, VoiceMatch.classifyScores(.64f, .90f, .70f))
        assertEquals(Speaker.OTHER, VoiceMatch.classifyScores(.30f, .35f, .70f))
        assertEquals(Speaker.USER, VoiceMatch.classifyShortScores(.80f, .80f, 2_000, 1_000))
    }
    @Test fun `before notice owner match is stricter`() {
        assertEquals(Speaker.UNKNOWN, VoiceMatch.classifyScores(.72f, .72f, .70f, strict))  // passes standard, not strict
        assertEquals(Speaker.USER, VoiceMatch.classifyScores(.75f, .75f, .70f, strict))
        assertEquals(Speaker.UNKNOWN, VoiceMatch.classifyScores(.69f, .85f, .70f, strict))  // balanced min .70
        assertEquals(Speaker.USER, VoiceMatch.classifyScores(.71f, .84f, .70f, strict))     // balanced: min .70, mean .775
        assertEquals(Speaker.UNKNOWN, VoiceMatch.classifyScores(.70f, .80f, .70f, strict))  // mean .75 < .77
        assertEquals(Speaker.OTHER, VoiceMatch.classifyScores(.2f, .3f, .7f, strict))
        // Short phrases (1.5-3 s) never become USER before notice.
        assertEquals(Speaker.UNKNOWN, VoiceMatch.classifyShortScores(.95f, .95f, 2_000, 1_000, .80f, strict))
    }
    @Test fun `identifyTurn passes thresholds through`() {
        val profile = vector(0)
        val near = blend(profile, vector(1), .72f)  // cosine about .72 with the profile
        val score = VoiceMatch.similarity(profile, near)
        assertTrue(score in 0.70f..0.75f, "score $score")
        assertEquals(Speaker.USER, VoiceMatch.identifyTurn(profile, listOf(near, near)))
        assertEquals(Speaker.UNKNOWN, VoiceMatch.identifyTurn(profile, listOf(near, near), strict))
    }
    @Test fun `session names are memory only and cleared`() {
        val voices = SessionVoices("test")
        val a = voices.observe(vector(0), 0)!!
        val b = voices.observe(vector(5), 10)!!
        assertEquals(2, voices.size)
        assertTrue(voices.name(a.key, "Sarah"))
        assertEquals("Sarah", voices.nameOf(a.key))
        assertNull(voices.nameOf(b.key))
        assertFalse(voices.name("spk_unknown_1", "Sarah"))
        assertFalse(voices.name(b.key, "<script>"))
        assertFalse(voices.name(b.key, "   "))
        assertEquals("Mary-Jo O'Neil", SessionVoices.cleanName("  Mary-Jo   O'Neil "))
        voices.clear()                                                 // forgetOthers / session end / strict place
        assertEquals(0, voices.size)
        assertNull(voices.nameOf(a.key))
        assertTrue(voices.names().isEmpty())
    }
    @Test fun `invitation patterns`() {
        assertEquals("5", VoiceInvitations.invitation("Meet me at 5"))
        assertEquals("5 pm tomorrow", VoiceInvitations.invitation("ok, see you at 5 pm tomorrow."))
        assertEquals("6:30", VoiceInvitations.invitation("Let's meet at 6:30"))
        assertNull(VoiceInvitations.invitation("I like meeting people"))
        assertEquals("5", VoiceInvitations.ownerReply("I'll meet you at 5"))
        assertEquals("noon", VoiceInvitations.ownerReply("I will see you at noon"))
        assertTrue(VoiceInvitations.isAcceptance("Sure"))
        assertTrue(VoiceInvitations.isAcceptance("OK, see you then."))
        assertFalse(VoiceInvitations.isAcceptance("Sure, but not at 5"))
        assertEquals("Sarah", VoiceInvitations.addressed("Hey Sarah, are you free?"))
        assertEquals("Sarah", VoiceInvitations.addressed("Sarah, what time works?"))
        assertNull(VoiceInvitations.addressed("Okay, let's go"))
        assertNull(VoiceInvitations.addressed("Tomorrow, I have class"))
        assertEquals("Sarah: meet at 5", VoiceInvitations.otherTitle("Sarah", "5"))
        assertEquals("Meet Sarah at 5", VoiceInvitations.ownerTitle("Sarah", "5"))
    }
    @Test fun `naming suggestion and acceptance windows`() {
        val naming = SessionNaming()
        val t0 = Instant.parse("2026-11-16T10:00:00Z")
        naming.ownerSpoke("Hey Sarah, are you coming?", t0)
        assertNull(naming.voiceSpoke("spk_a", null, t0.plusSeconds(6)))          // too late
        naming.ownerSpoke("Hey Sarah, are you coming?", t0.plusSeconds(10))
        assertEquals(SessionNaming.Suggestion("spk_a", "Sarah"), naming.voiceSpoke("spk_a", null, t0.plusSeconds(12)))
        assertNull(naming.voiceSpoke("spk_b", null, t0.plusSeconds(13)))         // consumed
        assertNull(naming.voiceSpoke("spk_a", "Sarah", t0.plusSeconds(20)))      // already named: no chip
        assertEquals("Sarah", naming.recentNamedVoice(t0.plusSeconds(40)))
        assertNull(naming.recentNamedVoice(t0.plusSeconds(60)))
        naming.invited("spk_a", "Sarah", "5", t0.plusSeconds(20))
        assertNull(naming.accepted("Maybe", t0.plusSeconds(25)))
        assertNull(naming.accepted("Sure", t0.plusSeconds(51)))                  // > 30 s
        naming.invited("spk_a", "Sarah", "5", t0.plusSeconds(60))
        assertEquals("5", naming.accepted("Sure", t0.plusSeconds(70))?.clock)
        assertNull(naming.accepted("Sure", t0.plusSeconds(71)))                  // consumed
        naming.invited("spk_a", "Sarah", "5", t0.plusSeconds(80))
        naming.clear()
        assertNull(naming.accepted("Sure", t0.plusSeconds(81)))
    }
    @Test fun `voiceprint consent and retention`() {
        val day = 24L * 60 * 60 * 1000
        assertFalse(VoiceprintPolicy.expired(savedMs = 0, lastUsedMs = 364 * day, nowMs = 700 * day))
        assertTrue(VoiceprintPolicy.expired(savedMs = 0, lastUsedMs = 300 * day, nowMs = 700 * day))
        assertTrue(VoiceprintPolicy.expired(savedMs = 0, lastUsedMs = null, nowMs = 366 * day))
        assertFalse(VoiceprintPolicy.canAgree("", true, false, false))
        assertFalse(VoiceprintPolicy.canAgree("Alex", false, false, false))
        assertTrue(VoiceprintPolicy.canAgree("Alex", true, false, false))
        assertFalse(VoiceprintPolicy.canAgree("Alex", true, true, false))
        assertTrue(VoiceprintPolicy.canAgree("Alex", true, true, true))
        assertTrue(VoiceprintConsent("Alex", 1, VoiceprintPolicy.NOTICE_VERSION, "checkbox").valid)
        assertFalse(VoiceprintConsent("Alex", 1, 0, "checkbox").valid)
    }

    private fun vector(seed: Int) = FloatArray(192) { i -> if (i % 12 == seed % 12) 1f else .01f * ((i * 31 + seed) % 7) }
    private fun blend(a: FloatArray, b: FloatArray, weight: Float): FloatArray {
        // Choose a mix whose cosine with [a] is close to [weight].
        val na = VoiceMatch.normalized(a); val nb = VoiceMatch.normalized(b)
        var lo = 0f; var hi = 1f
        repeat(40) {
            val mid = (lo + hi) / 2
            val mix = FloatArray(a.size) { mid * na[it] + (1 - mid) * nb[it] }
            if (VoiceMatch.similarity(na, mix) < weight) lo = mid else hi = mid
        }
        return VoiceMatch.normalized(FloatArray(a.size) { hi * na[it] + (1 - hi) * nb[it] })
    }
}

class TemporarySignatureTest {
    @Test fun `a compared signature is zeroed after use, also on error`() {
        val signature = FloatArray(192) { 0.1f + it }
        val score = VoiceMatch.useOnce(signature) { VoiceMatch.similarity(it, FloatArray(192) { 1f }) }
        assertTrue(score > 0f)
        assertTrue(signature.all { it == 0f })
        val other = FloatArray(192) { 1f }
        assertFailsWith<IllegalStateException> { VoiceMatch.useOnce(other) { error("model failed") } }
        assertTrue(other.all { it == 0f })
    }
}

class InvitationDatingTest {
    private val zone = java.time.ZoneId.of("America/Chicago")
    private val at = Instant.parse("2026-11-16T15:00:00Z") // 09:00 in Chicago
    @Test fun `bare hours are dated and flagged`() {
        val five = VoiceInvitations.propose("5 tomorrow", at, zone)!!
        assertEquals(Instant.parse("2026-11-17T23:00:00Z"), five.activityStartAt)       // 5 pm tomorrow
        assertTrue(five.needsReview.startsWith("Time read as"), five.needsReview)
        val today = VoiceInvitations.propose("5", at, zone)!!
        assertEquals(Instant.parse("2026-11-16T23:00:00Z"), today.activityStartAt)      // 5 pm today
        val ten = VoiceInvitations.propose("10", at, zone)!!
        assertEquals(Instant.parse("2026-11-16T16:00:00Z"), ten.activityStartAt)        // next 10 o'clock: 10 am today
        val explicit = VoiceInvitations.propose("5 pm tomorrow", at, zone)!!
        assertFalse(explicit.needsReview.startsWith("Time read as"))
        assertNull(VoiceInvitations.propose("25", at, zone))
    }
}

class AddressedNameTest {
    @Test fun `recognizer-style lowercase names are found after a greeting`() {
        assertEquals("Sarah", VoiceInvitations.addressed("hey sarah, are you free later"))
        assertEquals("Sarah", VoiceInvitations.addressed("Hey, Sarah. What time works?"))
        assertEquals("Sarah", VoiceInvitations.addressed("hi sarah"))
        assertEquals("Sarah", VoiceInvitations.addressed("Sarah, what time works?"))
        assertNull(VoiceInvitations.addressed("sarah, what time works?"))        // no greeting: needs a capital
        assertNull(VoiceInvitations.addressed("so, what do you think"))
        assertNull(VoiceInvitations.addressed("hey guys"))
        assertNull(VoiceInvitations.addressed("hey everyone, listen up"))
        assertNull(VoiceInvitations.addressed("I talked to Sarah yesterday"))
    }
    @Test fun `any voice may address, but never names itself`() {
        val naming = SessionNaming()
        val t0 = Instant.parse("2026-11-16T10:00:00Z")
        // Owner not recognized (short phrase): an unassigned voice says the name, a grouped voice answers.
        naming.spoke("hey sarah, are you coming", "spk_s_unassigned3", t0)
        assertEquals(SessionNaming.Suggestion("spk_s_2", "Sarah"), naming.voiceSpoke("spk_s_2", null, t0.plusSeconds(2)))
        // The grouped voice that said the name is not the one being named.
        naming.spoke("hey sarah", "spk_s_2", t0.plusSeconds(10))
        assertNull(naming.voiceSpoke("spk_s_2", null, t0.plusSeconds(11)))
        assertEquals("Sarah", naming.voiceSpoke("spk_s_3", null, t0.plusSeconds(12))?.name)
    }
}

class SessionVoiceLifetimeTest {
    private fun vector(seed: Int) = FloatArray(192) { i -> if (i % 12 == seed % 12) 1f else .01f * ((i * 31 + seed) % 7) }
    @Test fun `voice groups last 30 minutes without that voice`() {
        val voices = SessionVoices("life")
        val a = voices.observe(vector(0), 0)!!
        voices.name(a.key, "Sarah")
        assertEquals(a.key, voices.observe(vector(0), 29 * 60_000L)?.key)          // back after 29 min: same group, same name
        assertEquals("Sarah", voices.nameOf(a.key))
        val later = voices.observe(vector(0), 29 * 60_000L + SessionVoices.KEEP_MS + 1)!!
        assertNotEquals(a.key, later.key)                                           // quiet > 30 min: new anonymous group
        assertNull(voices.nameOf(later.key))
    }
}

class NoticeDeliveryTest {
    @Test fun `volume is raised to half for the notice, never lowered`() {
        assertEquals(8, NoticeAudibility.targetVolume(3, 15))
        assertEquals(12, NoticeAudibility.targetVolume(12, 15))
        assertEquals(15, NoticeAudibility.targetVolume(15, 15))
        assertEquals(53, NoticeAudibility.percent(8, 15))
    }
    @Test fun `quiet or covered playback does not count`() {
        val loud = List(40) { if (it % 4 == 0) .08f else .02f }
        assertTrue(NoticeAudibility.audible(loud))
        val pocket = List(40) { .002f }
        assertFalse(NoticeAudibility.audible(pocket))
        assertFalse(NoticeAudibility.audible(listOf(.5f, .5f)))                 // too few frames to judge
        assertFalse(NoticeAudibility.audible(List(40) { Float.NaN }))
    }
    @Test fun `auto announce on a new conversation, and at the two-hour cap in a meeting`() {
        val auto = AutoAnnouncer(cooldownMs = 120_000)
        assertFalse(auto.otherVoice(0, noticeActive = false))                   // setting off
        auto.enabled = true
        assertTrue(auto.otherVoice(1_000, noticeActive = false))                // first other voice of the session
        assertFalse(auto.otherVoice(2_000, noticeActive = false))               // already handled
        auto.failed()                                                            // too quiet
        assertFalse(auto.otherVoice(60_000, noticeActive = false))              // cooldown
        assertTrue(auto.otherVoice(130_000, noticeActive = false))              // retried after the cooldown
        assertFalse(auto.otherVoice(140_000, noticeActive = true))
        // Conversation ended: wait for the next voice, do not speak to an empty room.
        assertFalse(auto.expired(ConsentGate.EXPIRED_GAP, 2_000_000, othersLastMs = 200_000))
        assertTrue(auto.otherVoice(2_100_000, noticeActive = false))
        // Two-hour cap while people are talking: play right away.
        assertTrue(auto.expired(ConsentGate.EXPIRED_MAX_AGE, 9_000_000, othersLastMs = 8_990_000))
        // Two-hour cap after everyone went quiet: wait for the next voice.
        assertFalse(auto.expired(ConsentGate.EXPIRED_MAX_AGE, 20_000_000, othersLastMs = 10_000_000))
        assertTrue(auto.otherVoice(20_500_000, noticeActive = false))
    }
}

class NoticeCoverTest {
    @Test fun `proximity near means covered`() {
        assertTrue(NoticeAudibility.covered(0f, 5f))      // pocket or hand over the top
        assertFalse(NoticeAudibility.covered(5f, 5f))     // nothing in front
        assertFalse(NoticeAudibility.covered(Float.NaN, 5f))
        assertFalse(NoticeAudibility.covered(0f, 0f))     // broken sensor data: do not block
    }
}
