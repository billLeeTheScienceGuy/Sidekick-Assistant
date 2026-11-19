# SideKick: test history and results

*Compiled November 16, 2026 from `docs/evaluation/*.md`, `CHANGELOG.md` and the Git history. Numbers are copied from those sources; where a source is unclear, this is stated. Test IDs (T01–T64) are used only in this document.*

## 1. Introduction

SideKick is an offline Android assistant for spoken commitments. It transcribes speech on the phone (Whisper, SenseVoice, Omnilingual and other local models), extracts plans with a rule-based parser (an optional local Qwen model for typed notes existed from 0.4 until it was removed on November 16, 2026; see T68), matches the phone owner's voice so that only the owner's words can create personal plans, and applies recording-consent and voice-analysis rules by US state. There is no cloud transcription; the main app has no general network dependency for speech.

Four kinds of tests exist:

| Kind | Where it runs | What it is good for |
|---|---|---|
| Unit tests ("core tests") | Pure-Kotlin `core` module on the laptop/CI (`gradlew :core:test`) | Grammar, time/date logic, consent rules, thresholds, state machines. Deterministic. |
| Instrumented device tests | Isolated test packages (`com.sidekick.app.benchmark`, `com.sidekick.app.compacttest`) on a physical Pixel 7a or an Android 14 emulator | Real models, real Android services, Room migrations, UI. No microphone is opened. |
| Public-audio benchmarks | Pixel 7a (isolated package) or laptop | Recognition accuracy and speed on public (FLEURS, ASCEND, JFK, YouTube captions) and synthetic (TTS) audio. |
| Laptop evaluations | Host machine (ModelBench, sherpa-onnx JVM; host-only candidate models) | Broad model comparisons that are too slow to run on the phone. |

In addition, the project owner ran a small number of **real-microphone field trials** on the normal app. Their outcomes are reported below because they drove many changes; their spoken content is not reproduced.

**Data rule.** Automated tests use only public, synthetic or self-authored data and never instrument the personal app (`com.sidekick.app`); the docs repeat "Never instrument the personal com.sidekick.app package." No private recordings, transcripts, messages or voice signatures are stored as test evidence. Audio from the owner's field trials was discarded by the app and could not be replayed. Two honest exceptions to "personal app never touched" appear in the sources and are flagged where they occur: read-only inspections of the normal app's database/notifications after a field trial (0.19.2, 0.22.1, 0.15.1 Undo of an accidental duplicate), and the November 16 manual UI checks, in which a debug build was installed over the normal app on the owner's phone and exercised by hand (no instrumentation).

Unless stated otherwise, "Pixel" means the physical Pixel 7a (Tensor G2, 8 GB RAM, Android 17 / API 37), USB- or AC-powered. No test in this history measures unplugged battery drain.

---

## 2. Summary of all tests and evaluations

Dates are the date stated in the source. Where a source gives no date, the date of the commit that contains the version is given as "≤ date (commit)". CER = character error rate.

