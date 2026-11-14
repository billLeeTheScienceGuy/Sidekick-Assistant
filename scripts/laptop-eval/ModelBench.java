import com.k2fsa.sherpa.onnx.*;

import java.io.IOException;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.*;

/**
 * Candidate-model benchmark on the laptop (sherpa-onnx 1.13.8). Each model runs only on languages it supports,
 * with the clip's true language when the model needs one (a "manual language" setting). Output matches LaptopEval's
 * results.json schema with mode = model id, plus "lid" rows from the Whisper-tiny language identifier.
 *
 * java -cp sherpa-onnx-jvm.jar;native.jar ModelBench.java <set dirs ;-separated> <candidates dir> <app models dir> <model ids,> <workers> <threads> <out.json>
 */
public class ModelBench {
    record Row(Path dir, String file, String language, long audioMs, String reference) {}
    interface Engine { String run(float[] samples, String language); }
    record Spec(Set<String> languages, Function<Integer, Engine> factory) {}

    static Path candidates, appModels;

    static Path find(Path dir, String glob) {
        try (Stream<Path> s = Files.walk(dir)) {
            PathMatcher m = FileSystems.getDefault().getPathMatcher("glob:" + glob);
            List<Path> all = s.filter(p -> m.matches(p.getFileName())).sorted().toList();
            // Prefer int8 when both precisions ship.
            return all.stream().filter(p -> p.toString().contains("int8")).findFirst().orElse(all.isEmpty() ? null : all.get(0));
        } catch (IOException e) { throw new RuntimeException(e); }
    }
    static String f(Path dir, String glob) { Path p = find(dir, glob); if (p == null) throw new IllegalStateException(glob + " missing in " + dir); return p.toString(); }
    static Path c(String name) { return candidates.resolve(name); }

    static String clean(String text) { return text.replaceAll("<\\|[^|>]+\\|>", "").trim(); }

    static Engine offline(OfflineModelConfig.Builder model, int threads) { return offline(model, threads, null, 0); }
    static Engine offline(OfflineModelConfig.Builder model, int threads, String hotwords, float score) {
        OfflineRecognizerConfig.Builder b = OfflineRecognizerConfig.builder().setOfflineModelConfig(model.setNumThreads(threads).build());
        if (hotwords != null) b.setDecodingMethod("modified_beam_search").setMaxActivePaths(4).setHotwordsFile(hotwords).setHotwordsScore(score);
        OfflineRecognizer r = new OfflineRecognizer(b.build());
        return (samples, language) -> {
            OfflineStream s = r.createStream();
            try { s.acceptWaveform(samples, 16000); r.decode(s); return clean(r.getResult(s).getText()); } finally { s.release(); }
        };
    }
    static float[] pad(float[] x, int side) { float[] out = new float[x.length + 2 * side]; System.arraycopy(x, 0, out, side, x.length); return out; }

