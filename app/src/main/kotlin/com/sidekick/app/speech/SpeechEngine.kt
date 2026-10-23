package com.sidekick.app.speech

import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.sidekick.core.SpeechLanguage

class SpeechEngine(private val context: Context, private val language: () -> SpeechLanguage = { SpeechLanguage.ENGLISH }) {
    private val verified = mutableSetOf<String>()

    suspend fun transcribe(
        samples: FloatArray, threads: Int = 2, retainModel: Boolean = false, audioContext: Int = 0,
        selectedLanguage: SpeechLanguage = language(),
    ): String = try {
        gate.withLock {
            require(threads in 1..4)
            require(audioContext == 0 || (audioContext in 1..1500 && samples.size <= audioContext * 320))
            withContext(Dispatchers.IO) {
                val model = installModel(selectedLanguage)
                ensureActive()
                NativeSpeech.prepare()
                // Cancellation is cooperative in native code; never unload its context mid-call.
                suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation { NativeSpeech.cancel() }
                    try {
                        val result = NativeSpeech.transcribe(model.absolutePath, samples, threads, retainModel, audioContext, selectedLanguage.code)
                            .toString(Charsets.UTF_8).trim()
                        continuation.resumeWith(Result.success(result))
                    } catch (e: Exception) { continuation.resumeWith(Result.failure(e)) }
                }
            }
        }
    } finally { samples.fill(0f) }

    fun cancel() { NativeSpeech.cancel() }
    suspend fun release() = gate.withLock { withContext(Dispatchers.IO) { NativeSpeech.release() } }

    private fun installModel(language: SpeechLanguage): File {
        val model = File(context.noBackupFilesDir, if (language.multilingual) "ggml-tiny.bin" else "ggml-tiny.en.bin")
        val expected = if (language.multilingual) MULTILINGUAL_HASH else HASH
        if (model.name in verified && model.exists()) return model
        if (model.exists() && sha256(model) == expected) { verified += model.name; return model }
        val temp = File(context.noBackupFilesDir, "speech-model.download")
        try {
            context.assets.open("models/${model.name}").use { input -> temp.outputStream().use(input::copyTo) }
            check(sha256(temp) == expected) { "Speech model checksum mismatch." }
            check(temp.renameTo(model)) { "Could not install speech model." }
            verified += model.name
        } finally { temp.delete() }
        return model
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(64 * 1024)
            while (true) { val count = input.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val gate = Mutex()
        const val HASH = "921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f"
        const val MULTILINGUAL_HASH = "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21"
    }
}
