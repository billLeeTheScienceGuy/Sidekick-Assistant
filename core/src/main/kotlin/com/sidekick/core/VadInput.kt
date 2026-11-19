package com.sidekick.core

import kotlin.math.abs
import kotlin.math.sqrt

/** Only for a temporary VAD copy. Never normalize recognition or voice-profile PCM. */
object VadInput {
    /** One pass for the checks, loudness and peak (was three); same results, about a third of the work. */
    fun prepareInPlace(context: FloatArray) {
        require(context.isNotEmpty())
        var sum = 0.0
        var peak = 0f
        for (v in context) {
            require(v.isFinite() && v in -1f..1f)
            sum += v.toDouble() * v
            val a = abs(v); if (a > peak) peak = a
        }
        val rms = sqrt(sum / context.size)
        if (rms < .0002 || rms >= .02 || peak == 0f) return
        val gain = minOf(4.0, .02 / rms, .95 / peak).coerceAtLeast(1.0).toFloat()
        if (gain > 1f) for (i in context.indices) context[i] *= gain
    }
}
