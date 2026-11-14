package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.*
import com.sidekick.core.WavAudio
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * On-device speed (and text) of candidate speech models, mirroring scripts/laptop-eval/ModelBench.java.
 * Opt-in: `-e phoneBench true -e models a,b -e set timing`. Models live in files/cand/<release folder>,
 * clips in files/evaluation/<set>/ (manifest.tsv). Output: files/evaluation/<set>/bench-<model>.json.
 */
class PhoneModelBenchTest {
    private fun interface Engine { fun run(samples: FloatArray, language: String): String }

    private fun find(dir: File, regex: String): String {
        val all = dir.walkTopDown().filter { it.isFile && Regex(regex).matches(it.name) }.sortedBy { it.name }.toList()
        return (all.firstOrNull { "int8" in it.name } ?: all.firstOrNull() ?: error("$regex missing in $dir")).absolutePath
    }
    private fun offline(model: OfflineModelConfig) = OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = model)).let { r ->
        Engine { x, _ -> val s = r.createStream(); try { s.acceptWaveform(x, 16000); r.decode(s); r.getResult(s).text.replace(Regex("<\\|[^|>]+\\|>"), "").trim() } finally { s.release() } }
    }
    private fun pad(x: FloatArray) = FloatArray(x.size + 16000).also { x.copyInto(it, 8000) }

    private fun engine(id: String, root: File, app: File, threads: Int): Engine {
        fun c(name: String) = File(root, name)
        return when (id) {
            "sense2024" -> offline(OfflineModelConfig(senseVoice = OfflineSenseVoiceModelConfig(File(app, "sensevoice-ko.int8.onnx").path, "auto", true), tokens = File(app, "sensevoice-tokens.txt").path, numThreads = threads))
            "sense2025" -> c("sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09").let { d -> offline(OfflineModelConfig(senseVoice = OfflineSenseVoiceModelConfig(find(d, "model.*\\.onnx"), "auto", true), tokens = find(d, "tokens.txt"), numThreads = threads)) }
            "omni1" -> offline(OfflineModelConfig(omnilingual = OfflineOmnilingualAsrCtcModelConfig(File(app, "omni-model.int8.onnx").path), tokens = File(app, "omni-tokens.txt").path, numThreads = threads)).let { e -> Engine { x, l -> e.run(pad(x), l) } }
            "omni2" -> c("sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-v2-int8-2026-02-05").let { d -> offline(OfflineModelConfig(omnilingual = OfflineOmnilingualAsrCtcModelConfig(find(d, "model.*\\.onnx")), tokens = find(d, "tokens.txt"), numThreads = threads)).let { e -> Engine { x, l -> e.run(pad(x), l) } } }
            "zipko" -> c("sherpa-onnx-zipformer-korean-2024-06-24").let { d -> offline(OfflineModelConfig(transducer = OfflineTransducerModelConfig(find(d, "encoder.*\\.onnx"), find(d, "decoder.*\\.onnx"), find(d, "joiner.*\\.onnx")), tokens = find(d, "tokens.txt"), numThreads = threads)) }
            "parakeet110", "parakeet3", "fc20k", "gigaam3" -> c(mapOf("parakeet110" to "sherpa-onnx-nemo-parakeet_tdt_transducer_110m-en-36000-int8", "parakeet3" to "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
                "fc20k" to "sherpa-onnx-nemo-fast-conformer-transducer-be-de-en-es-fr-hr-it-pl-ru-uk-20k-int8", "gigaam3" to "sherpa-onnx-nemo-transducer-giga-am-v3-russian-2025-12-16").getValue(id)).let { d ->
                offline(OfflineModelConfig(transducer = OfflineTransducerModelConfig(find(d, "encoder.*\\.onnx"), find(d, "decoder.*\\.onnx"), find(d, "joiner.*\\.onnx")), tokens = find(d, "tokens.txt"), modelType = "nemo_transducer", numThreads = threads)) }
            "canary" -> c("sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8").let { d -> val byLanguage = mutableMapOf<String, Engine>()
                Engine { x, l -> byLanguage.getOrPut(l) { offline(OfflineModelConfig(canary = OfflineCanaryModelConfig(find(d, "encoder.*\\.onnx"), find(d, "decoder.*\\.onnx"), l, l, true), tokens = find(d, "tokens.txt"), numThreads = threads)) }.run(x, l) } }
            "moonar" -> c("sherpa-onnx-moonshine-base-ar-quantized-2026-02-27").let { d -> offline(OfflineModelConfig(moonshine = OfflineMoonshineModelConfig(encoder = find(d, "encoder_model.*"), mergedDecoder = find(d, "decoder_model_merged.*")), tokens = find(d, "tokens.txt"), numThreads = threads)) }
            "firered2" -> c("sherpa-onnx-fire-red-asr2-ctc-zh_en-int8-2026-02-25").let { d -> offline(OfflineModelConfig(fireRedAsrCtc = OfflineFireRedAsrCtcModelConfig(find(d, "model.*\\.onnx")), tokens = find(d, "tokens.txt"), numThreads = threads)) }
            "funnano" -> c("sherpa-onnx-sense-voice-funasr-nano-int8-2025-12-17").let { d -> offline(OfflineModelConfig(senseVoice = OfflineSenseVoiceModelConfig(find(d, "model.*\\.onnx"), "auto", true), tokens = find(d, "tokens.txt"), numThreads = threads)) }
            "qwen3" -> c("sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25").let { d -> offline(OfflineModelConfig(qwen3Asr = OfflineQwen3AsrModelConfig(convFrontend = find(d, "conv_frontend.*\\.onnx"), encoder = find(d, "encoder.*\\.onnx"),
                decoder = find(d, "decoder.*\\.onnx"), tokenizer = File(d, "tokenizer").path), numThreads = threads)) }
            "nemotron" -> c("sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-560ms-int8-2026-06-11").let { d ->
                val r = OnlineRecognizer(config = OnlineRecognizerConfig(modelConfig = OnlineModelConfig(transducer = OnlineTransducerModelConfig(find(d, "encoder.*\\.onnx"), find(d, "decoder.*\\.onnx"), find(d, "joiner.*\\.onnx")), tokens = find(d, "tokens.txt"), numThreads = threads)))
                Engine { x, _ -> val s = r.createStream(); try { s.acceptWaveform(x, 16000); s.acceptWaveform(FloatArray(16000), 16000); s.inputFinished(); while (r.isReady(s)) r.decode(s); r.getResult(s).text.trim() } finally { s.release() } } }
            "lid" -> c("sherpa-onnx-whisper-tiny").let { d ->
                val lid = SpokenLanguageIdentification(config = SpokenLanguageIdentificationConfig(SpokenLanguageIdentificationWhisperConfig(find(d, ".*encoder.*\\.onnx"), find(d, ".*decoder.*\\.onnx"), -1), threads))
                Engine { x, _ -> val s = lid.createStream(); try { s.acceptWaveform(x, 16000); lid.compute(s) } finally { s.release() } } }
            else -> error("unknown model $id")
        }
    }

    @Test fun timeCandidateModels() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("phoneBench") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wake = context.getSystemService(android.os.PowerManager::class.java)
            .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "SideKick:PhoneBench").apply { setReferenceCounted(false); acquire(6 * 3_600_000L) }
        try {
            val set = File(File(context.filesDir, "evaluation"), arguments.getString("set") ?: "timing")
            val rows = File(set, "manifest.tsv").readLines().filter { it.isNotBlank() }.map { it.split('\t', limit = 4) }
            val threads = arguments.getString("threads")?.toInt() ?: 4
            for (id in (arguments.getString("models") ?: "").split(',').filter { it.isNotBlank() }) {
                val out = JSONArray()
                val loadStart = android.os.SystemClock.elapsedRealtime()
                val engine = runCatching { engine(id, File(context.filesDir, "cand"), context.noBackupFilesDir, threads) }
                val loadMs = android.os.SystemClock.elapsedRealtime() - loadStart
                for (row in rows) {
                    val samples = WavAudio.decode(File(set, row[0]).readBytes())
                    val start = android.os.SystemClock.elapsedRealtime()
                    val text = engine.fold({ e -> runCatching { e.run(samples, row[1]) }.getOrElse { "ERROR: $it" } }, { "ERROR: $it" })
                    out.put(JSONObject().put("file", row[0]).put("language", row[1]).put("mode", id).put("audioMs", row[2].toLong())
                        .put("elapsedMs", android.os.SystemClock.elapsedRealtime() - start).put("loadMs", loadMs).put("reference", row[3]).put("recognized", text))
                }
                File(set, "bench-$id.json").writeText(out.toString(1))
                System.gc()
            }
        } finally { if (wake.isHeld) wake.release() }
    }
}
