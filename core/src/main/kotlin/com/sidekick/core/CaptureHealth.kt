package com.sidekick.core

/** Detects a stalled PCM source independently of amplitude: digital silence is valid data. */
class CaptureHealth(startedAtMs: Long, private val maxGapMs: Long = 5_000) {
    private var lastDataAt = startedAtMs
    init { require(startedAtMs >= 0 && maxGapMs > 0) }
    fun check(read: Int, nowMs: Long) {
        check(read >= 0) { "Microphone disconnected. Listening stopped." }
        if (read > 0) lastDataAt = nowMs
        check(nowMs - lastDataAt < maxGapMs) { "Microphone stopped supplying audio. Listening stopped; check the audio device and start again." }
    }
}
