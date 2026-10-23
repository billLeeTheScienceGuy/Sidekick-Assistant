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
    private var recentUser: FloatArray? = null
    private var recentUserAt = 0L
    private fun clearRecent() { recentUser?.fill(0f); recentUser = null; recentUserAt = 0L }
    private fun extractor(): SpeakerEmbeddingExtractor {
        extractor?.let { return it }
        val model = File(context.noBackupFilesDir, "voxceleb_resnet34_LM.onnx")
        fun hash(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        if (!model.exists() || hash(model) != HASH) {
            val temp = File(context.noBackupFilesDir, "speaker-model.download")
            try {
                context.assets.open("models/voxceleb_resnet34_LM.onnx").use { input -> temp.outputStream().use(input::copyTo) }
                check(hash(temp) == HASH) { "Speaker model checksum mismatch" }
                check(temp.renameTo(model)) { "Could not install speaker model" }
            } finally { temp.delete() }
        }
        return SpeakerEmbeddingExtractor(config = SpeakerEmbeddingExtractorConfig(model.absolutePath, numThreads = 1)).also {
            check(it.dim() == 256); extractor = it
        }
    }
    fun embedding(samples: FloatArray): FloatArray {
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
        val seconds = "%.1f".format(java.util.Locale.US, samples.size / 16000.0)
        val reference = profile.snapshot() ?: run {
            clearRecent()
            lastDiagnostic = if (profile.enabled.value) "${seconds}s: no enrolled profile" else "${seconds}s: voice matching is off"
            return Speaker.UNKNOWN
        }
        try {
            if (samples.size < 48_000) {
                val anchor = recentUser
                val age = nowMs - recentUserAt
                if (samples.size < 24_000 || anchor == null || age !in 0L..30_000L) {
                    lastDiagnostic = if (samples.size < 24_000) "${seconds}s: too short; need at least 1.5 seconds"
                        else "${seconds}s: no recent confident long phrase; speaker unassigned"
                    clearRecent()
                    return Speaker.UNKNOWN
                }
                return try {
                    val vector = embedding(samples)
                    try {
                        val score = VoiceMatch.similarity(reference, vector)
                        val recentScore = VoiceMatch.similarity(anchor, vector)
                        val result = VoiceMatch.classifyShortScores(score, recentScore, samples.size * 1000L / 16000L, age)
                        lastDiagnostic = String.format(java.util.Locale.US, "%ss: %s; short profile %.2f; recent voice %.2f; anchor age %ds",
                            seconds, result.name, score, recentScore, age / 1000)
                        if (result != Speaker.USER) clearRecent()
                        // Short phrases never extend the anchor's lifetime.
                        result
                    } finally { vector.fill(0f) }
                } catch (e: IllegalArgumentException) {
                    clearRecent(); lastDiagnostic = "${seconds}s: ${e.message}"; Speaker.UNKNOWN
                }
            }
            val split = samples.size / 2
            val first = samples.copyOfRange(0, split)
            val second = samples.copyOfRange(split, samples.size)
            return try {
                val a = embedding(first)
                val b = embedding(second)
                val result = VoiceMatch.identify(reference, a, b)
                clearRecent()
                if (result == Speaker.USER) {
                    recentUser = VoiceMatch.normalized(FloatArray(a.size) { a[it] + b[it] })
                    recentUserAt = nowMs
                }
                lastDiagnostic = String.format(java.util.Locale.US, "%ss: %s; profile %.2f / %.2f; consistency %.2f",
                    seconds, result.name, VoiceMatch.similarity(reference, a), VoiceMatch.similarity(reference, b), VoiceMatch.similarity(a, b))
                a.fill(0f); b.fill(0f)
                result
            }
                catch (e: IllegalArgumentException) { clearRecent(); lastDiagnostic = "${seconds}s: ${e.message}"; Speaker.UNKNOWN }
                finally { first.fill(0f); second.fill(0f) }
        } finally { reference.fill(0f) }
    }
    fun close() { clearRecent(); extractor?.release(); extractor = null }
    companion object { const val HASH = "e9848563da86f263117134dfd7ad63c92355b37de492b55e325400c9d9c39012" }
}
