package com.sidekick.app.speaker

import android.content.Context
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.sidekick.core.Speaker
import com.sidekick.core.VoiceMatch
import java.io.File
import java.security.MessageDigest

/** Called only on the single speech worker, before Whisper wipes each PCM chunk. */
class SpeakerEngine(private val context: Context, private val profile: VoiceProfile) {
    var lastDiagnostic: String = "No speech analyzed"
        private set
    private var extractor: SpeakerEmbeddingExtractor? = null
    private var extractorModel: SpeakerModel? = null
    private val anchors = com.sidekick.core.VoiceAnchors()
    private var profileLanguage = profile.language.value
    private fun clearRecent() = anchors.clear()
    private fun extractor(): SpeakerEmbeddingExtractor {
        val selected = profile.model.value
        if (extractorModel != selected) { extractor?.release(); extractor = null; clearRecent(); extractorModel = selected }
        extractor?.let { return it }
        val model = File(context.noBackupFilesDir, selected.fileName)
        fun hash(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        if (!model.exists() || hash(model) != selected.hash) {
            val temp = File(context.noBackupFilesDir, "speaker-model.download")
            try {
                context.assets.open("models/${selected.fileName}").use { input -> temp.outputStream().use(input::copyTo) }
                check(hash(temp) == selected.hash) { "Speaker model checksum mismatch" }
                check(temp.renameTo(model)) { "Could not install speaker model" }
            } finally { temp.delete() }
        }
        return SpeakerEmbeddingExtractor(config = SpeakerEmbeddingExtractorConfig(model.absolutePath, numThreads = 1)).also {
            check(it.dim() == selected.dimension); extractor = it
        }
    }
    fun embedding(samples: FloatArray): FloatArray {
        val span = com.sidekick.core.SpeakerAudio.speechSpan(samples) ?: throw IllegalArgumentException("No clear speech for voice setup.")
        val speech = samples.copyOfRange(span.first, span.last + 1)
        return try { computeEmbedding(speech) } finally { speech.fill(0f) }
    }
    private fun computeEmbedding(samples: FloatArray): FloatArray {
        require(samples.size >= 24_000) { "Speak in longer phrases for voice setup." }
        require(samples.count { kotlin.math.abs(it) > .005f } >= samples.size / 10) { "Speak louder, closer to the microphone." }
        require(samples.count { kotlin.math.abs(it) >= .99f } < samples.size / 100) { "Microphone was too loud; move farther away." }
        val engine = extractor()
        val stream = engine.createStream()
        return try {
            stream.acceptWaveform(samples, 16000); stream.inputFinished()
            check(engine.isReady(stream)) { "Not enough speech for voice setup." }
            VoiceMatch.normalized(engine.compute(stream))
        } finally { stream.release() }
    }
    fun identify(samples: FloatArray, nowMs: Long = android.os.SystemClock.elapsedRealtime()): Speaker {
        val span = com.sidekick.core.SpeakerAudio.speechSpan(samples)
        if (span == null) {
            clearRecent(); lastDiagnostic = "No clear speech after excluding quiet edges"; return Speaker.UNKNOWN
        }
        val speech = samples.copyOfRange(span.first, span.last + 1)
        return try {
            identifySpeech(speech, nowMs).also {
                lastDiagnostic = String.format(java.util.Locale.US, "%.1fs captured · %s", samples.size / 16000.0, lastDiagnostic.replaceFirst("s:", "s speech:"))
            }
        } finally { speech.fill(0f) }
    }
    private fun identifySpeech(samples: FloatArray, nowMs: Long): Speaker {
        if (profileLanguage != profile.language.value) { clearRecent(); profileLanguage = profile.language.value }
        val seconds = "%.1f".format(java.util.Locale.US, samples.size / 16000.0)
        val reference = profile.snapshot() ?: run {
            clearRecent()
            lastDiagnostic = if (profile.enabled.value) "${seconds}s: no enrolled profile" else "${seconds}s: voice matching is off"
            return Speaker.UNKNOWN
        }
        try {
            if (samples.size < 48_000) {
                val age = anchors.ageMs(nowMs)
                if (samples.size < 24_000 || age == null) {
                    lastDiagnostic = if (samples.size < 24_000) "${seconds}s: too short; need at least 1.5 seconds"
                        else "${seconds}s: no recent confident long phrase; speaker unassigned"
                    clearRecent()
                    return Speaker.UNKNOWN
                }
                return try {
                    val vector = embedding(samples)
                    try {
                        val score = VoiceMatch.similarity(reference, vector)
                        val support = anchors.support(vector, nowMs) ?: return Speaker.UNKNOWN
                        val recentScore = support.score
                        val result = VoiceMatch.classifyShortScores(score, recentScore, samples.size * 1000L / 16000L, support.ageMs, profile.model.value.shortThreshold)
                        lastDiagnostic = String.format(java.util.Locale.US, "%ss: %s; short profile %.2f; recent mean %.2f; two-window support %.2f; anchor age %ds",
                            seconds, result.name, score, support.meanScore, support.windowScore, support.ageMs / 1000)
                        if (result != Speaker.USER && (score < profile.model.value.shortThreshold || recentScore < profile.model.value.shortThreshold - .15f)) clearRecent()
                        // Borderline evidence stays unassigned but does not erase a trusted turn.
                        // Short phrases never add embeddings or extend the anchor's lifetime.
                        result
                    } finally { vector.fill(0f) }
                } catch (e: IllegalArgumentException) {
                    clearRecent(); lastDiagnostic = "${seconds}s: ${e.message}"; Speaker.UNKNOWN
                }
            }
            // Speaker windows are independent of the ASR window and overlap to expose turn changes.
            val width = if (samples.size < 64_000) samples.size / 2 else 48_000
            val starts = (0..samples.size - width step 32_000).toMutableSet().apply { add(samples.size - width) }.sorted()
            val vectors = mutableListOf<FloatArray>()
            return try {
                for (start in starts) {
                    val window = samples.copyOfRange(start, start + width)
                    try { vectors += embedding(window) } finally { window.fill(0f) }
                }
                val result = VoiceMatch.identifyTurn(reference, vectors)
                if (result == Speaker.USER) anchors.remember(vectors, nowMs) else clearRecent()
                val scores = vectors.joinToString(" / ") { String.format(java.util.Locale.US, "%.2f", VoiceMatch.similarity(reference, it)) }
                val consistency = vectors.zipWithNext { a, b -> VoiceMatch.similarity(a, b) }.minOrNull() ?: 0f
                lastDiagnostic = String.format(java.util.Locale.US, "%ss: %s; %d speaker windows; profile %s; minimum consistency %.2f",
                    seconds, result.name, vectors.size, scores, consistency)
                result
            }
                catch (e: IllegalArgumentException) { clearRecent(); lastDiagnostic = "${seconds}s: ${e.message}"; Speaker.UNKNOWN }
                finally { vectors.forEach { it.fill(0f) } }
        } finally { reference.fill(0f) }
    }
    fun close() { clearRecent(); extractor?.release(); extractor = null; extractorModel = null }
    companion object { const val HASH = "e9848563da86f263117134dfd7ad63c92355b37de492b55e325400c9d9c39012" }
}
