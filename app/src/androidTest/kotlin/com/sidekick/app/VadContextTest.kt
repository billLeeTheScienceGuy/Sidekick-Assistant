package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.speech.NativeVad
import com.sidekick.app.speech.VadModel
import com.sidekick.core.WavAudio
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * How much earlier audio the speech detector needs. Runs a real phone-microphone recording (files/evaluation/replay.wav,
 * about 20 minutes of speech and room noise) through the detector exactly as LiveSpeechProcessor does, with the current
 * context (16,384 samples = 1 s) and shorter ones, and compares every 32 ms speech/no-speech decision and the time spent.
 * Opt-in: -e vadContext true. Output: files/evaluation/vad-context.txt.
 */
class VadContextTest {
    /** [context] = total samples per call (earlier audio + [step] new); [step] = new audio per call. */
    private fun decisions(audio: FloatArray, context: Int, step: Int = 4096): Pair<BooleanArray, Long> {
        val history = FloatArray(context); var historySize = 0
        val out = BooleanArray(audio.size / 512)
        var frame = 0; var nanos = 0L
        var at = 0
        while (at + step <= audio.size) {
            val samples = audio.copyOfRange(at, at + step)
            val keep = minOf(historySize, history.size - samples.size)
            history.copyInto(history, 0, historySize - keep, historySize)
            samples.copyInto(history, keep); historySize = keep + samples.size
            val padded = ((historySize + 511) / 512) * 512
            val input = history.copyOf(historySize).copyOf(padded)
            com.sidekick.core.VadInput.prepareInPlace(input)
            val start = android.os.Debug.threadCpuTimeNanos()
            val p = NativeVad.probabilities(input)
            nanos += android.os.Debug.threadCpuTimeNanos() - start
            val offset = historySize - samples.size
            for (i in 0 until step step 512) { out[frame++] = p[(offset + i) / 512] >= .5f }
            at += step
        }
        return out to nanos
    }

    /** One decision per 32 ms frame from a streaming detector that keeps its state; processor time of the calls. */
    private fun streaming(audio: FloatArray, model: String, ten: Boolean, gain: Boolean): Pair<BooleanArray, Long> {
        val window = if (ten) 256 else 512
        val config = com.k2fsa.sherpa.onnx.VadModelConfig(
            sileroVadModelConfig = if (ten) com.k2fsa.sherpa.onnx.SileroVadModelConfig() else com.k2fsa.sherpa.onnx.SileroVadModelConfig(model, .5f, .25f, .25f, window, 30f),
            tenVadModelConfig = if (ten) com.k2fsa.sherpa.onnx.TenVadModelConfig(model, .5f, .25f, .25f, window, 30f) else com.k2fsa.sherpa.onnx.TenVadModelConfig(),
            sampleRate = 16000, numThreads = 1, provider = "cpu", debug = false)
        val vad = com.k2fsa.sherpa.onnx.Vad(null, config)
        val out = BooleanArray(audio.size / 512); var frame = 0; var nanos = 0L
        try {
            var at = 0
            while (at + 16_384 <= audio.size) {
                val batch = audio.copyOfRange(at, at + 16_384)
                if (gain) com.sidekick.core.VadInput.prepareInPlace(batch)
                for (i in 0 until 16_384 step 512) {
                    val start = android.os.Debug.threadCpuTimeNanos()
                    var p = 0f
                    for (k in 0 until 512 step window) p = maxOf(p, vad.compute(batch.copyOfRange(i + k, i + k + window)))
                    nanos += android.os.Debug.threadCpuTimeNanos() - start
                    out[frame++] = p >= .5f
                }
                at += 16_384
            }
        } finally { vad.release() }
        return out to nanos
    }

