package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.speech.SpeechEngine
import com.sidekick.core.SpeechLanguage
import com.sidekick.core.SpeechQuality
import com.sidekick.core.WavAudio
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.text.Normalizer
import java.util.Locale

/**
 * Natural-speech recognition check on the installed models. Opt-in: pass `-e naturalSpeech true`.
 * Input: `manifest.tsv` (file, language code, audio ms, reference) plus 16 kHz mono WAVs pushed to the app's
 * private files dir `evaluation/<set>/`. Each clip runs in Auto-detect and in its own language setting.
 * Output: `evaluation/<set>/results.json` with text, backend, retry reason, latency and character error rate.
 */
class NaturalSpeechEvaluationTest {
    @Test fun naturalSpeechAcrossLanguages() = runBlocking<Unit> {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("naturalSpeech") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Long unattended runs: keep the CPU running with the screen off (same permission the listening service uses).
        val wake = context.getSystemService(android.os.PowerManager::class.java)
            .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "SideKick:Evaluation").apply { setReferenceCounted(false); acquire(6 * 3_600_000L) }
        try { evaluate(context, arguments) } finally { if (wake.isHeld) wake.release() }
    }

    private suspend fun evaluate(context: android.content.Context, arguments: android.os.Bundle) {
        val set = arguments.getString("set") ?: "natural"
        val only = arguments.getString("languages")?.split(',')?.toSet()
        val modes = arguments.getString("modes")?.split(',')?.toSet() ?: setOf("auto", "manual")
        // Private storage: copy in with `adb shell run-as <package> cp -r /data/local/tmp/<set> files/evaluation/`.
        val directory = File(File(context.filesDir, "evaluation"), set)
        // `-e perLanguage N` takes the first N clips of each language for quick iterations; `-e threads 1..4` (default 4).
        val perLanguage = arguments.getString("perLanguage")?.toInt() ?: Int.MAX_VALUE
        val threads = arguments.getString("threads")?.toInt() ?: 4
        val rows = File(directory, "manifest.tsv").readLines(Charsets.UTF_8).filter { it.isNotBlank() }.map { it.split('\t') }
            .groupBy { it[1] }.values.flatMap { it.take(perLanguage) }
        val output = File(directory, "results.json")
        // `-e resume true` keeps finished clips from an interrupted run instead of repeating them.
        val results = if (arguments.getString("resume") == "true" && output.exists()) JSONArray(output.readText()) else JSONArray()
        val finished = (0 until results.length()).map { results.getJSONObject(it).let { r -> r.getString("mode") + "/" + r.getString("file") } }.toSet()
        fun normalized(text: String) = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFKC)
            .codePoints().toArray().filter { Character.isLetterOrDigit(it) }.toIntArray()
        fun edits(a: IntArray, b: IntArray): Int {
            var previous = IntArray(b.size + 1) { it }
            for (index in a.indices) {
                val current = IntArray(b.size + 1); current[0] = index + 1
                for (j in b.indices) current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (a[index] == b[j]) 0 else 1)
                previous = current
            }
            return previous.last()
        }
        // auto/manual: the app's routing. sense/omni/whisper: one model alone with its own language detection.
        for (mode in listOf("auto", "manual", "sense", "omni", "whisper").filter { it in modes }) {
            for ((language, group) in rows.groupBy { it[1] }) {
                if (only != null && language !in only) continue
                val selected = if (mode == "auto") SpeechLanguage.AUTO else SpeechLanguage.fromCode(language)
                val engine = SpeechEngine(context, { selected }, { SpeechQuality.ADAPTIVE })
                try {
                    for (row in group) {
                        if ("$mode/${row[0]}" in finished) continue
                        val samples = WavAudio.decode(File(directory, row[0]).readBytes())
                        val reference = normalized(row[3])
                        val start = android.os.SystemClock.elapsedRealtime()
                        var detected: String? = null
                        val text = runCatching {
                            if (mode == "auto" || mode == "manual") engine.transcribe(samples, threads, retainModel = true)
                            else engine.single(samples, mode, threads).also { detected = it.second }.first
                        }.getOrElse { "ERROR: ${it.message}" }
                        val elapsed = android.os.SystemClock.elapsedRealtime() - start
                        results.put(JSONObject().put("file", row[0]).put("language", language).put("mode", mode)
                            .put("audioMs", row[2].toLong()).put("elapsedMs", elapsed).put("reference", row[3]).put("recognized", text)
                            .put("backend", if (mode == "auto" || mode == "manual") engine.lastBackend else mode).put("detected", detected)
                            .put("retry", engine.lastRetryDiagnostic).put("uncertain", engine.lastUncertain)
                            .put("referenceCharacters", reference.size).put("characterEdits", edits(reference, normalized(text))))
                        output.writeText(results.toString(1)) // Partial results survive an interrupted run.
                    }
                } finally { engine.release() }
            }
        }
    }
}
