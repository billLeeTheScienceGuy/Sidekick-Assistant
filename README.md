# SideKick

An Android, local-first memory assistant for spoken commitments. Version 0.2 records your voice, transcribes it with bundled Whisper, proposes commitments, and delivers reminders you approve. See [the plan](docs/PLAN.md), [model shortlist](docs/MODELS.md), and [phone test guide](docs/TRY_IT.md).

Primary device: **Pixel 7a (Tensor G2, 8 GB RAM)**. Whisper tiny.en CPU inference has passed a known-recording test on a physical Pixel 7a running Android 17. Qwen3 remains a future candidate, not an installed model. See [validation](docs/VALIDATION.md) for the scope of testing.

## What works now

- Native Kotlin/Compose Android app with an inbox of proposed, accepted, and dismissed commitments.
- Offline English transcription via whisper.cpp JNI and a SHA-256-verified, bundled Whisper tiny.en model. No account, model setup, or inference API needed on the phone.
- Explicit microphone recording capped at 30 seconds; Finish, Discard, progress, cancellation and a 120-second inference timeout. PCM stays in RAM and is discarded after processing.
- Import 16 kHz mono PCM16 WAV files up to 30 seconds. Other audio formats are rejected clearly.
- A small English rules baseline for first-person promises and requests; source wording, capture timestamp, and timezone are retained. Edit transcripts before extraction or add commitments manually.
- Room local persistence and deletion controls. No network permission or cloud fallback; Android backup is disabled.
- Review/edit titles and reminder times. Unambiguous dates may be suggested; ambiguous dates stay for review. DST gaps/overlaps are rejected rather than silently shifted.
- Local reminder notifications; completing, dismissing, or deleting an item cancels its reminder. Pending reminders are restored after reboot, app update or launch. Inexact alarms may be delayed by Android battery saving.
- Non-destructive Room migration from the initial app; existing memory is retained.
- Pure Kotlin interfaces for VAD, speaker identification, transcription, and extraction; unit tests for text gating and buffer behavior.
- Gradle wrapper, exported Room schema, and GitHub Actions build/test/lint workflow.

## Not implemented yet

Always-on listening, neural VAD, voice enrollment/speaker recognition, diarization, Qwen intent extraction, comprehensive date parsing, calendar conflict detection, summaries, retention expiry, and additional database encryption. Record only your own voice and review transcripts. The current commitment rules can miss or misinterpret language. No model is trained by this app, and there is no automatic scheduling without review.

## Run

1. Open this folder in Android Studio. Use **JDK 17** for Gradle (a newer IDE-bundled JDK may not be compatible with the pinned Gradle version).
2. Install Android SDK platform **35** and build tools **35.0.0**. Set `ANDROID_HOME` or let Android Studio create the ignored `local.properties` file with `sdk.dir`.
3. Run `scripts/prepare-speech.ps1` on Windows (or `python scripts/prepare_speech.py` with Python 3.11+). This downloads the MIT-licensed 77.7 MB model, verifies its pinned checksum, and places it in the ignored model-assets folder. This computer's workspace already has it.
4. Sync Gradle and run `app` on the phone. Gradle installs NDK **27.0.12077973** and CMake **3.22.1** when licenses are available. Native builds fetch checksum-pinned whisper.cpp source. Build/download steps need connectivity; inference does not.
5. Tap **Record my voice**, speak a short promise, then **Finish & transcribe**. Review the words, confirm they are yours, and tap **Find commitments**. Use **Review & accept** to edit the action and optional reminder.

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
separate app package and public test audio; continuous microphone capture is not yet
connected to the UI. Battery drain and representative speech accuracy remain unmeasured.

Collect real Pixel 7a microphone, accuracy, latency and reminder feedback using [TRY_IT.md](docs/TRY_IT.md). Then add Silero VAD and benchmark Qwen3-0.6B extraction against the rules baseline. Keep speaker identity, consent and calendar integration separate from model predictions. The APK includes MIT license notices for Whisper and whisper.cpp in `assets/licenses/`.
