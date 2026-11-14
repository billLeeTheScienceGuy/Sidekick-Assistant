package com.sidekick.core

/** 16 kHz live speech: half-second onset cushion, short breath allowance, ten-second bounded chunks. */
object SpeechCapturePolicy {
    fun segmenter() = SpeechSegmenter(160_000, 8_192, 20_480, 3_072, overlapSamples = 16_000)
}
