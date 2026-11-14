# SideKick

0.33 improves speech onset/pauses, quiet-speech detection and short CJK automatic
recognition. Conflicting acoustic readings stay as temporary text instead of
automatically changing plans. See the [recorded Pixel evaluation and limits](docs/RECOGNITION_033.md).

0.32 simplifies Settings with plain-language labels and optional details, keeps
privacy information beside each action, and improves short bilingual recognition
by avoiding retries that force mixed speech into one language.
See the [paired Pixel measurements and limits](docs/RECOGNITION_NEXT_STEPS.md).
The six-category settings, Auto-detect switch and optional start tips introduced
in 0.31 remain available.

0.30 adds an adaptive Android interface with **Simple / Detailed** home layouts,
independent text and interface sizes, Calm / Classic / Warm design presets,
Light / Dark / System appearance and optional manual note-taking.
Use **Settings** to personalize it. Persistent navigation adapts from a bottom bar
to a side rail. See the [interface benchmark](docs/INTERFACE_BENCHMARK.md) for
comparisons, supported devices and the reproducible layout checks.

0.29 improves Automatic and longer multilingual speech using measured local-model
routing, adds explicit microphone-stall/interruption handling, and keeps owner
context stable across anonymous speaker clusters. See the [broader evaluation](docs/ROBUSTNESS_EVALUATION.md)
for noisy public speech, short Arabic limits and recovery measurements.

0.28 adds [offline speaker-turn segmentation](docs/SPEAKER_TURNS.md), session-only
anonymous voices, [opt-in calendar mirroring](docs/CALENDAR_SYNC.md) with edit
protection, and optional [region privacy guidance](docs/PRIVACY_AND_RISK.md).
A [ten-minute controlled Pixel trial](docs/SUSTAINED_TURNS.md) passed without
queue overflow or second-voice owner matches; it is not a battery/field study.

0.27 adds opt-in memory retention, private event export, local English risk
advisories and labelled settings controls. Start with the
[capstone checklist](docs/CAPSTONE_CHECKLIST.md), [architecture](docs/ARCHITECTURE.md)
and [privacy behavior](docs/PRIVACY_AND_RISK.md). Reproduce the emulator checks with
`scripts/validate_prototype.ps1`; the submission bundle includes dated evidence
and known limitations.

0.26 adds a [92-case text evaluation](docs/ACCURACY_REPORT.md), a Get started checklist, [verified offline model packs](docs/MODEL_PACKS.md), and a compact APK about 75% smaller. 0.26.1 fixes eight of the corpus's ten missed-reminder cases while preserving ambiguity and ownership checks. A [prototype submission report](docs/SUBMISSION_REPORT.md) and `scripts/prepare_submission.py` provide reproducible demo deliverables. The evaluation is curated text coverage, not real-world speech accuracy.

0.25 adds [daily summaries and temporary conversation highlights](docs/SUMMARIES.md), explicit calendar-editor handoff, and stronger named/multilingual attendance updates. A [prototype demo checklist](docs/DEMO.md) describes the supported scope.

0.24 integrates [broader English dates](docs/DATE_UNDERSTANDING.md), preserves exact relative reminder delays, and rejects ambiguous times across the supported grammars. Ambiguous “next weekday” suggestions do not trigger automatic reminders.

0.23.1 prevents delayed message notifications from rewinding conversation context and handles long notes within validation limits. Expanded device checks cover Planner editing, sharing, calendar filtering, uncertain-speaker updates and profile isolation.

0.23 adds [conversation context](docs/CONVERSATION_CONTEXT.md): split date/time details in seven languages, stable updates to untimed plans, and English attendance changes with reminder updates and Undo. Ambiguous references stay unapplied.

0.22.1 separates notification delivery from audible/visible alert settings, adds a direct reminder-channel settings link, and enables vibration/pop-up defaults for new channels. Existing Android channel preferences are preserved.

0.22 adds [end-to-end reliability improvements](docs/RELIABILITY.md): Undo in Planner, recovery from scheduling failures, reminder retry when returning to the app, and a one-tap notification check in Settings.

0.21.2 closes a Korean mixed-script clock retry gap. See the [current roadmap](docs/ROADMAP.md) for remaining prototype work; speech accuracy remains a known limitation.

0.21 adds Arabic and improves Adaptive recognition across the seven selected languages using measured on-device model choices. It also retries invalid Korean 12-hour clocks with a second offline model under strict context guards. See [quality measurements and limits](docs/ASR_EVALUATION.md) and [Arabic support](docs/ARABIC.md).

0.20 prevents replaying already processed corrections when analyzing copied live notes, and keeps separate voice signatures for selected languages. Korean setup preserves the existing English signature; matching still needs real-device verification.

0.19.1 fixes Korean corrections when older matching events exist by using the recently discussed matching event, and handles an extra recognized Korean particle in generic cancellation commands.

