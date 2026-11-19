package com.sidekick.app.audio

import com.sidekick.app.speech.SpeechEngine
import com.sidekick.app.speech.NativeVad
import com.sidekick.core.SpeechSegmenter
import com.sidekick.core.SpeakerPart
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicInteger

/** Capture/VAD feeds a bounded queue while one worker transcribes concurrently. */
class LiveSpeechProcessor(
    private val speech: SpeechEngine,
    private val inspect: (FloatArray) -> Boolean = { true },
    private val contextKey: () -> String? = { null },
    /** Current voice track, so Auto-detect can remember each voice's language. */
    private val voiceKey: () -> String? = { null },
    private val onBoundary: (Boolean) -> Unit = {},
    private val partition: ((FloatArray) -> List<SpeakerPart>)? = null,
    /** Called with each speaker part before recognition; false skips it (never transcribed). */
    private val onPart: (FloatArray, SpeakerPart) -> Boolean = { _, _ -> true },
    private val onChunkProcessed: (Long, Int) -> Unit = { _, _ -> },
    private val onText: suspend (String, Long, Long) -> Unit,
) {
    private val segmenter = com.sidekick.core.SpeechCapturePolicy.segmenter()
    private val speechContext = com.sidekick.core.SpeechContext()
    private val queued = AtomicInteger()
    private val queue = Channel<SpeechSegmenter.Segment>(2, onUndeliveredElement = { queued.decrementAndGet(); it.samples.fill(0f) })
    private val history = FloatArray(28_672)
    private var historySize = 0
    private val transcriptions = AtomicInteger()
    val transcriptionCount: Int get() = transcriptions.get()
    private val noise = AtomicInteger()
    /** Phrases dropped as music or noise. */
    val noiseCount: Int get() = noise.get()

    suspend fun consume() {
        try {
            for (chunk in queue) {
                queued.decrementAndGet()
                val start = android.os.SystemClock.elapsedRealtime()
                try {
                    if (!inspect(chunk.samples)) { speechContext.clear(); continue }
                    val parts = partition?.invoke(chunk.samples) ?: listOf(SpeakerPart(0, chunk.samples.size, null, false))
                    require(parts.isNotEmpty() && parts.size <= 4 && parts.first().start == 0 && parts.last().end == chunk.samples.size &&
                        parts.all { it.start >= 0 && it.end > it.start && it.end <= chunk.samples.size } &&
                        parts.zipWithNext().all { (a,b) -> a.end == b.start }) { "Invalid speaker partition" }
                    for ((index, part) in parts.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val audio = if (parts.size == 1) chunk.samples else chunk.samples.copyOfRange(part.start, part.end)
                    try {
                    if (!onPart(audio, part)) { speechContext.clear(); continue }
                    transcriptions.incrementAndGet()
                    val continues = index == 0 && chunk.continuesPrevious && !part.ambiguous
                    if (!continues) speechContext.clear()
                    val sourceStart = chunk.startSample + part.start
                    val window = speechContext.prepare(audio, sourceStart, contextKey())
                    val shortBoost = audio.size < speech.shortBoostLimit() && queued.get() == 0 && parts.size == 1
                    val heard = try { withTimeout(30_000) { speech.transcribe(window, 2, true, speech.liveContext(shortBoost), shortPhrase = shortBoost, voiceKey = voiceKey()) } }
                        catch (_: TimeoutCancellationException) { error("Transcription timed out. Listening stopped; review text captured so far.") }
                        finally { if (window !== chunk.samples) window.fill(0f) }
                    // Recognizer loops ("thank you" ×12) keep one copy; music and noise scraps are never shown or used.
                    val unlooped = com.sidekick.core.RepeatFilter.collapse(heard)
                    val text = if (com.sidekick.core.PhraseFilter.isNoise(unlooped, audio.size * 1000L / 16000)) { noise.incrementAndGet(); "" } else unlooped
                    onBoundary(continues)
                    onText(text, sourceStart, android.os.SystemClock.elapsedRealtime() - start)
                    } finally { if (audio !== chunk.samples) audio.fill(0f) }
                    }
                    onChunkProcessed(android.os.SystemClock.elapsedRealtime() - start, chunk.samples.size)
                } finally { chunk.samples.fill(0f) }
            }
        } finally { speechContext.clear(); withContext(NonCancellable) { speech.release() } }
    }

    /** Test switch only (phone battery comparison): false runs the speech detector on every chunk, as before. */
    @Volatile var quietGate = true
    /**
     * The speech detector runs on 1 s of new audio at a time (after 12,288 samples of earlier audio), not every
     * quarter-second: 44% of the detector work, and on 20 minutes of real phone recording no speech stretch was lost
     * (it flags slightly more audio as speech). Speech is recognized as started up to 1 s later; no audio is dropped.
     * Test switch: false = the old quarter-second runs.
     */
    @Volatile var batchVad = true
    private val pending = FloatArray(BATCH)
    private var pendingSize = 0
    private var lastDetected = false

    /** At most 4096 new samples; VAD sees a rolling context to avoid per-frame state resets. */
    fun accept(samples: FloatArray): Boolean {
        require(samples.size in 1..4096)
        if (!batchVad) return detect(samples, 16_384)
        samples.copyInto(pending, pendingSize); pendingSize += samples.size
        if (pendingSize + 4096 <= BATCH) return lastDetected
        return flushPending()
    }

    /** Runs the detector on audio waiting for a full second (at a pause or the end of listening). */
    fun flushPending(): Boolean {
        if (pendingSize == 0) return lastDetected
        val batch = pending.copyOf(pendingSize)
        pending.fill(0f); pendingSize = 0
        return try { detect(batch, PRE + BATCH).also { lastDetected = it } } finally { batch.fill(0f) }
    }

    private fun detect(samples: FloatArray, capacity: Int): Boolean {
        val keep = minOf(historySize, capacity - samples.size)
        history.copyInto(history, 0, historySize - keep, historySize)
        samples.copyInto(history, keep)
        historySize = keep + samples.size
        // A truly silent room (below -60 dBFS; the softest speech measured on the phone microphone was -56 dBFS): skip
        // the speech detector, which is the main cost while nobody talks. The audio is still kept as context.
        if (quietGate && com.sidekick.core.QuietGate.silent(samples)) {
            for (index in samples.indices step 512) {
                val frame = samples.copyOfRange(index, minOf(index + 512, samples.size))
                try { segmenter.accept(frame, false).forEach(::enqueue) } finally { frame.fill(0f) }
            }
            return false
        }
        val paddedSize = ((historySize + 511) / 512) * 512
        // Thirty-two frames of context retain VAD continuity across native state resets.
        val context = history.copyOf(historySize).copyOf(paddedSize)
        val probabilities = try { com.sidekick.core.VadInput.prepareInPlace(context); NativeVad.probabilities(context) } finally { context.fill(0f) }
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

    fun finish() { flushPending(); segmenter.finish()?.let(::enqueue); queue.close() }
    fun close() { queue.cancel(); segmenter.clear(); history.fill(0f); historySize = 0; pending.fill(0f); pendingSize = 0 }

    private companion object { const val PRE = 12_288; const val BATCH = 16_384 }
}
