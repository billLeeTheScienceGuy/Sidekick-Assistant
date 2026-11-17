package com.sidekick.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class OwnerSessionTest {
    private fun unit(seed: Int, dim: Int = 192): FloatArray { val r = Random(seed); return VoiceMatch.normalized(FloatArray(dim) { r.nextFloat() - .5f }) }
    /** [base] moved toward random noise so that its similarity to [base] is about [target]. */
    private fun near(base: FloatArray, target: Float, seed: Int): FloatArray {
        val noise = unit(seed, base.size)
        val away = VoiceMatch.normalized(FloatArray(base.size) { noise[it] - VoiceMatch.similarity(noise, base) * base[it] })
        val k = kotlin.math.sqrt(1 - target * target)
        return VoiceMatch.normalized(FloatArray(base.size) { target * base[it] + k * away[it] })
    }
    private val rule = VoiceMatch.Thresholds.STANDARD.copy(session = VoiceMatch.SessionCarry(minimum = .80f, mean = .82f, profileFloor = .40f))

    @Test fun `carries only after a profile-confirmed turn and only when close to it`() {
        val session = OwnerSession(); val me = unit(1)
        assertFalse(session.carry(near(me, .95f, 2), .6f, 0, rule).owner)
        session.confirm(me, 0)
        assertTrue(session.carry(near(me, .90f, 3), .6f, 1000, rule).owner)
        assertFalse("too far from the session voice", session.carry(near(me, .70f, 4), .6f, 1000, rule).owner)
        assertFalse("profile far off", session.carry(near(me, .95f, 5), .30f, 1000, rule).owner)
    }

    @Test fun `every confirmed turn must agree`() {
        val session = OwnerSession(); val me = unit(1)
        session.confirm(me, 0); session.confirm(near(me, .9f, 6), 10)
        val drifted = near(near(me, .9f, 6), .9f, 7)
        val result = session.carry(drifted, .6f, 20, rule)
        assertEquals(VoiceMatch.similarity(me, drifted) >= .80f && VoiceMatch.similarity(near(me, .9f, 6), drifted) >= .80f, result.owner)
    }

    @Test fun `off before notice, expires, and clears`() {
        val session = OwnerSession(); val me = unit(1)
        session.confirm(me, 0)
        assertFalse(session.carry(near(me, .95f, 2), .6f, 10, VoiceMatch.Thresholds.BEFORE_NOTICE).owner)
        assertFalse(session.carry(near(me, .95f, 2), .6f, OwnerSession.KEEP_MS + 1, rule).owner)
        assertEquals(0, session.size)
        session.confirm(me, 0); session.clear(); assertEquals(0, session.size)
        assertFalse(session.carry(near(me, .95f, 2), .6f, 10, rule).owner)
    }

    @Test fun `keeps at most eight turns and resets on a model change`() {
        val session = OwnerSession(); val me = unit(1)
        repeat(12) { session.confirm(near(me, .95f, 100 + it), it.toLong()) }
        assertEquals(OwnerSession.MAX_TURNS, session.size)
        session.confirm(unit(9, 256), 20)
        assertEquals(1, session.size)
        assertFalse(session.carry(near(me, .99f, 2), .9f, 30, rule).owner)
    }

    @Test fun `stranger cohort normalization fails closed and ranks closeness`() {
        val cohort = StrangerCohort(List(60) { unit(1000 + it) })
        val me = unit(1); val profile = near(me, .99f, 2)
        val strict = VoiceMatch.Thresholds.STANDARD.copy(cohortZ = 3f)
        val mine = listOf(near(me, .85f, 3), near(me, .85f, 4))
        val strangers = listOf(unit(50), unit(51))
        assertTrue(VoiceMatch.aboveStrangers(profile, mine, strict, cohort))
        assertFalse(VoiceMatch.aboveStrangers(profile, strangers, strict, cohort))
        assertFalse("no cohort, no tuned accept", VoiceMatch.aboveStrangers(profile, mine, strict, null))
        assertTrue("rule without z ignores the cohort", VoiceMatch.aboveStrangers(profile, strangers, VoiceMatch.Thresholds.STANDARD, null))
        assertEquals(Speaker.UNKNOWN, VoiceMatch.identifyProfiles(listOf(profile), mine, strict, null))
        assertEquals(Speaker.USER, VoiceMatch.identifyProfiles(listOf(profile), mine, strict, cohort))
    }

    @Test fun `quiet-room profile gets the lower bar, other settings the higher one`() {
        val t = VoiceMatch.Thresholds(both = .71f, balancedMin = .66f, balancedMean = .73f, consistency = .65f, quietOffset = .07f)
        val q = t.quiet()
        assertEquals(.64f, q.both, 1e-6f); assertEquals(.59f, q.balancedMin, 1e-6f); assertEquals(.66f, q.balancedMean, 1e-6f)
        val me = unit(1); val windows = listOf(near(me, .675f, 3), near(me, .675f, 4))
        val consistent = VoiceMatch.similarity(windows[0], windows[1]) >= .65f
        assertEquals(if (consistent) Speaker.USER else Speaker.UNKNOWN, VoiceMatch.identifyProfiles(listOf(me), windows, t, quiet = listOf(true)))
        assertNotEquals(Speaker.USER, VoiceMatch.identifyProfiles(listOf(me), windows, t, quiet = listOf(false)))
        assertSame(VoiceMatch.Thresholds.BEFORE_NOTICE, VoiceMatch.Thresholds.BEFORE_NOTICE.quiet())
        assertThrows(IllegalArgumentException::class.java) { t.copy(quietOffset = .2f) }
    }

    @Test fun `held chunk becomes the owner only between two owner chunks that it sounds like`() {
        val me = unit(1); val n = NeighborChunks()
        assertEquals(null to false, n.next(Speaker.USER, near(me, .95f, 2), 0, .72f))
        assertEquals(null to true, n.next(Speaker.UNKNOWN, near(me, .9f, 3), 1000, .72f))
        assertEquals(true to false, n.next(Speaker.USER, near(me, .95f, 4), 2000, .72f))
        // Next chunk is someone else: the held chunk stays unassigned.
        n.next(Speaker.UNKNOWN, near(me, .9f, 5), 3000, .72f)
        assertEquals(false to false, n.next(Speaker.OTHER, unit(9), 4000, .72f))
        // A stranger between two owner chunks does not sound like them.
        n.next(Speaker.USER, near(me, .95f, 6), 5000, .72f)
        assertEquals(null to true, n.next(Speaker.UNKNOWN, unit(10), 6000, .72f))
        assertEquals(false to false, n.next(Speaker.USER, near(me, .95f, 7), 7000, .72f))
        // Off (no bar, e.g. before notice), and too far apart.
        n.next(Speaker.USER, near(me, .95f, 8), 8000, null)
        assertEquals(null to false, n.next(Speaker.UNKNOWN, near(me, .9f, 9), 9000, null))
        n.next(Speaker.USER, near(me, .95f, 8), 10_000, .72f); n.next(Speaker.UNKNOWN, near(me, .9f, 9), 11_000, .72f)
        assertEquals(false to false, n.next(Speaker.USER, near(me, .95f, 10), 11_000 + NeighborChunks.MAX_GAP_MS + 1, .72f))
    }

    @Test fun `session-adapted profile moves toward confirmed turns and has its own bar`() {
        val quiet = unit(1); val today = near(quiet, .6f, 2)
        val adapt = VoiceMatch.SessionAdapt(weight = .3f, both = .67f)
        val moved = adapt.profile(quiet, today)
        assertTrue(VoiceMatch.similarity(moved, today) > VoiceMatch.similarity(quiet, today))
        assertTrue(VoiceMatch.similarity(moved, quiet) > .9f)
        assertEquals(.62f, adapt.rule(VoiceMatch.Thresholds.STANDARD).balancedMin, 1e-6f)
        val session = OwnerSession(); assertNull(session.mean(0))
        session.confirm(today, 0); assertEquals(1f, VoiceMatch.similarity(session.mean(10)!!, today), 1e-5f)
        assertNull(session.mean(OwnerSession.KEEP_MS + 1))
    }

    @Test fun `cohort file round trip`() {
        val vectors = List(50) { unit(2000 + it, 256) }
        val buffer = java.nio.ByteBuffer.allocate(8 + 50 * 256 * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(50).putInt(256); vectors.forEach { v -> v.forEach(buffer::putFloat) }
        val cohort = StrangerCohort.read(buffer.array())
        assertEquals(256, cohort.dimension); assertEquals(50, cohort.size)
        assertThrows(IllegalArgumentException::class.java) { StrangerCohort.read(buffer.array().copyOf(100)) }
    }
}
