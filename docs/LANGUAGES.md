# Current model option

Version 0.21 adds Arabic and evaluated Adaptive routing. Korean/Chinese use
SenseVoice; Arabic uses Omnilingual int8. French uses Whisper base for short clear-queue
phrases and Omnilingual for longer speech. German uses full-context Whisper tiny for
short phrases and Omnilingual for longer speech. Russian uses base for short phrases
and full-context tiny for longer speech. English keeps its prior short-phrase boost.
Auto retains Whisper language detection. Explicit Fast/Base choices remain available.
See [measurements and limits](ASR_EVALUATION.md) and [Arabic scope](ARABIC.md).

# Multilingual speech — 0.10.0

Settings → Speech language offers English, Automatic, Korean, Chinese, French,
German and Russian. English remains the existing default and uses tiny.en.
The other modes share one multilingual Whisper tiny model. All weights are
packaged with the APK and inference stays on the Pixel; no network permission
or cloud fallback was added. Only one speech model is cached at a time.

Automatic detection happens per bounded speech chunk. It is not guaranteed to
follow rapid code-switching or multiple languages within a phrase; selecting the
spoken language is more reliable. Transcription preserves its language rather
than translating everything to English. Language selection is disabled during
listening/finishing. UTF-8 JNI output supports Hangul, Han and Cyrillic scripts.

Version 0.10.0 added speech transcription without multilingual event grammar.
Version 0.11.0 adds basic event/date extraction in the five languages; see
MULTILINGUAL_EVENTS.md for supported phrases and tests. Full multilingual reasoning
and translated UI are not implemented. Voice matching has not been calibrated
across languages, and every event still requires review.

## Model and build

Run scripts/prepare_speech.py or scripts/prepare-speech.ps1. Both verify the new
77,691,713-byte model in addition to the existing English model and VAD. CI runs
the Python preparer. First use verifies the model again before installing its
private working copy. Original English and separately imported Qwen stay intact.

- Model: multilingual Whisper tiny, MIT.
- File: ggml-tiny.bin.
- Source: https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-tiny.bin
- SHA-256: be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21
- Pinned runtime remains whisper.cpp a8d002cfd879315632a579e73f0148d06959de36.
- https://github.com/ggml-org/whisper.cpp/tree/a8d002cfd879315632a579e73f0148d06959de36/models

## Physical Pixel evaluation (2026-10-23)

One public FLEURS validation example per added language was transcribed on-device,
with explicit language selection. These are smoke/quality samples, not representative
benchmarks. Normalized character error removes whitespace/punctuation, keeps accents
and script variants, and uses edit distance divided by reference character length.

| Language | Clip seconds | Inference ms | Normalized character error |
|---|---:|---:|---:|
| Korean | 10.56 | 6,055 | 0.1500 |
| Chinese | 18.38 | 5,542 | 0.2727 |
| French | 14.46 | 5,700 | 0.1040 |
| German | 12.96 | 5,490 | 0.1328 |
| Russian | 13.68 | 5,751 | 0.0435 |

Chinese output used Traditional characters while the reference used Simplified;
the error above counts those differences and should not be called a pure recognition
error rate. All samples contain errors. The Korean first call includes model setup;
these full-clip timings use the default encoder window, not live five-second settings.
Automatic Korean and English, switching back to the English model, and the language
setting persistence/UI check passed. The combined run took 56.461 seconds.

A paced Korean live test in Automatic mode also passed in 15.93 seconds: two
progressive chunks, Korean-script output and no queue overflow. Maximum chunk
processing latency was 6,753 ms, so this does not prove sustained real-time or
all-day performance. Language detection adds work, and the first call can be slow.
Use a selected language to avoid repeated language detection when appropriate.

58 core tests and both build/lint variants passed. No automated test captured the
physical microphone. No real multilingual conversation, code-switching accuracy,
cross-language speaker-ID accuracy or multilingual event extraction is validated.

## Reproduce public-fixture evaluation

Run python scripts/prepare_language_fixtures.py, then build the benchmark test APK.
The preparer fetches validation row zero for each FLEURS language, converts only
public float WAV fixtures to PCM16, and records references and hashes in an ignored
.deps/language-fixtures manifest. Those files enter only the test APK, never the
normal app. Dataset: Google FLEURS, CC BY 4.0:
https://huggingface.co/datasets/google/fleurs

Run MultilingualSpeechTest with instrumentation argument verifyLanguages=true.
It reports output/reference errors and exercises auto-detection plus model switching.
Ordinary test runs skip these optional fixture checks.

## Prior English short-correction trial

The user's 0.9.3 live test still did not pass: the long phrase was UNKNOWN (profile
0.75/0.66, half consistency 0.52); the short phrase then correctly had no confident
anchor. Whisper misheard the short end-time statement. Its discarded PCM cannot be
re-evaluated. Multilingual support does not resolve this existing microphone-quality
and speaker-attribution issue, and no thresholds were weakened to mask it.
