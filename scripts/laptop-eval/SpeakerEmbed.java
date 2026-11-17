import com.k2fsa.sherpa.onnx.*;
import com.sidekick.core.SpeakerAudio;
import kotlin.ranges.IntRange;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Laptop twin of SpeakerEngine's embedding steps, for owner-matching evaluation. Same sherpa-onnx extractor, same
 * SpeakerAudio trimming and window layout (from core.jar). Each clip stands for one captured chunk (at most 10 s):
 *   kind 0  whole trimmed chunk (session voice groups, enrollment samples)
 *   kind 1  long-turn speaker windows (3 s, 2 s step, as identifySpeech), only when the chunk has 3 s of speech
 *   kind 2  a 2.5 s short phrase cut from the start of the speech
 * Output: little-endian records {int clip, byte kind, short dim, float[dim] normalized}.
 *
 * java -cp core.jar;kotlin-stdlib.jar;sherpa-onnx.jar;sherpa-native.jar SpeakerEmbed.java <corpus dir> <model.onnx> <out.bin> <workers>
 */
public class SpeakerEmbed {
    /** Input scale check: SCALE=32768 feeds int16-range samples (diagnostic only). */
    static final float SCALE = Float.parseFloat(System.getenv().getOrDefault("SCALE", "1"));
    /** DENOISE=gtcrn:<model.onnx> or dpdfnet:<model.onnx>: clean each clip before voice matching (ASR audio untouched). */
    static final String DENOISE = System.getenv().getOrDefault("DENOISE", "");
    static float[] denoise(OfflineSpeechDenoiser d, float[] audio) {
        if (d == null) return audio;
        float[] out = d.run(audio, 16000).getSamples();
        return out.length == audio.length ? out : Arrays.copyOf(out, audio.length);
    }
    static float[] wav(Path path) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN);
        int offset = 12;
        while (offset + 8 <= b.limit()) {
            String id = new String(b.array(), offset, 4, StandardCharsets.US_ASCII);
            int size = b.getInt(offset + 4);
            if (id.equals("data")) {
                float[] out = new float[Math.min(size, b.limit() - offset - 8) / 2];
                for (int i = 0; i < out.length; i++) out[i] = b.getShort(offset + 8 + i * 2) / 32768f;
                return out;
            }
            offset += 8 + size + (size % 2);
        }
        throw new IOException("no data chunk: " + path);
    }

    static float[] normalized(float[] v) {
        double n = 0; for (float x : v) n += x * x;
        n = Math.sqrt(n); float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / n);
        return out;
    }

    /** Same input checks as SpeakerEngine.computeEmbedding; null where the app would refuse the audio. */
    static float[] embed(SpeakerEmbeddingExtractor engine, float[] samples) {
        if (samples.length < 24_000) return null;
        int loud = 0, clipped = 0;
        for (float s : samples) { if (Math.abs(s) > .005f) loud++; if (Math.abs(s) >= .99f) clipped++; }
        if (loud < samples.length / 10 || clipped >= samples.length / 100) return null;
        OnlineStream stream = engine.createStream();
        try {
            float[] input = samples;
            if (SCALE != 1f) { input = new float[samples.length]; for (int k = 0; k < samples.length; k++) input[k] = samples[k] * SCALE; }
            stream.acceptWaveform(input, 16000); stream.inputFinished();
            if (!engine.isReady(stream)) return null;
            return normalized(engine.compute(stream));
        } finally { stream.release(); }
    }

    public static void main(String[] args) throws Exception {
        Path corpus = Paths.get(args[0]); String model = args[1]; Path out = Paths.get(args[2]); int workers = Integer.parseInt(args[3]);
        List<String> files = new ArrayList<>();
        for (String line : Files.readAllLines(corpus.resolve("manifest.tsv"))) if (!line.isBlank()) files.add(line.split("\t")[0]);
        ThreadLocal<SpeakerEmbeddingExtractor> engines = ThreadLocal.withInitial(() ->
            new SpeakerEmbeddingExtractor(SpeakerEmbeddingExtractorConfig.builder().setModel(model).setNumThreads(1).build()));
        ThreadLocal<OfflineSpeechDenoiser> denoisers = ThreadLocal.withInitial(() -> {
            if (DENOISE.isEmpty()) return null;
            String kind = DENOISE.substring(0, DENOISE.indexOf(':')), path = DENOISE.substring(DENOISE.indexOf(':') + 1);
            OfflineSpeechDenoiserModelConfig.Builder m = OfflineSpeechDenoiserModelConfig.builder().setNumThreads(1);
            if (kind.equals("gtcrn")) m.setGtcrn(OfflineSpeechDenoiserGtcrnModelConfig.builder().setModel(path).build());
            else m.setDpdfnet(OfflineSpeechDenoiserDpdfNetModelConfig.builder().setModel(path).build());
            return new OfflineSpeechDenoiser(OfflineSpeechDenoiserConfig.builder().setModel(m.build()).build());
        });
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        ByteArrayOutputStream[] results = new ByteArrayOutputStream[files.size()];
        AtomicInteger done = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int c = 0; c < files.size(); c++) {
            final int clip = c;
            futures.add(pool.submit(() -> {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try {
                    float[] audio = denoise(denoisers.get(), wav(corpus.resolve(files.get(clip))));
                    IntRange span = SpeakerAudio.INSTANCE.speechSpan(audio);
                    if (span != null) {
                        int from = span.getFirst(), until = Math.min(span.getLast() + 1, from + 160_000);
                        float[] speech = Arrays.copyOfRange(audio, from, until);
                        SpeakerEmbeddingExtractor engine = engines.get();
                        write(bytes, clip, 0, embed(engine, speech));
                        if (speech.length >= 48_000) {
                            int width = speech.length < 64_000 ? speech.length / 2 : 48_000;
                            TreeSet<Integer> starts = new TreeSet<>();
                            for (int s = 0; s <= speech.length - width; s += 32_000) starts.add(s);
                            starts.add(speech.length - width);
                            for (int s : starts) {
                                IntRange r = SpeakerAudio.INSTANCE.matchingWindow(speech, s, width);
                                write(bytes, clip, 1, r == null ? null : embed(engine, Arrays.copyOfRange(speech, r.getFirst(), r.getLast() + 1)));
                            }
                        }
                        if (speech.length >= 40_000) write(bytes, clip, 2, embed(engine, Arrays.copyOf(speech, 40_000)));
                    }
                } catch (Exception e) { System.err.println("skip " + files.get(clip) + ": " + e); }
                results[clip] = bytes;
                int n = done.incrementAndGet();
                if (n % 500 == 0) System.out.println(n + "/" + files.size());
                return null;
            }));
        }
        for (Future<?> f : futures) f.get();
        pool.shutdown();
        try (OutputStream o = new BufferedOutputStream(Files.newOutputStream(out))) {
            for (ByteArrayOutputStream r : results) r.writeTo(o);
        }
        System.out.println("done " + files.size() + " clips -> " + out);
    }

    /** A window the app would refuse is written with dim 0, so the turn counts as unassigned, as on the phone. */
    static void write(ByteArrayOutputStream bytes, int clip, int kind, float[] v) {
        int dim = v == null ? 0 : v.length;
        ByteBuffer b = ByteBuffer.allocate(4 + 1 + 2 + dim * 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(clip).put((byte) kind).putShort((short) dim);
        for (int i = 0; i < dim; i++) b.putFloat(v[i]);
        bytes.writeBytes(b.array());
    }
}
