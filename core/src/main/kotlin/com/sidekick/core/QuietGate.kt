package com.sidekick.core

import kotlin.math.sqrt

/**
 * Skips speech detection for audio that is too quiet to hold speech. Measured on the Pixel microphone (20 minutes of
 * replayed speech in a room, 2,845 speech chunks): the softest speech chunk was -56 dBFS and the room itself -54 dBFS,
 * so the gate sits at -60 dBFS (no speech chunk below it). It only saves work in rooms quieter than that, such as at
 * night; in a normal room it never triggers.
 */
object QuietGate {
    const val LEVEL_DBFS = -60.0
    private val level = Math.pow(10.0, LEVEL_DBFS / 20).toFloat() // 0.001

    fun rms(samples: FloatArray): Float {
        var sum = 0.0
        for (s in samples) sum += s * s
        return if (samples.isEmpty()) 0f else sqrt(sum / samples.size).toFloat()
    }

    fun silent(samples: FloatArray): Boolean = rms(samples) < level
}
