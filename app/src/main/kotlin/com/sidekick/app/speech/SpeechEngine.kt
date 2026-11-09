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
    private val verified = mutableMapOf<String, File>()
    private val senseVoice = SenseVoiceEngine(context)
    private val omni = OmniSpeechEngine(context)
    @Volatile var lastClockDiagnostic: String? = null
        private set
    @Volatile var lastBackend: String? = null
        private set
    fun shortBoostLimit(): Int = if (language() in setOf(SpeechLanguage.AUTO, SpeechLanguage.FRENCH, SpeechLanguage.GERMAN, SpeechLanguage.RUSSIAN)) 128_000 else 48_000
    fun liveContext(shortPhrase: Boolean): Int = quality().liveContext(language(), shortPhrase)

    suspend fun transcribe(
        samples: FloatArray, threads: Int = 2, retainModel: Boolean = false, audioContext: Int = 0,
        selectedLanguage: SpeechLanguage = language(),
        selectedQuality: SpeechQuality = quality(),
        beamSize: Int = 1,
        shortPhrase: Boolean = samples.size < if (selectedLanguage in setOf(SpeechLanguage.AUTO, SpeechLanguage.FRENCH, SpeechLanguage.GERMAN, SpeechLanguage.RUSSIAN)) 128_000 else 48_000,
    ): String = try {
        gate.withLock {
            lastClockDiagnostic = null
            lastBackend = null
            require(threads in 1..4)
            require(beamSize in 1..5)
            require(audioContext == 0 || (audioContext in 1..1500 && samples.size <= audioContext * 320))
            withContext(Dispatchers.IO) {
                val speechSamples = if (selectedQuality == SpeechQuality.ADAPTIVE && selectedLanguage in setOf(SpeechLanguage.AUTO, SpeechLanguage.FRENCH, SpeechLanguage.GERMAN, SpeechLanguage.RUSSIAN))
                    com.sidekick.core.SpeakerAudio.speechSpan(samples)?.let { it.last - it.first + 1 } ?: samples.size else samples.size
                if (selectedQuality.usesOmni(selectedLanguage, shortPhrase, speechSamples)) {
                    senseVoice.release(); NativeSpeech.release(); ensureActive()
                    var result = omni.transcribe(samples, threads, retainModel)
                    lastBackend = "Omnilingual int8"
                    if (selectedLanguage == SpeechLanguage.AUTO && shortPhrase && speechSamples < 80_000) {
                        val hint = com.sidekick.core.ShortLanguageHint.identify(result)
                        if (hint != null && hint != SpeechLanguage.ARABIC) {
                            val primary = result
                            try {
                                val revised = refineShortAutomatic(samples, threads, retainModel, hint)
                                if (revised.isNullOrBlank()) lastBackend = "Omnilingual int8" else result = revised
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { result = primary; lastBackend = "Omnilingual Â· short refinement unavailable" }
                        }
                    }
                    ensureActive(); result
                } else if (selectedQuality.usesSenseVoice(selectedLanguage)) {
                    omni.release()
                    NativeSpeech.release()
                    ensureActive()
                    var result = senseVoice.transcribe(samples, threads, retainModel, selectedLanguage.code)
                    lastBackend = "SenseVoice int8"
                    if (selectedLanguage == SpeechLanguage.KOREAN) result = repairKoreanClock(result, samples, threads, retainModel)
                    ensureActive()
                    result
                } else {
                    omni.release()
                    senseVoice.release()
                val model = installModel(selectedLanguage, selectedQuality.model(selectedLanguage, shortPhrase))
                ensureActive()
                whisper(model, samples, threads, retainModel, audioContext, selectedLanguage, beamSize).also {
                    lastBackend = if (model.name == "ggml-base.bin") "Whisper Base" else "Whisper Tiny"
                }
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

    /** Queue-clear short audio only; the second model hears the original PCM, not corrected text. */
    private suspend fun refineShortAutomatic(samples: FloatArray, threads: Int, retain: Boolean, language: SpeechLanguage): String? {
        if (language in setOf(SpeechLanguage.KOREAN, SpeechLanguage.CHINESE)) {
            if (!ModelPacks.available(context, SenseVoiceEngine.MODEL) || !ModelPacks.available(context, SenseVoiceEngine.TOKENS)) return null
            omni.release(); NativeSpeech.release(); currentCoroutineContext().ensureActive()
            return senseVoice.transcribe(samples, threads, retain, language.code).also { lastBackend = "SenseVoice Â· automatic short speech" }
        }
        val quality = if (language in setOf(SpeechLanguage.FRENCH, SpeechLanguage.RUSSIAN)) SpeechQuality.ACCURATE else SpeechQuality.FAST
        val required = if (quality == SpeechQuality.ACCURATE) "ggml-base.bin" else if (language == SpeechLanguage.ENGLISH) "ggml-tiny.en.bin" else "ggml-tiny.bin"
        if (!ModelPacks.available(context, required)) return null
        omni.release(); senseVoice.release(); currentCoroutineContext().ensureActive()
        val result = whisper(installModel(language, quality), samples, threads, retain, if (language == SpeechLanguage.GERMAN) 0 else 512, language, 1)
        lastBackend = "Whisper ${if (quality == SpeechQuality.ACCURATE) "Base" else "Tiny"} Â· automatic short speech"
        return result
    }

    /** Cross-check only suspicious Korean clock readings; never merge unrelated wording. */
    private suspend fun repairKoreanClock(original: String, samples: FloatArray, threads: Int, retain: Boolean): String {
        if (!com.sidekick.core.KoreanClockRepair.needsRetry(original)) return original
        var result = original
        val readings = mutableListOf("SenseVoice: ${com.sidekick.core.KoreanClockRepair.clockText(original)}")
        senseVoice.release(); currentCoroutineContext().ensureActive()
        try {
            val second = whisper(installModel(SpeechLanguage.KOREAN, SpeechQuality.ACCURATE), samples, threads, false, 0, SpeechLanguage.KOREAN, 1)
            readings += "Whisper base: ${com.sidekick.core.KoreanClockRepair.clockText(second)}"
            result = com.sidekick.core.KoreanClockRepair.select(result, second)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { readings += "Whisper retry unavailable" }
        // A missing optional Whisper pack must not prevent the independent Omni retry.
        if (result == original) {
            NativeSpeech.release(); currentCoroutineContext().ensureActive()
            try {
                val third = omni.transcribe(samples, threads, retain)
                readings += "Omnilingual: ${com.sidekick.core.KoreanClockRepair.clockText(third)}"
                result = com.sidekick.core.KoreanClockRepair.select(result, third)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { readings += "Omnilingual retry unavailable" }
        }
        lastClockDiagnostic = readings.joinToString(" · ") + if (result == original) " · unresolved; original kept" else " · clock corrected; other wording kept"
        lastBackend = "SenseVoice · clock cross-check"
        return result
    }

    private fun installModel(language: SpeechLanguage, quality: SpeechQuality): File {
        val name = if (quality == SpeechQuality.ACCURATE) "ggml-base.bin" else if (language.multilingual) "ggml-tiny.bin" else "ggml-tiny.en.bin"
        val expected = if (quality == SpeechQuality.ACCURATE) BASE_HASH else if (language.multilingual) MULTILINGUAL_HASH else HASH
        verified[name]?.takeIf { it.isFile }?.let { return it }
        return ModelPacks.ensure(context, name, expected).also { verified[name] = it }
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
        internal suspend fun <T> modelMaintenance(block: suspend () -> T): T = gate.withLock { block() }
        const val HASH = "921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f"
        const val MULTILINGUAL_HASH = "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21"
        const val BASE_HASH = "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe"
    }
}
