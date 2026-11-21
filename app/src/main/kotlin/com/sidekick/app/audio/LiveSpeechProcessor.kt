package com.sidekick.app.audio

import com.sidekick.app.speech.SpeechEngine
import com.sidekick.app.speech.NativeVad
import com.sidekick.core.SpeechSegmenter
import com.sidekick.core.SpeakerPart
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicInteger

/**
 * Capture/VAD feeds a waiting line while one worker transcribes. Nothing is skipped and listening never stops for being
 * busy: waiting speech is kept compactly (16-bit, about 2 MB per minute) in a line sized by the phone's free memory, and
 * when the measured recognizer speed predicts a long wait, phrases use faster recognition until it has caught up
 * (BacklogPolicy). Only if Android reports memory as low and the line is still over its limit is the oldest audio dropped.
 */
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
    /** Free memory in bytes and whether Android reports it as low (for sizing the waiting line). */
    private val memory: () -> Pair<Long, Boolean> = { Runtime.getRuntime().let { it.maxMemory() - (it.totalMemory() - it.freeMemory()) } to false },
    /** Android's thermal status (0 = none ... 3 = severe and above). */
    private val thermal: () -> Int = { 0 },
    /** Whether faster recognition can run for the current language (its files are installed). */
    private val fasterAvailable: () -> Boolean = { false },
    private val onText: suspend (String, Long, Long) -> Unit,
) {
    private val segmenter = com.sidekick.core.SpeechCapturePolicy.segmenter()
    private val speechContext = com.sidekick.core.SpeechContext()
    private class Waiting(val pcm: ShortArray, val startSample: Long, val continuesPrevious: Boolean)
    private val lock = Any()
    private val waiting = java.util.ArrayDeque<Waiting>()   // guarded by lock
    private var waitingSamples = 0L                         // guarded by lock
    @Volatile private var closed = false
    private val wake = Channel<Unit>(Channel.CONFLATED)
    val policy = com.sidekick.core.BacklogPolicy()
    private val history = FloatArray(28_672)
    private var historySize = 0
    private val transcriptions = AtomicInteger()
    val transcriptionCount: Int get() = transcriptions.get()
    private val noise = AtomicInteger()
    private val dropped = AtomicInteger()
    /** Phrases dropped because Android reported memory as low while the waiting line was over its limit. */
    val droppedCount: Int get() = dropped.get()
    private val failed = AtomicInteger()
    /** Phrases whose recognition failed twice (listening continued). */
    val failedCount: Int get() = failed.get()
    private val fasterPhrases = AtomicInteger()
    /** Phrases recognized with faster recognition to catch up. */
    val fasterCount: Int get() = fasterPhrases.get()
    /** Seconds of speech waiting to be recognized. */
    val waitingSeconds: Long get() = synchronized(lock) { waitingSamples } / 16_000
    @Volatile var catchingUp = false
        private set
    /** Phrases recognized as noise or music. */
    val noiseCount: Int get() = noise.get()

    private fun next(): Waiting? = synchronized(lock) { waiting.pollFirst()?.also { waitingSamples -= it.pcm.size } }

    suspend fun consume() {
        try {
            while (true) {
                val item = next()
                if (item == null) {
                    if (closed && synchronized(lock) { waiting.isEmpty() }) break
                    wake.receiveCatching(); continue
                }
                val samples = FloatArray(item.pcm.size) { item.pcm[it] / 32768f }
                item.pcm.fill(0)
                val start = android.os.SystemClock.elapsedRealtime()
                try {
                    if (!inspect(samples)) { speechContext.clear(); continue }
                    // A failed or invalid speaker split never stops listening: the phrase is treated as one speaker.
                    val split = try { partition?.invoke(samples) } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { android.util.Log.w("SidekickListen", "speaker split failed: ${e.javaClass.simpleName}"); null }
                    val valid = split != null && split.isNotEmpty() && split.size <= 4 && split.first().start == 0 && split.last().end == samples.size &&
                        split.all { it.start >= 0 && it.end > it.start && it.end <= samples.size } && split.zipWithNext().all { (a, b) -> a.end == b.start }
                    val parts = if (valid) split!! else listOf(SpeakerPart(0, samples.size, null, false))
                    for ((index, part) in parts.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val audio = if (parts.size == 1) samples else samples.copyOfRange(part.start, part.end)
                    try {
                    if (!onPart(audio, part)) { speechContext.clear(); continue }
                    transcriptions.incrementAndGet()
                    val continues = index == 0 && item.continuesPrevious && !part.ambiguous
                    if (!continues) speechContext.clear()
                    val sourceStart = item.startSample + part.start
                    val window = speechContext.prepare(audio, sourceStart, contextKey())
                    val behind = synchronized(lock) { waitingSamples } * 1000 / 16_000
                    val faster = policy.useFaster(behind, thermal()) && fasterAvailable()
                    catchingUp = faster
                    if (faster) fasterPhrases.incrementAndGet()
                    val shortBoost = !faster && audio.size < speech.shortBoostLimit() && behind == 0L && parts.size == 1
                    val began = android.os.SystemClock.elapsedRealtime()
                    // A slow phrase is allowed to finish (never skipped); the phrases behind it catch up with faster recognition.
                    // A recognition error never stops listening: the phrase is tried once more with faster recognition,
                    // and if that fails too only this phrase is lost (counted, logged without words). The engine wipes
                    // the audio it is given, so each attempt gets a copy.
                    val heard = try {
                        if (faster) speech.transcribe(window.copyOf(), 2, true, 0, selectedQuality = com.sidekick.core.SpeechQuality.FAST, shortPhrase = false, voiceKey = voiceKey())
                        else speech.transcribe(window.copyOf(), 2, true, speech.liveContext(shortBoost), shortPhrase = shortBoost, voiceKey = voiceKey())
                    } catch (e: CancellationException) { throw e } catch (e: Exception) {
                        android.util.Log.w("SidekickListen", "recognition failed: ${e.javaClass.simpleName}: ${e.message}")
                        if (!faster && fasterAvailable()) try {
                            speech.transcribe(window.copyOf(), 2, true, 0, selectedQuality = com.sidekick.core.SpeechQuality.FAST, shortPhrase = false, voiceKey = voiceKey())
                        } catch (e2: CancellationException) { throw e2 } catch (_: Exception) { failed.incrementAndGet(); "" }
                        else { failed.incrementAndGet(); "" }
                    } finally { window.fill(0f) }
                    val workMs = android.os.SystemClock.elapsedRealtime() - began
                    policy.measured(workMs, audio.size * 1000L / 16_000)
                    if (workMs > 30_000) android.util.Log.w("SidekickListen", "slow phrase: ${workMs / 1000} s (kept)")
                    // Recognizer loops ("thank you" x12) keep one copy; music and noise scraps are never shown or used.
                    val unlooped = com.sidekick.core.RepeatFilter.collapse(heard)
                    val text = if (com.sidekick.core.PhraseFilter.isNoise(unlooped, audio.size * 1000L / 16000)) { noise.incrementAndGet(); "" } else unlooped
                    onBoundary(continues)
                    onText(text, sourceStart, android.os.SystemClock.elapsedRealtime() - start)
                    } finally { if (audio !== samples) audio.fill(0f) }
                    }
                    onChunkProcessed(android.os.SystemClock.elapsedRealtime() - start, samples.size)
                } finally { samples.fill(0f) }
                if (synchronized(lock) { waiting.isEmpty() }) catchingUp = false
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
        // The detector at background priority (only around this call, so no shared thread keeps it): Android runs it on
        // the efficient cores. Measured: processor energy 40.6 -> 23.6 mW while listening to silence.
        val tid = android.os.Process.myTid()
        val before = if (lowPriority) android.os.Process.getThreadPriority(tid) else 0
        if (lowPriority) android.os.Process.setThreadPriority(tid, android.os.Process.THREAD_PRIORITY_BACKGROUND)
        return try { detect(batch, PRE + BATCH).also { lastDetected = it } }
        finally { batch.fill(0f); if (lowPriority) android.os.Process.setThreadPriority(tid, before) }
    }
    @Volatile var lowPriority = true

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
        val pcm = ShortArray(chunk.samples.size) { (chunk.samples[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
        chunk.samples.fill(0f)
        synchronized(lock) {
            waiting.addLast(Waiting(pcm, chunk.startSample, chunk.continuesPrevious)); waitingSamples += pcm.size
            // Only when Android reports memory as low and the line is over its share: drop the oldest (counted, shown).
            val (free, low) = runCatching { memory() }.getOrDefault(Long.MAX_VALUE to false)
            while (waiting.size > 1 && policy.mustDrop(waitingSamples * 2, free, low)) {
                waiting.pollFirst()?.let { waitingSamples -= it.pcm.size; it.pcm.fill(0) }
                dropped.incrementAndGet()
                android.util.Log.w("SidekickListen", "oldest waiting phrase dropped: memory low")
            }
        }
        wake.trySend(Unit)
    }

    fun finish() { flushPending(); segmenter.finish()?.let(::enqueue); closed = true; wake.trySend(Unit) }
    fun close() {
        closed = true; wake.close()
        synchronized(lock) { waiting.forEach { it.pcm.fill(0) }; waiting.clear(); waitingSamples = 0 }
        segmenter.clear(); history.fill(0f); historySize = 0; pending.fill(0f); pendingSize = 0
    }

    private companion object { const val PRE = 12_288; const val BATCH = 16_384 }
}
