package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.*
import com.sidekick.core.*
import java.io.File
import kotlin.math.sqrt
import org.junit.Assume.assumeTrue
import org.junit.Test

class SpeakerModelComparisonTest {
    @Test fun compareDisjointShortPhrasesAndDifferentSpeakers() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("compareSpeakerModels") == "true")
        val models = listOf("voxceleb_resnet34_LM.onnx", "3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx", "3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx")
        fun normalized(input: FloatArray): FloatArray { val norm = sqrt(input.sumOf { it.toDouble() * it }); return FloatArray(input.size) { (input[it] / norm).toFloat() } }
        fun similarity(a: FloatArray, b: FloatArray) = a.indices.sumOf { a[it].toDouble() * b[it] }
        for (model in models) {
            val file = File(context.cacheDir, model)
            val source = if (model.startsWith("voxceleb")) context.assets else instrumentation.context.assets
            source.open(if (model.startsWith("voxceleb")) "models/$model" else model).use { input -> file.outputStream().use(input::copyTo) }
            val extractor = SpeakerEmbeddingExtractor(config = SpeakerEmbeddingExtractorConfig(file.absolutePath, numThreads = 1))
            val vectors = linkedMapOf<String, FloatArray>()
            val start = android.os.SystemClock.elapsedRealtime()
            try {
                for (voice in 0..1) for (i in 0..2) {
                    val name = "speaker-$voice-$i.wav"
                    val pcm = instrumentation.context.assets.open(name).use { WavAudio.decode(it.readBytes()) }
                    val span = SpeakerAudio.speechSpan(pcm)!!
                    val speech = pcm.copyOfRange(span.first, span.last + 1)
                    val stream = extractor.createStream()
                    try { stream.acceptWaveform(speech, 16000); stream.inputFinished(); vectors[name] = normalized(extractor.compute(stream)) }
                    finally { stream.release(); pcm.fill(0f); speech.fill(0f) }
                }
                val jfk = instrumentation.context.assets.open("jfk.wav").use { WavAudio.decode(it.readBytes()) }
                for ((key, begin, end) in listOf(Triple("jfk-enroll", 0, 80000), Triple("jfk-short", 96000, 129600))) {
                    val speech = jfk.copyOfRange(begin, end)
                    val stream = extractor.createStream()
                    try { stream.acceptWaveform(speech, 16000); stream.inputFinished(); vectors[key] = normalized(extractor.compute(stream)) }
                    finally { stream.release(); speech.fill(0f) }
                }
                jfk.fill(0f)
                for (language in listOf("ko", "zh", "fr", "de", "ru")) {
                    val pcm = instrumentation.context.assets.open("$language.wav").use { WavAudio.decode(it.readBytes()) }
                    val short = pcm.copyOfRange(0, minOf(33600, pcm.size))
                    val stream = extractor.createStream()
                    try { stream.acceptWaveform(short, 16000); stream.inputFinished(); vectors[language] = normalized(extractor.compute(stream)) }
                    finally { stream.release(); pcm.fill(0f); short.fill(0f) }
                }
                val own = (0..1).flatMap { voice -> (1..2).map { similarity(vectors["speaker-$voice-0.wav"]!!, vectors["speaker-$voice-$it.wav"]!!) } } + similarity(vectors["jfk-enroll"]!!, vectors["jfk-short"]!!)
                val other = (0..1).flatMap { voice -> vectors.filterKeys { !it.startsWith("speaker-$voice-") }.values.map { similarity(vectors["speaker-$voice-0.wav"]!!, it) } } + vectors.filterKeys { !it.startsWith("jfk") }.values.map { similarity(vectors["jfk-enroll"]!!, it) }
                println("MODEL_COMPARE model=$model; dimension=${extractor.dim()}; own=${own.joinToString()}; ownMin=${own.minOrNull()}; otherMax=${other.maxOrNull()}; elapsedMs=${android.os.SystemClock.elapsedRealtime()-start}")
            } finally { vectors.values.forEach { it.fill(0f) }; extractor.release(); file.delete() }
        }
    }
}
