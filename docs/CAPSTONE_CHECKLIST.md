# Capstone acceptance goals

This defines a prototype finish line in the absence of a course rubric. It does
not promise a grade or equate software checks with field validation. Requirements
come from the Phase 1 proposal and subsequent user decisions: Pixel 7a, free local
models, temporary transcripts, optional online places/messages, hands-off changes
with Undo, and phone timezone.

## Acceptance criteria

| Goal | Acceptance evidence | Boundary |
|---|---|---|
| Reproducible build | Core tests, Android build/lint, GitHub CI; versioned APK/checksum manifest | Development-signed prototype, not a store release |
| Offline pipeline | Native fixture → ASR; VAD/voice tests; event/context integration | Controlled recordings do not establish natural speech accuracy |
| Correct actions | Event → correction → cancellation → Undo → real reminder receiver | Inexact Android alarms; no automatic calendar sync |
| Privacy controls | No recording archive, explicit start/consent, deletion, verified packs, opt-in retention/export | No legal-location engine or per-person consent verification |
| Usability | Missing-pack setup, optional voice setup, date/time pickers, Planner/Summary, labelled settings and large-text test | Not a user study or full accessibility audit |
| Measured quality | Reproducible 92-case text corpus including failures; dated physical ASR comparisons | Development data, not representative or native-audited accuracy |
| Explainable risk support | Offline English warning signs, dismiss/disable, generic alerts and no credential copying | Not general multilingual fraud classification |
| Reviewable submission | Architecture, demo script, proposal mapping, limits and reproducible build/test commands | Final course materials/user studies may require student participation |

## Proposal module mapping

| Phase 1 module | Implemented evidence | Remaining limitation |
|---|---|---|
| Calibration & privacy | Per-language/model voice enrollment, permission/consent flow, deletion, retention/export | Jurisdiction lookup and per-person consent database absent |
| Background audio & detection | Foreground service, bounded VAD/chunks, sequential decode, assistant recovery | Android force-stop/permission restrictions; all-day endurance unproven |
| Recording & diarization | No saved recording; user/other/unknown attribution and uncertainty handling | No overlapping-speaker diarization or named-person recognition |
| Local speech-to-text | Whisper/SenseVoice/Omnilingual routing, free weights, verified packs, offline fixtures | Natural/noisy/mixed speech and Korean numbers remain imperfect |
| Intent/entities | Seven-language rules, evidence, dates/times/end/places, ownership/context | General semantics and dialects exceed the grammar |
| Context/actions | Deduplication, corrections, cancellation, attendance, Undo, calendar conflict reads, reminders and risk advisories | Calendar writing requires explicit editor handoff |
| Memory/summary | Room, Planner, derived summaries, temporary excerpts, deletion, retention, JSON export | Factual/extractive digest, not unrestricted LLM summarization |

## Reproduce and demonstrate

1. Follow README model/runtime preparation once and use JDK 17.
2. Start and unlock `Pixel_7a` in Android Studio. Run
   `powershell -File scripts/validate_prototype.ps1 -Suite Compact` for setup,
   privacy, message, migration, native offline inference and UI checks.
3. Run `powershell -File scripts/validate_prototype.ps1 -Suite Full` for the
   broader pipeline/Planner/notifications/receiver suite. These scripts only use
   isolated emulator packages and never instrument the personal app database.
4. Build normal `gradlew :app:assembleDebug :app:lintDebug`, then
   `python scripts/prepare_submission.py`. Follow DEMO.md with synthetic inputs.
5. Use VALIDATION.md for dated results/scope. Package the local
   `dist/SideKick-<version>` folder; optional model packs remain separate.

## What software work cannot certify

Natural conversation, unfamiliar accents/speakers, noisy overlap, hours of
unplugged screen-off listening, actual messaging accounts, accessibility and user
benefit need field evidence. Earlier physical measurements are dated and scoped
in ASR_EVALUATION.md and STREAMING_BENCHMARK.md. A few trials, a full battery
indicator or emulator tests cannot certify 24/7 reliability. Do not claim legal
compliance or fabricate user-study/clinical outcomes.
