# SideKick

An Android, local-first memory assistant for spoken commitments. Version 0.3 adds continuous live notes: speech detection feeds bounded audio chunks to on-device Whisper, with temporary text and proposals for review. Only approved commitments are saved. See [the plan](docs/PLAN.md), [model shortlist](docs/MODELS.md), and [phone test guide](docs/TRY_IT.md).

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

- Version 0.10.0: local Korean, Chinese, French, German and Russian transcription,
  plus Automatic language detection in Settings. A shared multilingual Whisper tiny
  model is bundled; English keeps tiny.en. Transcripts retain the spoken language.
  Event/date extraction remains English-only. See [language validation](docs/LANGUAGES.md).

- Native Kotlin/Compose Android app with an inbox of proposed, accepted, and dismissed commitments.
- Offline English transcription via whisper.cpp JNI and a SHA-256-verified, bundled Whisper tiny.en model. No account, model setup, or inference API needed on the phone.
- User-enabled continuous listening with Silero VAD, overlapping five-second chunks, concurrent capture/transcription and a bounded queue. No session timer or audio files. Finish drains pending speech; Discard clears that session's temporary notes. Interruption or overload is shown explicitly.
- Import 16 kHz mono PCM16 WAV files up to 30 seconds. Other audio formats are rejected clearly.
- A small English rules baseline for first-person promises and requests; source wording, capture timestamp, and timezone are retained. Edit transcripts before extraction or add commitments manually.
- Recent live text and unapproved proposals stay in RAM. Only accepted commitments enter Room storage; delete controls remain available. No network permission or cloud inference fallback; optional address lookup delegates to the system geocoder or Maps. Android backup is disabled.
- Review/edit titles and reminder times. Unambiguous dates may be suggested; ambiguous dates stay for review. DST gaps/overlaps are rejected rather than silently shifted.
- Local reminder notifications; completing, dismissing, or deleting an item cancels its reminder. Pending reminders are restored after reboot, app update or launch. Inexact alarms may be delayed by Android battery saving.
- Non-destructive Room migration from the initial app; existing memory is retained.
- Pure Kotlin interfaces for VAD, speaker identification, transcription, and extraction; unit tests for text gating and buffer behavior.
- Gradle wrapper, exported Room schema, and GitHub Actions build/test/lint workflow.

## Not implemented yet

Diarization, comprehensive date parsing, automatic conflict resolution, summaries, retention expiry, and additional database encryption. Qwen extraction is an optional, conservative prototype; complex corrections/cancellations still need evaluation. Reliable all-day battery life and background behavior remain unverified. Start listening from the open app; it does not restart automatically after reboot or process death. Review results. Chunk boundaries and noise can cause repeated or missing words. No model is trained by this app, and there is no automatic scheduling without review.

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
3. Run `scripts/prepare-speech.ps1` on Windows (or `python scripts/prepare_speech.py` with Python 3.11+). This downloads the MIT-licensed 77.7 MB model, verifies its pinned checksum, and places it in the ignored model-assets folder. This computer's workspace already has it.
4. Run `python scripts/prepare_speaker.py` (Python 3.11+) to fetch the verified speaker model and Android AAR. Sync Gradle and run `app` on the phone. Gradle installs NDK **27.0.12077973** and CMake **3.22.1** when licenses are available. Native builds fetch checksum-pinned whisper.cpp source. Build/download steps need connectivity; inference does not.
5. In Settings, choose **Set up my voice** and complete setup alone. Then tap **Start live notes**, confirm session consent, and grant notification/microphone permissions. Live text and temporary proposals appear as you speak. Tap **Finish listening**, then **Review & accept** to save an item and optional reminder. Typed/imported words still require the separate consent checkbox.

Windows PowerShell:

```powershell
# Point JAVA_HOME to your JDK 17 and ANDROID_HOME to your Android SDK.
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug
# With a USB-debugging-enabled phone connected:
.\gradlew.bat :app:connectedDebugAndroidTest
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

Live-transcription engine benchmarks now include a successful 16-minute paced run on
Pixel 7a and exploratory shorter-window/Silero measurements. See
[measurements and limitations](docs/STREAMING_BENCHMARK.md). These benchmarks use a
separate app package and public test audio. Version 0.3 connects this pipeline to the
microphone and UI. Battery drain and representative speech accuracy remain unmeasured.

Collect real Pixel 7a microphone, accuracy, latency and screen-off feedback using [TRY_IT.md](docs/TRY_IT.md). Then benchmark Qwen3-0.6B extraction against the rules baseline, followed by planner improvements. Speaker identity comes afterward. The APK includes MIT notices for Whisper, whisper.cpp and Silero in `assets/licenses/`.
