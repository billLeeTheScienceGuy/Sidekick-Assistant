package com.sidekick.app.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import java.io.File

/**
 * One single-language transducer from an optional pack (overnight benchmark):
 * - ENGLISH, NVIDIA Parakeet TDT 110M: 0.5% clean / 2.0% noisy English vs SenseVoice 2.1% / 3.2%; ~0.4 s per clip on a Pixel 7a.
 * - RUSSIAN, GigaAM v3: 4.5% clean / 4.9% noisy Russian vs Omnilingual 6.3% / 14.1%; ~1.1 s per clip.
 * - KOREAN, icefall Zipformer (KsponSpeech): weaker overall, used only to cross-check unclear Korean times; ~0.5 s per clip.
 * Owned by SpeechEngine's process-wide gate. Audio remains in caller-owned RAM.
 */
internal class TransducerSpeechEngine(private val context: Context, private val model: Model) {
    class Model(val encoder: Pair<String, String>, val decoder: Pair<String, String>, val joiner: Pair<String, String>, val tokens: Pair<String, String>,
        val type: String = "nemo_transducer") {
        val files = listOf(encoder, decoder, joiner, tokens)
    }
    private var recognizer: OfflineRecognizer? = null
    private val verified = mutableMapOf<String, File>()
    private fun install(file: Pair<String, String>): String {
        verified[file.first]?.takeIf { it.isFile }?.let { return it.absolutePath }
        return ModelPacks.ensure(context, file.first, file.second).also { verified[file.first] = it }.absolutePath
    }
    fun available() = model.files.all { (name, _) -> ModelPacks.available(context, name) }
    fun transcribe(samples: FloatArray, threads: Int, retain: Boolean): String {
        require(samples.size in 1600..480000 && samples.all { it.isFinite() })
        lastUsedMs = android.os.SystemClock.elapsedRealtime()
        try {
            val engine = recognizer ?: OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(encoder = install(model.encoder), decoder = install(model.decoder), joiner = install(model.joiner)),
                tokens = install(model.tokens), modelType = model.type, numThreads = threads))).also { recognizer = it }
            val stream = engine.createStream()
            return try {
                stream.acceptWaveform(samples, 16000); engine.decode(stream)
                engine.getResult(stream).text.trim()
            } finally { stream.release() }
        } finally { if (!retain) release() }
    }
    private var lastUsedMs = 0L
    fun release() { recognizer?.release(); recognizer = null }
    /** Frees the loaded model when unused for [idleMs]; it reloads on the next phrase that needs it. */
    fun releaseIfIdle(nowMs: Long, idleMs: Long) { if (recognizer != null && nowMs - lastUsedMs > idleMs) release() }
    companion object {
        val ENGLISH = Model(
            "parakeet-encoder.int8.onnx" to "0f35509ddeb9b39002fb077d979a9fe74f06eb0bc4dd5c34f512f82e5111d657",
            "parakeet-decoder.int8.onnx" to "f7c331c5504c2e593c76ed22b728e3f554af6c4a383dde862e719ced08b1da19",
            "parakeet-joiner.int8.onnx" to "bf7dff69e9f2cdbe9943d70da358f38b361c115ba0105bae7e908e0d6ec782f6",
            "parakeet-tokens.txt" to "450e56bd2f036fe5b6aa821865838cc5aa9d8b0106134ce9a9ba0664abe6cd10")
        val RUSSIAN = Model(
            "gigaam-encoder.int8.onnx" to "0ec54e5f130a91c0f228d21795ede10b31c992a1f73e45dcd827e843a71a9b50",
            "gigaam-decoder.onnx" to "5d1cfef155d8e07f213bd2f3229b5f6c5c29355c7fa5c1b9eb62cb1925725254",
            "gigaam-joiner.onnx" to "fd1d02f45c2ad3d6b67cc149811ad794ab4b020ed49a0a9e2790a8619d1cddd8",
            "gigaam-tokens.txt" to "17cc514451bcceac9c280068c71502f8448f99e9fb1456b8d0761651fd0392f2")
        val KOREAN = Model(
            "zipko-encoder.int8.onnx" to "8b196d723421a0513c98ec25da2c43420c029e817f5e4a90b29ff80291c0af2b",
            "zipko-decoder.int8.onnx" to "2cc8c04ea080a657c18ebc59702e6b049cef08163eba5d68ac5bf707925cb0fb",
            "zipko-joiner.int8.onnx" to "eb654db1ea2cc9d63474855f65958b6059084692a9f2eb4f3812aceb1e416a20",
            "zipko-tokens.txt" to "016bdf0965029263b7ad01b742366ee542ef0bef38261510e8176ff6f2e9e668", type = "transducer")
    }
}
