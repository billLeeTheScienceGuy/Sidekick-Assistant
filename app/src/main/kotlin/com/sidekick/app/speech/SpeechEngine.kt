package com.sidekick.app.speech

import android.content.Context
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.sidekick.core.SpeechLanguage
import com.sidekick.core.SpeechQuality

class SpeechEngine(private val context: Context, private val language: () -> SpeechLanguage = { SpeechLanguage.ENGLISH }, private val quality: () -> SpeechQuality = { SpeechQuality.FAST }) {
    private val verified = mutableMapOf<String, File>()
    private val senseVoice = SenseVoiceEngine(context)
    private val omni = OmniSpeechEngine(context)
    private val englishModel = TransducerSpeechEngine(context, TransducerSpeechEngine.ENGLISH)
    private val russianModel = TransducerSpeechEngine(context, TransducerSpeechEngine.RUSSIAN)
    @Volatile var lastClockDiagnostic: String? = null
        private set
    @Volatile var lastBackend: String? = null
        private set
    @Volatile var lastUncertain: Boolean = false
        private set
    @Volatile var lastRetryDiagnostic: String? = null
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
            lastUncertain = false
            lastRetryDiagnostic = null
            lastClockDiagnostic = null
            lastBackend = null
            require(threads in 1..4)
            require(beamSize in 1..5)
            require(audioContext == 0 || (audioContext in 1..1500 && samples.size <= audioContext * 320))
            withContext(Dispatchers.IO) {
                if (selectedLanguage == SpeechLanguage.AUTO && selectedQuality == SpeechQuality.ADAPTIVE &&
                    ModelPacks.available(context, SenseVoiceEngine.MODEL) && ModelPacks.available(context, SenseVoiceEngine.TOKENS))
                    return@withContext detectAndTranscribe(samples, threads, retainModel)
                englishModel.release(); russianModel.release() // only the Auto path uses them; free their memory for the other models
                val speechSamples = if (selectedQuality == SpeechQuality.ADAPTIVE && selectedLanguage in setOf(SpeechLanguage.AUTO, SpeechLanguage.FRENCH, SpeechLanguage.GERMAN, SpeechLanguage.RUSSIAN))
                    com.sidekick.core.SpeakerAudio.speechSpan(samples)?.let { it.last - it.first + 1 } ?: samples.size else samples.size
                if (selectedQuality.usesOmni(selectedLanguage, shortPhrase, speechSamples)) {
                    senseVoice.release(); NativeSpeech.release(); ensureActive()
                    var result = omni.transcribe(samples, threads, retainModel)
                    lastBackend = "Omnilingual int8"
                    if (selectedLanguage == SpeechLanguage.AUTO) {
                        val short = shortPhrase && speechSamples < 80_000
                        val hint = com.sidekick.core.ShortLanguageHint.identify(result)
                        val senseReady = ModelPacks.available(context,SenseVoiceEngine.MODEL) && ModelPacks.available(context,SenseVoiceEngine.TOKENS)
                        // Korean or Korean/English speech of any length goes to SenseVoice; other hints stay short-only.
                        val koreanHint = hint == SpeechLanguage.KOREAN
                        val cjkRetry = senseReady && !koreanHint && hint != SpeechLanguage.CHINESE &&
                            (com.sidekick.core.ShortLanguageHint.koreanSpeech(result) || hint == null && com.sidekick.core.ShortLanguageHint.cjkFallback(result,speechSamples))
                        val hintRetry = hint != null && hint != SpeechLanguage.ARABIC && (short || koreanHint || hint == SpeechLanguage.CHINESE && senseReady)
                        if (hintRetry || cjkRetry) {
                            val primary = result
                            try {
                                val hints = com.sidekick.core.ShortLanguageHint
                                var forcedKorean = false
                                val revised = if(cjkRetry) {
                                    omni.release(); NativeSpeech.release(); ensureActive()
                                    val sense = senseVoice.transcribe(samples,threads,retainModel,"auto")
                                    val merged = hints.restoreLatinEdges(primary, sense)
                                    lastBackend = if (merged != sense.trim()) "SenseVoice Korean + Omnilingual English" else "SenseVoice · automatic Korean/English speech"
                                    // SenseVoice's language guess can lose the Korean, or drop English inside a Korean sentence.
                                    // For mostly-Korean speech, a forced-Korean reading keeps the Korean and writes names in Hangul.
                                    if ((!hints.keepsKorean(primary, merged) || !hints.preservesLatin(primary, merged)) && hints.koreanDominant(primary)) {
                                        ensureActive(); forcedKorean = true
                                        senseVoice.transcribe(samples,threads,retainModel,"ko").also { lastBackend = "SenseVoice · Korean reading (English in Hangul)" }
                                    } else merged
                                } else refineShortAutomatic(samples, threads, retainModel, checkNotNull(hint))
                                val selection = when {
                                    cjkRetry && revised != null && !hints.keepsKorean(primary, revised) ->
                                        com.sidekick.core.RecognitionRetry.Selection(primary,false,reason="Second reading lost the Korean")
                                    cjkRetry && !forcedKorean && revised != null && !hints.preservesLatin(primary,revised) ->
                                        com.sidekick.core.RecognitionRetry.Selection(primary,false,reason="Second reading dropped a spoken Latin name")
                                    else -> com.sidekick.core.RecognitionRetry.select(primary, revised)
                                }
                                result = selection.text; lastUncertain = selection.uncertain; lastRetryDiagnostic = readings(selection, primary, revised)
                                if (!selection.accepted) lastBackend = "Omnilingual · retry kept primary"
                                // Same invalid-clock cross-check the manual Korean mode uses.
                                if ((koreanHint || cjkRetry) && !selection.uncertain) result = repairKoreanClock(result, samples, threads, retainModel)
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { result = primary; lastBackend = "Omnilingual · short refinement unavailable" }
                        } else if (com.sidekick.core.ShortLanguageHint.latinOnly(result) && ModelPacks.available(context, "ggml-base.bin")) {
                            // Omnilingual mangles plain English ("an tuse a three p m" for "On Tuesday at 3 pm").
                            // Whisper Base detects the language itself; the usual time/day/negation checks pick the reading.
                            val primary = result
                            try {
                                omni.release(); senseVoice.release(); ensureActive()
                                val second = whisper(installModel(SpeechLanguage.AUTO, SpeechQuality.ACCURATE), samples, threads, retainModel, 0, SpeechLanguage.AUTO, 1).trim()
                                val selection = if (!com.sidekick.core.ShortLanguageHint.latinOnly(second))
                                    com.sidekick.core.RecognitionRetry.Selection(primary, false, reason = "Whisper changed the language")
                                else com.sidekick.core.RecognitionRetry.select(primary, second)
                                result = selection.text; lastUncertain = selection.uncertain; lastRetryDiagnostic = readings(selection, primary, second)
                                lastBackend = if (selection.accepted) "Whisper Base · Latin-script check" else "Omnilingual · Whisper check kept primary"
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { result = primary; lastBackend = "Omnilingual · Whisper check unavailable" }
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
    } finally {
        // Backend names only; transcripts never reach logcat.
        android.util.Log.i("SidekickSpeech", "language=${selectedLanguage.code} backend=$lastBackend retry=${lastRetryDiagnostic?.substringBefore(" · first:") ?: "none"} uncertain=$lastUncertain")
        samples.fill(0f)
    }

    private suspend fun whisper(model: File, samples: FloatArray, threads: Int, retain: Boolean, audioContext: Int, language: SpeechLanguage, beamSize: Int): String {
        NativeSpeech.prepare()
        // Cooperative cancellation keeps the native context alive until decoding returns.
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { NativeSpeech.cancel() }
            try { continuation.resumeWith(Result.success(NativeSpeech.transcribe(model.absolutePath, samples, threads, retain, audioContext, language.code, beamSize).toString(Charsets.UTF_8).trim())) }
            catch (e: Exception) { continuation.resumeWith(Result.failure(e)) }
        }
    }

    /** In-app review text only (the recognition checks list); never logged. Shows both readings so a wrong pick is visible. */
    private fun readings(selection: com.sidekick.core.RecognitionRetry.Selection, first: String, second: String?) =
        "${if (selection.accepted) "Second reading used" else selection.reason ?: "First reading kept"} · first: “${first.take(140)}” · second: “${second.orEmpty().take(140)}”"

    /**
     * Auto-detect: SenseVoice first; its language tag and reading decide whether Omnilingual is needed (see AutoRoute).
     * Both stay loaded in a live session so a French or Arabic speaker does not reload a model every chunk.
     */
    private suspend fun detectAndTranscribe(samples: FloatArray, threads: Int, retain: Boolean): String {
        NativeSpeech.release(); currentCoroutineContext().ensureActive()
        val speechMs = com.sidekick.core.AutoRoute.speechMs(samples)
        val (sense, tag) = senseVoice.transcribeTagged(samples, threads, retain, "auto")
        val suffices = com.sidekick.core.AutoRoute.senseVoiceSuffices(sense, tag, speechMs)
        if (suffices && tag == "ko") { lastBackend = "SenseVoice · detected ko"; return repairKoreanClock(sense, samples, threads, retain) }
        // Plain English: the optional Parakeet pack (benchmark winner for English) re-reads it. When the two English
        // readings disagree, the speech is probably another language SenseVoice labeled "en", so Omnilingual decides.
        val english = if (com.sidekick.core.AutoRoute.englishOnly(sense, tag) && englishModel.available()) {
            currentCoroutineContext().ensureActive()
            try { englishModel.transcribe(samples, threads, retain) } catch (_: Exception) { null }
        } else null
        if (english != null && com.sidekick.core.AutoRoute.agreeOnEnglish(sense, english)) { lastBackend = "Parakeet English · agrees with SenseVoice"; return english }
        if (suffices && english == null) { lastBackend = "SenseVoice · detected $tag"; return sense }
        currentCoroutineContext().ensureActive()
        val second = omni.transcribe(samples, threads, retain)
        val keep = if (english == null) com.sidekick.core.AutoRoute.preferSenseVoice(sense, tag, second) else second.none { it.isLetter() }
        lastBackend = when {
            english != null && keep -> "SenseVoice · English readings disagreed, Omnilingual heard nothing"
            english != null -> "Omnilingual · English readings disagreed"
            keep -> "SenseVoice · checked with Omnilingual ($tag)"
            else -> "Omnilingual · SenseVoice heard $tag"
        }
        lastRetryDiagnostic = "${if (keep) "SenseVoice kept" else "Omnilingual used"} · first: “${sense.take(140)}” · second: “${second.take(140)}”"
        if (keep) return sense
        // Plain Russian (no Latin words, which the Russian-only model would drop): re-read with the optional GigaAM pack.
        if (com.sidekick.core.AutoRoute.russianOnly(second) && russianModel.available()) {
            currentCoroutineContext().ensureActive()
            val russian = try { russianModel.transcribe(samples, threads, retain) } catch (_: Exception) { "" }
            if (russian.any { it.isLetter() }) { lastBackend = "GigaAM Russian · Omnilingual heard Russian"; return russian }
        }
        return second
    }

    /** Whisper encodes 30 s (1500 frames) by default; 50 frames per second of real audio plus a margin is far cheaper. */
    private fun fittedContext(samples: Int) = ((samples + 319) / 320 + 64).coerceIn(256, 1500)

    /** Evaluation only: one model with automatic language, no routing or retries. Returns text and detected language. */
    internal suspend fun single(samples: FloatArray, backend: String, threads: Int = 2): Pair<String, String?> = try {
        gate.withLock { withContext(Dispatchers.IO) { when (backend) {
            "sense" -> { omni.release(); NativeSpeech.release(); senseVoice.transcribeTagged(samples, threads, true, "auto") }
            "parakeet" -> { omni.release(); senseVoice.release(); NativeSpeech.release(); englishModel.transcribe(samples, threads, true) to "en" }
            "gigaam" -> { omni.release(); senseVoice.release(); NativeSpeech.release(); russianModel.transcribe(samples, threads, true) to "ru" }
            "omni" -> { senseVoice.release(); NativeSpeech.release(); omni.transcribe(samples, threads, true) to null }
            "whisper", "whisperfit" -> { omni.release(); senseVoice.release()
                val context = if (backend == "whisperfit") fittedContext(samples.size) else 0
                whisper(installModel(SpeechLanguage.AUTO, SpeechQuality.ACCURATE), samples, threads, true, context, SpeechLanguage.AUTO, 1) to NativeSpeech.lastLanguage() }
            else -> error("Unknown backend $backend")
        } } }
    } finally { samples.fill(0f) }

    fun cancel() { NativeSpeech.cancel() }
    suspend fun release() = gate.withLock { withContext(Dispatchers.IO) { senseVoice.release(); omni.release(); englishModel.release(); russianModel.release(); NativeSpeech.release() } }

    /** Queue-clear short audio only; the second model hears the original PCM, not corrected text. */
    private suspend fun refineShortAutomatic(samples: FloatArray, threads: Int, retain: Boolean, language: SpeechLanguage): String? {
        if (language in setOf(SpeechLanguage.KOREAN, SpeechLanguage.CHINESE)) {
            if (!ModelPacks.available(context, SenseVoiceEngine.MODEL) || !ModelPacks.available(context, SenseVoiceEngine.TOKENS)) return null
            omni.release(); NativeSpeech.release(); currentCoroutineContext().ensureActive()
            return senseVoice.transcribe(samples, threads, retain, language.code).also { lastBackend = "SenseVoice · automatic short speech" }
        }
        // Whisper Tiny misreads English that Base gets right; use Base for English whenever it is installed.
        val quality = if (language in setOf(SpeechLanguage.FRENCH, SpeechLanguage.RUSSIAN) ||
            language == SpeechLanguage.ENGLISH && ModelPacks.available(context, "ggml-base.bin")) SpeechQuality.ACCURATE else SpeechQuality.FAST
        val required = if (quality == SpeechQuality.ACCURATE) "ggml-base.bin" else if (language == SpeechLanguage.ENGLISH) "ggml-tiny.en.bin" else "ggml-tiny.bin"
        if (!ModelPacks.available(context, required)) return null
        omni.release(); senseVoice.release(); currentCoroutineContext().ensureActive()
        val result = whisper(installModel(language, quality), samples, threads, retain, if (language == SpeechLanguage.GERMAN) 0 else 512, language, 1)
        lastBackend = "Whisper ${if (quality == SpeechQuality.ACCURATE) "Base" else "Tiny"} · automatic short speech"
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
        // An impossible 12-hour clock ("저녁 18시") that no second reading fixes is kept as heard but never scheduled.
        if (result == original) lastUncertain = true
        lastClockDiagnostic = readings.joinToString(" · ") + if (result == original) " · unresolved; original kept, not scheduled" else " · clock corrected; other wording kept"
        lastBackend = "SenseVoice · clock cross-check"
        return result
    }

    private fun installModel(language: SpeechLanguage, quality: SpeechQuality): File {
        val name = if (quality == SpeechQuality.ACCURATE) "ggml-base.bin" else if (language.multilingual) "ggml-tiny.bin" else "ggml-tiny.en.bin"
        val expected = if (quality == SpeechQuality.ACCURATE) BASE_HASH else if (language.multilingual) MULTILINGUAL_HASH else HASH
        verified[name]?.takeIf { it.isFile }?.let { return it }
        return ModelPacks.ensure(context, name, expected).also { verified[name] = it }
    }

    companion object {
        private val gate = Mutex()
        internal suspend fun <T> modelMaintenance(block: suspend () -> T): T = gate.withLock { block() }
        const val HASH = "921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f"
        const val MULTILINGUAL_HASH = "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21"
        const val BASE_HASH = "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe"
    }
}
