package com.sidekick.app.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import java.io.File

/**
 * NVIDIA Parakeet TDT 110M (English only), optional "english" pack. On the overnight benchmark it was the most accurate
 * English model tested (0.5% clean, 2.0% noisy/far vs SenseVoice 2.1% / 3.2%) and the fastest on the Pixel 7a (~0.4 s per clip).
 * Owned by SpeechEngine's process-wide gate. Audio remains in caller-owned RAM.
 */
internal class EnglishSpeechEngine(private val context: Context) {
    private var recognizer: OfflineRecognizer? = null
    private val verified = mutableMapOf<String, File>()
    private fun install(name: String, expected: String): File {
        verified[name]?.takeIf { it.isFile }?.let { return it }
        return ModelPacks.ensure(context, name, expected).also { verified[name] = it }
    }
    fun available() = FILES.all { (name, _) -> ModelPacks.available(context, name) }
    fun transcribe(samples: FloatArray, threads: Int, retain: Boolean): String {
        require(samples.size in 1600..480000 && samples.all { it.isFinite() })
        try {
            val engine = recognizer ?: OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = install(ENCODER, ENCODER_HASH).absolutePath, decoder = install(DECODER, DECODER_HASH).absolutePath,
                    joiner = install(JOINER, JOINER_HASH).absolutePath),
                tokens = install(TOKENS, TOKENS_HASH).absolutePath, modelType = "nemo_transducer", numThreads = threads))).also { recognizer = it }
            val stream = engine.createStream()
            return try {
                stream.acceptWaveform(samples, 16000); engine.decode(stream)
                engine.getResult(stream).text.trim()
            } finally { stream.release() }
        } finally { if (!retain) release() }
    }
    fun release() { recognizer?.release(); recognizer = null }
    companion object {
        const val ENCODER = "parakeet-encoder.int8.onnx"
        const val ENCODER_HASH = "0f35509ddeb9b39002fb077d979a9fe74f06eb0bc4dd5c34f512f82e5111d657"
        const val DECODER = "parakeet-decoder.int8.onnx"
        const val DECODER_HASH = "f7c331c5504c2e593c76ed22b728e3f554af6c4a383dde862e719ced08b1da19"
        const val JOINER = "parakeet-joiner.int8.onnx"
        const val JOINER_HASH = "bf7dff69e9f2cdbe9943d70da358f38b361c115ba0105bae7e908e0d6ec782f6"
        const val TOKENS = "parakeet-tokens.txt"
        const val TOKENS_HASH = "450e56bd2f036fe5b6aa821865838cc5aa9d8b0106134ce9a9ba0664abe6cd10"
        val FILES = listOf(ENCODER to ENCODER_HASH, DECODER to DECODER_HASH, JOINER to JOINER_HASH, TOKENS to TOKENS_HASH)
    }
}
