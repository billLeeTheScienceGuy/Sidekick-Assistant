package com.sidekick.app.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import java.io.File
import java.security.MessageDigest

/** Owned by SpeechEngine's process-wide gate. Audio remains in caller-owned RAM. */
internal class OmniSpeechEngine(private val context: Context) {
    private var recognizer: OfflineRecognizer? = null
    private val verified = mutableMapOf<String, File>()
    private fun install(name: String, expected: String): File {
        verified[name]?.takeIf { it.isFile }?.let { return it }
        return ModelPacks.ensure(context, name, expected).also { verified[name] = it }
    }
    fun transcribe(samples: FloatArray, threads: Int, retain: Boolean): String {
        require(samples.size in 1600..480000 && samples.all { it.isFinite() })
        try {
            val engine = recognizer ?: OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = OfflineModelConfig(
                omnilingual = OfflineOmnilingualAsrCtcModelConfig(model = install(MODEL, MODEL_HASH).absolutePath),
                tokens = install(TOKENS, TOKENS_HASH).absolutePath, numThreads = threads))).also { recognizer = it }
            val stream = engine.createStream()
            // Speech touching the clip edge often decodes to nothing: 0.5 s of silence on each side cut empty
            // outputs from 32 to 17 of 107 natural clips and German/French errors from ~55% to ~15% (YODAS evaluation).
            val padded = FloatArray(samples.size + 2 * EDGE).also { samples.copyInto(it, EDGE) }
            return try {
                stream.acceptWaveform(padded, 16000); engine.decode(stream)
                engine.getResult(stream).text.replace(Regex("<\\|[^|>]+\\|>"), "").trim()
            } finally { stream.release(); padded.fill(0f) }
        } finally { if (!retain) release() }
    }
    fun release() { recognizer?.release(); recognizer = null }
    companion object {
        private const val EDGE = 8_000
        const val MODEL = "omni-model.int8.onnx"
        const val MODEL_HASH = "e7c4e54ee4c4c47829cc6667d5d00ed8ea7bef1dcfeef0fce766f77752a2726c"
        const val TOKENS = "omni-tokens.txt"
        const val TOKENS_HASH = "a7a044c52cb29cbe8b0dc1953e92cefd4ca16b0ed968177b6beab21f9a7d0b31"
    }
}
