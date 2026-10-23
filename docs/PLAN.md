# SideKick foundation plan

## Product and scope

An Android, local-first assistant that turns consenting conversations into reviewable commitments. Start with English and Android 8+ (API 26). No paid APIs, accounts, server, or network permission are required by the foundation app. Models run on the device once installed. Free models still have license and attribution requirements.

The Phase 1 report remains the product direction: explicit listening sessions evolve toward low-interaction operation; user voice matching gates heavier processing; a 2-second pre-roll expands to bounded utterances. Voice identity is not consent. Unknown speakers and overlapping speech must not silently become user commitments.

## Architecture

One Android app module and one pure Kotlin core module. Organize the seven conceptual stages as interfaces/packages, rather than seven independently deployed services. Use manual dependency injection until dependencies warrant a framework.

AudioRecord (16 kHz mono PCM) -> bounded pre-roll -> VAD -> utterance assembly -> speaker matching/consent -> local transcription -> intent extraction -> validated proposed commitment -> local review inbox -> explicit action.

The text path can be developed and tested independently of audio. The current extractor uses a deliberately narrow, labeled rule baseline, not simulated AI. Version 0.2 adds real Whisper transcription and user-approved local reminder delivery. Calendar integration remains a separate future adapter.

Core contracts retain source text, capture instant, timezone, speaker, and unresolved time wording. Do not convert 'at five' to a definite timestamp or use a model's self-reported confidence to authorize actions. Model output is untrusted: validate fields, evidence, ownership, and dates in code.

## Delivery stages and exit checks

1. Foundation (implemented): Android build, Compose review inbox, Room persistence, pure Kotlin pipeline contracts, conservative text extraction, CI and tests.
2. Speech (prototype implemented): pinned whisper.cpp JNI, bundled tiny.en and Silero, user-enabled continuous listening, rolling speech detection, overlapping five-second utterances, concurrent transcription with a bounded queue, and temporary live text/proposals. WAV import, timeout/cancellation and transient PCM cleanup remain supported. Next: real microphone, boundary accuracy, screen-off and unplugged battery testing.
3. Intent: integrate Qwen3 through llama.cpp with constrained JSON and non-thinking mode. Compare 0.6B and 1.7B against the rules baseline. Add deterministic time normalization, missing-field review, correction and cancellation handling, evidence validation, and duplicate detection.
4. Speaker/context: enrollment, threshold calibration, unknown-speaker rejection, explicit consent settings, optional diarization, calendar reads/conflict detection, then user-approved scheduling.
5. Reliability: retention settings and expiry, process-death/reboot behavior, notification permission and delivery tests, battery/thermal testing, accessibility and user study. Add summaries after commitment precision is acceptable.

## Initial model decision

See MODELS.md for primary sources and licensing. This is a shortlist, not a device benchmark. Start with Silero VAD + Whisper tiny.en, compare base.en for quality, and Qwen3-0.6B Q4 for extraction with 1.7B Q4 as the quality candidate. Use WeSpeaker ResNet34 for speaker embeddings later. Full diarization is optional and comes after basic user matching.

## Evaluation before declaring a model best

Create a consented, held-out set including silence, noise, accents, names, multiple speakers, negation, quotations, hypothetical promises, corrections, missing dates, and timezone/DST boundaries. Split speakers between tuning and evaluation. Measure transcription word error AND name/time accuracy; commitment precision/recall; wrong-owner rate; date normalization accuracy; p50/p95 latency; peak RAM; battery per hour; thermal throttling; and real-time processing ratio. Compare the same inputs and device conditions. Favor fewer wrong commitments over aggressive recall. Record exact model file hash, quantization, runtime revision and device details with results.

## Current limitations

Version 0.3 listens continuously after explicit user activation and uses neural VAD to suppress silent inference. Audio stays in bounded RAM buffers. Recent text and unapproved proposals are temporary; only approved commitments are saved. Interruption, processing overload or a full proposal inbox stops listening visibly. It does not restart automatically after process death or reboot. All-day reliability remains unverified. Voice matching, diarization and Qwen extraction remain unimplemented. The rules baseline handles only simple first-person English promises and requires review of dates. Room is app-private with Android backup disabled; additional database encryption is not implemented. Inexact reminders may be delayed by Android; lifecycle/Doze testing remains necessary.
