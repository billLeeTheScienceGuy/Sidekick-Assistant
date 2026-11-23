package com.sidekick.core

/**
 * How SideKick keeps up with speech without skipping or stopping. It measures, as it runs:
 * - the recognizer's current speed (seconds of work per second of speech, averaged over recent phrases),
 * - how much speech is waiting, and how much memory the phone has free,
 * - how hot the phone is (Android's thermal status 0 = none ... 4 = severe and above).
 * From these it decides whether to use the faster recognizer for a while, and (only when Android says memory is
 * critically low and nothing else helps) whether the oldest waiting audio must be dropped.
 */
class BacklogPolicy(
    private val catchUpAboveMs: Long = 60_000,   // predicted wait that switches to faster recognition
    private val relaxBelowMs: Long = 15_000,     // predicted wait that switches back
    private val memoryShare: Double = 0.25,      // share of free memory the waiting line may use
    private val minimumBytes: Long = 32L * 1024 * 1024,
) {
    /** Recent seconds of work per second of speech (starts at 1: as long as the speech itself). */
    var speed = 1.0
        private set
    var catchingUp = false
        private set

    /** After each phrase: how long recognition took for how much speech. */
    fun measured(workMs: Long, audioMs: Long) {
        if (audioMs <= 0) return
        val now = (workMs.toDouble() / audioMs).coerceIn(0.01, 20.0)
        speed = speed * 0.7 + now * 0.3
    }

    /** Predicted time to clear what is waiting, at the measured speed. */
    fun predictedWaitMs(waitingAudioMs: Long): Long = (waitingAudioMs * speed).toLong()

    /** Whether to use faster recognition now (with a gap between switching on and off, so it does not flip-flop). */
    fun useFaster(waitingAudioMs: Long, thermalStatus: Int): Boolean {
        val hot = thermalStatus >= 3 // severe: catch up earlier, everything is slower when hot
        val wait = predictedWaitMs(waitingAudioMs)
        catchingUp = if (catchingUp) wait > relaxBelowMs else wait > (if (hot) catchUpAboveMs / 2 else catchUpAboveMs)
        return catchingUp
    }

    /** Most bytes the waiting line may hold: a share of the memory that is free right now. */
    fun limitBytes(freeMemoryBytes: Long, memoryLow: Boolean): Long =
        maxOf(minimumBytes, (freeMemoryBytes * if (memoryLow) memoryShare / 4 else memoryShare).toLong())

    /** Drop the oldest waiting audio only when over the limit while Android reports memory as low. */
    fun mustDrop(waitingBytes: Long, freeMemoryBytes: Long, memoryLow: Boolean): Boolean =
        memoryLow && waitingBytes > limitBytes(freeMemoryBytes, memoryLow = true)
}
