package com.sidekick.core

import kotlin.math.ceil

/**
 * Did the spoken notice actually play out loud? Checks, in order:
 * 1. Sound goes to the phone's own speaker, not headphones or Bluetooth.
 * 2. The proximity sensor does not read "near" (pocket, bag, face-down, covered top).
 * 3. The media volume is at least [MIN_VOLUME_FRACTION] (raised for the notice, then restored) and not muted.
 * 4. The microphone measured real playback (no audio kept, only frame levels).
 * Calibration on a Pixel 7a: on a table 0.0485, bottom edge covered by a hand 0.0768. The phone's own microphone sits
 * next to its speaker, so loudness cannot tell a covered phone from an open one; check 4 only catches "no sound came
 * out". Pockets are caught by check 2. None of this shows that everyone heard it.
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
    /** Proximity reading: anything below the sensor's maximum range means something is right in front of it. */
    fun covered(distance: Float, maxRange: Float): Boolean = distance.isFinite() && maxRange > 0f && distance < maxRange
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
