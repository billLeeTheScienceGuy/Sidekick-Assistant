package com.sidekick.app.speech

import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.sidekick.core.SpeechLanguage
import com.sidekick.core.SpeechQuality

class SpeechEngine(private val context: Context, private val language: () -> SpeechLanguage = { SpeechLanguage.ENGLISH }, private val quality: () -> SpeechQuality = { SpeechQuality.FAST }) {
    private val verified = mutableSetOf<String>()
    private val senseVoice = SenseVoiceEngine(context)
    private val omni = OmniSpeechEngine(context)
    @Volatile var lastClockDiagnostic: String? = null
        private set
    fun shortBoostLimit(): Int = if (language() in setOf(SpeechLanguage.FRENCH, SpeechLanguage.GERMAN, SpeechLanguage.RUSSIAN)) 128_000 else 48_000
    fun liveContext(shortPhrase: Boolean): Int = quality().liveContext(language(), shortPhrase)

    suspend fun transcribe(
        samples: FloatArray, threads: Int = 2, retainModel: Boolean = false, audioContext: Int = 0,
        selectedLanguage: SpeechLanguage = language(),
        selectedQuality: SpeechQuality = quality(),
        beamSize: Int = 1,
        shortPhrase: Boolean = samples.size < if (selectedLanguage in setOf(SpeechLanguage.FRENCH, SpeechLanguage.GERMAN, SpeechLanguage.RUSSIAN)) 128_000 else 48_000,
    ): String = try {
        gate.withLock {
            lastClockDiagnostic = null
            require(threads in 1..4)
            require(beamSize in 1..5)
            require(audioContext == 0 || (audioContext in 1..1500 && samples.size <= audioContext * 320))
            withContext(Dispatchers.IO) {
                if (selectedQuality.usesOmni(selectedLanguage, shortPhrase)) {
                    senseVoice.release(); NativeSpeech.release(); ensureActive()
                    val result = omni.transcribe(samples, threads, retainModel)
                    ensureActive(); result
                } else if (selectedQuality.usesSenseVoice(selectedLanguage)) {
                    omni.release()
                    NativeSpeech.release()
                    ensureActive()
                    var result = senseVoice.transcribe(samples, threads, retainModel, selectedLanguage.code)
                    if (selectedLanguage == SpeechLanguage.KOREAN && com.sidekick.core.KoreanClockRepair.needsRetry(result)) {
                        val original = result
                        val readings = mutableListOf("SenseVoice: ${com.sidekick.core.KoreanClockRepair.clockText(original)}")
                        senseVoice.release(); ensureActive()
                        try {
                            val second = whisper(installModel(selectedLanguage, SpeechQuality.ACCURATE), samples, threads, false, 0, selectedLanguage, 1)
                            readings += "Whisper base: ${com.sidekick.core.KoreanClockRepair.clockText(second)}"
                            result = com.sidekick.core.KoreanClockRepair.select(result, second)
                            if (result == original) {
                                NativeSpeech.release(); ensureActive()
                                val third = omni.transcribe(samples, threads, retainModel)
                                readings += "Omnilingual: ${com.sidekick.core.KoreanClockRepair.clockText(third)}"
                                result = com.sidekick.core.KoreanClockRepair.select(result, third)
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { readings += "Retry unavailable" }
                        lastClockDiagnostic = readings.joinToString(" · ") + if (result == original) " · unresolved; original kept" else " · clock corrected; other wording kept"
                    }
                    ensureActive()
                    result
                } else {
                    omni.release()
                    senseVoice.release()
                val model = installModel(selectedLanguage, selectedQuality.model(selectedLanguage, shortPhrase))
                ensureActive()
                whisper(model, samples, threads, retainModel, audioContext, selectedLanguage, beamSize)
                }
            }
        }
    } finally { samples.fill(0f) }

    private suspend fun whisper(model: File, samples: FloatArray, threads: Int, retain: Boolean, audioContext: Int, language: SpeechLanguage, beamSize: Int): String {
        NativeSpeech.prepare()
        // Cooperative cancellation keeps the native context alive until decoding returns.
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { NativeSpeech.cancel() }
            try { continuation.resumeWith(Result.success(NativeSpeech.transcribe(model.absolutePath, samples, threads, retain, audioContext, language.code, beamSize).toString(Charsets.UTF_8).trim())) }
            catch (e: Exception) { continuation.resumeWith(Result.failure(e)) }
        }
    }

    fun cancel() { NativeSpeech.cancel() }
    suspend fun release() = gate.withLock { withContext(Dispatchers.IO) { senseVoice.release(); omni.release(); NativeSpeech.release() } }

    private fun installModel(language: SpeechLanguage, quality: SpeechQuality): File {
        val model = File(context.noBackupFilesDir, if (quality == SpeechQuality.ACCURATE) "ggml-base.bin" else if (language.multilingual) "ggml-tiny.bin" else "ggml-tiny.en.bin")
        val expected = if (quality == SpeechQuality.ACCURATE) BASE_HASH else if (language.multilingual) MULTILINGUAL_HASH else HASH
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
        const val BASE_HASH = "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe"
    }
}