    static Map<String, Spec> registry() {
        Set<String> all = Set.of("en", "ko", "zh", "fr", "de", "ru", "ar");
        Map<String, Spec> m = new LinkedHashMap<>();
        m.put("sense2024", new Spec(all, t -> offline(OfflineModelConfig.builder().setTokens(appModels.resolve("sensevoice-tokens.txt").toString())
            .setSenseVoice(OfflineSenseVoiceModelConfig.builder().setModel(appModels.resolve("sensevoice-ko.int8.onnx").toString()).setLanguage("auto").setInverseTextNormalization(true).build()), t)));
        // SenseVoice's audio-event tag (Speech, BGM, Laughter…) as "[event] text": used to test dropping music.
        m.put("senseevent", new Spec(all, t -> {
            OfflineRecognizer r = new OfflineRecognizer(OfflineRecognizerConfig.builder().setOfflineModelConfig(OfflineModelConfig.builder()
                .setTokens(appModels.resolve("sensevoice-tokens.txt").toString()).setNumThreads(t)
                .setSenseVoice(OfflineSenseVoiceModelConfig.builder().setModel(appModels.resolve("sensevoice-ko.int8.onnx").toString()).setLanguage("auto").setInverseTextNormalization(true).build()).build()).build());
            return (x, l) -> { OfflineStream s = r.createStream();
                try { s.acceptWaveform(x, 16000); r.decode(s); var res = r.getResult(s); return "[" + res.getEvent() + "] " + clean(res.getText()); } finally { s.release(); } };
        }));
        Path s25 = c("sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09");
        m.put("sense2025", new Spec(all, t -> offline(OfflineModelConfig.builder().setTokens(f(s25, "tokens.txt"))
            .setSenseVoice(OfflineSenseVoiceModelConfig.builder().setModel(f(s25, "model*.onnx")).setLanguage("auto").setInverseTextNormalization(true).build()), t)));
        m.put("omni1", new Spec(all, t -> { Engine e = offline(OfflineModelConfig.builder().setTokens(appModels.resolve("omni-tokens.txt").toString())
            .setOmnilingual(OfflineOmnilingualAsrCtcModelConfig.builder().setModel(appModels.resolve("omni-model.int8.onnx").toString()).build()), t);
            return (x, l) -> e.run(pad(x, 8000), l); }));
        Path o2 = c("sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-v2-int8-2026-02-05");
        m.put("omni2", new Spec(all, t -> { Engine e = offline(OfflineModelConfig.builder().setTokens(f(o2, "tokens.txt"))
            .setOmnilingual(OfflineOmnilingualAsrCtcModelConfig.builder().setModel(f(o2, "model*.onnx")).build()), t);
            return (x, l) -> e.run(pad(x, 8000), l); }));
        Path zko = c("sherpa-onnx-zipformer-korean-2024-06-24");
        Function<Integer, OfflineModelConfig.Builder> zkoModel = t -> OfflineModelConfig.builder().setTokens(f(zko, "tokens.txt"))
            .setTransducer(OfflineTransducerModelConfig.builder().setEncoder(f(zko, "encoder*.onnx")).setDecoder(f(zko, "decoder*.onnx")).setJoiner(f(zko, "joiner*.onnx")).build());
        m.put("zipko", new Spec(Set.of("ko"), t -> offline(zkoModel.apply(t), t)));
        Path mko = c("sherpa-onnx-moonshine-tiny-ko-quantized-2026-02-27"), men = c("sherpa-onnx-moonshine-base-en-quantized-2026-02-27"), mar = c("sherpa-onnx-moonshine-base-ar-quantized-2026-02-27");
        for (Object[] mm : new Object[][]{{"moonko", mko, "ko"}, {"moonen", men, "en"}, {"moonar", mar, "ar"}}) {
            Path d = (Path) mm[1];
            m.put((String) mm[0], new Spec(Set.of((String) mm[2]), t -> offline(OfflineModelConfig.builder().setTokens(f(d, "tokens.txt"))
                .setMoonshine(OfflineMoonshineModelConfig.builder().setEncoder(f(d, "encoder_model*")).setMergedDecoder(f(d, "decoder_model_merged*")).build()), t)));
        }
        Path can = c("sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8");
        m.put("canary", new Spec(Set.of("en", "de", "fr"), t -> {
            Map<String, Engine> byLanguage = new ConcurrentHashMap<>();
            return (x, l) -> byLanguage.computeIfAbsent(l, lang -> offline(OfflineModelConfig.builder().setTokens(f(can, "tokens.txt"))
                .setCanary(OfflineCanaryModelConfig.builder().setEncoder(f(can, "encoder*.onnx")).setDecoder(f(can, "decoder*.onnx")).setSrcLang(lang).setTgtLang(lang).setUsePnc(true).build()), t)).run(x, l);
        }));
        for (Object[] nn : new Object[][]{
                {"fc20k", "sherpa-onnx-nemo-fast-conformer-transducer-be-de-en-es-fr-hr-it-pl-ru-uk-20k-int8", Set.of("en", "de", "fr", "ru")},
                {"parakeet110", "sherpa-onnx-nemo-parakeet_tdt_transducer_110m-en-36000-int8", Set.of("en")},
                // Every language: what the English model writes for non-English speech (Auto routing's agreement check).
                {"parakeet110all", "sherpa-onnx-nemo-parakeet_tdt_transducer_110m-en-36000-int8", Set.of("en", "ko", "zh", "de", "fr", "ru", "ar")},
                {"parakeet3", "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8", Set.of("en", "de", "fr", "ru")},
                {"gigaam3", "sherpa-onnx-nemo-transducer-giga-am-v3-russian-2025-12-16", Set.of("ru")}}) {
            Path d = c((String) nn[1]);
            @SuppressWarnings("unchecked") Set<String> langs = (Set<String>) nn[2];
            m.put((String) nn[0], new Spec(langs, t -> offline(OfflineModelConfig.builder().setTokens(f(d, "tokens.txt")).setModelType("nemo_transducer")
                .setTransducer(OfflineTransducerModelConfig.builder().setEncoder(f(d, "encoder*.onnx")).setDecoder(f(d, "decoder*.onnx")).setJoiner(f(d, "joiner*.onnx")).build()), t)));
        }
        Path fr2 = c("sherpa-onnx-fire-red-asr2-ctc-zh_en-int8-2026-02-25");
        m.put("firered2", new Spec(Set.of("zh", "en"), t -> offline(OfflineModelConfig.builder().setTokens(f(fr2, "tokens.txt"))
            .setFireRedAsrCtc(OfflineFireRedAsrCtcModelConfig.builder().setModel(f(fr2, "model*.onnx")).build()), t)));
        Path nano = c("sherpa-onnx-sense-voice-funasr-nano-int8-2025-12-17");
        // Ships in SenseVoice format (model.int8.onnx + tokens.txt), not the multi-part Fun-ASR-Nano LLM layout.
        m.put("funnano", new Spec(Set.of("zh", "en", "ko"), t -> offline(OfflineModelConfig.builder().setTokens(f(nano, "tokens.txt"))
            .setSenseVoice(OfflineSenseVoiceModelConfig.builder().setModel(f(nano, "model*.onnx")).setLanguage("auto").setInverseTextNormalization(true).build()), t)));
        Path q3 = c("sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25");
        m.put("qwen3", new Spec(all, t -> offline(OfflineModelConfig.builder()
            .setQwen3Asr(OfflineQwen3AsrModelConfig.builder().setConvFrontend(f(q3, "conv_frontend*.onnx")).setEncoder(f(q3, "encoder*.onnx"))
                .setDecoder(f(q3, "decoder*.onnx")).setTokenizer(q3.resolve("tokenizer").toString()).build()), t)));
        Path nem = c("sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-560ms-int8-2026-06-11");
        m.put("nemotron", new Spec(all, t -> {
            OnlineRecognizer r = new OnlineRecognizer(OnlineRecognizerConfig.builder().setOnlineModelConfig(OnlineModelConfig.builder()
                .setTransducer(OnlineTransducerModelConfig.builder().setEncoder(f(nem, "encoder*.onnx")).setDecoder(f(nem, "decoder*.onnx")).setJoiner(f(nem, "joiner*.onnx")).build())
                .setTokens(f(nem, "tokens.txt")).setNumThreads(t).build()).build());
            return (x, l) -> {
                OnlineStream s = r.createStream();
                try {
                    s.acceptWaveform(x, 16000); s.acceptWaveform(new float[16000], 16000); s.inputFinished();
                    while (r.isReady(s)) r.decode(s);
                    return clean(r.getResult(s).getText());
                } finally { s.release(); }
            };
        }));
        Path wt = c("sherpa-onnx-whisper-tiny");
        m.put("lid", new Spec(all, t -> {
            SpokenLanguageIdentification lid = new SpokenLanguageIdentification(SpokenLanguageIdentificationConfig.builder()
                .setWhisper(SpokenLanguageIdentificationWhisperConfig.builder().setEncoder(f(wt, "*encoder*.onnx")).setDecoder(f(wt, "*decoder*.onnx")).build())
                .setNumThreads(t).build());
            Map<String, String> codes = Map.of("english", "en", "korean", "ko", "chinese", "zh", "french", "fr", "german", "de", "russian", "ru", "arabic", "ar");
            return (x, l) -> { OfflineStream s = lid.createStream(); try { s.acceptWaveform(x, 16000); String r = lid.compute(s).trim().toLowerCase(Locale.ROOT); return codes.getOrDefault(r, r); } finally { s.release(); } };
        }));
        return m;
    }