0.19 uses offline SenseVoice int8 for Korean in Adaptive speech mode, preserves quiet-pause boundaries, and rejects mixed fragments that would borrow a later sentence’s clock. Other speech modes remain available.

0.18 extends explicit event rescheduling/cancellation to Korean, Chinese, French, German and Russian, with the same Undo and reminder handling.

0.17 adds an evaluated CAM++ speaker-model option with separate voice setup, and explicit phone/activity timezone labels.

0.16 links matching announcements and personal attendance, archives automatic duplicates with Undo, and keeps local results when smart analysis fails.

0.15.1 adds adaptive short English speech recognition and fixes duplicates when reanalyzing copied live notes.

0.15 adds [automatic rescheduling and cancellation](docs/EVENT_CHANGES.md) with Undo.
Pixel reboot recovery has been verified after a user-started listening session.

An Android, local-first memory assistant for spoken commitments. Version 0.13 adds [Hands-off mode](docs/HANDS_OFF.md): detected events and matching corrections save automatically, with Undo in notifications. Personal plans with clear times get reminders; other voices stay information-only. Review mode is available in Settings. Audio and full transcripts stay temporary. See [the plan](docs/PLAN.md), [model shortlist](docs/MODELS.md), and [phone test guide](docs/TRY_IT.md).

Version 0.4 adds optional local Qwen3-0.6B smart extraction for edited/typed notes after
listening stops. [Install its model once](docs/INTENT_MODEL.md); the 484 MB model stays
outside the APK and survives app updates. Live capture keeps using the lighter rules
path. Every model suggestion requires review and approval.

Version 0.5 adds a [Planner tab](docs/PLANNER.md) with search, reminder ordering,
review/history filters, date/time pickers and same-minute reminder warnings. Calendar
access is optional in version 0.6: choose activity start/end times in the editor and tap **Check phone calendar**. Read permission is requested only then; no calendar writes occur.

Version 0.8 adds [optional places and messages](docs/PLACES_AND_MESSAGES.md):
Ask/Passive guidance, activity end-time suggestions, information-only events, literal
venue names and confirmed address suggestions, manual/automatic lookup, chosen-city/
phone-location context, shared-message import and opt-in notification scanning.
Speech and intent stay local; optional address lookup can use an online system provider.

Version 0.8.1 adds Settings readiness/location checks and prevents changed venues from retaining old addresses.

Version 0.8.2 adds speech/waiting status and [continuous-listening validation](docs/CONTINUOUS_LISTENING.md).

Primary device: **Pixel 7a (Tensor G2, 8 GB RAM)**. Whisper tiny.en and optional Qwen3-0.6B CPU inference have passed focused fixture tests on a physical Pixel 7a running Android 17. See [validation](docs/VALIDATION.md) for the scope of testing.

## What works now

- Version 0.14.0 adds an optional [default-assistant mode](docs/ASSISTANT_MODE.md).
  After selection and one user-started session, enabled listening can recover from
  system process restarts and reboots. Stop disarms it. Emulator recovery is verified;
  the Pixel is configured and still needs its user-started recovery trial.

- Version 0.13.0: automatic event saving, matching corrections, personal reminders,
  incoming/shared-message processing and persistent notification Undo. No editor or
  approval is required in Hands-off mode after setup and starting listening.

- Version 0.12.0 adds ten-second live windows, bounded RAM speech context,
  independent speaker windows, optional Whisper base and review-only uncertain
  corrections. See [model comparison and limits](docs/SPEECH_QUALITY.md).
  Version 0.12.1 prevents matching repeated speech across voice-label changes from
  creating duplicate events; changed details remain subject to review.

- Version 0.10.0: local Korean, Chinese, French, German and Russian transcription,
  plus Automatic language detection in Settings. A shared multilingual Whisper tiny
  model is bundled; English keeps tiny.en. Transcripts retain the spoken language.
  Version 0.11.0 adds basic dated-event, end-time and place rules in all five languages;
  complex grammar remains limited. See [language validation](docs/LANGUAGES.md) and
  [event grammar](docs/MULTILINGUAL_EVENTS.md).

