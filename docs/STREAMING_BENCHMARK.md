# Live transcription measurements

Test device: physical Pixel 7a, Android 17, ARM64. Runtime: pinned whisper.cpp v1.7.6,
bundled Whisper tiny.en, CPU only. Measurements collected 2026-10-22.

## Initial comparison

### Sustained result

All 96 scheduled 10-second chunks completed in 964.03 seconds overall. With four
threads and the full encoder window: median 3.821 s, p95 3.908 s, maximum 4.011 s.
Maximum scheduling lateness was 22 ms; no accumulating backlog was observed.
First ten median: 3.8365 s; last ten median: 3.8345 s. Sampled maximum PSS was
298,780 KiB (291.8 MiB). Android thermal status stayed at 0. Battery temperature
rose from 24.0 to 28.4 degrees C while USB powered. This is not a battery-drain result.

### Short-window result and next candidate

Two threads, retained model, 512-position encoder window, two measurements per length:

| Audio chunk | Median processing time | RTF |
| --- | ---: | ---: |
| 3 seconds | 1.531 s | 0.510 |
| 5 seconds | 1.389 s | 0.278 |
| 10 seconds | 1.636 s | 0.164 |

Sampled maximum PSS was 212,289 KiB (207.3 MiB). The ten-second output retained the
known sentence. The five-second output changed "ask not" to "asked not", illustrating
why throughput alone does not establish transcription quality. These six calls are
an exploratory comparison, not a sustained test of the shorter window.

Next candidate: two threads, retained model during speech, approximately five-second
chunks with a 512-position encoder window, and a lightweight speech detector. Test
boundary overlap and reconciliation before connecting continuous capture. Expected
first-text latency for a fixed five-second chunk is roughly 6.4 seconds on this fixture,
including collection time; this is not word-by-word instantaneous transcription.
No claim is made that this is the most energy-efficient configuration without unplugged
measurements or a comparison with other streaming recognition engines.

### Speech detection

Silero v5.1.2, one CPU thread, 20 evaluations per case: one-second windows averaged
9.79 ms for silence and 9.99 ms for speech; ten-second windows averaged 95.70 ms and
95.62 ms respectively. Digitally silent windows had no frames over the 0.5 threshold.
This supports testing a low-cost speech gate but does not establish noise rejection,
speaker identity, or all-day battery life.

Raw records: [transcription](benchmarks/transcription.txt), [VAD](benchmarks/vad.txt).
The original reload-per-call comparison is also retained in [baseline](benchmarks/baseline.txt).

### Full-window comparison

Median processing time, three measurements per cell (including one cold call per run):

| Audio chunk | Two threads, reload each call | Two threads, retain model | Four threads, retain model |
| --- | ---: | ---: | ---: |
| 3 seconds | 5.145 s | 4.945 s | 3.572 s |
| 5 seconds | 5.120 s | 4.932 s | 3.550 s |
| 10 seconds | 5.391 s | 5.204 s | 3.799 s |

Three-second chunks do not keep up with the full encoder window, even with four threads.
Five-second chunks with four threads keep up on this fixture, but have less headroom
than ten-second chunks. Retaining the model gives a modest speed improvement in this
comparison. This is not an energy-efficiency comparison: thread count, CPU time and
battery consumption are different measurements.

## What is being tested

The benchmark app uses application ID `com.sidekick.app.benchmark`. It does not replace
the ordinary app or access its commitments. Tests feed the existing public JFK fixture
directly into the engine. They do not open the microphone or save captured audio.
Only benchmark metrics and the public fixture's recognized words are logged.

Each comparison uses the first 3, 5 or 10 seconds of that fixture. Round zero includes
cold engine loading; the original baseline also includes first-time model installation.
Subsequent tests have the model installed. The baseline reloads the model per call.
The cached comparisons keep its context in RAM until the benchmark finishes.

RTF (real-time factor) is processing time divided by audio duration. Below 1 means
processing is faster than the arrival of that much audio. A value near 1 leaves little
room for other phone activity. These are measurements of one sample, not general
accuracy scores or a representative workload.

The sustained test schedules one 10-second sample every 10 seconds for 96 samples.
It measures engine throughput and scheduling backlog for 16 minutes of wall time.
It is not an end-to-end microphone, VAD, queue, screen-off or background-service test.
There is no overlapping audio in this benchmark. More frequent partial revisions and
overlap will add work and need separate measurements.

