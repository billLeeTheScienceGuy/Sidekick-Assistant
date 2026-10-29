package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.*
import com.sidekick.app.speech.SpeechEngine
import com.sidekick.app.speech.NativeSpeech
import com.sidekick.core.*
import java.io.File
import java.time.*
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in comparisons use only public synthetic fixtures; never opens a microphone. */
class MultilingualQualityTest {
    @Test fun compareAllSupportedLanguagesOnDevice() = runBlocking {
        val i = InstrumentationRegistry.getInstrumentation(); val context = i.targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("compareLanguages") == "true")
        val fullContext = InstrumentationRegistry.getArguments().getString("compareFullContext") == "true"
        val small = InstrumentationRegistry.getArguments().getString("compareSmall") == "true"
        val moonshine = InstrumentationRegistry.getArguments().getString("compareMoonshine") == "true"
        val omni = InstrumentationRegistry.getArguments().getString("compareOmni") == "true"
        val cases = i.context.assets.open("asr-commands.json").bufferedReader().use { JSONArray(it.readText()) }
        val public = i.context.assets.open("languages.json").bufferedReader().use { JSONArray(it.readText()) }
        for (n in 0 until public.length()) {
            val row = public.getJSONObject(n)
            cases.put(JSONObject().put("code", row.getString("code")).put("file", row.getString("code") + ".wav").put("reference", row.getString("reference")).put("action", "public").put("hour", 0))
        }
        val results = JSONArray(); val speech = SpeechEngine(context)
        val now = Instant.parse("2026-10-28T14:00:00Z"); val zone = ZoneId.of("America/New_York")
        fun semantic(text: String, row: JSONObject): Boolean = when(row.getString("action")) {
            "create" -> ActivityTiming.extract(Transcript(text, now, zone, Speaker.OTHER, true), true).singleOrNull()?.activityStartAt?.atZone(zone)?.hour == row.getInt("hour")
            "move" -> EventChange.parse(text)?.newStart(now.plusSeconds(86400), now, zone)?.atZone(zone)?.hour == row.getInt("hour")
            "cancel" -> EventChange.parse(text)?.cancel == true
            "public" -> text.isNotBlank()
            else -> EventChange.parse(text) == null && EventChange.looksLikeChange(text)
        }
        fun record(backend: String, row: JSONObject, text: String, elapsed: Long) {
            val memory = android.os.Debug.MemoryInfo().also { android.os.Debug.getMemoryInfo(it) }
            val result = JSONObject().put("backend", backend).put("language", row.getString("code")).put("file", row.getString("file"))
                .put("action", row.getString("action")).put("reference", row.getString("reference")).put("output", text)
                .put("semantic", semantic(text, row)).put("elapsedMs", elapsed).put("pssKb", memory.totalPss)
            results.put(result); File(context.cacheDir, "multilingual-quality.json").writeText(results.toString(2))
            println("LANGUAGE_QUALITY backend=$backend; lang=${row.getString("code")}; action=${row.getString("action")}; ms=$elapsed; semantic=${semantic(text,row)}; output=$text")
        }
        if (moonshine || omni) {
            val files = (if(omni) listOf("omni-model.int8.onnx", "omni-tokens.txt") else listOf("moonshine-ar-encoder.ort", "moonshine-ar-decoder.ort", "moonshine-ar-tokens.txt")).map { name ->
                File(context.cacheDir, name).also { file -> (if (omni) context.assets.open("models/$name") else i.context.assets.open(name)).use { input -> file.outputStream().use(input::copyTo) } }
            }
            val config = if (omni) OfflineModelConfig(omnilingual = OfflineOmnilingualAsrCtcModelConfig(model=files[0].absolutePath), tokens=files[1].absolutePath,numThreads=2)
                else OfflineModelConfig(moonshine = OfflineMoonshineModelConfig(encoder = files[0].absolutePath, mergedDecoder = files[1].absolutePath), tokens = files[2].absolutePath, numThreads = 2)
            val engine = OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = config))
            try {
                for (n in 0 until cases.length()) {
                    val row = cases.getJSONObject(n); if (!omni && row.getString("code") != "ar") continue
                    val pcm = i.context.assets.open(row.getString("file")).use { WavAudio.decode(it.readBytes()) }
                    val stream = engine.createStream(); val start = android.os.SystemClock.elapsedRealtime()
                    try { stream.acceptWaveform(pcm, 16000); engine.decode(stream); record(if (omni) "Omni_int8" else "Moonshine_ar", row, engine.getResult(stream).text, android.os.SystemClock.elapsedRealtime()-start) }
                    finally { stream.release(); pcm.fill(0f) }
                }
            } finally { engine.release(); files.forEach { it.delete() } }
            return@runBlocking
        }
        try {
            val smallModel = File(context.cacheDir, "ggml-small-q5_1.bin")
            if (small) i.context.assets.open("ggml-small-q5_1.bin").use { input -> smallModel.outputStream().use(input::copyTo) }
            for (quality in if (small) listOf(SpeechQuality.FAST) else listOf(SpeechQuality.FAST, SpeechQuality.ACCURATE)) {
                for (n in 0 until cases.length()) {
                    val row = cases.getJSONObject(n)
                    if (small && row.getString("code") != "ar") continue
                    if (!small && fullContext && (row.getString("code") !in listOf("fr", "de", "ru", "ar") || row.getString("action") !in listOf("create", "move"))) continue
                    val pcm = i.context.assets.open(row.getString("file")).use { WavAudio.decode(it.readBytes()) }
                    val start = android.os.SystemClock.elapsedRealtime()
                    val text = if (small) { NativeSpeech.prepare(); try { NativeSpeech.transcribe(smallModel.absolutePath, pcm, 2, true, 0, "ar", 1).toString(Charsets.UTF_8).trim() } finally { pcm.fill(0f) } }
                        else speech.transcribe(pcm, retainModel = true, selectedLanguage = SpeechLanguage.fromCode(row.getString("code")), selectedQuality = quality, audioContext = if(!fullContext && pcm.size <= 163840) 512 else 0)
                    record(if (small) "SMALL_Q5" else if (fullContext) "FULL_${quality.name}" else quality.name, row, text, android.os.SystemClock.elapsedRealtime() - start)
                }
            }
        } finally { speech.release() }
        if (fullContext || small) return@runBlocking
        val model = File(context.cacheDir, "quality-sense.onnx"); val tokens = File(context.cacheDir, "quality-sense-tokens.txt")
        try {
            context.assets.open("models/sensevoice-ko.int8.onnx").use { input -> model.outputStream().use(input::copyTo) }
            context.assets.open("models/sensevoice-tokens.txt").use { input -> tokens.outputStream().use(input::copyTo) }
            for (code in listOf("ko", "zh")) for (itn in listOf(true, false)) {
                val engine = OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = OfflineModelConfig(senseVoice = OfflineSenseVoiceModelConfig(model = model.absolutePath, language = code, useInverseTextNormalization = itn), tokens = tokens.absolutePath, numThreads = 2)))
                try {
                    for (n in 0 until cases.length()) {
                        val row = cases.getJSONObject(n); if (row.getString("code") != code) continue
                        val pcm = i.context.assets.open(row.getString("file")).use { WavAudio.decode(it.readBytes()) }
                        val stream = engine.createStream(); val start = android.os.SystemClock.elapsedRealtime()
                        try { stream.acceptWaveform(pcm,16000); engine.decode(stream); record("SenseVoice-itn=$itn",row,engine.getResult(stream).text,android.os.SystemClock.elapsedRealtime()-start) }
                        finally { stream.release(); pcm.fill(0f) }
                    }
                } finally { engine.release() }
            }
        } finally { model.delete(); tokens.delete() }
    }
}
