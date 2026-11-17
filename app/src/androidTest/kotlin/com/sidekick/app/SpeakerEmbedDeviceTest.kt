package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.sidekick.core.SpeakerAudio
import com.sidekick.core.WavAudio
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Phone twin of scripts/laptop-eval/SpeakerEmbed.java (benchmark package only): same windows, same output format, but
 * with the app's own sherpa-onnx build on the phone. Public evaluation clips only, pushed to files/<corpus>/.
 *
 * -e speakerEmbed true -e corpus corpus -e model <file in files/ or "asset:models/x.onnx"> -e out embed.bin -e workers 4
 */
class SpeakerEmbedDeviceTest {
    @Test fun embedCorpus() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("speakerEmbed") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".benchmark"))
        val corpus = File(context.filesDir, args.getString("corpus") ?: "corpus")
        val modelArg = args.getString("model")!!
        val model = if (modelArg.startsWith("asset:")) File(context.cacheDir, modelArg.substringAfterLast('/')).also { file ->
            if (!file.exists()) context.assets.open(modelArg.removePrefix("asset:")).use { input -> file.outputStream().use(input::copyTo) }
        } else File(context.filesDir, modelArg)
        val workers = args.getString("workers")?.toIntOrNull() ?: 4
        val files = File(corpus, "manifest.tsv").readLines().filter { it.isNotBlank() }.map { it.split('\t')[0] }
        val engines = ThreadLocal.withInitial { SpeakerEmbeddingExtractor(config = SpeakerEmbeddingExtractorConfig(model.absolutePath, numThreads = 1)) }
        val pool = Executors.newFixedThreadPool(workers)
        val parts = arrayOfNulls<ByteArray>(files.size)
        val futures = mutableListOf<Future<*>>()
        for ((clip, name) in files.withIndex()) futures += pool.submit {
            val out = java.io.ByteArrayOutputStream()
            fun write(kind: Int, v: FloatArray?) {
                val dim = v?.size ?: 0
                val b = ByteBuffer.allocate(7 + dim * 4).order(ByteOrder.LITTLE_ENDIAN)
                b.putInt(clip).put(kind.toByte()).putShort(dim.toShort()); v?.forEach(b::putFloat); out.write(b.array())
            }
            fun embed(samples: FloatArray): FloatArray? {
                if (samples.size < 24_000) return null
                if (samples.count { kotlin.math.abs(it) > .005f } < samples.size / 10 || samples.count { kotlin.math.abs(it) >= .99f } >= samples.size / 100) return null
                val engine = engines.get()!!; val stream = engine.createStream()
                return try {
                    stream.acceptWaveform(samples, 16000); stream.inputFinished()
                    if (!engine.isReady(stream)) null else engine.compute(stream).let { v ->
                        val n = kotlin.math.sqrt(v.sumOf { it.toDouble() * it }).toFloat(); FloatArray(v.size) { v[it] / n } }
                } finally { stream.release() }
            }
            runCatching {
                val audio = WavAudio.decode(File(corpus, name).readBytes())
                val span = SpeakerAudio.speechSpan(audio)
                if (span != null) {
                    val speech = audio.copyOfRange(span.first, minOf(span.last + 1, span.first + 160_000))
                    write(0, embed(speech))
                    if (speech.size >= 48_000) {
                        val width = if (speech.size < 64_000) speech.size / 2 else 48_000
                        val starts = (0..speech.size - width step 32_000).toSortedSet().apply { add(speech.size - width) }
                        for (s in starts) {
                            val r = SpeakerAudio.matchingWindow(speech, s, width)
                            write(1, r?.let { embed(speech.copyOfRange(it.first, it.last + 1)) })
                        }
                    }
                    if (speech.size >= 40_000) write(2, embed(speech.copyOf(40_000)))
                }
            }
            parts[clip] = out.toByteArray()
            if (clip % 250 == 0) println("SPEAKER_EMBED $clip/${files.size}")
        }
        futures.forEach { it.get() }
        pool.shutdown()
        File(context.filesDir, args.getString("out") ?: "embed.bin").outputStream().buffered().use { o -> parts.forEach { o.write(it ?: ByteArray(0)) } }
        println("SPEAKER_EMBED done ${files.size}")
    }
}