- Native Kotlin/Compose Android app with an inbox of proposed, accepted, and dismissed commitments.
- Offline English transcription via whisper.cpp JNI and a SHA-256-verified, bundled Whisper tiny.en model. No account, model setup, or inference API needed on the phone.
- User-enabled continuous listening with Silero VAD, overlapping ten-second chunks, concurrent capture/transcription and a bounded queue. No session timer or audio files. Finish drains pending speech; Discard clears that session's temporary notes. Interruption or overload is shown explicitly.
- Import 16 kHz mono PCM16 WAV files up to 30 seconds. Other audio formats are rejected clearly.
- A small English rules baseline for first-person promises and requests; source wording, capture timestamp, and timezone are retained. Edit transcripts before extraction or add commitments manually.
- Recent live text stays in RAM. Hands-off saves extracted events and matching updates automatically; Review mode keeps proposals temporary until approval. Saved events and a bounded Undo journal enter Room storage; delete controls remain available. No network permission or cloud inference fallback; optional address lookup delegates to the system geocoder or Maps. Android backup is disabled.
- Review/edit titles and reminder times. Unambiguous dates may be suggested; ambiguous dates stay for review. DST gaps/overlaps are rejected rather than silently shifted.
- Local reminder notifications; completing, dismissing, or deleting an item cancels its reminder. Pending reminders are restored after reboot, app update or launch. Inexact alarms may be delayed by Android battery saving.
- Non-destructive Room migration from the initial app; existing memory is retained.
- Pure Kotlin interfaces for VAD, speaker identification, transcription, and extraction; unit tests for text gating and buffer behavior.
- Gradle wrapper, exported Room schema, and GitHub Actions build/test/lint workflow.

## Not implemented yet

Comprehensive event/date understanding, simultaneous-voice source separation,
named-person recognition and automatic conflict resolution remain future work.
Neural speaker turns, optional one-way calendar mirroring, derived summaries and
retention expiry are implemented. Legal guidance is limited and never authorizes listening.
Hands-off saving/corrections and optional default-assistant recovery are already
implemented. All-day reliability, speaker attribution and real integrations still
need broader evaluation. Qwen is an optional manual-analysis prototype. See the
[prioritized roadmap](docs/ROADMAP.md), including current speech limitations.

Version 0.9.0 adds optional local voice enrollment in Settings. Three speech samples
produce a private, deletable voice signature; raw enrollment audio is discarded and
never transcribed. WeSpeaker ResNet34-LM through sherpa-onnx matches two windows of
each speech chunk. Only confident user matches can create personal plans; other or
uncertain voices produce information-only event suggestions. Thresholds are a
conservative prototype, not representative accuracy claims. No profile or disabled
matching means all live speakers are unassigned. Voice identity is not consent.

## Run

1. Open this folder in Android Studio. Use **JDK 17** for Gradle (a newer IDE-bundled JDK may not be compatible with the pinned Gradle version).
2. Install Android SDK platform **35** and build tools **35.0.0**. Set `ANDROID_HOME` or let Android Studio create the ignored `local.properties` file with `sdk.dir`.
3. Run `python scripts/prepare_speech.py --compact` with Python 3.11+. This downloads and verifies only the bundled English/VAD models into ignored assets; optional speech packs are not needed to build the normal app.
4. Run `python scripts/prepare_speaker.py` and `python scripts/prepare_diarization.py` (Python 3.11+) to fetch the verified speaker/segmentation models and Android AAR. Sync Gradle and run `app` on the phone. Gradle installs NDK **27.0.12077973** and CMake **3.22.1** when licenses are available. Native builds fetch checksum-pinned whisper.cpp source. Build/download steps need connectivity; inference does not.
5. Optionally choose **Set up my voice** in Settings and complete setup alone. Tap **Start live notes**, confirm session consent, and grant notification/microphone permissions. Hands-off saves detected events and corrections automatically, with Undo in notifications. Personal plans with clear times get reminders; other/unassigned speech remains information-only. Select Review mode in Settings for manual approval. Typed/imported words still require the separate consent checkbox.

Windows PowerShell:

```powershell
# Point JAVA_HOME to your JDK 17 and ANDROID_HOME to your Android SDK.
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug
# For full benchmark/device tests, first prepare all optional weights:
# python scripts/prepare_speech.py
# python scripts/prepare_sensevoice.py
# python scripts/prepare_omnilingual.py
# Device tests must use the isolated benchmark app to protect normal app data.
.\gradlew.bat -PspeechBenchmark :app:assembleBenchmark :app:assembleBenchmarkAndroidTest
```

macOS/Linux:

```sh
bash ./gradlew :core:test :app:assembleDebug :app:lintDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

See [validation results](docs/VALIDATION.md) for checks performed and the device tests still required.

Connected instrumentation tests currently use the app's normal package and may uninstall it during cleanup. Run them only with disposable test data. For ordinary use, install/update the debug APK or use Android Studio's Run action.

## Layout

```text
app/                 Android UI, Room storage, microphone foreground service
core/                Platform-independent contracts, rules baseline, PCM buffer and tests
docs/PLAN.md         Architecture, stages, evaluation gates and limitations
docs/MODELS.md       Free model candidates, licenses, sources and integration requirements
.github/workflows/   Build, core tests and Android lint
```

## Next milestone

Harden complete capture → event → correction → reminder → Undo journeys, then
measure extended unplugged, screen-off reliability on Pixel 7a. Voice ownership,
broader event understanding, real integration checks and release packaging follow
the [current prioritized roadmap](docs/ROADMAP.md). Existing focused trials do not
establish all-day reliability or representative multilingual accuracy.
