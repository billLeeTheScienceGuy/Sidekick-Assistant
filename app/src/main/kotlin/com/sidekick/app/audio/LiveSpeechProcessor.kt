package com.sidekick.app.audio

import com.sidekick.app.speech.SpeechEngine
import com.sidekick.app.speech.NativeVad
import com.sidekick.core.SpeechSegmenter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicInteger

/** Capture/VAD feeds a bounded queue while one worker transcribes concurrently. */
class LiveSpeechProcessor(
    private val speech: SpeechEngine,
    private val inspect: (FloatArray) -> Boolean = { true },
    private val contextKey: () -> String? = { null },
    private val onBoundary: (Boolean) -> Unit = {},
    private val onText: suspend (String, Long, Long) -> Unit,
) {
    private val segmenter = SpeechSegmenter(160_000, 4_096, 16_384, 3_072, overlapSamples = 16_000)
    private val speechContext = com.sidekick.core.SpeechContext()
    private val queued = AtomicInteger()
    private val queue = Channel<SpeechSegmenter.Segment>(2, onUndeliveredElement = { queued.decrementAndGet(); it.samples.fill(0f) })
    private val history = FloatArray(16_384)
    private var historySize = 0
    private val transcriptions = AtomicInteger()
    val transcriptionCount: Int get() = transcriptions.get()

    suspend fun consume() {
        try {
            for (chunk in queue) {
                queued.decrementAndGet()
                val start = android.os.SystemClock.elapsedRealtime()
                try {
                    if (!inspect(chunk.samples)) { speechContext.clear(); continue }
                    transcriptions.incrementAndGet()
                    if (!chunk.continuesPrevious) speechContext.clear()
                    val window = speechContext.prepare(chunk.samples, chunk.startSample, contextKey())
                    // Boost isolated short English phrases; let tiny catch up when speech is queued.
                    val shortBoost = chunk.samples.size < speech.shortBoostLimit() && queued.get() == 0
                    val text = try { withTimeout(30_000) { speech.transcribe(window, 2, true, speech.liveContext(shortBoost), shortPhrase = shortBoost) } }
                        catch (_: TimeoutCancellationException) { error("Transcription timed out. Listening stopped; review text captured so far.") }
                        finally { if (window !== chunk.samples) window.fill(0f) }
                    onBoundary(chunk.continuesPrevious)
                    onText(text, chunk.startSample, android.os.SystemClock.elapsedRealtime() - start)
                } finally { chunk.samples.fill(0f) }
            }
        } finally { speechContext.clear(); withContext(NonCancellable) { speech.release() } }
    }

    /** At most 4096 new samples; VAD sees a rolling context to avoid per-frame state resets. */
    fun accept(samples: FloatArray): Boolean {
        require(samples.size in 1..4096)
        val keep = minOf(historySize, history.size - samples.size)
        history.copyInto(history, 0, historySize - keep, historySize)
        samples.copyInto(history, keep)
        historySize = keep + samples.size
        val paddedSize = ((historySize + 511) / 512) * 512
        // Thirty-two frames of context retain VAD continuity across native state resets.
        val context = history.copyOf(historySize).copyOf(paddedSize)
        val probabilities = try { NativeVad.probabilities(context) } finally { context.fill(0f) }
        val offset = historySize - samples.size
        var detected = false
        for (index in samples.indices step 512) {
            val frame = samples.copyOfRange(index, minOf(index + 512, samples.size))
            try {
                val speaking = probabilities[(offset + index) / 512] >= .5f
                detected = detected || speaking
                segmenter.accept(frame, speaking).forEach(::enqueue)
            } finally { frame.fill(0f) }
        }
        return detected
    }

    private fun enqueue(chunk: SpeechSegmenter.Segment) {
        if (chunk.samples.size < 1600) { chunk.samples.fill(0f); return }
        queued.incrementAndGet()
        if (queue.trySend(chunk).isFailure) {
            queued.decrementAndGet()
            chunk.samples.fill(0f)
            error("Transcription fell behind. Listening stopped; review the text captured so far.")
        }
    }

    fun finish() { segmenter.finish()?.let(::enqueue); queue.close() }
    fun close() { queue.cancel(); segmenter.clear(); history.fill(0f); historySize = 0 }
}
