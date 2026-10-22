# Version 0.2 validation

Verified 2026-10-22 with JDK 17, Gradle 8.12, AGP 8.9.2, SDK 35, NDK 27.0.12077973 and CMake 3.22.1. Physical test device: Pixel 7a, Android 17, ARM64.

## Passed

- App and instrumentation APKs build, including ARM64 and x86_64 whisper.cpp JNI libraries.
- 15 core unit tests: consent/ownership gates, extraction evidence, negative examples, input bounds, PCM ring behavior, WAV validation/amplitude, date normalization and DST ambiguity.
- Seven physical-device instrumentation tests: text UI/consent, persisted memory after database reopen, v1-to-v2 migration, known-recording Whisper inference, native cancellation followed by successful inference, reminder title/time updates and completion, notification delivery/idempotency/deletion.
- Known-speech testing transcribes the public `jfk.wav` fixture and checks expected words. It took approximately 5.7 seconds in the final run including model setup/loading. This is a functional observation, not an accuracy or throughput benchmark.
- Android lint: zero errors, 11 warnings (older dependency/SDK advisories and Kotlin API style suggestions).
- Android 17 UI-test compatibility was fixed with Espresso 3.7.0 and AndroidX Test runner 1.7.0. Initial failures were in the older test framework's reflective InputManager access.

Commands:

```powershell
.\scripts\prepare-speech.ps1
.\gradlew.bat :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

## Still needs user testing

Automated tests do not activate the microphone, change network settings, or inspect personal conversations. They do not establish real microphone accuracy, screen-off behavior, alarm timing under Doze, reboot restoration, peak RAM, battery drain, or thermal stability. Reminder tests exercise real notification posting and scheduling/cancel APIs, but do not wait for a naturally delayed OS alarm or reboot the phone. See TRY_IT.md.

The earlier emulator/debug-server command was blocked by automatic approval review. The user subsequently connected a physical Pixel 7a, allowing device tests to complete without restarting that server.

All inference is local and the main app has no INTERNET permission. Whisper is integrated; neural VAD, speaker identification, diarization, Qwen extraction and calendar conflict checking remain unimplemented. Commitments still use a labeled rule extractor and require review.

Development branch: `codex/android-foundation`. APK/model/build caches are ignored by Git; model preparation scripts reproduce the verified asset for fresh checkouts.

The connected-test runner removed its app installation after the test run. The normal debug APK was then reinstalled and successfully launched on the phone. Until a separate test application ID is configured, run connected instrumentation only with disposable app data; use normal APK updates for user testing.