| ID | Date | Version | Area | What was tested | Data (size) | Key result | Outcome / decision |
|---|---|---|---|---|---|---|---|
| T01 | 2026-10-22 | 0.2 | Build / reminders / ASR | First validation: build, unit tests, device tests | 15 core; 7 Pixel tests; public `jfk.wav` | All passed; known speech ~5.7 s incl. setup; lint 0 errors / 11 warnings | Baseline; Espresso/runner upgraded for Android 17 |
| T02 | 2026-10-22 | pre-0.3 | Speech recognition (speed) | Whisper tiny.en throughput, sustained and per-window | JFK clip, 96 × 10 s chunks; 3/5/10 s windows | Sustained median 3.821 s per 10 s chunk (4 threads), no backlog; 512-window 10 s chunk RTF 0.164 | 0.3 uses 2 threads, 512-position window, VAD, bounded queue |
| T03 | 2026-10-22 | pre-0.3 | Speech detection | Silero v5.1.2 VAD cost | Silence/speech windows, 20 evals each | ~9.8–10.0 ms per 1 s window; ~95.7 ms per 10 s; no false frames on digital silence | Adopted as speech gate |
| T04 | ≤ 2026-10-22 (3079404) | 0.3 | Capture pipeline | Live VAD + Whisper pipeline, approval-only storage | 6 Pixel tests; public audio | Passed | Continuous live notes shipped |
| T05 | ≤ 2026-10-22 (3079404) | 0.4 | Plan extraction | Optional local Qwen3-0.6B extraction | 3 Pixel tests (52.295 s) | Passed; quoted/conditional fixtures rejected | Qwen kept optional; every suggestion reviewed |
| T06 | ≤ 2026-10-23 (69160c1) | 0.5 | Planner / reminders | Planner UI and reminder ordering | 33 core; 3 Pixel tests (4.225 s) | Passed | Planner tab shipped |
| T07 | ≤ 2026-10-23 | 0.6 | Calendar | Calendar overlap reading | 36 core; synthetic Calendar Provider | Passed after a SQLite query fix | Read-only calendar check shipped |
| T08 | ≤ 2026-10-23 | 0.7 | Plan extraction (timing) | End-time inference, unknown durations | 44 core; 6 Pixel (9.901 s) | Passed; lint 0 / 12 | Shipped |
| T09 | ≤ 2026-10-23 | 0.8 | Places / messages | Message ingestion, venue/address updates | 48 core; 12 Pixel tests | Passed after a test-teardown fix | Shipped |
| T10 | ≤ 2026-10-23 | 0.8 | Location lookup | Notification listener, geocoding, provider fallback | Android 14 emulator, 14 tests (72.023 s) | Passed; fallback bug found and fixed | Lookup checks both fused and network fixes |
| T11 | 2026-10-23 | 0.8 | Location lookup | Real approximate location on Pixel | 1 opt-in test (1.703 s) | Fix 200 s old, 2,000 m accuracy; 1 candidate | Coarse location adequate for lookup; precise GPS not verified |
| T12 | ≤ 2026-10-23 | 0.8.1 | Location / readiness | Settings readiness, venue change clears address | 51 core; 4 Pixel (5.646 s); location check 2.408 s | Passed | Shipped |
| T13 | ≤ 2026-10-23 | 0.8.2 | Capture reliability | Continuous listening with silence; screen-off | Pixel bursts (118.623 s); emulator 6 min (367.416 s) | No transcription during silence; 0 chunks screen-off | VAD gating confirmed |
| T14 | 2026-10-23 | 0.8.x–0.9 | Plan extraction | Multi-turn correction replay (playback evaluation) | 5 ActivityTimingTest checks (0.223 s) | Passed; spoken version never scored | Speaker-ID direction agreed |
| T15 | ≤ 2026-10-23 | 0.9.0 | Speaker matching | First voice enrollment and matching | 54 core; 8 Pixel (3.287 s); paced check 16.1 s | Passed; 3 of 4 fixture chunks left unassigned | Conservative thresholds; uncertain → information-only |
| T16 | ≤ 2026-10-23 | 0.9.3 | Speaker matching | Short corrections vs recent long phrase | 57 core; 3 Pixel (3.104 s) | Passed (fixture overlaps enrollment) | Integration evidence only |
| T17 | ≤ 2026-10-23 | 0.10.0 | Speech recognition (languages) | Five new languages + Automatic | 58 core; 1 FLEURS clip per language | Passed (56.461 s); paced Korean max 6,753 ms | Multilingual Whisper tiny bundled |
| T18 | ≤ 2026-10-23 | 0.11.0 | Plan extraction (languages) | Event rules in 5 languages | 65 core; 6 Pixel (0.281 s) | Passed | Basic multilingual grammar shipped |
| T19 | 2026-10-23 | 0.12.0 | Speech recognition | Whisper tiny vs base, paced pipeline | 1 FLEURS clip × 5 languages; JFK 44 s | Base better on 2, tied 1, worse 2; tiny paced: 9 chunks, 7 user matches | Tiny stays default; base optional |
| T20 | 2026-10-23 | 0.12.1 | Plan extraction (duplicates) | Duplicate prevention after a field trial | 7 + 11 Pixel checks (2.487 s) | Passed; field trial times/venue right, speaker unassigned, repeated cards | Strict duplicate matching added |
| T21 | 2026-10-23 | 0.13.0 | Plan extraction (automation) | Hands-off automatic saving with Undo | 29 Pixel (7.684 s) | Passed after harness fixes | Hands-off mode shipped |
| T22 | 2026-10-24 | 0.14–0.15.0 | Event changes / recovery | Reschedule/cancel; reboot recovery | 75 core; 29 emulator (6.365 s); 8 Pixel (1.124 s); one reboot | Passed; service resumed after reboot and update | Automatic rescheduling/cancel shipped |
| T23 | 2026-10-24 | 0.15.1 | Speech recognition (short English) | Short-command errors after a field trial | 8 synthetic clips; 78 core; 21 Pixel (81.285 s) | Tiny wrong on cancel for both voices; Adaptive 8/8 exact | Adaptive (base for < 3 s English) made default |
| T24 | 2026-10-27 | 0.15.2 | Speaker matching | Trimming silence before matching | 82 core; 3 Pixel (11.144 s) | Passed; field trial event correct, 3 chunks unassigned | Speaker-only edge trimming |
| T25 | 2026-10-27 | 0.16.0 | Plan extraction / speaker | Announcement + attendance linking, Qwen fallback | 86 core; 32 Pixel (24.725 s) then 24 (5.211 s) | Passed; field trial 6 USER / 1 unassigned | Known plans bypass Qwen; duplicates archived with Undo |
| T26 | 2026-10-27 | 0.16.1 | Speaker matching | Short phrases using recent long-turn references | 90 core; 3 Pixel (20.83 s) | 3 of 5 own clips matched; 0 USER from 8 others | Two-turn reference memory, 60 s expiry |
| T27 | 2026-10-27 | 0.17.0 | Speaker matching (model choice) | WeSpeaker vs CAM++ English vs CAM++ zh/en advanced | 13 embeddings (synthetic + public) | CAM++ advanced: same .651–.852, different max .234 | CAM++ advanced default for new installs |
| T28 | 2026-10-27 | 0.18.0 | Event changes (languages) | Reschedule/cancel in 6 languages | 98 core; 19 + 11 + 3 Pixel checks | Passed; English field trial with CAM++ passed | Multilingual changes shipped |
| T29 | 2026-10-28 | 0.19.0–0.19.2 | Speech recognition (Korean) | Korean ASR after a failed field trial | 8 synthetic Korean commands; 1 public clip | SenseVoice 8/8 in 139–218 ms; field trials: fail, fail, pass (0.19.2) | SenseVoice for Korean in Adaptive |
| T30 | 2026-10-28 | 0.20.0 | Speaker matching (languages) | Per-language voice profiles | 103 core; 22 Pixel (11.608 s) | Passed; Korean field trial 3 USER, time misread | Separate signature per language |
| T31 | 2026-10-28 | 0.21.0 | Speech recognition (7 languages) | Whisper tiny/base, SenseVoice, Omnilingual | 34 synthetic phrases + 6 FLEURS clips | Arabic CER 46.6% / 38.4% / 9.6% (tiny/base/Omni) | Per-language Adaptive routing; Arabic added |
| T32 | 2026-10-28 | 0.21.1 | Language routing (Korean clock) | Invalid-clock retry | 2 Pixel tests (70.854 s); 111 core | Synthetic 8 pm fixtures pass; field trial still failed | Retry order: Whisper base, then Omnilingual |
| T33 | 2026-10-28 | 0.21.2 | Language routing (Korean clock) | Mixed-script clock detection | 112 core | Passed; field report 1 success, 1 failure | Limit documented, not declared resolved |
| T34 | ≤ 2026-10-29 (9b9d650) | 0.22.0 | Reminders | Undo, scheduling-failure rollback | 112 core; 33 + 3 Pixel (30.467 s, 6.199 s) | Passed | Reliability features shipped |
| T35 | ≤ 2026-10-29 | 0.22.1 | Reminders | Perceived missed reminder | Read-only inspection; 5 Pixel (6.517 s) | Delivered 14.037 s late; phone silent, channel muted | Setup status separates delivery from sound settings |
| T36 | ≤ 2026-10-29 | 0.23 | Plan extraction (context) | Split date/time in 7 languages | 121 core; 46 Pixel tests | Passed after fixes | Conversation context shipped |
| T37 | ≤ 2026-10-29 | 0.23.1 | Plan extraction / UI | Broader self-test pass | 122 core; 41 Pixel tests | 40/41, then rerun passed | Stale fixtures fixed |
| T38 | ≤ 2026-11-04 (5651339) | 0.24 | Plan extraction (dates) | English dates; emulator-only run | 140 core; 80 emulator checks | Passed; Pixel unavailable | Ambiguous "next weekday" gets no auto reminder |
| T39 | ≤ 2026-11-08 (e92773c) | 0.25 | Plan extraction / summaries | Summaries, attendance, delayed corrections | 147 core; 82 emulator (131.312 s) | Real bug found (wall-time anchors) and fixed | Capture timestamps used for context |
| T40 | ≤ 2026-11-08 (d7186b4) | 0.26 | Plan extraction / packaging | 92-case text corpus; model packs | 153 core; 83 emulator; 2 compact | 10 missed reminders; APK ~225 MB vs 905 MB | Compact APK + verified packs |
| T41 | ≤ 2026-11-08 (31a2144) | 0.26.1 | Plan extraction | Missed-reminder fixes | 157 core; 7 emulator (12.88 s) | Missed reminders 10 → 2; recall 97.6% | Shipped |
| T42 | ≤ 2026-11-08 (3a2b405) | 0.27 | Privacy / plan extraction | Retention, export privacy, risk advisories | 163 core; 95 emulator; 4 Pixel; 15 compact | Recall 98.8%, 1 missed; notification bug fixed | Retention/export shipped |
| T43 | 2026-11-08 (capture) | 0.27 | Plan extraction | Text-understanding accuracy report | 92 self-authored cases, 7 languages | Precision 100.0%, recall 98.8%; 1 Arabic miss | Published as curated, not real-world |
| T44 | 2026-11-08 / 11-09 | 0.28 | Speaker turns / calendar / privacy | Integrated speaker turns, calendar mirror, region privacy | 175 core; 116 emulator; 8 Pixel; 27 compact | All passed; CI passed | 0.28 released |
| T45 | 2026-11-08 | 0.28 | Endurance / speaker | 10-minute sustained speaker-turn trial | 600 s public + synthetic audio | 180 segments; 0 of 60 party segments as owner; work ratio 0.442 | Bounded pipeline confirmed for 10 min |
| T46 | 2026-11-09 | 0.29 | Speech recognition / speaker | Public-speech robustness (clean vs 10 dB noise) | 28 FLEURS clips × 2; 87 speaker trials; 112 raw calls | Noisy ru 41.2% Adaptive vs 12.5% Omni; 0 unfamiliar regions as owner | Omnilingual for Automatic, Arabic, long fr/de/ru |
| T47 | 2026-11-09 | 0.29 | Capture reliability | Silence, mute, process kill, permission revoke | No-audio emulator | Recovery after 2.074 s; revoke stops after 2.757 s | Explicit stall/interruption handling |
| T48 | 2026-11-09 | 0.29 | Regression / CI | Full regression after robustness work | 186 core; 117 emulator; 27 compact; 9 Pixel | All passed after harness fix | 0.29 released |
| T49 | 2026-11-13 | 0.30 | Interface | Adaptive layout matrix and UI tests | 8 Pixel UI; 45 emulator; 6 layout profiles | All passed after a font-scaling fix | Simple/Detailed home, size controls |
| T50 | 2026-11-13 | 0.30–0.31 | Interface | Desk review of product docs and wording | 8 + 3 public references | Not a usability study | Layout and plain-language wording |
| T51 | 2026-11-13 | 0.31 | Interface / ASR recheck | Settings categories; recorded ASR recheck | 22 Pixel UI (42.671 s); 34 commands | UI passed; 31/34 commands | Six settings categories |
| T52 | 2026-11-13 | 0.32 | Language routing (bilingual) | Bilingual retry guard; SenseVoice auto candidate | 12 synthetic bilingual clips | Venue kept 4/12 → 7/12; CER 20.2% → 17.4% | Guard shipped; SenseVoice auto rejected |
| T53 | 2026-11-13 | 0.33 | Language routing / capture | Paired 41-condition recognition and capture review | 6 ASCEND + 7 FLEURS clips; 13 recordings; 8 controls | CER 9.39% → 8.21%; quiet detection 12/13 → 13/13 | Onset/pause, gain-for-detection, conflict holding |
| T54 | ≤ 2026-11-14 (d1fa280) | 0.33.1 | Language routing / speaker | Korean–English field trial; second-reading experiment | 1 field trial; 12 synthetic clips | Field trial failed; experiment 7/12, decode 46.2 → 60.6 s | Experiment removed; profile routing changed |
| T55 | ≤ 2026-11-14 | 0.34.0 | Plan extraction / speaker | Field trial on 0.33.1; parser and enrollment fixes | 1 field trial | Event saved but correction not applied; 4 chunks unassigned | `change … to …` parsing, stricter enrollment |
| T56 | 2026-11-14 (results) | 0.35.0 | Speech recognition (model choice) | ModelBench: 16 models on the laptop | 1,377 distinct clips; 11,281 scored results | English: Parakeet 110M 0.5% clean / 2.0% noisy | Auto-detect routing rebuilt in 0.35.0 |
| T57 | 2026-11-14 | 0.35.0 | Speaker matching | Device-health run (cited only) | 30 other + 8 owner clips | Others accepted 0/30; owner accepted 0/8 | Owner acceptance flagged as unmeasured |
| T58 | 2026-11-16 | 0.35 + branch | Privacy / consent / location | Privacy work package: build, unit, device, manual | 244 core; 4 + 18 device tests; manual UI | All passed after one bug fix | Two-rule state model, notice records, voice consent |
| T59 | 2026-11-16 | 0.35 + branch | Endurance / speaker | 60-minute sustained trial | 3,600 s public + synthetic audio | 1,079 segments; 0 of 360 as owner; work ratio 0.428 | Supports "60 minutes" claim with stated limits |
| T60 | 2026-11-16 | branch | Privacy / capture | Owner's phone follow-up (notice, name suggestion) | Device logs, notice records | Two real bugs; fixed; 246 core; 7/7 device | Mic muted during notice; name suggestion fixed |
| T61 | 2026-11-16 | branch | Privacy / consent | Notice lifetime | 250 core | Passed | Notice expires after 30 min gap or 2 h |
| T62 | 2026-11-16 | branch | Privacy / consent | Notice delivery: volume, loudness, proximity | Pixel calibration; 253 core; 9/9 device | Table 0.0485/0.0464; covered 0.0768 | Loudness threshold 0.01; proximity check added |
| T63 | (not run) | — | Speaker matching | Threshold tuning with consenting participants | Planned: owner + ≥ 30 people | No data | Thresholds unchanged |
| T64 | 2026-11-16 | branch | Speech recognition | Repetition-loop filter (CHANGELOG only) | ~20,000 human transcripts; saved recognizer output | No change to human transcripts; 34 loops caught | RepeatFilter + one Whisper re-decode |

**Count: 64 entries (63 tests or evaluations that produced results, plus one planned evaluation, T63, that has not been run).**

---

## 3. Results by area

### 3.1 Speech recognition: accuracy, speed and model choice

