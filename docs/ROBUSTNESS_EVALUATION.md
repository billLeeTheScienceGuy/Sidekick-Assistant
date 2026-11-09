# Public-speech robustness evaluation

Physical Pixel 7a, local CPU inference, USB connected, no microphone capture.
Twenty-eight new public FLEURS validation readings (four per selected language),
each replayed clean and with deterministic 10 dB stationary white noise.
The selection and converted PCM hashes were frozen before inference. The dataset
viewer could not serve the test split because of a row-group scan-size limit;
validation data is explicitly used instead. This is not a blind holdout, user
study, representative dialect sample or measured room-noise recording.

[FLEURS dataset and CC BY 4.0 attribution](https://huggingface.co/datasets/google/fleurs).
See evaluation/robustness-selection.json for revision, rows, references and hashes.
The bounded selection has uneven gender coverage: the available Arabic clips
have female labels and German clips male labels. Published labels do not prove
distinct speakers. No population or demographic fairness result is claimed.

## Recognition

Cells show micro-averaged normalized character error rate / processing-time-to-audio ratio.
NFKC, case folding and removal of non-letter/digit characters are applied.
Number spelling, dialect, Chinese script variants and diacritics can still affect scores.
Calls include model preparation/switching and warm reuse; PSS is a post-call
whole-process snapshot. This is not peak model memory or battery measurement.

| Language | Adaptive clean | Adaptive 10 dB noise | Candidate Omni clean | Candidate Omni noise |
|---|---:|---:|---:|---:|
| en | 0.0% / 0.20 | 13.9% / 0.19 | — | — |
| ko | 6.3% / 0.15 | 9.0% / 0.06 | — | — |
| zh | 8.9% / 0.14 | 8.9% / 0.06 | — | — |
| fr | 8.6% / 0.54 | 23.9% / 0.44 | 7.5% / 0.36 | 14.5% / 0.30 |
| de | 7.9% / 0.70 | 28.9% / 0.62 | 5.2% / 0.30 | 13.8% / 0.30 |
| ru | 6.9% / 0.56 | 41.2% / 0.56 | 4.2% / 0.31 | 12.5% / 0.31 |
| ar | 3.2% / 0.36 | 6.4% / 0.30 | — | — |

These paired candidate clips are development comparisons, not independent
validation after tuning. Short command negation/correction fixtures must also pass
before any routing change. Character accuracy does not establish event/action safety.

## Speaker attribution

One public JFK voice was enrolled from three overlapping windows; clean, lower-gain
and noisy excerpts of that same recording are controls, not independent owner recall.
Each unrelated modern FLEURS clip was replayed clean, noisy, and simultaneously
mixed with JFK at equal energy. No invented identity or consent is assigned.

| Condition | Audio trials | Emitted regions | USER regions |
|---|---:|---:|---:|
| clean-owner | 1 | 1 | 1 |
| noisy-owner-10dB | 1 | 1 | 0 |
| quiet-owner | 1 | 1 | 1 |
| simultaneous-equal-energy | 28 | 64 | 0 |
| unfamiliar-clean | 28 | 29 | 0 |
| unfamiliar-noise-10dB | 28 | 28 | 0 |

Unfamiliar/overlap regions assigned to USER: **0**.
UNKNOWN is a valid conservative result. These controls do not measure diarization
error rate, unfamiliar-owner generalization, authentication or multilingual consent.

## Reproduce

1. Run `python scripts/prepare_robustness_fixtures.py`; only test assets are populated.
2. Build/install the isolated benchmark app and its test APK with `-PspeechBenchmark`.
3. Run RobustnessEvaluationTest with `-e runRobustness true`. Never instrument
   the personal com.sidekick.app package. These tests open no microphone.
4. Retrieve files/robust-asr.json, robust-candidate.json and robust-speakers.json
   using adb exec-out run-as on com.sidekick.app.benchmark, then run this script.
5. Keep failures and the frozen selection; do not replace clips after seeing output.

Input report SHA-256:

- baseline: `de8bd7015fb7c18d4411ed984dd4740a9f191f0f8feab350241fc8acf6f01f16`
- candidate: `71f406155fca29c672b1f40f320a2b54d3f3d208d70845e29743b83d631ef403`
- speakers: `96b2406055fe5d86c86fd5520154c2b25131c75a99e1be69be8e2d5e375e13dc`
- final: `ae3b6c3b542b1067c3e0414b1c7572e13230a920b250400d172f59c2d6410278`
- automatic: `4c61843aadb49b0beed838c1e95a6c9b358fd40e2e54f4ba786373d7f3999941`
- commands: `58741ea71c90eef62237acec88662802f7cbcc7e65b648af9c82f0d37bb79bdb`
- live: `331e394b181971dbc0818c1551f5360a258084babda006240b0c74543cfffdea`
- recovery: `a611e56876be62be8e44b661d596b70b84c7f39fd2324360b4a9b3399ad0e6cc`

## Final selected-language routing

Adaptive Automatic and Arabic use Omnilingual. French, German and Russian
use it when the detected speech span reaches five seconds; quiet padding does
not qualify. Short selected-language paths retain their measured Whisper
or SenseVoice choices. Fast/Larger model remain explicit Whisper options.

| Language | Final clean CER / work ratio | Final noisy CER / work ratio |
|---|---:|---:|
| en | 0.0% / 0.20 | 13.9% / 0.19 |
| ko | 6.3% / 0.15 | 9.0% / 0.06 |
| zh | 8.9% / 0.14 | 8.9% / 0.06 |
| fr | 6.8% / 0.36 | 14.5% / 0.37 |
| de | 7.5% / 0.52 | 13.8% / 0.42 |
| ru | 5.5% / 0.36 | 12.5% / 0.36 |
| ar | 3.2% / 0.30 | 6.4% / 0.30 |

This same-selection regression measures the implemented route after development;
it is not a new holdout. Post-call memory snapshots cannot establish peak memory.

## Automatic-language acoustic comparison

The same 28 clips were decoded clean/noisy with multilingual Whisper Tiny
in detection mode and with Omnilingual, without a forced language: 112 calls.
These raw-model measurements predate short-utterance refinement.

| Language | Whisper clean | Whisper noisy | Omni clean | Omni noisy |
|---|---:|---:|---:|---:|
| en | 2.0% / 1.22 | 16.4% / 1.21 | 1.1% / 0.36 | 8.5% / 0.31 |
| ko | 30.6% / 1.39 | 35.1% / 1.39 | 16.2% / 0.30 | 18.0% / 0.30 |
| zh | 40.6% / 1.34 | 49.5% / 1.34 | 18.8% / 0.30 | 20.8% / 0.30 |
| fr | 20.0% / 1.30 | 35.1% / 1.31 | 7.5% / 0.30 | 14.5% / 0.30 |
| de | 12.1% / 1.39 | 50.2% / 1.39 | 5.2% / 0.30 | 13.8% / 0.31 |
| ru | 7.6% / 1.23 | 62.6% / 1.24 | 4.2% / 0.31 | 12.5% / 0.31 |
| ar | 18.4% / 1.45 | 37.6% / 1.45 | 3.2% / 0.30 | 6.4% / 0.31 |

## Short Automatic commands: measured limits

Final hybrid route: **31/34** synthetic commands had the expected action/time;
the earlier Omni-only pass recognized 22/34. These are development fixtures,
not a natural-conversation success rate. Every final fixture rejected wrong
actions/times. Three unresolved Arabic clock cases remain visible below.

Queue-clear speech under five seconds may be read again from the original PCM
using installed SenseVoice/Whisper packs, guided by conservative script and
grammatical clues in the primary output. The app does not choose a decoder
based on whether its output makes an event. Missing optional packs keep the
primary reading; the 31/34 result used all optional packs. No cloud is used.

| Unresolved fixture | Actual recognition |
|---|---|
| ar-ar-SA-ZariyahNeural-0.wav | سأحضر الاجتماع غدا الساعة السادس مساه |
| ar-ar-SA-ZariyahNeural-1.wav | امقل الاجتماع إلى غد الساعة السابعة مساه |
| ar-ar-SA-HamedNeural-1.wav | أنقل الاجتماع إلى غدا الساعة السادع مساء |

Whisper Tiny/Base Arabic alternatives were also tried (16 calls); neither
solved the short-clock problem. A host-only 458 MB NVIDIA Arabic FastConformer
CTC export was evaluated on eight commands and four public readings. It
still malformed clock words and showed no reliable aggregate command benefit,
so it is not shipped. Host runtime speed is not Pixel performance.

[NVIDIA source model, CC BY 4.0](https://huggingface.co/nvidia/stt_ar_fastconformer_hybrid_large_pc_v1.0),
[public ONNX export](https://huggingface.co/OpenVoiceOS/stt_ar_fastconformer_hybrid_large_pc_v1.0_onnx).
The frozen export revision/hash and candidate outputs are in evaluation/arabic-candidate.json.

## Paced combined pipeline

Seven public languages with quiet gaps emitted 8 regions; final drain 0.864 seconds.
The physical Pixel ran real-time-paced VAD, neural speaker partitioning,
anonymous attribution and Automatic ASR together without opening a microphone.
No owner was enrolled in this isolated profile. This does not test within-sentence
code switching, user voice matching, live acoustics or unplugged endurance.

## Silent capture and interruption recovery

A no-host-audio emulator completed six minutes screen-off without ASR chunks
or a false stall. Digital zero samples are valid silence; a five-second absence
of PCM reads or a native read error explicitly stops capture. Global microphone
mute also stopped capture and produced a generic private interruption notice.
A verified own-process kill recovered the armed assistant session after 2.074 seconds (host observation).
Microphone permission revocation stopped and disarmed it after 2.757 seconds.
Granting permission again did not restart it. This is not a force-stop bypass
or a guarantee across device vendors. Capture permission checks run four times
per second instead of querying recording configuration on every PCM poll.
Run scripts/test_capture_recovery.ps1 only against a no-audio emulator.
