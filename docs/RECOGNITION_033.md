# Speech recognition review — 0.33, November 13, 2026

## Changes

- Keep continuity/overlap when a ten-second cap lands inside a short breath.
- Retain 512 ms of onset audio and allow 1.28 seconds of silence before ending a phrase. PCM chunks remain at most ten seconds; the session has no such cap.
- Apply at most 4× gain only to a temporary detection copy, with a peak limit. Recognition and speaker-matching audio remain unchanged.
- For very short CJK-looking speech, optionally try the already-installed SenseVoice automatic model when existing language clues are inconclusive. Reject that reading if it drops recognized Latin words. Never force CJK over Arabic, Cyrillic or Japanese-script input.
- Protect numeric facts, explicit clocks, today/tomorrow and negation across readings. Equivalent clock formats are accepted; room-number differences remain conflicts. The supported invalid Korean-clock repair remains narrow.
- Keep conflicting speech as temporary text only: do not create or change plans, and clear its conversation-reference context. The next clear phrase can work normally.

## Paired recorded evaluation

Physical Pixel 7a, CPU recognition, USB connected. No microphone or private speech. The 41-condition comparison uses identical fixed files and evaluation logic before/after. Six ASCEND clips are genuine short Chinese–English utterances, but all are from one published speaker. They were used to guide the fallback, so this is development evidence, not a blind test. Seven FLEURS validation clips cover the supported languages.

| Condition | Cases | 0.32 character errors | 0.33 character errors |
|---|---:|---:|---:|
| Natural Chinese–English | 6 | 78.90% | 67.89% |
| Public monolingual clean | 7 | 1.99% | 0.85% |
| Quiet −16 dB | 7 | 1.99% | 1.14% |
| White noise 10 dB | 7 | 7.98% | 7.98% |
| Simulated distance | 7 | 4.56% | 3.70% |
| Overlapping speech −10 dB | 7 | 8.83% | 8.83% |
| All conditions | 41 | 9.39% | 8.21% |

The natural bilingual error rate remains poor despite improvement. Very short fragments, fillers and wrong-language hypotheses remain problems. This does not establish Korean–English or other bilingual-pair accuracy. Character scores penalize number formatting, filler transcription and transliteration. Noise/distance/overlap are controlled transforms, not actual room acoustics; overlap is scored against the foreground reference only. No battery, demographic or general accuracy claim is made.

## Capture and safety checks

- All 13 clean recordings were detected in both versions. Reduced-volume detection improved from 12/13 to 13/13. The previous missed English recording now reaches transcription input.
- Eight new silence/white-noise/click controls produced zero speech segments. This does not cover all environmental sounds or music.
- The real capture/VAD pipeline preserved original PCM exactly and wiped owned buffers. Clean-energy onset is a coverage proxy, not hand-labeled phoneme truth.
- Core checks reproduce a delayed detector onset, a short internal breath, and a cap during quiet speech. The twenty-minute bounded-chunk check remains passing.
- The fixed command set remains 31/34 expected actions/times, with no wrong action/time in that set. Three Arabic clock cases remain unresolved. New uncertainty is included in command scoring.
- Real-time-paced public speech in seven languages passed; the final decode queue drained in 1307 ms.
- All 195 core checks, debug lint and seven isolated on-device checks passed; the device group took 434.478 seconds. It includes capture/noise controls, unreliable-text integration, command/paced replays, bilingual retention, acoustic stresses and speaker-scoped corrections.
- The synthetic bilingual venue check retained 7/12, compared with 7/12 in 0.32.

## Attribution and reproduction

[ASCEND / Lovenia et al., LREC 2022](https://huggingface.co/datasets/CAiRE/ASCEND) — CC BY-SA 4.0. [FLEURS / Google](https://huggingface.co/datasets/google/fleurs) — CC BY 4.0. ASCEND-derived evaluation text in the JSON is supplied under CC BY-SA 4.0, with source, revision, row IDs and PCM hashes in recognition-selection-033.json. Test audio is not bundled in the normal app.

Prepare the existing FLEURS/command fixtures, then run `scripts/prepare_recognition_review.py`. Build the isolated benchmark app/test APK with `-PspeechBenchmark`, install those packages, and run `RecognitionReviewTest`, `CaptureBoundaryReviewTest` and `AutomaticLanguageTest` with `-e runRobustness true`. `RecognitionUncertaintyTest` tests the temporary-memory integration without microphone capture. See evaluation/recognition-review-033.json for outputs and failures.

## Next required field check

Use a consenting natural Korean–English conversation on the actual microphone, with quiet pauses, a spoken 8 pm time, an end time and a venue correction. Stored fixtures cannot verify the user’s voice, microphone placement, real room noise or language preferences. Longer screen-off/battery trials remain separate work.