**T02 — Whisper throughput benchmark (2026-10-22)**
- *Purpose:* find chunk length, thread count and encoder window that keep up with real time on the Pixel.
- *Method:* the public JFK clip fed directly to whisper.cpp v1.7.6 tiny.en in an isolated benchmark package; no microphone. Sustained run: one 10 s chunk every 10 s for 96 chunks.
- *Results:* sustained run completed 96 chunks in 964.03 s; median 3.821 s, p95 3.908 s, max 4.011 s per chunk (four threads, full window); max scheduling lateness 22 ms; max PSS 291.8 MiB; thermal status 0; battery 24.0 → 28.4 °C on USB. With a 512-position window and two threads: RTF 0.510 (3 s), 0.278 (5 s), 0.164 (10 s); the 5 s output changed "ask not" to "asked not". With the full window, 3 s chunks did not keep up even with four threads.
- *Not verified:* microphone accuracy, energy use, end-to-end pipeline.
- *What it made us do:* version 0.3 used two threads, the 512-position window, Silero VAD, boundary overlap and a bounded queue (STREAMING_BENCHMARK "Design implications").

**T19 — Whisper tiny vs base (0.12.0, 2026-10-23)**
- *Purpose:* decide whether the larger Whisper base model should replace tiny.
- *Method:* one public FLEURS clip per language (Korean, Chinese, French, German, Russian), two threads, full window, Pixel.
- *Results:* CER tiny/base — Korean 15.0%/5.0%, Chinese 27.3%/27.3%, French 10.4%/6.4%, German 13.3%/15.6%, Russian 4.3%/9.9%. Base took 12.5–13.4 s per clip vs 5.5–6.0 s for tiny; PSS about 402–407 MiB vs 288–293 MiB. Paced JFK pipeline: tiny 9 chunks, 7 user matches, no overflow, max combined chunk 7,062 ms; base 3 chunks, 1 user match, max 15,364 ms. One clip per language is a sample, not a language accuracy rate.
- *What it made us do:* tiny stayed the default; base became an optional "Larger model" (CHANGELOG 0.12.0).

**T23 — Short English commands (0.15.1, 2026-10-24)**
- *Purpose:* a field trial showed a short cancel command misrecognized by tiny, so the cancellation was not applied.
- *Method:* the error reproduced with two synthetic voices on the Pixel; tried beam-3, full encoder window, silence padding and base. 8 short fixtures.
- *Results:* beam-3, full window and padding did not fix tiny (full window cost about 5 s per clip). Base decoded both cancellation voices correctly and kept "Do not cancel it". The Adaptive path gave exact normalized text on all eight fixtures in 3.169–3.610 s per clip. 78 core tests; 21 Pixel checks passed in 81.285 s.
- *What it made us do:* "Adaptive" became the default: base for isolated English phrases under 3 s, tiny otherwise (CHANGELOG 0.15.1). Copied live notes keep voice provenance so reanalysis does not create a duplicate.

**T29 — Korean recognition (0.19.x, 2026-10-28)**
- *Purpose:* a Korean field trial on 0.18 produced garbled create/move/cancel phrases.
- *Method:* eight synthetic Korean commands (two voices) and one 10.56 s public Korean clip on the Pixel; Whisper tiny, base and SenseVoice Small int8.
- *Results:* SenseVoice matched all eight command references, 139–218 ms each; tiny took 1.35–4.10 s and base 3.24–3.80 s, with command errors. On the public clip base was best (5.0% CER) vs SenseVoice 13.3% and tiny 15.0%. SenseVoice adds about 239 MB; post-decode PSS about 403–428 MB. Three `KoreanRecognitionTest` checks passed in 47.718 s. Field follow-ups: the first 0.19 trial did not pass (cancel phrase garbled; older entries made corrections ambiguous); 0.19.1 failed again (an extra space inside a keyword); 0.19.2 passed — read-only database inspection confirmed the move and cancellation, but the speaker stayed UNKNOWN and the screen wrongly showed "Event change unclear".
- *What it made us do:* Adaptive Korean uses SenseVoice (CHANGELOG 0.19); 0.19.1 added recent-event scoping and particle normalization; 0.19.2 tolerated spaces inside keywords. The inconsistent status message is recorded as needing correction; the sources do not say it was fixed.

**T31 — Seven-language model comparison (0.21.0, 2026-10-28)**
- *Purpose:* add Arabic and choose a model per language.
- *Method:* 34 synthetic event/correction phrases across seven languages (Arabic with two voices) plus six public FLEURS readings; Pixel, two threads.
- *Results (one clip each, CER):*

| Language | Whisper tiny | Whisper base | SenseVoice | Omnilingual int8 |
|---|---:|---:|---:|---:|
| Korean | 15.0% | 5.0% | 13.3% | 15.0% |
| Chinese | 27.3% | 27.3% | 0.0% | 9.1% |
| French | 10.4% | 6.4% | unsupported | 0.5% |
| German | 13.3% | 15.6% | unsupported | 7.8% |
| Russian | 4.3% | 9.9% | unsupported | 7.5% |
| Arabic | 46.6% | 38.4% | unsupported | 9.6% |

  Omnilingual took 3.36–6.48 s per clip, PSS about 846 MiB. Omnilingual's first Arabic command run recovered five of eight command meanings. Whisper small Q5 took about 26.4–26.7 s per short Arabic call and was stopped; Moonshine Arabic missed the public clip and has license restrictions. 111 core tests; 24 Pixel checks.
- *What it made us do:* per-language Adaptive routing (table in ASR_EVALUATION.md): SenseVoice for Korean/Chinese, Omnilingual for Arabic and longer French/German speech, base/tiny for short commands. Whisper small and Moonshine not shipped.

**T46 — Public-speech robustness (0.29, 2026-11-09)**
- *Purpose:* measure clean vs noisy accuracy and choose a final routing before release.
- *Method:* 28 public FLEURS validation readings (four per language), each clean and with 10 dB white noise; selection and hashes frozen before inference. Validation split used because the test split could not be served. Pixel, no microphone.
- *Results (CER / processing-to-audio ratio):* Adaptive noisy CER was high for Russian (41.2%), German (28.9%) and French (23.9%); the Omnilingual candidate gave 12.5%, 13.8% and 14.5%. Final route: en 0.0%/13.9%, ko 6.3%/9.0%, zh 8.9%/8.9%, fr 6.8%/14.5%, de 7.5%/13.8%, ru 5.5%/12.5%, ar 3.2%/6.4% (clean/noisy). In 112 raw calls without a forced language, Omnilingual beat Whisper tiny in every language (e.g. ru noisy 12.5% vs 62.6%). Short Automatic commands: 31/34 correct with the final hybrid route vs 22/34 Omni-only; all 34 rejected wrong actions/times; three Arabic clock commands remain wrong. A host-only 458 MB NVIDIA Arabic FastConformer was also tried and still malformed clock words.
- *Limits:* same clips used for tuning and final check (not a holdout); uneven gender coverage; noise is synthetic.
- *What it made us do:* Omnilingual for Automatic and Arabic; French/German/Russian switch to Omnilingual at 5 s of detected speech (CHANGELOG 0.29). Arabic FastConformer rejected.

**T56 — ModelBench laptop comparison (results 2026-11-14; summary generated 2026-11-16)**
- *Purpose:* compare open models broadly before rebuilding Auto-detect.
- *Method:* laptop, sherpa-onnx JVM; 16 models (15 recognizers + a Whisper-tiny language identifier); 1,377 distinct clips (ar 250, de 161, en 204, fr 184, ko 198, ru 199, zh 181) across 8 sets; 12,126 scored model-clip results, 11,281 after a natural-caption filter.
- *Results (English CER):* Parakeet TDT 110M 0.5% (39 clean clips), 2.0% (117 noisy/far clips), 13.3% (37 natural passages), 35.1% (3 app WAV fixtures); SenseVoice 2024 2.1%, 3.2%, 15.1%, 0.0%. Best noisy English was qwen3 at 1.2%. Long natural passages are much harder for all models (12.6%–28.9%).
- *Ambiguity:* the source does not explain the 35.1% result for Parakeet on the three app WAV fixtures; with only three clips it should not be generalized.
- *What it made us do:* 0.35.0 rebuilt Auto-detect: SenseVoice tags the language, Parakeet re-reads English (two readings must agree), Omnilingual v2 for other languages, GigaAM re-reads Russian (CHANGELOG 0.35.0). Presentation wording fixed to "0.5% of letters wrong in quiet, 2% noisy or far", not to be quoted for long passages.

**T64 — Repetition-loop filter (2026-11-16; CHANGELOG only)**
- *Result as stated:* the filter changed none of about 20,000 human transcripts and caught 34 real loops in saved recognizer output. *Decision:* keep one copy of a repeated phrase; Whisper re-decodes a looping reading once with a little randomness. *Ambiguity:* there is no evaluation document for this; the data source of the "saved recognizer output" is not described.

### 3.2 Language routing, short commands and bilingual speech

**T17 — First multilingual transcription (0.10.0, ≤ 2026-10-23).** One public FLEURS clip each in Korean, Chinese, French, German and Russian, plus Automatic Korean/English, on the Pixel (56.461 s); paced Korean test 15.93 s, 2 chunks, max 6,753 ms, no overflow. 58 core tests. Event extraction and cross-language speaker accuracy were not tested. *Decision:* bundle multilingual Whisper tiny; English keeps tiny.en.