    /** 16 kHz mono 16-bit WAV of any length (WavAudio.decode allows 30 s). */
    private fun longWav(bytes: ByteArray): FloatArray {
        val b = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        var at = 12
        while (at + 8 <= bytes.size) {
            val id = String(bytes, at, 4, Charsets.US_ASCII); val size = b.getInt(at + 4)
            if (id == "data") { val n = minOf(size, bytes.size - at - 8) / 2; return FloatArray(n) { b.getShort(at + 8 + 2 * it) / 32768f } }
            at += 8 + size + (size and 1)
        }
        error("no data chunk")
    }

    @Test fun shorterContextKeepsTheSameDecisions() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("vadContext") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(File(context.filesDir, "evaluation"), "replay.wav")
        assumeTrue(file.isFile)
        val audio = longWav(file.readBytes())
        NativeVad.open(VadModel.install(context))
        try {
            val (base, baseNs) = decisions(audio, 16_384)
            fun dump(label: String, d: BooleanArray) = File(File(context.filesDir, "evaluation"), "vad-$label.txt").writeText(d.joinToString("") { if (it) "1" else "0" })
            dump("original", base)
            val lines = mutableListOf("audio ${audio.size / 16000} s; baseline 16384: speech frames ${base.count { it }}, detector time ${baseNs / 1_000_000} ms")
            // Same 12,288 samples of earlier audio before each piece, but 0.5 s or 1 s of new audio per call.
            for ((size, step) in listOf(12_288 + 8_192 to 8_192, 12_288 + 16_384 to 16_384)) {
                val (d, ns) = decisions(audio, size, step)
                dump("batch$step", d)
                val n = minOf(base.size, d.size)
                var missed = 0; var extra = 0
                for (i in 0 until n) { if (base[i] && !d[i]) missed++; if (!base[i] && d[i]) extra++ }
                // Speech stretches (8+ frames = 0.25 s) the baseline found that the shorter context misses entirely.
                var lost = 0; var i = 0
                while (i < n) {
                    if (base[i]) { var j = i; var any = false; while (j < n && base[j]) { any = any || d[j]; j++ }; if (j - i >= 8 && !any) lost++; i = j } else i++
                }
                lines += "new audio per call $step (total $size): time ${ns / 1_000_000} ms (${"%.0f".format(100.0 * ns / baseNs)}%), frames differing ${missed + extra} of $n " +
                    "(${"%.2f".format(100.0 * (missed + extra) / n)}%; missed $missed, extra $extra), speech stretches lost entirely $lost"
            }
            // Streaming detectors (sherpa-onnx): state kept between calls, every sample read once. Loudness evened out
            // per second of audio (as the app does per call) or not at all.
            val dir = File(context.filesDir, "evaluation")
            for ((name, gain) in listOf("silero_vad_v5.onnx" to true, "silero_vad_v5.onnx" to false, "ten-vad.onnx" to true, "ten-vad.onnx" to false)) {
                val model = File(dir, name); if (!model.isFile) continue
                val (d, ns) = streaming(audio, model.path, name.startsWith("ten"), gain)
                dump("${name.substringBefore('.').substringBefore('_')}${if (gain) "-even" else ""}", d)
                val n = minOf(base.size, d.size)
                var missed = 0; var extra = 0
                for (i in 0 until n) { if (base[i] && !d[i]) missed++; if (!base[i] && d[i]) extra++ }
                var lost = 0; var i = 0
                while (i < n) {
                    if (base[i]) { var j = i; var any = false; while (j < n && base[j]) { any = any || d[j]; j++ }; if (j - i >= 8 && !any) lost++; i = j } else i++
                }
                lines += "streaming $name${if (gain) " (loudness evened)" else ""}: time ${ns / 1_000_000} ms (${"%.0f".format(100.0 * ns / baseNs)}%), frames differing ${missed + extra} of $n " +
                    "(${"%.2f".format(100.0 * (missed + extra) / n)}%; missed $missed, extra $extra), speech stretches lost entirely $lost"
            }
            File(File(context.filesDir, "evaluation"), "vad-context.txt").writeText(lines.joinToString("\n") + "\n")
            lines.forEach { android.util.Log.i("SidekickVad", it) }
        } finally { NativeVad.close() }
    }
}
