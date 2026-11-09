package com.sidekick.core

import kotlin.math.sqrt

/** Trim quiet edges only. Never join speech across internal pauses or change ASR audio. */
object SpeakerAudio {
    /** Expand a matching window around pauses; never concatenate speech or modify ASR samples. */
    fun matchingWindow(samples: FloatArray, start: Int, width: Int): IntRange? {
        require(start >= 0 && width >= 24_000 && start + width <= samples.size)
        val maximum = maxOf(width, minOf(72_000, samples.size * 3 / 4))
        var from = start; var until = start + width
        while (true) {
            val energy = (from until until step 160).map { at ->
                val end = minOf(at + 160, until)
                val mean = (at until end).sumOf { samples[it].toDouble() } / (end - at)
                sqrt((at until end).sumOf { val v = samples[it] - mean; v * v } / (end - at))
            }
            if (energy.any { !it.isFinite() }) return null
            val sorted = energy.sorted()
            val threshold = maxOf(.0025, sorted[((sorted.size - 1) * .95).toInt()] * .06)
            if (energy.count { it >= threshold } * 160 >= 24_000) return from until until
            if (until - from >= maximum) return null
            val grow = minOf(8000, maximum - (until - from))
            var left = minOf(from, grow / 2)
            var right = minOf(samples.size - until, grow - left)
            left = minOf(from, grow - right)
            right = minOf(samples.size - until, grow - left)
            if (left + right == 0) return null
            from -= left; until += right
        }
    }
    fun speechSpan(samples: FloatArray): IntRange? {
        if (samples.isEmpty() || samples.any { !it.isFinite() }) return null
        val frame = 160 // 10 ms at 16 kHz
        val energy = (samples.indices step frame).map { start ->
            val end = minOf(start + frame, samples.size)
            val mean = (start until end).sumOf { samples[it].toDouble() } / (end - start)
            sqrt((start until end).sumOf { val value = samples[it] - mean; value * value } / (end - start))
        }
        val sorted = energy.sorted()
        val threshold = maxOf(.0025, sorted[((sorted.size - 1) * .95).toInt()] * .06)
        // A click must not establish either speech boundary.
        val runs = (0..energy.size - 3).filter { i -> (i until i + 3).all { energy[it] >= threshold } }
        if (runs.isEmpty()) return null
        val start = maxOf(0, runs.first() * frame - 640)
        val end = minOf(samples.size, (runs.last() + 3) * frame + 640)
        return start until end
    }
}
