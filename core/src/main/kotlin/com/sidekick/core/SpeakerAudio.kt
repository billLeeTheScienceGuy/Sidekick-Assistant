package com.sidekick.core

import kotlin.math.sqrt

/** Trim quiet edges only. Never join speech across internal pauses or change ASR audio. */
object SpeakerAudio {
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
