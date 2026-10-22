package com.sidekick.core

/** Initial limits for Pixel 7a; tuning hypotheses, not measured performance claims. */
object Pixel7aProfile {
    const val sampleRate = 16_000
    const val preRollSeconds = 2
    const val maxUtteranceSeconds = 30
    const val maxQueuedUtterances = 1
    const val initialCpuThreads = 2
    const val maxLlmContextTokens = 2_048
    const val maxLlmOutputTokens = 256
    const val speechModel = "whisper-tiny.en"
    const val intentModel = "Qwen3-0.6B-Q4_K_M"
    const val nativeAbi = "arm64-v8a"
}
