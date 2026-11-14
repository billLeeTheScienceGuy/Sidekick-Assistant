# Current work — 0.33

The onset/pause, quiet-detection, short CJK fallback, safe-retry and acoustic-stress
work is implemented and measured in [RECOGNITION_033.md](RECOGNITION_033.md).
Very short natural bilingual fragments remain weak. Next priorities are a real
Korean–English microphone trial, independent speakers/other bilingual pairs,
stronger acoustic language identification, and calibrated retry selection.
Current rejection is based on protected evidence, not calibrated model confidence.
Real room noise, overlapping speakers and battery behavior need field evidence.

# Recognition quality: next work

0.32 implements a conservative bilingual retry guard without new model weights.
ShortLanguageHint declines a forced-language retry when the primary reading has
substantial Latin and non-Latin letters, multiple non-Latin scripts, or strong
function-word clues for two Latin-script languages. The unrestricted multilingual
reading is kept. The guard does not report calibrated language confidence or
recover speech that the primary recognizer already missed.

## 0.32 paired Pixel experiment

Twelve newly assembled synthetic English/other-language clips were frozen before
running the previous engine and update with the same test APK on the same Pixel.
They combine existing synthetic commands with a new English venue phrase, in
both orders, across Korean, Chinese, French, German, Russian and Arabic.
No private audio or physical microphone was used.

| Measure | Previous engine | 0.32 guard |
|---|---:|---:|
| Exact English venue retained | 4/12 | 7/12 |
| Normalized character error rate | 20.2% | 17.4% |
| Sum of recorded decoding time | 67.101 s | 36.715 s |

Korean and German venue loss improved. French/Russian/Arabic cases still fail.
German number words versus digits increase the character score despite preserving
the spoken number, so this metric is not a direct measure of event correctness.
Runtime includes model setup/hash checks and the phone’s existing listening
service remained enabled; this is not a battery or universal throughput claim.

A SenseVoice automatic-mode candidate on the four Korean/Chinese clips scored
32.1% character errors versus the previous route’s 23.8% on those same clips.
It was rejected. No extra weights, cloud recognition or always-on second decoder
were added. See evaluation/bilingual-recognition-032.json for references, hashes,
per-clip outputs and failures. These development fixtures do not establish
natural bilingual or accent/dialect accuracy.

## Earlier 0.31 evidence

The November 13 on-Pixel recorded-audio recheck retained 31/34 expected synthetic
command actions/times. All 34 avoided a wrong action/time in this bounded set;
three Arabic clock phrases remain unresolved. Seven public language recordings
with pauses emitted eight regions and drained the last queue in 964 ms. These
are development fixtures, not a natural-conversation accuracy rate or proof of
within-sentence language switching. See ROBUSTNESS_EVALUATION.md for model
comparisons and limitations. No physical microphone was used for this recheck.

## Priority experiments

1. Build a held-out bilingual test set that switches inside sentences, including
   names, dates and clock numbers. Score transcription errors separately from
   event extraction and language selection; the current recognizer interface
   returns text, not calibrated language confidence.
2. Measure speech boundaries: clipped first words, pauses inside a phrase, and
   adjacent chunks. Compare bounded overlap/reconciliation using temporary PCM,
   with checks against duplicate plans and added background memory cost.
3. Extend the bilingual retry guard with measured evidence. Eligible single-language
   retries still accept a nonblank second reading; calibrated scores or independent
   acoustic agreement could reduce bad replacements.
   Do not compare uncalibrated confidence values across different models or infer
   missing numbers from what would make a plausible event.
4. Compare bilingual language restriction or a small language identifier against
   the existing unrestricted route, including an unknown-language fallback. Keep
   automatic per-segment switching; avoid sticking to yesterday’s language.
5. Evaluate near/far speech, noise, accents and dialects on held-out recordings.
   Compare latency, queue growth, memory, power and thermal behavior on Pixel 7a
   before adding a larger decoder or another retry.

Acceptance should require improved held-out transcription and time/action
accuracy without more fabricated times, queue backlog or persistent audio.
Arabic clock words and rapid code switching remain explicit limitations until
independent recordings demonstrate improvement. No new model is selected solely
because it fits the existing 34 synthetic commands.

## Primary model/runtime reference

[sherpa-onnx SenseVoice language options](https://k2-fsa.github.io/sherpa/onnx/sense-voice/pretrained.html)
include automatic mode. Availability of that option is not evidence of better
code switching; the on-device candidate comparison above determined rejection.

## 0.32 regression checks

The same 34 short automatic commands retained 31 expected actions/times; all 34
avoided a wrong action/time in this bounded set. The three unresolved Arabic
clock cases remain. Seven paced public languages emitted eight regions and
cleared the final queue in 869 ms. The bilingual measurement plus these two
checks passed in 265.356 seconds on the Pixel; they do not prove within-sentence
human code switching or general dialect accuracy. Raw command/pipeline evidence
is in evaluation/automatic-regression-032.json.
