import com.k2fsa.sherpa.onnx.*;
import com.sidekick.core.AutoRoute;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;

/**
 * Laptop twin of NaturalSpeechEvaluationTest for fast iteration: same sherpa-onnx version, same model files,
 * same AutoRoute rules (from core.jar), same scoring and results.json schema. Not for timing: CPU speed differs.
 * Modes: sense, omni, auto (SenseVoice first, Omnilingual when AutoRoute asks; the rare Korean clock retry is skipped).
 *
 * java -cp core.jar;kotlin-stdlib.jar;sherpa-onnx.jar;sherpa-native.jar LaptopEval.java <set dir> <models dir> <modes> <workers> <threads> <out.json>
 */
public class LaptopEval {
    record Row(String file, String language, long audioMs, String reference) {}

    static String clean(String text) { return text.replaceAll("<\\|[^|>]+\\|>", "").trim(); }

    static OfflineRecognizer sense(Path models, int threads) { return sense(models, threads, "auto", true); }
    static OfflineRecognizer sense(Path models, int threads, String language, boolean itn) {
        return new OfflineRecognizer(OfflineRecognizerConfig.builder().setOfflineModelConfig(OfflineModelConfig.builder()
            .setSenseVoice(OfflineSenseVoiceModelConfig.builder().setModel(models.resolve("sensevoice-ko.int8.onnx").toString())
                .setLanguage(language).setInverseTextNormalization(itn).build())
            .setTokens(models.resolve("sensevoice-tokens.txt").toString()).setNumThreads(threads).build()).build());
    }

    static OfflineRecognizer omni(Path models, int threads) {
        return new OfflineRecognizer(OfflineRecognizerConfig.builder().setOfflineModelConfig(OfflineModelConfig.builder()
            .setOmnilingual(OfflineOmnilingualAsrCtcModelConfig.builder().setModel(models.resolve("omni-model.int8.onnx").toString()).build())
            .setTokens(models.resolve("omni-tokens.txt").toString()).setNumThreads(threads).build()).build());
    }

    static OfflineRecognizerResult run(OfflineRecognizer recognizer, float[] samples) {
        OfflineStream stream = recognizer.createStream();
        try { stream.acceptWaveform(samples, 16000); recognizer.decode(stream); return recognizer.getResult(stream); }
        finally { stream.release(); }
    }

