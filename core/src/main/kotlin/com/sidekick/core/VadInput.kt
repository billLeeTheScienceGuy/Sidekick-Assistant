package com.sidekick.core

import kotlin.math.abs
import kotlin.math.sqrt

/** Only for a temporary VAD copy. Never normalize recognition or voice-profile PCM. */
object VadInput {
    fun prepareInPlace(context: FloatArray) {
        require(context.isNotEmpty() && context.all { it.isFinite() && it in -1f..1f })
        val rms=sqrt(context.sumOf{it.toDouble()*it}/context.size)
        val peak=context.maxOf{abs(it)}
        if(rms<.0002 || rms>=.02 || peak==0f)return
        val gain=minOf(4.0,.02/rms,.95/peak).coerceAtLeast(1.0).toFloat()
        if(gain>1f)for(i in context.indices)context[i]*=gain
    }
}
