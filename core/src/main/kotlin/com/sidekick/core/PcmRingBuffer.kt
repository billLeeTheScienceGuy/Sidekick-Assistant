package com.sidekick.core

/** Single-writer bounded pre-roll. Snapshots are copies in chronological order. */
class PcmRingBuffer(private val capacity: Int) {
    init { require(capacity > 0) }
    private val buffer = ShortArray(capacity)
    private var cursor = 0
    var size: Int = 0
        private set

    fun append(samples: ShortArray, count: Int = samples.size) {
        require(count in 0..samples.size)
        for (i in 0 until count) {
            buffer[cursor] = samples[i]
            cursor = (cursor + 1) % capacity
            if (size < capacity) size++
        }
    }

    fun snapshot(): ShortArray = ShortArray(size) { buffer[(cursor - size + capacity + it) % capacity] }
    fun clear() { buffer.fill(0); cursor = 0; size = 0 }
}