    static float[] wav(Path path) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN);
        int offset = 12;
        while (offset + 8 <= b.limit()) {
            String id = new String(b.array(), offset, 4, StandardCharsets.US_ASCII); int size = b.getInt(offset + 4);
            if (id.equals("data")) { float[] out = new float[size / 2]; for (int i = 0; i < out.length; i++) out[i] = b.getShort(offset + 8 + i * 2) / 32768f; return out; }
            offset += 8 + size + (size % 2);
        }
        throw new IOException("no data: " + path);
    }
    static int[] normalized(String text) { return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFKC).codePoints().filter(Character::isLetterOrDigit).toArray(); }
    static int edits(int[] a, int[] b) {
        int[] prev = new int[b.length + 1]; for (int j = 0; j <= b.length; j++) prev[j] = j;
        for (int i = 0; i < a.length; i++) { int[] cur = new int[b.length + 1]; cur[0] = i + 1;
            for (int j = 0; j < b.length; j++) cur[j + 1] = Math.min(Math.min(cur[j] + 1, prev[j + 1] + 1), prev[j] + (a[i] == b[j] ? 0 : 1)); prev = cur; }
        return prev[b.length];
    }
    static String json(String s) {
        if (s == null) return "null";
        StringBuilder o = new StringBuilder("\"");
        for (char ch : s.toCharArray()) switch (ch) { case '"' -> o.append("\\\""); case '\\' -> o.append("\\\\"); case '\n' -> o.append("\\n"); case '\t' -> o.append("\\t");
            default -> { if (ch < 0x20) o.append(String.format("\\u%04x", (int) ch)); else o.append(ch); } }
        return o.append('"').toString();
    }

    public static void main(String[] args) throws Exception {
        candidates = Path.of(args[1]); appModels = Path.of(args[2]);
        List<String> ids = List.of(args[3].split(","));
        int workers = Integer.parseInt(args[4]), threads = Integer.parseInt(args[5]);
        LibraryUtils.load();
        List<Row> rows = new ArrayList<>();
        for (String dir : args[0].split(";")) {
            Path d = Path.of(dir);
            for (String line : Files.readAllLines(d.resolve("manifest.tsv"), StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue; String[] p = line.split("\t", 4);
                rows.add(new Row(d, p[0], p[1], Long.parseLong(p[2]), p[3]));
            }
        }
        Map<String, Spec> registry = registry();
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        for (String id : ids) {
            Spec spec = registry.get(id);
            if (spec == null) { System.out.println("unknown model " + id); continue; }
            List<Row> mine = rows.stream().filter(r -> spec.languages().contains(r.language())).toList();
            ExecutorService pool = Executors.newFixedThreadPool(workers);
            ThreadLocal<Engine> engine = ThreadLocal.withInitial(() -> spec.factory().apply(threads));
            long begin = System.currentTimeMillis();
            List<Future<?>> jobs = new ArrayList<>();
            for (Row row : mine) jobs.add(pool.submit(() -> {
                String text; long elapsed;
                try {
                    float[] samples = wav(row.dir().resolve(row.file()));
                    long start = System.currentTimeMillis();
                    text = engine.get().run(samples, row.language());
                    elapsed = System.currentTimeMillis() - start;
                } catch (Throwable e) { text = "ERROR: " + e; elapsed = 0; }
                int[] ref = normalized(row.reference());
                boolean lid = id.equals("lid");
                results.add("{\"file\":" + json(row.file()) + ",\"set\":" + json(row.dir().getFileName().toString()) + ",\"language\":" + json(row.language())
                    + ",\"mode\":" + json(id) + ",\"audioMs\":" + row.audioMs() + ",\"elapsedMs\":" + elapsed + ",\"reference\":" + json(row.reference())
                    + ",\"recognized\":" + json(text) + ",\"backend\":" + json(id) + ",\"detected\":" + json(lid ? text : null)
                    + ",\"referenceCharacters\":" + ref.length + ",\"characterEdits\":" + (lid ? (text.equals(row.language()) ? 0 : ref.length) : edits(ref, normalized(text))) + "}");
                return null;
            }));
            int failed = 0;
            for (Future<?> j : jobs) try { j.get(); } catch (ExecutionException e) { failed++; }
            pool.shutdownNow();
            System.out.printf("%-12s %4d clips in %6.1f s%s%n", id, mine.size(), (System.currentTimeMillis() - begin) / 1000.0, failed > 0 ? " (" + failed + " failed)" : "");
            Files.writeString(Path.of(args[6]), "[\n" + String.join(",\n", results) + "\n]", StandardCharsets.UTF_8);
        }
        System.exit(0);
    }
}