**T32 / T33 — Korean clock repair (0.21.1, 0.21.2, 2026-10-28)**
- *Purpose:* a Korean field trial showed an impossible clock (afternoon + 18 o'clock) instead of 8 pm.
- *Method:* two synthetic 8 pm phrases through the real full-context base model, plus the captured invalid text (not the audio, which was discarded).
- *Results:* both fixtures produced 20:00 with unchanged context; 2 device tests passed in 70.854 s; 111 core tests. The next field trial still failed (0.21.1); a later report gave one success and one failure (0.21.2). An earlier test showed SenseVoice's text-normalization toggle did not change clock meanings on synthetic clips, so the root cause was not reproduced.
- *What it made us do:* retry order full-context Whisper base, then Omnilingual, replacing only the clock span; RAM-only diagnostics. 0.21.2 covered mixed-script teens. The sources state the limit is "documented rather than declared resolved".

**T51 — Recorded recheck (0.31, 2026-11-13).** 31/34 synthetic commands kept the expected action/time; all 34 avoided a wrong action/time; three Arabic clock phrases unresolved; seven paced public languages emitted eight regions, final queue drained in 964 ms. No microphone.

**T52 — Bilingual retry guard (0.32, 2026-11-13)**
- *Purpose:* short mixed-language speech was being forced into one language on retry.
- *Method:* 12 synthetic clips joining an English venue phrase with Korean, Chinese, French, German, Russian or Arabic commands (both orders), frozen before running old and new engine on the same Pixel.
- *Results:* exact English venue kept 4/12 → 7/12; CER 20.2% → 17.4%; summed decode time 67.101 s → 36.715 s. French/Russian/Arabic cases still fail. SenseVoice automatic mode on the four Korean/Chinese clips: 32.1% vs 23.8% for the existing route. Command set 31/34; paced queue drained in 869 ms; 187 core tests.
- *What it made us do:* the guard shipped (CHANGELOG 0.32); SenseVoice automatic mode rejected.

**T53 — Paired recognition and capture review (0.33, 2026-11-13)**
- *Purpose:* measure onset/pause, quiet-speech and short CJK fallback changes against 0.32.
- *Method:* 41 conditions on fixed files, same logic before/after: 6 natural Chinese–English ASCEND clips (one published speaker; used to guide the fallback, so development evidence) and 7 FLEURS clips in clean, quiet (−16 dB), white noise (10 dB), simulated distance and overlap (−10 dB) versions.

| Condition | Cases | 0.32 CER | 0.33 CER |
|---|---:|---:|---:|
| Natural Chinese–English | 6 | 78.90% | 67.89% |
| Public monolingual clean | 7 | 1.99% | 0.85% |
| Quiet −16 dB | 7 | 1.99% | 1.14% |
| White noise 10 dB | 7 | 7.98% | 7.98% |
| Simulated distance | 7 | 4.56% | 3.70% |
| Overlapping speech −10 dB | 7 | 8.83% | 8.83% |
| All conditions | 41 | 9.39% | 8.21% |

- *Other results:* command set still 31/34; synthetic bilingual venue retention 7/12 (unchanged); 195 core tests; 7 Pixel checks in 434.478 s. Capture results are in 3.3.
- *What it made us do:* 0.33 kept 512 ms of onset audio, 1.28 s of silence before ending a phrase, detection-only gain, and held conflicting readings as temporary text that cannot change plans (CHANGELOG, "0.33").

**T54 — Korean–English field trial and second-reading experiment (0.33.1; undated, ≤ 2026-11-14)**
- *Results:* the real Korean–English microphone trial on 0.33.0 did not pass: all five voice chunks unassigned, several words and the time correction garbled, expected event not saved. "Synthetic/public fixture results did not predict this real conversation result." An automatic second reading for longer CJK/English phrases left the 12 bilingual clips at 7/12 and 17.44% CER while decode time rose from 46.2 to 60.6 s. 196 core tests; five Pixel tests passed.
- *What it made us do:* the second reading was removed; routing stayed as in 0.33.0. Auto-detect speaker matching now checks all enrolled language profiles (one turn must match one profile).

### 3.3 Speech detection, capture reliability and endurance

**T03 — Silero VAD cost (2026-10-22).** One CPU thread, 20 evaluations per case: 1 s windows 9.79 ms (silence) / 9.99 ms (speech); 10 s windows 95.70 / 95.62 ms; no frames over the 0.5 threshold on digital silence. Not a noise-rejection test. *Decision:* use Silero as the speech gate.

**T04 — Live pipeline (0.3).** Two live-pipeline tests (real paced VAD/Whisper plus silence rejection, approval-only storage), two foundation/UI tests and two reminder tests passed on the Pixel without the microphone. (STREAMING_BENCHMARK also records 22 core tests and four device regressions at this stage; the exact version is not stated.)

**T13 — Continuous listening (0.8.2).** Pixel: three paced bursts; transcription counts 4/8/12 and no new calls during each following 15 s of silence (118.623 s). Emulator with real AudioRecord and host microphone off: 6 minutes screen-off, zero transcription chunks, capture continued across the wake-lock renewal (367.416 s). Not measured: room-noise VAD errors, battery, all-day reliability.

**T22 (recovery part) — Reboot recovery (0.14–0.15.0, 2026-10-24).** Emulator recovery was verified in 0.14 (CHANGELOG). On the Pixel, after the owner started listening and rebooted, adb confirmed the foreground service resumed and AudioRecord was active and not silenced; it also resumed after a data-preserving update to 0.15.0. This verifies recovery, not recognition quality.

**T45 — 10-minute sustained trial (2026-11-08)**
- *Method:* isolated compacttest app, Pixel, USB; public JFK voice enrolled from three windows, alternating with synthetic party speech, paced in real time; no microphone.
- *Results:* 600 s audio, wall time 686.709 s, 180 segments, processing work 265.306 s (ratio 0.442), final drain 1.581 s, 0 of 60 party segments labelled owner, max PSS 399.3 MiB, thermal 0, battery 24.5 → 25.7 °C.
- *What it made us do:* supported the 0.28 release statement that a controlled trial passed without overflow or owner matches; explicitly "not a battery/field study".

**T47 — Silent capture and interruption recovery (0.29, 2026-11-09)**
- *Method:* no-host-audio emulator via `scripts/test_capture_recovery.ps1`.
- *Results:* 6 minutes screen-off with no ASR chunks and no false stall; global mute stopped capture with a generic private notice (8.863 s check); own-process kill recovered the session after 2.074 s; permission revocation stopped and disarmed it after 2.757 s; re-granting did not restart it.
- *What it made us do:* explicit stall and interruption handling: digital silence is valid, but 5 s without PCM reads or a native error stops capture (CHANGELOG 0.29). Not a guarantee across device vendors.

**T53 (capture part) — 0.33 capture review (2026-11-13).** All 13 clean recordings detected in both versions; reduced-volume detection 12/13 → 13/13; eight silence/white-noise/click controls produced zero speech segments; original PCM preserved exactly and buffers wiped; the twenty-minute bounded-chunk check still passed; paced seven-language replay drained in 1307 ms. Music and all environmental sounds are not covered.

**T59 — 60-minute sustained trial (2026-11-16)**
- *Method:* same as T45, 3,600 s, AC power, built from the privacy branch.
- *Results:* completed; wall time 4,124.4 s; 1,079 segments; processing work 1,540.2 s (ratio 0.428); final drain 1.592 s; 0 of 360 party segments labelled owner; PSS 343,852 → 403,375 KiB (max 410,359 KiB, 400.7 MiB); thermal 0; battery 23.8 → 26.4 °C. The laptop's adb connection dropped before the end (exit code 255); the result file written by the test on the phone was read back afterwards.
- *What it made us do:* the "60 minutes nonstop" claim is supported with wording that memory rose over the hour, "so a longer run is needed before claiming it is flat". Next run must keep the laptop awake.

**T60 (capture part) — SideKick transcribed its own notice (2026-11-16).** Found from the owner's phone logs: the spoken notice was kept as an "other" voice. Fix: microphone input replaced by silence during the notice plus 0.7 s. Verified on the phone: notice played, 0 speech chunks.

### 3.4 Speaker matching

**T15 — First speaker identification (0.9.0).** WeSpeaker ResNet34-LM via sherpa-onnx. 54 core tests; eight Pixel checks passed in 3.287 s (enrollment lifecycle, attribution isolation, event updates); combined paced Silero/speaker/Whisper check 16.1 s, no overflow. Three of four fixture chunks were left unassigned. *Decision:* only confident owner matches create personal plans; other or uncertain voices give information-only suggestions (CHANGELOG 0.9.0, PLAYBACK_EVALUATION "Agreed speaker-ID direction").

**T16 — Short corrections (0.9.3).** Real-model 2.1 s match against a recent long-phrase signature, expiry/interruption rejection; 3 Pixel checks in 3.104 s. The positive fixture overlaps enrollment, so this is integration evidence, not held-out accuracy.

**T20 / T24 / T25 / T26 — Field trials and short-phrase rules (0.12.1–0.16.1, Oct 23–27)**
- *Field results:* 0.12.1 — times and venue correct, speaker unassigned, repeated cards. 0.15.1 trial (reported in 0.15.2) — event changes correct, three chunks unassigned; first-chunk scores .74/.63, consistency .59. 0.16.0 — after re-enrollment, 6 chunks USER and 1 unassigned; attendance saved as personal with a reminder. 0.16.1 lecture trial — event correctly timed but informational: short profile .83 / recent mean .77 failed identity.
- *Fixture results:* 0.15.2: 82 core tests (four edge-selection checks); 3 Pixel checks in 11.144 s. 0.16.1: 90 core tests; 3 Pixel checks in 20.83 s; of five own-speaker clips at a 39 s gap, three matched and two (trimmed below 1.5 s) correctly stayed UNKNOWN; eight different-speaker fixtures produced no USER.
- *What it made us do:* 0.15.2 trims quiet padding before speaker matching only; 0.16.1 keeps references from up to two accepted long turns, extends reference expiry from 30 s to 60 s, and still requires profile ≥ .80 for short phrases. Thresholds were not lowered.

**T27 — Speaker model comparison (0.17.0, 2026-10-27)**
- *Method:* three models on the Pixel, same runtime; two synthetic voices, a public JFK pair and five public different-speaker clips (13 embeddings).

| Model | Same-speaker range | Different-speaker max | 13 embeddings |
|---|---|---|---|
| WeSpeaker | .776–.885 | .596 | 6.557 s |
| CAM++ English | .264–.593 | .782 | 2.030 s |
| CAM++ Chinese/English advanced | .651–.852 | .234 | 2.027 s |

- *Results:* 92 core tests; comparison 12.871 s; CAM++ integration 5.593 s including a held-out phrase (.61/.62) and eight different-speaker rejections. A first test failure was a test error (zero float tolerance), corrected to 1e-6.
- *What it made us do:* CAM++ English excluded; CAM++ advanced (28.3 MB) offered and made the default for fresh installs, with its own .60 cutoffs (CHANGELOG 0.17). A real CAM++ English field trial (0.18) then saved a personal event with a reminder — "one successful real example".

**T30 — Per-language voice profiles (0.20.0, 2026-10-28)**
- *Results:* the 0.19.2 Korean flow passed but attribution did not (a 2.3 s phrase had no confident reference; a 3.6 s phrase scored 0.34/0.43, consistency 0.47). After Korean enrollment, the next trial counted 3 USER, 0 OTHER, 0 UNKNOWN (window scores .71–.79), but the time was misrecognized, so no plan was saved — an attribution pass only. 103 core tests; 22 Pixel tests in 11.608 s.
- *What it made us do:* separate voice signatures per selected language (CHANGELOG 0.20); thresholds not relaxed.

**T46 (speaker part) — Public speaker trials (0.29, 2026-11-09).** One public voice enrolled from three windows; 28 unfamiliar FLEURS clips replayed clean, noisy and mixed at equal energy with the owner voice.

| Condition | Audio trials | Emitted regions | USER regions |
|---|---:|---:|---:|
| clean-owner | 1 | 1 | 1 |
| noisy-owner-10dB | 1 | 1 | 0 |
| quiet-owner | 1 | 1 | 1 |
| simultaneous-equal-energy | 28 | 64 | 0 |
| unfamiliar-clean | 28 | 29 | 0 |
| unfamiliar-noise-10dB | 28 | 28 | 0 |

Zero unfamiliar or overlap regions were assigned to the owner; noisy owner recall is weak. Owner controls are excerpts of the same recording, not independent recall. (SPEAKER_TUNING summarizes the same public trials as "28 other-person recordings, 121 tries, 0 accepted (53 OTHER, 68 UNKNOWN); owner 2 of 3 matched".)

**T54 / T55 — Owner attribution in bilingual field trials (0.33.0, 0.33.1).** All five chunks (0.33.0 trial) and all four chunks (0.33.1 trial) were unassigned, despite checking both enrolled profiles. *What it made us do:* 0.34 voice setup requires at least four seconds of trimmed speech per sample and validates matching windows; rejected samples get retry guidance (CHANGELOG 0.34). The 0.34 document says actual owner matching "requires a new user-initiated setup and microphone trial"; no result is recorded.

**T57 — Device-health run (2026-11-14; cited, report not in `docs/evaluation`).** `othersAccepted=0` of 30 other clips and `ownerAccepted=0` of 8 owner clips. The sources call the owner result "a warning sign: the current thresholds may already be too strict for real owners". *Decision:* presentation wording changed to "0 of 28 other voices (121 tries)" with a note that owner acceptance is not yet measured on real owners.

**T58 (speaker part) — Stricter thresholds before notice (2026-11-16).** Unit tests: a 0.72/0.72 score is USER under `STANDARD` and UNKNOWN under `BEFORE_NOTICE`; short phrases are never USER before notice. The in-memory signature is zeroed after its one comparison (task 10a), with a test.

**T63 — Threshold tuning with participants (not run).** SPEAKER_TUNING.md defines the procedure (owner plus at least 30 consenting people; quiet, noisy and far conditions; goal stranger accept = 0) but states "Status: not yet run" and has no numbers.

### 3.5 Plan extraction, event changes and text understanding

**T05 — Optional Qwen extraction (0.4).** Real-model extraction found an action outside the rules vocabulary; validation rejected quoted/conditional fixtures; cancellation/recovery passed; a bad replacement import left the old model intact; 3 tests in 52.295 s. Correction quality, general accuracy and peak RAM were not evaluated. *Decision:* Qwen stays optional and only for typed/edited notes; every suggestion needs review (CHANGELOG 0.4).

**T08 / T14 / T18 — Timing, replay and multilingual rules (0.7–0.11).** 0.7: 44 core; six Pixel tests (9.901 s) for cross-chunk ending inference and unknown durations. Playback evaluation (2026-10-23): five ActivityTimingTest checks passed in 0.223 s, including replay of a multi-turn script (start, end time, end correction, venue correction, address). These are text-to-proposal checks; the spoken version of the script "has not been scored yet". 0.11: 65 core; six Pixel checks (0.281 s) for Korean, Chinese and German event rules.

**T20 / T21 — Duplicates and automation (0.12.1, 0.13.0, 2026-10-23).** Seven duplicate checks (all five added languages) plus eleven regressions passed in 2.487 s; 29 automation checks in 7.684 s, after earlier UI failures were traced to the test harness. *Decisions:* strict duplicate matching (same opening, start time, capture within 2 min); Hands-off mode saves automatically with Undo (CHANGELOG 0.13).

**T22 / T28 — Event changes (0.15.0, 0.18.0).** 0.15.0: 75 core; 29 emulator checks in 6.365 s; eight event-change checks on the Pixel in 1.124 s. 0.18.0: 98 core; 19 Pixel checks in 6.037 s, 11 more in 20.656 s, three native-flow checks in 0.946 s, covering create/move/end/cancel/Undo in Korean, Chinese, French, German and Russian. Real Korean ASR remained the open question (see T29).

**T25 — Announcement/attendance linking (0.16.0).** 86 core; 32 Pixel checks (24.725 s), then 24 targeted (5.211 s) including forced Qwen token failure. *Decision:* known local plans bypass Qwen; failures keep local results with a notice; matching informational duplicates move to History with Undo (CHANGELOG 0.16).

**T36–T39 — Context, dates and summaries (0.23–0.25)**
- 0.23: 121 core; 46 distinct Pixel tests; earlier failures exposed missing attendance reminders, lost recent-event anchors and duplicate inferred-date refinements, now covered.
- 0.23.1: 122 core; a 41-test run passed 40; the failures were stale fixtures; rerun passed (7.152 s).
- 0.24: 140 core; 77 emulator checks (109.27 s) plus 3 assistant checks (6.159 s); the Pixel was unavailable, so this is emulator-only. *Decision:* ambiguous "next Friday" gets no automatic reminder.
- 0.25: 147 core; 82 emulator tests (131.312 s). A real bug was found: processing wall-time anchors could discard later pronoun commands. *Decision:* anchors and context use capture timestamps.

**T40 / T41 / T42 / T43 — 92-case text corpus (0.26–0.27)**
- *Purpose:* measure local extraction from text with given speaker labels (not ASR, not speaker ID).
- *Method:* 92 self-authored cases in seven languages (62 baseline, 30 challenge); fixed capture time 2026-11-08T15:00Z; corpus SHA-256 recorded; `AccuracyEvaluationTest`.
- *Results over versions:* 0.26 — no false or mistimed reminders, 10 expected reminders missed. 0.26.1 — 2 missed, precision 100%, recall 97.6%. 0.27 — precision 100%, recall 98.8%, 1 missed reminder. Final report:

| Group | Cases | TP / FP / FN | Precision | Recall | Missed reminders |
|---|---:|---|---:|---:|---:|
| All | 92 | 82 / 0 / 1 | 100.0% | 98.8% | 1 |
| Baseline | 62 | 55 / 0 / 0 | 100.0% | 100.0% | 0 |
| Challenge | 30 | 27 / 0 / 1 | 100.0% | 96.4% | 1 |
| Arabic | 11 | 9 / 0 / 1 | 100.0% | 90.0% | 1 |

  Time, attendance and place accuracy were 100% on matched events. The remaining miss is challenge case ch11 (Arabic, dialectal "party"). Colloquial German and dialectal Arabic were the earlier failures.
- *What it made us do:* 0.26.1 fixed eight of the ten misses (CHANGELOG). The report is published with its challenge failures and must not be presented as real-world accuracy.

**T55 — Correction parsing after a field trial (0.34.0).** The 0.33.1 trial saved one informational event with the original time and venue and no end time; the English correction text was "largely present" but not applied. *What it made us do:* parse explicit `change … to …`, spaced meridiem ("nine p m"), split time and venue corrections, keep the new venue before "instead of", tolerate spaces in Korean keywords (CHANGELOG 0.34). *Ambiguity:* RECOGNITION_034 says validation was "in progress"; no results are recorded in the evaluation docs.

### 3.6 Reminders, planner and calendar

**T01 — First device tests (0.2, 2026-10-22).** Seven Pixel tests: text UI/consent, persisted memory, v1→v2 migration, known-recording Whisper inference, native cancellation, reminder updates, notification delivery. 15 core tests. Not verified: alarm timing under Doze, reboot, battery.

**T06 / T07 — Planner and calendar (0.5, 0.6).** 0.5: 33 core; planner UI plus two reminder tests in 4.225 s. 0.6: 36 core; Room 2→3 upgrade and a synthetic Calendar Provider (recurring, all-day, boundaries, declined, hidden, null cursor). The synthetic test caught a SQLite comparison-affinity error in a COALESCE filter; the query was corrected. No real calendar was read.

**T34 — Reminder/Undo reliability (0.22.0).** 112 core; 33 Pixel checks in 30.467 s through a real PendingIntent/ReminderReceiver and NotificationManager; 3 more in 6.199 s. Scheduler failure was injected at the adapter, so AlarmManager timing and Doze were not verified.

**T35 — Perceived missed reminder (0.22.1).** Read-only inspection of the normal app showed the reminder was delivered 14.037 s late and held by Android; the phone was in Silent mode, the notification stream muted, channel vibration off and importance DEFAULT. Five Pixel tests passed in 6.517 s. *What it made us do:* Setup status distinguishes delivery from sound, vibration, pop-up and Do Not Disturb, with a direct channel link; new channels default to high importance with vibration (CHANGELOG 0.22.1). A sound/vibration field check is still pending.

### 3.7 Places, messages and location lookup

**T09 — Places and messages (0.8).** 48 core; 12 Pixel tests (migration, message ingestion, activity details, planner/share UI, reminders). Sharing passed its functional checks but failed test teardown; the test was changed and its rerun passed in 1.676 s. No personal messages, calendars or location were used.

**T10 — Emulator service and location (0.8).** 14-test suite in 72.023 s, including real notification-listener delivery with synthetic messages. A regression with an empty fused location and a network location failed until lookup was changed to try fresh fixes from both providers; final two checks passed in 29.313 s. The emulator never produced a fix without injected data.

**T11 / T12 — Physical location (2026-10-23, 0.8.1).** Opt-in `PhysicalLocationTest` in the benchmark package: non-mock fused fix 200 s old with 2,000 m accuracy; one candidate; coordinates not printed or saved (1.703 s). After the owner enabled location, the foreground check passed again (2.408 s; fix 494 s old, about 2 km). 51 core; four Pixel regressions in 5.646 s. *Decision:* a changed venue now clears its old address. Precise GPS accuracy was not verified.

### 3.8 Interface

**T49 — Adaptive interface (0.30, 2026-11-13)**
- *Method:* eight Pixel UI checks (no microphone permission granted); an Android 14 emulator suite of 45 checks; `scripts/test_interface_layouts.ps1` changing real emulator size, density, font scale and locale across six profiles (simple portrait 360×737; small phone 320×585; large text at font scale 2.0; short landscape 800×308; larger display 960×539; right-to-left).
- *Results:* Pixel 8/8 in 17.290 s. Emulator 44/45 in the first 273.186 s run; the foreground check correctly failed because a system UI "not responding" overlay covered the app; after clearing it, the check passed (14.468 s) and all six layout checks passed. The physical test exposed a nonlinear font-scaling interaction (text size changed when controls were zoomed); typography was fixed. Two harness issues were fixed and not counted as app failures. Final APK 226,282,009 bytes; CI passed.
- *What it made us do:* Simple/Detailed home, separate text and control sizes, Calm/Classic/Warm presets, bottom bar below 600 dp and side rail above (CHANGELOG 0.30).

**T50 — Interface desk review (2026-11-13).** Official help pages from eight references (note-taking apps, Android adaptive guidance, phone display settings) and a second wording review of three. Explicitly "not a timed competitor usability study". It led to the layout decisions above and to plain-language labels in 0.31–0.32 (e.g. "Interface size" → "Buttons & spacing").

**T51 / T52 (UI part) — Settings categories (0.31, 0.32).** 22 focused Pixel UI and regression checks at system font scale 1.4: 42.671 s (0.31) and 42.619 s (0.32). An earlier 0.31 run exposed an outdated test path, a missing test notification permission and keyboard interference; these were corrected before the final run. The 0.30 emulator matrix was not rerun for 0.31 or 0.32.

### 3.9 Privacy, consent and jurisdiction

**T42 / T44 (privacy part) — Retention, export and region privacy (0.27, 0.28).** 0.27: retention and export cases (opt-in defaults, strict cutoff, privacy exclusions in JSON export); 13 compact emulator checks in 38.542 s; 4 Pixel checks in 5.763 s. A broader run passed 94/95 and exposed a notification ingestion timeout; listener rebind handling was added and all 95 then passed in 205.305 s. 0.28: opt-in coarse-region cache and URI validation passed on the Pixel (8 checks, 12.314 s).

**T58 — Privacy, consent and location work package (2026-11-16)**
- *Purpose:* verify the two-rule state model (recording: one-party/all-party; voice: none/notice/strict), location following, notices, voice consent, session-only names, and call detection. The report states it is "an engineering approximation of US law, not legal advice".
- *Method:* laptop (Windows 11, JDK 21) and Pixel (API 37, AC power). Border data from the Census 1:500k file: 56 areas, 120,671 points, 1.93 MiB.
- *Results:*

| Check | Result |
|---|---|
| Build + lint | Passed; 0 errors, 65 warnings |
| Core unit tests | 244 / 244 (23 in new or rewritten consent classes) |
| `PrivacyConsentDeviceTest` | 4 / 4 after one bug fix |
| Existing speaker/privacy device tests (9 classes) | 18 / 18 |
| Manual checks on the phone | Settings, consent screen, start dialogs, live status, attested notice, notice record, *Finish*; all as expected |

  Legal example cases (coarse location unless noted): deep in Ohio → one-party / none; deep in Illinois → all-party / strict; Wisconsin 2 km from Illinois → all-party / strict; Texas near New Mexico → one-party / notice; Oregon 2 km from Washington → all-party / notice; Nevada 2 km from California → all-party / none; no fix → all-party / strict; with precise 50 m location, Wisconsin 2 km from Illinois → one-party / none, 1 km → all-party / strict.
- *Bugs found:* (1) "meet me at 5" produced no event because a bare hour without am/pm was not dated; fixed (1–7 read as pm, flagged for checking). (2) A pre-existing bug: the old location loop never ended, so a session could stay in "Finishing"; *Finish* now returns in 3 s. (3) A scroll swipe drew outside the signature box; canvas clipped.
- *Deviation:* the location buffer for coarse fixes was made stricter than the handoff formula so that the handoff's own test cases hold.
- *Not verified:* spoken notice on the phone (tested at night; only the attested path), phone-call detection (no second phone), crossing a border, precise location (prompt declined). The owner's existing voice profile now needs consent; it was not signed for them.
- *What it made us do:* the Unreleased CHANGELOG entries (strictest-of-nearby rule, notice records in Room v7, written voice consent, 12-month profile retention, session-only names).

**T60 / T61 / T62 — Follow-ups from the owner's phone testing (2026-11-16)**
- *T60:* the notice wording worked (records showed the voice line included; ~9.7 s with the line, 4.2 s base only). Two real bugs: SideKick transcribed its own notice (fixed, 0 speech chunks), and the name suggestion could not fire with real speech (owner's short phrases were unassigned; recognized names were lowercase). Fixed; `AddressedNameTest` (2 cases); core 246/246; device 7/7; lint 0 errors. By the owner's decision, the voice-analysis line is now spoken in every state.
- *T61:* a notice expires after 30 minutes without other voices or 2 hours after it was given; voice groups last 30 minutes (was 15). Four new core cases; core 250/250. Not built by choice: expiry on moving 500 m; re-asking when a new voice joins.
- *T62:* notice volume raised to at least 50%. Loudness calibration on the Pixel at 68% volume: on a table 0.0485 and 0.0464; bottom edge covered by a hand 0.0768 (louder, because the microphone sits next to the speaker). The loudness check therefore cannot detect a covered phone; it was kept with threshold 0.01 only to catch "no sound". A proximity check was added: uncovered `proximity=5.0`, played and counted; covered `proximity=0.0`, not played, not counted. Headphone/Bluetooth suppression was not tested on the phone (no headset). Room v8: the owner's phone upgraded v7 → v8 with all 13 records kept. Core 253/253; device 9/9; lint 0 errors.

### 3.10 Build, packaging, CI and regression totals

**Packaging.** 0.26 moved optional speech weights out of the APK: about 225 MB instead of 905 MB (T40). 0.29 confirmed a compact build without the six optional weight/token files (43 s) and that the rebuilt APK was byte-identical to the installed one. APK sizes recorded: 226,618,054 bytes (0.28), 226,238,474 (0.29), 226,282,009 (0.30).

**CI.** Linux CI passed on main and the working branch for 0.27 (previous main), 0.28 (`ade3797`), 0.29 (`0f7ebbc`) and 0.30 (`8bd667f`). 0.31–0.33 explicitly claim no new CI run.

**Growth of the core unit-test suite** (as stated per version):

| Version | Core tests | Version | Core tests |
|---|---:|---|---:|
| 0.2 | 15 | 0.19.0 | 102 |
| 0.3 (approx.) | 22 | 0.19.2 / 0.20 | 103 |
| 0.5 | 33 | 0.21.0 / 0.21.1 | 111 |
| 0.6 | 36 | 0.21.2 / 0.22 | 112 |
| 0.7 | 44 | 0.23 | 121 |
| 0.8 | 48 | 0.23.1 | 122 |
| 0.8.1 | 51 | 0.24 | 140 |
| 0.9.0 | 54 | 0.25 | 147 |
| 0.9.3 | 57 | 0.26 | 153 |
| 0.10 | 58 | 0.26.1 | 157 |
| 0.11 | 65 | 0.27 | 163 |
| 0.12 | 68 | 0.28 | 175 |
| 0.15.0 | 75 | 0.29–0.31 | 186 |
| 0.15.1 | 78 | 0.32 | 187 |
| 0.15.2 | 82 | 0.33 | 195 |
| 0.16.0 | 86 | 0.33.1 | 196 |
| 0.16.1 | 90 | Privacy package | 244 → 246 → 250 → 253 |
| 0.17 | 92 | | |
| 0.18 | 98 | | |

Recurring pattern: many first-run device failures were test-harness problems (stale fixtures, lifecycle matching, permission sheets, keyboard overlap); the sources record each one and state that no application failure was waived. Real application bugs were found by tests in 0.6 (SQLite filter), 0.8 (location fallback), 0.25 (time anchors), 0.27 (notification timeout), 0.30 (font scaling) and the privacy package (bare-hour invitations, endless location loop, signature canvas, self-transcribed notice, name suggestion).

---

## 4. Known gaps / not yet verified

Collected from all sources; each item is stated in at least one document as untested or needing real users.

**Speech recognition and language**
- Natural, within-sentence code switching (e.g. Korean–English): field trials on 0.33.0 and 0.33.1 failed; natural Chinese–English CER is still 67.89% on six clips from one speaker.
- Arabic short clock words: three synthetic commands remain wrong; dialects need native speakers.
- Korean spoken clock repair: field result mixed (one success, one failure); limit documented, not resolved.
- A held-out (blind) test set: robustness and 0.33 clips were also used for tuning; FLEURS validation was used instead of test.
- Real room noise, distance, accents, demographic coverage (Arabic clips all female-labelled, German male-labelled).
- The ModelBench Parakeet result on the three app WAV fixtures (35.1%) is unexplained in the source.
- 0.34 parser/enrollment changes: validation "in progress"; no recorded results. 0.35.0 routing has no dedicated evaluation document beyond ModelBench.

**Speaker matching**
- Owner acceptance on real owners: 0 of 8 owner clips in the 2026-11-14 device-health run; owner chunks unassigned in several field trials.
- Threshold tuning with the owner and at least 30 consenting participants (T63): not run; requires IRB check and written consent.
- Calibrated false-accept/false-reject rates, diarization error, overlap handling and replay resistance: not measured. Speaker matching is not authentication or consent.
- Per-language (Korean) speaker-model accuracy.

**Capture, endurance and power**
- Unplugged battery drain and screen-off battery: never measured (all runs on USB or AC).
- Sessions longer than 60 minutes; whether memory is flat (it rose 336 → 394 MiB over the hour).
- Real-microphone VAD error rates, music and other environmental sounds.
- Recovery across device vendors; no force-stop bypass.

**Plans and reminders**
- The spoken multi-turn correction script from the playback evaluation has never been scored.
- The 92-case corpus is self-authored text; it is not real-world accuracy.
- Reminder timing under Doze and after natural OS alarm delay; sound/vibration field check after 0.22.1.
- Real calendar overlap check on the owner's calendar; real messaging-app notification payloads; geocoder result quality; precise GPS accuracy.
- The "Event change unclear" status inconsistency seen in 0.19.2 is not recorded as fixed.

**Privacy, consent and location**
- Spoken notice end-to-end on the phone in the original test (only the attested path was tested that night; later follow-ups verified playback and records).
- Headphone/Bluetooth notice suppression (no headset available).
- Phone-call detection (needs a second phone).
- Crossing a state border during a session; precise-location buffer on the phone.
- A live two-person name-suggestion check.
- First-lookup cost of the larger border file (not measured separately).
- Random per-session voice key still stored in `sourceKey`.
- Legal review by the university's legal office, written sign-off for the temporary-signature option, and IRB determination. No legal-compliance certification exists.

**Interface**
- No timed usability study; the desk review is not one. Full interface translation is not claimed. The 0.30 emulator layout matrix was not rerun for 0.31–0.33.

---

## 5. November 16, 2026 session

Tests run on the laptop (16 threads), the Pixel 7a (isolated `com.sidekick.app.benchmark` package) and both together.
Public or synthetic audio only, plus the owner's own 8 voice memos (kept on the laptop, never committed).

| ID | Area | What was tested | Data | Key result | Decision |
|---|---|---|---|---|---|
| T65 | Speech recognition | Repetition-loop filter on stored transcripts | 20,676 human references, 20,676 recognizer outputs | 0 references changed; 34 outputs collapsed, all real loops | `RepeatFilter` before noise filtering |
| T66 | Speech recognition | Whisper loop retry (temperature .4 on low entropy) vs old settings, on the phone | 104 command cases, 56 robustness cases, 112 automatic cases | Fast-mode CER 188% → 126% (24% with the filter); Accurate, SenseVoice and robustness unchanged; speed within 2–3% | Keep the retry and the filter |
| T67 | Speaker matching | Owner matching on public voices in 5 rooms, 5 models, 4 improvement ideas, 2 noise removers; replay through the phone microphone | 381 stranger voices, about 163,000 stranger turns; 236 replayed clips | 82% of owner turns (was 54%), 0 strangers; 73% through the phone microphone with phone setup | New CAM++ rule, session ideas, setup per room; ResNet retired; noise removal rejected |
| T68 | Plan extraction | Rules vs Qwen3-0.6B vs rules + Qwen, on the phone | 92 self-authored cases, 7 languages | Rules 98.8% recall in 7 ms; Qwen alone 22.9% in 14.4 s; Qwen added nothing | Qwen removed (484 MB freed) |
| T69 | Regression | Core unit tests and the phone device suite after all changes | 268 core tests; 33 device test classes | Core 268 passed (1 skipped); device suite all passed after 2 outdated tests were updated | Two tests updated |

### T65 / T66 — Repetition loops and Whisper settings

- *Purpose:* recognizers sometimes repeat a phrase many times ("thank you" ×12).
- *Method:* a filter collapses a unit repeated in a row (one word ×6+, 2–3 words ×4+, 4+ words ×3+, unspaced CJK units),
  checked against every stored transcript; Whisper's entropy-triggered retry was re-enabled and measured on the phone
  with the old and new native build, same clips.
- *Results:* no human transcript changed; 34 recognizer loops collapsed. On the phone, one Arabic loop disappeared,
  every other output was identical, Accurate CER 12.9% both, and Fast CER fell from 187.8% to 126.1% (24.0% once the
  filter collapses Whisper Tiny's repeated sentences). Mean time per clip changed by less than 3%.
- *What it made us do:* both changes kept (commit e69f726).

### T67 — Owner voice matching

- *Purpose:* recognise the owner more often (the 2026-11-14 health run found 0 of 8) without letting any stranger in.
- *Method:* see SPEAKER_TUNING.md, "Public-voice tuning". A laptop harness reproduces the app's trimming and windows; a
  phone run of the same 188 clips produced identical signatures. Every bar sits at least .08 above the closest stranger
  seen in any room. Two replay runs played clips out loud from the laptop and recorded them with the phone microphone.
- *Results:*
  - The old ResNet model scores different people about .55 alike (strangers came as close as .93), on the phone as on
    the laptop. Centering helped but not enough. CAM++ beat TitaNet-small, ERes2Net and ERes2NetV2 at equal safety.
  - CAM++ with the new rule: 82% of owner turns (quiet 93%, noisy 70%, babble 36%, far 83%), 0 of about 163,000
    stranger turns. Ideas: session carry +3.3 points, session-adapted profile +1.8, second model +0.6 (not worth
    27 MB), neighbouring chunks +3.2. Noise removal made it worse by bringing strangers closer (GTCRN 71%, DPDFNet 77%).
  - Phone microphone: a profile made from clean audio recognised 15.5%; setup through the phone microphone 72.6%,
    0 of 1,460 stranger turns. The owner's own replayed clips: 2 of 4 at the quiet bar (values .63, .58, .77, .67).
- *What it made us do:* the tuned CAM++ rule with session carry, session-adapted profile and neighbouring chunks;
  voice setup per room (quiet, noise, table); a 198-signature stranger cohort check; ResNet no longer offered.
  Before notice in all-party places nothing was loosened. Commit 9d52869 and follow-up.
- *Not verified:* real conversations, accents, children, relatives, played-back recordings of the owner, and the
  participant study (owner + 30 people). With 381 voices, zero accepted bounds the stranger rate below about 1 in 130.

### T68 — Rules vs Qwen

- *Purpose:* decide whether the optional Qwen model earns its 484 MB.
- *Method and results:* see guides/INTENT_MODEL.md. Rules: 82 of 83 events, 0 false, 7 ms per sentence. Qwen alone:
  19 of 83, 1 false, 14.4 s median (58.8 s worst), 4 failures; 0 found in six non-English languages. Rules + Qwen:
  identical to rules.
- *What it made us do:* Qwen, its native build and its import screen were removed; the app deletes the old model file.

### T69 — Regression after the changes

Core unit tests: 268 passed, 1 skipped (the corpus check needs local data). Phone device suite (Pixel 7a, benchmark
package, 33 classes): the first run passed everything except the 13 screen tests. Those refuse a physical phone unless
`allowPhysicalInterface=true` is passed (8 tests), one still expected the removed Qwen import button, and one expected
"Start voice setup" where the written-consent form now comes first. After updating those two tests, the 13 screen tests
passed on the phone with the flag (13 of 13).

### Still not verified after this session

- The participant voice study; owner recognition in the owner's real conversations (more owner clips would help).
- Spoken notice played aloud, phone-call detection, crossing a state border, headphone and pocket checks, the live
  two-person naming check, battery and heat over a day.

## 6. November 17, 2026

| ID | Area | What was tested | Data | Key result | Decision |
|---|---|---|---|---|---|
| T70 | Speaker matching | More stranger voices; quality-aware bars; learning across sessions | 454 voices, 409,213 stranger turns | Full rule 81.9%, 0 strangers; quality bars 62.2% -> 69.3% at the base rule; learning +0.7 | Quality bars and learning shipped (consent v2) |
| T71 | Plan extraction | Hands-off saving: event kinds, all-day plans, visible assumptions, cancellations | AccuracyEvaluationTest 104 cases (+12) | Recall 98.9%, precision 100%, 0 false reminders; core tests 268 -> 288 | Plans save automatically; only real conflicts go to Review (docs/guides/HANDS_OFF.md) |
| T72 | Interface | Simple layout as the default | Device UI tests on the Pixel | See T73 | Today / Plans / More; four settings sections; plain wording |
| T73 | Regression | Full device suite after merging T70–T72 | 124 test runs | First run 5 failures: 3 real bugs fixed (two venues merged; a change command merged into an older plan; an assumed length hid a repeat), 1 test missed by the new cancel rule, 1 Korean recognizer reading of a synthetic voice (separate, not caused by these changes) | Fixes merged; final result below |
| T74 | Speaker matching | Larger stranger set in all 7 app languages (LibriSpeech train-clean-100, Common Voice ko/ar/ru/zh-TW/en/fr/de) | 1,813 stranger voices, about 6.6 million stranger turns | 97 stranger turns from 17 pairs of near-identical voices (11 Korean, 3 Russian, 2 English, 1 LibriSpeech); 0 in Chinese, Arabic, French, German; owner 78.9% | Rule kept; claim changed from "0 strangers" to "very rare, near-identical voices can pass" (SPEAKER_TUNING.md) |
| T75 | Speaker matching | Second model on the 17 near-identical pairs; learned calibration instead of hand-set bars | 30 people x 4 models; 962 owners, about 12 million stranger turns (two halves) | All 4 models score every pair as one person (0 of 17 separated); calibration keeps 0.2% of owner turns vs 57.7% for the raw score at zero strangers | Neither adopted; the pairs are most likely duplicate people |
| T76 | Transcription | Korean name correction against known names (`NameCorrection`), only before a person particle | 49-clip Korean names set (synthetic voices, 7 recognizers); 547 other Korean sentences | SenseVoice names right 27 -> 40 of 49; Qwen3 35 -> 43; Omni 20 -> 31; 0 of 547 other sentences changed; core 294 passed | Wired into the app with names from saved plans (Settings > Saved plans > Names); phone run, see note |

**What T73 found and fixed.** (1) Plans of the same kind at different places were merged; merging now requires the
same place. (2) "Move the meeting to 7" also read as a new plan and was merged into an older 7 PM meeting, which then
became "the meeting just discussed"; change commands are no longer merged. (3) A repeated plan with an assumed
1-hour length looked edited and was skipped; an assumed length no longer replaces a saved plan's end. (4) The
settings fold closed when a bigger text size was chosen inside it; it now stays open.

**T73 final result.** Core tests 289 passed (1 skipped). Device suite: 123 of 124 passed. The remaining failure is
`KoreanRecognitionTest`: the Korean recognizer reads one synthetic clip as "회의가어있어요" (an extra syllable). No
speech-recognition code changed in this work, and the November 16 Whisper comparison showed this clip unchanged, so it
is tracked as a separate recognizer issue.

**Follow-up.** The Korean failure came from SenseVoice writing a hesitation sound ("어") as a separate word. `KoreanFillers`
drops a lone filler between Korean words (unit-tested, including cases that must stay); the 3 Korean phone tests now pass.

**T76 note.** The first version also changed 12 of 547 ordinary sentences (따라 -> 사라, 사자는 -> 사라는, 한다는 -> 한나는).
Two-syllable names are now corrected only before particles used for people (한테, 랑, 씨, 네 …), and a particle as the next
word must match exactly; after that 0 of 547 changed. The names set was also used to tune these rules, so real recordings
are the next check.

**T76 on the phone (Pixel, SenseVoice, app routing).** Same 49 clips, no typed name list: names right 28 of 49 without
names from plans, 40 of 49 with them; character errors 18.6% -> 17.6%. All 12 changed sentences were fixes. Names come from
saved plans (not Review, not dismissed); a setting keeps them after a plan is deleted, and Delete all clears them.

**T77 Call notice.** A spoken notice given to people in the room no longer covers a phone call that starts afterwards
(`ConsentGate.callStarted`); during a call only the owner's words are kept. Unit test added; core 296 passed.

**November 18 additions (unit-tested; phone run to follow).** Voice questions about plans in all seven languages
(`PlanQuestion`, 4 tests: answers in each language, a manual language understands only itself, no name means no
answer); Auto-detect limited to chosen languages (`DetectLanguages`, 3 tests); speech files can be switched off,
removed or downloaded with sizes shown; read-aloud reminders option; home-screen widget; names learned from plans.
Core 303 passed. An earlier phone suite with the widget, names and read-aloud options: 128 of 128 passed.
Battery-day procedure: `scripts/phone-tests/battery-day.sh`.

**Language-aware speech files (November 18).** `LanguageReadiness` (5 core tests): each language shows what is missing
in the chosen mode, only missing files are downloaded, a switched-off file needs no download, and removing or
switching off a file first says which languages stop and which keep working in Faster mode. Core 308 passed. Phone:
full suite 129 of 129 (before this screen); then NewOptionsTest, ListeningSettingsTest and BundledOfflineTest 9 of 9
on the new build.

**T78 Speech models on the phone (November 18).** 16 models, 140 noisy clips (20 per language), on the Pixel, locked
and unplugged (docs/evaluation/PHONE_MODEL_BENCH.md). The app's models stay best for English (Parakeet 1.4%),
Korean (SenseVoice 10.3%) and Russian (GigaAM 2.8%). Qwen3-ASR is more accurate for Chinese (14.4% vs 19.6%) and French
(11.9% vs 15.8%) but needs 7-10 s per phrase and 954 MB. Cohere Transcribe: German 3.9% (vs 11.4%) but 30 s per phrase
and out of memory; not usable on this phone.

**T79 Battery while listening (November 18).** `BatteryListeningTest` on the Pixel, real microphone, started from the
open app, screen off, no speech, 5 minutes each: old microphone loop (checked every 10 ms, checks 4 times a second,
status updates with the screen off) 29.7 processor-seconds per minute; new loop (waits for a quarter-second of audio,
checks every 2 s, no screen-off status updates, speech detector skipped below -60 dBFS) 20.9 per minute: 30% less.
The silence gate did not trigger in this room (louder than -60 dBFS). The remaining cost is mostly the speech detector
re-reading about 1 s of audio every quarter-second. Pause when resting: paused after a 20 s test window with the screen
off, stayed paused while untouched, resumed and kept recording when the screen woke. Full phone suite 129 of 129;
core 311. Wake-word chip research: EU DMA decision of July 16, 2026 requires Google to open custom low-power wake words
to all assistants by Android 18 (August 1, 2027); today only rooted/Shizuku hacks exist.

**T80 Speech detector work while listening (November 18).** Thread sampling showed the remaining cost was the
listening thread: the speech detector (whisper.cpp Silero, restarted on every call with 1 s of earlier audio) and its
input loudness pass, both slow with the screen off. `VadContextTest` on 20 minutes of real phone recording: a shorter
warm-up changes decisions (12,288 samples: 5.4% of frames differ, 3 speech stretches lost), so it was rejected; running
the detector on 1 s of new audio after the same 12,288 samples uses 44% of the detector time, loses no speech stretch
and flags slightly more audio as speech (0.5 s pieces lost one stretch, rejected). The loudness pass is now one loop
instead of three (same results). Screen-off silence, 5 minutes each: old loop 32.0 processor-seconds per minute, new
12.0 (62% less), still recording. Resting pause test passed; full phone suite 129 of 129; core passed.

**T81 Real power while listening (November 19).** Pixel power meters (ODPM rails via Perfetto, no settings changed),
screen off, silence, 5 minutes each (`scripts/phone-tests/power-rails.sh`, `power_report.py`). Whole phone: not
listening 186 mW; old listening loop 303 mW; new loop 256 mW; new loop with the speech detector at background
priority 227 mW. Listening's own cost: 117 -> 70 -> 41 mW (65% less); processor rails 82.4 -> 40.6 -> 23.6 mW (the
detector moves to the efficient cores; processor time barely changes, 11.3 vs 11.7 s per minute). Background priority
is now the default, with an 8 s microphone buffer so a delayed detector never loses audio. Streaming detectors tested
on the 20-minute recording against true speech timings: sherpa-onnx Silero caught 21-43% of speech (rejected); TEN VAD
caught 96.8% (current 94.7%) at about half the detector time, but its license adds a no-competing-with-Agora condition,
so it is not used (owner decision, November 19: skipped). Resting pause test passed; full phone suite 129 of 129.

**T82 Location, microphone reads, call events, resting suggestion (November 19).** Location: low-power (Wi-Fi/cell)
every 5 minutes far from state borders, precise location only within 20 km of another state (back to low power past
25 km; `RecordingJurisdiction.nearestBorderMeters`, core test). Microphone read once a second (quarter-second around
SideKick's own speech; a block that overlaps its speech is muted). Calls detected by Android's mode-change event
(Android 12+), notice timer checked every 30 s instead of every second. One-time card after 3 hours of listening
suggesting "Pause when the phone is resting" (RestingTipTest). Power with location allowed (indoors, phone still,
screen off, 5 minutes each): old 260.9 mW, new 230.3 mW whole phone; listening's own share now about 44 mW (41 mW
without location permission), so location adds about 3 mW here. Note: the old loop varied between runs (261-303 mW);
the new one was steady (227-230 mW). Moving outdoors with GPS was not measured. Pause test passed; full phone suite
129 of 130, the one failure being the new card test's harness (fixed, then passed): 130 of 130.

**T83 Listening stopped "when locked" (November 19).** Android's recording log showed the owner's SideKick listening
10 hours overnight (02:09-12:03), then silenced twice when a leftover test copy (com.sidekick.app.benchmark, same name
and icon) started listening and Android gave it the microphone. The test copies were uninstalled. Listening now waits
while another app has the microphone and carries on when it comes back (`MicHandback`, 3 core tests); it stops only
after 10 minutes taken, or at once if the microphone is muted or the permission removed. The battery-day run was
interrupted and must restart from a full charge.
