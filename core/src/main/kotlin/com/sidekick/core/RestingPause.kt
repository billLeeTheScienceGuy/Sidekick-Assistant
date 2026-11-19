package com.sidekick.core

/**
 * "Pause when the phone is resting" (optional, off by default). Listening pauses (microphone fully off) after
 * [quietMs] with no speech, no screen turned on and no carrying motion; any of those resumes it. While the phone sits
 * untouched and paused, a conversation that starts nearby is not heard until someone moves the phone or turns on its screen.
 */
class RestingPause(startMs: Long, private val quietMs: Long = 30 * 60_000L) {
    private var lastActivityMs = startMs
    var paused = false
        private set

    /** Speech, the screen turning on, an unlock or carrying motion. */
    fun activity(nowMs: Long) { lastActivityMs = maxOf(lastActivityMs, nowMs) }

    /** True when listening should pause now. */
    fun shouldPause(nowMs: Long, enabled: Boolean, callActive: Boolean): Boolean =
        enabled && !paused && !callActive && nowMs - lastActivityMs >= quietMs

    fun pause() { paused = true }
    fun resume(nowMs: Long) { paused = false; lastActivityMs = nowMs }
}
