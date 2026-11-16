package com.sidekick.core

import kotlin.math.ceil

/**
 * Was the spoken notice loud enough to count? The phone's volume is raised to at least [MIN_VOLUME_FRACTION] for the
 * notice, and the microphone measures how loud the playback actually was (no audio is kept, only frame levels).
 * A notice that was barely picked up (phone in a pocket, speaker covered) does not count. This shows the notice was
 * audible near the phone, not that everyone heard it.
 */
object NoticeAudibility {
    const val MIN_VOLUME_FRACTION = 0.5f
    /** RMS level (full scale = 1.0) the loudest quarter of playback frames must reach. Calibrated on a Pixel 7a. */
    const val MIN_LEVEL = 0.01f

    /** Volume step to use for the notice: the current one, or at least half of the maximum. */
    fun targetVolume(current: Int, max: Int): Int = maxOf(current, ceil(max * MIN_VOLUME_FRACTION).toInt()).coerceAtMost(max)
    fun percent(volume: Int, max: Int): Int = if (max <= 0) 0 else (100 * volume + max / 2) / max
    /** Mean RMS of the loudest quarter of frames measured while the notice played; 0 if too few frames. */
    fun loudness(levels: List<Float>): Float {
        val clean = levels.filter { it.isFinite() && it >= 0f }
        if (clean.size < 4) return 0f
        val top = clean.sortedDescending().take(clean.size / 4)
        return top.sum() / top.size
    }
    fun audible(levels: List<Float>, minimum: Float = MIN_LEVEL): Boolean = loudness(levels) >= minimum
}

/**
 * "Announce automatically when a new conversation starts" (setting, off by default).
 * - The first time another voice speaks while no notice is in effect, the notice is played and that phrase is dropped
 *   (it was said before the notice). This covers the first conversation of a session and every one after an expiry.
 * - At the 2-hour cap in an ongoing meeting (others spoke in the last [meetingWindowMs]), it is played right away.
 * - A failed or too-quiet notice is retried on the next other voice, at most once per [cooldownMs].
 */
class AutoAnnouncer(private val cooldownMs: Long = 120_000L, private val meetingWindowMs: Long = 5 * 60_000L) {
    @Volatile var enabled: Boolean = false
    private var pending = true
    private var lastAttemptMs: Long? = null

    private fun attempt(nowMs: Long): Boolean {
        if (lastAttemptMs?.let { nowMs - it in 0 until cooldownMs } == true) return false
        lastAttemptMs = nowMs; pending = false
        return true
    }
    /** A notice expired. Returns true if it should be played right now (the 2-hour cap during a meeting). */
    @Synchronized fun expired(reason: String, nowMs: Long, othersLastMs: Long?): Boolean {
        pending = true
        if (!enabled) return false
        val meeting = othersLastMs != null && nowMs - othersLastMs in 0..meetingWindowMs
        return reason == ConsentGate.EXPIRED_MAX_AGE && meeting && attempt(nowMs)
    }
    /** Another voice spoke. Returns true if the notice should be played now and this phrase dropped. */
    @Synchronized fun otherVoice(nowMs: Long, noticeActive: Boolean): Boolean {
        if (noticeActive) { pending = false; return false }
        return enabled && pending && attempt(nowMs)
    }
    /** The notice did not count (too quiet, no voice, error): try again on the next other voice. */
    @Synchronized fun failed() { pending = true }
}
