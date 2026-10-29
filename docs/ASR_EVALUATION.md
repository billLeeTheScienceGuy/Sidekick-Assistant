# Pixel language quality evaluation — 2026-10-28

Physical Pixel 7a, two configured CPU threads, local inference. Fixtures comprise
34 synthetic event/correction phrases across English, Korean, Chinese, French,
German, Russian and Arabic, plus six public FLEURS readings. Arabic has two
synthetic voices. Test audio is public/synthetic, not the user's discarded audio.
Only isolated benchmark packages are instrumented; no physical microphone is opened.

## Public reading samples

Normalized character error removes case, punctuation and whitespace. Each cell is
one clip, not representative language accuracy. Accents, simplified/traditional
differences and number spelling differences still count.

| Language | Whisper tiny | Whisper base | SenseVoice | Omnilingual int8 |
|---|---:|---:|---:|---:|
| Korean | 15.0% | 5.0% | 13.3% | 15.0% |
| Chinese | 27.3% | 27.3% | 0.0% | 9.1% |
| French | 10.4% | 6.4% | unsupported | 0.5% |
| German | 13.3% | 15.6% | unsupported | 7.8% |
| Russian | 4.3% | 9.9% | unsupported | 7.5% |
| Arabic | 46.6% | 38.4% | unsupported | 9.6% |

Omnilingual processed these clips in 3.36–6.48 seconds; tiny took about 5.5–5.8
seconds, base 12.6–13.0 seconds. SenseVoice took 0.57s for Korean and 1.12s for
Chinese. Clip lengths differ across languages. Post-call whole-process PSS reached
about 300 MiB (tiny), 413 MiB (base), 435 MiB (SenseVoice), 846 MiB (Omnilingual).
These are snapshots, not peak/model-only memory, battery or long-session measurements.

## Commands and routing decisions

The reduced 512-frame Whisper encoder context sometimes caused repetitive output
in Korean, French, German, Russian and Arabic. Full context improved German short
creation/rescheduling from one of two to two of two with tiny. Base still lost the
German word boundary in one case. French/Russian short commands worked better with
base (four of four in each language) than tiny. Omnilingual was strong on longer
French/German readings but less reliable on short commands. Therefore:

| Selected language | Adaptive choice |
|---|---|
| English | Existing short base boost, otherwise tiny.en |
| Korean | SenseVoice, with a guarded Omnilingual retry for an invalid 12-hour clock |
| Chinese | SenseVoice |
| French | Base for short clear-queue phrases; Omnilingual for longer/backlogged speech |
| German | Full-context tiny for short clear-queue phrases; Omnilingual for longer/backlogged speech |
| Russian | Base for short clear-queue phrases; full-context tiny otherwise |
| Arabic | Omnilingual |
| Automatic | Existing multilingual Whisper detection |

French/German/Russian short-window limit is eight seconds; English retains three
seconds. The bounded queue remains two chunks. Explicit Fast/Base settings retain
their selected model. Models are released before switching backends; raw audio is
wiped after inference. No cloud fallback or network permission is added.

Arabic is not solved: the initial Omnilingual command run recovered five of eight
command meanings; two cancellation-negative outputs were safely rejected by the
grammar. Some movement verbs/hours were misrecognized and left unapplied. Arabic
grammar tests independently cover explicit times, two digit styles, diacritics,
ordinals/cardinals, source preservation, locations, duration/end, corrections and
negative/ambiguous inputs. Native/dialect speakers still need to validate real use.

Whisper small Q5 was also tested. Its short Arabic calls took about 26.4–26.7s;
the run was stopped early because that latency is unsuitable for continuous input.
Moonshine Arabic decoded synthetic commands quickly but missed the public clip.
It also carries Community License restrictions. Neither model is shipped.

SenseVoice's inverse-text-normalization toggle did not change the supported clock
meanings in the synthetic Korean clips, including two eight-pm phrases. That does
not reproduce or prove the cause of the real 18-vs-8 failure. A targeted second
acoustic read now handles an invalid meridiem/hour only when words outside that
clock, meridiem, and minutes are unchanged. After the real 0.21 retry also failed,
0.21.1 tries full-context Whisper base before Omnilingual and shows RAM-only clock
diagnostics. Only the clock span is replaced. This rare retry costs extra latency.
Valid clocks, changed names/dates,
negations, quotations and questions cannot be silently rewritten by that guard.
Real microphone validation of this retry remains required.

## Reproduction and sources

Prepare normal assets with `scripts/prepare_speech.py`, `scripts/prepare_speaker.py`,
`scripts/prepare_sensevoice.py`, and `scripts/prepare_omnilingual.py`; every weight
has a pinned SHA-256. Omnilingual adds 365,352,120 bytes plus its 86,423-byte vocabulary.
Weights are ignored by Git and prepared during CI.

Optional fixture tools: `scripts/prepare_asr_commands.py` (host Edge TTS) and
`scripts/prepare_language_fixtures.py` (FLEURS, CC BY 4.0). Run isolated
`MultilingualQualityTest` with `compareLanguages=true`; optional comparison flags
are `compareFullContext`, `compareOmni`, `compareSmall`, and `compareMoonshine`.
Experimental small/Moonshine flags require their unshipped test assets. Results
are written inside benchmark cache to `multilingual-quality.json`; do not treat
public-fixture logging as permission to log private microphone transcripts.

- [Whisper models and limits](https://github.com/openai/whisper/blob/main/model-card.md)
- [SenseVoice model deployment](https://k2-fsa.github.io/sherpa/onnx/sense-voice/pretrained.html)
- [Meta Omnilingual model card and Apache 2.0 license](https://huggingface.co/facebook/omniASR-CTC-300M)
- [Pinned ONNX export family](https://k2-fsa.github.io/sherpa/onnx/omnilingual-asr/models.html)
- [FLEURS dataset](https://huggingface.co/datasets/google/fleurs)