    /** Same importer rules as WavAudio: PCM16 mono 16 kHz. */
    static float[] wav(Path path) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN);
        int offset = 12;
        while (offset + 8 <= b.limit()) {
            String id = new String(b.array(), offset, 4, StandardCharsets.US_ASCII);
            int size = b.getInt(offset + 4);
            if (id.equals("data")) {
                float[] out = new float[size / 2];
                for (int i = 0; i < out.length; i++) out[i] = b.getShort(offset + 8 + i * 2) / 32768f;
                return out;
            }
            offset += 8 + size + (size % 2);
        }
        throw new IOException("no data chunk: " + path);
    }

    static float[] standardized(float[] x) {
        double mean = 0, var = 0;
        for (float v : x) mean += v;
        mean /= x.length;
        for (float v : x) var += (v - mean) * (v - mean);
        double std = Math.sqrt(var / x.length) + 1e-7;
        float[] out = new float[x.length];
        for (int i = 0; i < x.length; i++) out[i] = (float) ((x[i] - mean) / std);
        return out;
    }

    static float[] padded(float[] x) { return padded(x, 8000); }
    static float[] padded(float[] x, int side) {
        float[] out = new float[x.length + 2 * side];
        System.arraycopy(x, 0, out, side, x.length);
        return out;
    }

    static int[] normalized(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFKC).codePoints().filter(Character::isLetterOrDigit).toArray();
    }

    static int edits(int[] a, int[] b) {
        int[] previous = new int[b.length + 1];
        for (int j = 0; j <= b.length; j++) previous[j] = j;
        for (int i = 0; i < a.length; i++) {
            int[] current = new int[b.length + 1]; current[0] = i + 1;
            for (int j = 0; j < b.length; j++) current[j + 1] = Math.min(Math.min(current[j] + 1, previous[j + 1] + 1), previous[j] + (a[i] == b[j] ? 0 : 1));
            previous = current;
        }
        return previous[b.length];
    }

    static String json(String s) {
        if (s == null) return "null";
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) { case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\"); case '\n' -> out.append("\\n"); case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int) c)); else out.append(c); } }
        }
        return out.append('"').toString();
    }

    public static void main(String[] args) throws Exception {
        Path set = Path.of(args[0]), models = Path.of(args[1]);
        List<String> modes = List.of(args[2].split(","));
        int workers = Integer.parseInt(args[3]), threads = Integer.parseInt(args[4]);
        LibraryUtils.load();
        List<Row> rows = new ArrayList<>();
        for (String line : Files.readAllLines(set.resolve("manifest.tsv"), StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] p = line.split("\t", 4);
            rows.add(new Row(p[0], p[1], Long.parseLong(p[2]), p[3]));
        }
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        ThreadLocal<OfflineRecognizer> senseLocal = ThreadLocal.withInitial(() -> sense(models, threads));
        ThreadLocal<OfflineRecognizer> senseRaw = ThreadLocal.withInitial(() -> sense(models, threads, "auto", false));
        ThreadLocal<OfflineRecognizer> senseKo = ThreadLocal.withInitial(() -> sense(models, threads, "ko", true));
        ThreadLocal<OfflineRecognizer> senseKoRaw = ThreadLocal.withInitial(() -> sense(models, threads, "ko", false));
        ThreadLocal<OfflineRecognizer> omniLocal = ThreadLocal.withInitial(() -> omni(models, threads));
        List<Future<String>> jobs = new ArrayList<>();
        long begin = System.currentTimeMillis();
        for (String mode : modes) for (Row row : rows) jobs.add(pool.submit(() -> {
            float[] samples = wav(set.resolve(row.file()));
            long start = System.currentTimeMillis();
            String text, backend = mode, detected = null, retry = null;
            switch (mode) {
                case "sense" -> { OfflineRecognizerResult r = run(senseLocal.get(), samples); text = clean(r.getText()); detected = r.getLang().replace("<|", "").replace("|>", ""); }
                case "senseraw" -> text = clean(run(senseRaw.get(), samples).getText());
                case "senseko" -> text = clean(run(senseKo.get(), samples).getText());
                case "sensekoraw" -> text = clean(run(senseKoRaw.get(), samples).getText());
                case "omni" -> text = clean(run(omniLocal.get(), padded(samples)).getText()); // as OmniSpeechEngine
                case "omniraw" -> text = clean(run(omniLocal.get(), samples).getText()); // before the edge-padding fix
                // Experiments: zero-mean/unit-variance input; 0.5 s of silence on each side; both.
                case "omninorm" -> text = clean(run(omniLocal.get(), standardized(samples)).getText());
                case "omnipad" -> text = clean(run(omniLocal.get(), padded(samples)).getText());
                case "omnipadnorm" -> text = clean(run(omniLocal.get(), standardized(padded(samples))).getText());
                case "omnipad25" -> text = clean(run(omniLocal.get(), padded(samples, 4000)).getText());
                case "omnipad100" -> text = clean(run(omniLocal.get(), padded(samples, 16000)).getText());
                case "omnipad200" -> text = clean(run(omniLocal.get(), padded(samples, 32000)).getText());
                case "auto" -> {
                    OfflineRecognizerResult r = run(senseLocal.get(), samples);
                    String sense = clean(r.getText()), tag = r.getLang().replace("<|", "").replace("|>", "");
                    detected = tag;
                    if (AutoRoute.INSTANCE.senseVoiceSuffices(sense, tag, AutoRoute.INSTANCE.speechMs(samples))) { text = sense; backend = "SenseVoice · detected " + tag; }
                    else {
                        String second = clean(run(omniLocal.get(), padded(samples)).getText());
                        boolean keep = AutoRoute.INSTANCE.preferSenseVoice(sense, tag, second);
                        text = keep ? sense : second;
                        backend = keep ? "SenseVoice · checked with Omnilingual (" + tag + ")" : "Omnilingual · SenseVoice heard " + tag;
                        retry = "first: " + sense + " · second: " + second;
                    }
                }
                default -> throw new IllegalArgumentException("mode " + mode);
            }
            long elapsed = System.currentTimeMillis() - start;
            int[] reference = normalized(row.reference());
            return "{\"file\":" + json(row.file()) + ",\"language\":" + json(row.language()) + ",\"mode\":" + json(mode)
                + ",\"audioMs\":" + row.audioMs() + ",\"elapsedMs\":" + elapsed + ",\"reference\":" + json(row.reference())
                + ",\"recognized\":" + json(text) + ",\"backend\":" + json(backend) + ",\"detected\":" + json(detected)
                + ",\"retry\":" + json(retry) + ",\"referenceCharacters\":" + reference.length
                + ",\"characterEdits\":" + edits(reference, normalized(text)) + "}";
        }));
        List<String> results = new ArrayList<>();
        for (Future<String> job : jobs) results.add(job.get());
        pool.shutdown();
        Files.writeString(Path.of(args[5]), "[\n" + String.join(",\n", results) + "\n]", StandardCharsets.UTF_8);
        System.out.printf("%d results in %.1f s%n", results.size(), (System.currentTimeMillis() - begin) / 1000.0);
        System.exit(0);
    }
}
