package com.sidekick.core

/** Bounded PCM chunking. The caller supplies speech decisions from its detector.
 * Single-threaded; returned audio belongs to the caller and should be wiped after use.
 */
class SpeechSegmenter(
    private val maxSamples: Int,
    private val preRollSamples: Int,
    private val silenceSamples: Int,
    private val minSpeechSamples: Int,
    private val overlapSamples: Int = 0,
) {
    data class Segment(val startSample: Long, val samples: FloatArray, val continuesPrevious: Boolean = false)

    init {
        require(maxSamples > 0)
        require(preRollSamples in 0 until maxSamples)
        require(silenceSamples in 1..maxSamples)
        require(minSpeechSamples in 1..(maxSamples - preRollSamples))
        require(overlapSamples in 0 until maxSamples)
    }

    private val audio = FloatArray(maxSamples)
    private val preRoll = FloatArray(preRollSamples)
    private var preCount = 0
    private var preNext = 0
    private var count = 0
    private var speechCount = 0
    private var quietCount = 0
    private var position = 0L
    private var start = 0L
    private var continuation = false
    private var freshSpeech = false

    /** Frames must be bounded by maxSamples; one frame may finish multiple chunks. */
    fun accept(frame: FloatArray, speech: Boolean): List<Segment> {
        require(frame.size <= maxSamples)
        require(frame.all { it.isFinite() && it in -1f..1f })
        val ready = mutableListOf<Segment>()
        for (sample in frame) {
            if (count == 0 && !speech) continuation = false
            if (count == 0 && speech) {
                start = position - preCount
                for (i in 0 until preCount) {
                    audio[count++] = preRoll[(preNext - preCount + i + preRoll.size) % preRoll.size]
                }
                preRoll.fill(0f)
                preCount = 0
                preNext = 0
            }
            if (count > 0 || speech) {
                audio[count++] = sample
                if (speech) freshSpeech = true
                if (speech) { speechCount++; quietCount = 0 } else quietCount++
                if (count == maxSamples || quietCount >= silenceSamples) {
                    val continues = count == maxSamples && speech
                    val tail = if (continues && overlapSamples > 0) audio.copyOfRange(count - overlapSamples, count) else null
                    val segment = finish()
                    segment?.let(ready::add)
                    continuation = continues && segment != null
                    if (continuation && tail != null) {
                        tail.copyInto(audio)
                        count = tail.size
                        speechCount = tail.size
                        start = position + 1 - tail.size
                    }
                    tail?.fill(0f)
                }
            } else if (preRoll.isNotEmpty()) {
                preRoll[preNext] = sample
                preNext = (preNext + 1) % preRoll.size
                preCount = minOf(preCount + 1, preRoll.size)
            }
            position++
        }
        return ready
    }

    /** Flushes pending speech when the user finishes. Silence alone produces no chunk. */
    fun finish(): Segment? {
        val result = if (freshSpeech && (speechCount >= minSpeechSamples || (continuation && speechCount > 0)))
            Segment(start, audio.copyOf(count), continuation) else null
        audio.fill(0f)
        preRoll.fill(0f)
        count = 0
        speechCount = 0
        quietCount = 0
        preCount = 0
        preNext = 0
        continuation = false
        freshSpeech = false
        return result
    }

    /** Discards buffered audio and resets the session clock. */
    fun clear() {
        audio.fill(0f)
        preRoll.fill(0f)
        count = 0
        speechCount = 0
        quietCount = 0
        preCount = 0
        preNext = 0
        position = 0
        start = 0
        continuation = false
        freshSpeech = false
    }
}
