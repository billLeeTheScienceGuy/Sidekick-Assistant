package com.sidekick.core

/** Bounded on-heap context, only across independently matched user speech. */
class SpeechContext {
    private var tail = FloatArray(0)
    private var previousEnd = 0L
    private var previousKey: String? = null
    fun prepare(samples: FloatArray, start: Long, key: String?): FloatArray {
        val gap = start - previousEnd
        val use = key != null && key == previousKey && samples.size < 48_000 && gap in -16_000L..240_000L
        val end = (tail.size - maxOf(0L, -gap).toInt()).coerceAtLeast(0)
        val result = if (use && end > 0) tail.copyOfRange(0, end) + samples else samples
        clear()
        if (key != null) {
            tail = samples.copyOfRange(maxOf(0, samples.size - 64_000), samples.size)
            previousEnd = start + samples.size; previousKey = key
        }
        return result
    }
    fun clear() { tail.fill(0f); tail = FloatArray(0); previousKey = null; previousEnd = 0L }
}