Memory is process PSS, sampled about every 100 ms, including model/runtime overhead.
The sampled maximum can miss brief peaks. Baseline memory was measured only after
inference and cannot be compared with the later sampled peak as if both were peaks.
Thermal status is Android's reported status; battery temperature is not CPU temperature.
The phone is USB powered. Battery-drain or all-day-runtime conclusions are invalid
under these conditions.
During the sustained run, Android reported the device awake with stay-awake-while-powered
enabled. The test did not alter that setting.

## Reproduce

Use JDK 17, the Android SDK and the prepared speech model as described in README.

```powershell
# Cached model, four threads, 3/5/10-second chunks:
.\scripts\benchmark-speech.ps1 -Threads 4 -RetainModel
# Sixteen-minute paced run, using only the public fixture:
.\scripts\benchmark-speech.ps1 -Threads 4 -RetainModel -Sizes 10 -Rounds 96 -Paced
python scripts/summarize_benchmark.py app/build/benchmarks/<run-id>.jsonl
```

Results are saved under the ignored `app/build/benchmarks` directory. The scripts
require a successful test result and the expected number of records. For multiple
connected devices pass `-Serial`. They do not clear device logs or uninstall the
ordinary app.

Optional Silero benchmark preparation and invocation (after building/installing the
benchmark APKs):

```powershell
python scripts/prepare_vad_benchmark.py
.\gradlew.bat -PspeechBenchmark :app:assembleBenchmark :app:assembleBenchmarkAndroidTest
adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk
adb install -r app/build/outputs/apk/androidTest/benchmark/app-benchmark-androidTest.apk
adb shell am instrument -w -e class com.sidekick.app.VadBenchmarkTest -e vad true com.sidekick.app.benchmark.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d -s SidekickVadBenchmark:I
```

Silero v5.1.2 is fetched from the pinned [upstream model repository](https://huggingface.co/ggml-org/whisper-vad/tree/9ffd54a1e1ee413ddf265af9913beaf518d1639b),
SHA-256 `29940d98d42b91fbd05ce489f3ecf7c72f0a42f027e4875919a28fb4c04ea2cf`.
Its MIT notice is included in test assets. The model is only in the optional test APK;
neither the ordinary app nor the CI build requires it. Each VAD case measures 20 repeated
evaluations with one CPU thread, after a separate model load. Digitally silent audio
is not representative of background noise. The pinned runtime resets the detector's
recurrent state on each call, so this tests windowed detection, not stateful frame-by-frame
streaming. That distinction must be addressed when integrating the live detector.

Encoder-window experiments use `-AudioContext 512` (10.24 seconds) or `256`
(5.12 seconds). Defaults remain zero, which uses the full model window. The wrapper
rejects audio longer than the requested window. A faster setting is only a candidate
until its accuracy and boundary behavior have been evaluated on representative speech.

## Design implications

Keep microphone capture independent of transcription, use bounded RAM buffers, and
run only one inference worker. Reuse the model during active speech; consider releasing
it during long silence after measuring reload cost versus idle memory. A speech detector
should suppress silent inference, and short utterances may need batching to avoid
paying Whisper's inference overhead too often. Session length must not be coupled to
chunk length. No audio files are needed for this architecture.

For continuous speech, test approximately 5–10-second chunks before attempting
subsecond transcript updates. The upstream [stream example](https://github.com/ggml-org/whisper.cpp/tree/master/examples/stream)
demonstrates repeated-window inference, but its example settings are not a Pixel benchmark.
Boundary overlap, text reconciliation and preserving utterance context still need testing.

No finite queue can guarantee lossless capture when sustained processing falls below
real time. Any overload must be visible; do not silently drop words or claim uninterrupted
coverage. Production behavior for overload remains a product decision.

## Still required

- Representative microphone recordings with names, dates, accents, noise and long speech.
- Neural speech-detection false positives/negatives and CPU cost during silence.
- Live producer/consumer tests, chunk-boundary accuracy and partial-text latency.
- Unplugged, screen-off battery comparisons with an idle baseline, followed by longer
  thermal and lifecycle tests. USB-powered inference measurements do not establish these.
- Speaker matching before treating other people's speech as the user's commitments.

These measurements were collected before the live UI was connected. Version 0.3 now
uses the two-thread, 512-position candidate in its continuous pipeline with Silero,
boundary overlap and a bounded queue. Real microphone accuracy and unplugged battery
impact remain separate validation requirements; benchmark timings do not prove those.

Build validation: normal and benchmark APKs and test APKs build; 22 core tests pass;
both lint variants report zero errors and 11 warnings. Optional device benchmarks are
kept separate from the user's ordinary app installation.
Four device regression tests also passed: database migration, known speech, one-shot
cancellation/recovery, and cached-model cancellation/recovery/release. No microphone
was activated by these tests.
