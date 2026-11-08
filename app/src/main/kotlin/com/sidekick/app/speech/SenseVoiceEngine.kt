package com.sidekick.app.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import java.io.File
import java.security.MessageDigest

/** Owned by SpeechEngine's process-wide gate. Audio remains in caller-owned RAM. */
internal class SenseVoiceEngine(private val context: Context) {
    private var recognizer: OfflineRecognizer? = null
    private var recognizerLanguage: String? = null
    private val verified = mutableMapOf<String, File>()
    private fun install(name: String, expected: String): File {
        verified[name]?.takeIf { it.isFile }?.let { return it }
        return ModelPacks.ensure(context, name, expected).also { verified[name] = it }
    }
    fun transcribe(samples: FloatArray, threads: Int, retain: Boolean, language: String): String {
        require(language in setOf("ko", "zh"))
        require(samples.size in 1600..480000 && samples.all { it.isFinite() })
        try {
            if (recognizerLanguage != language) { release(); recognizerLanguage = language }
            val engine = recognizer ?: OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = install(MODEL, MODEL_HASH).absolutePath, language = language, useInverseTextNormalization = true),
                tokens = install(TOKENS, TOKENS_HASH).absolutePath, numThreads = threads))).also { recognizer = it }
            val stream = engine.createStream()
            return try {
                stream.acceptWaveform(samples, 16000); engine.decode(stream)
                engine.getResult(stream).text.replace(Regex("<\\|[^|>]+\\|>"), "").trim()
            } finally { stream.release() }
        } finally { if (!retain) release() }
    }
    fun release() { recognizer?.release(); recognizer = null; recognizerLanguage = null }
    companion object {
        const val MODEL = "sensevoice-ko.int8.onnx"
        const val MODEL_HASH = "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51"
        const val TOKENS = "sensevoice-tokens.txt"
        const val TOKENS_HASH = "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc"
    }
}
