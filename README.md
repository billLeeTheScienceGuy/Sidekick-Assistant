# SideKick

[![Android foundation](https://github.com/billLeeTheScienceGuy/Sidekick-Assistant/actions/workflows/android.yml/badge.svg)](https://github.com/billLeeTheScienceGuy/Sidekick-Assistant/actions/workflows/android.yml)

**An Android assistant that listens in the background and turns the plans you make out loud into calendar events and reminders, entirely on your phone.**

Say *“I'll call you at 5”* or *“내일 저녁 8시에 만나요”* in conversation, and SideKick adds it to your planner and reminds you, with no tapping, no cloud and no audio files.

## Highlights

- **Background listening.** Voice detection wakes the pipeline only when someone speaks; audio stays in memory and is never written to storage.
- **On-device speech recognition in 7 languages:** English, Korean, Chinese, French, German, Russian and Arabic, including Korean–English mixed speech. Each phrase is routed to the best of several open models, chosen by benchmarking 16 models on about 1,380 clips.
- **Plans, not just transcripts.** Dates, times, places and commitments become events and reminders, with calendar-overlap warnings, corrections (“move it to 7”), Undo, daily summaries and scam advisories.
- **Privacy by design.** The app has no internet permission. A consent gate applies the recording rule of the US state you are in (detected offline) and drops speech it may not keep before transcribing it. Voice profiles, retention and deletion are under your control.
- **Careful about mistakes.** When models disagree about a time, the plan waits in Review instead of being saved automatically.

## How it works

```text
Microphone → voice detection → who is speaking → consent gate → speech recognition → understanding → actions → memory
             (Silero VAD)       (voice profile)   (state rule)   (per-phrase model     (dates, times,  (reminders,  (planner,
                                                                  router)               places)         calendar)    summaries)
```

See the [architecture](docs/design/ARCHITECTURE.md) for the modules and data flow.

## Try it

1. On an Android phone, open the [latest release](https://github.com/billLeeTheScienceGuy/Sidekick-Assistant/releases/latest) and install `SideKick-<version>.apk` (allow your browser to install apps when asked).
2. In SideKick, tap **Get started → Get speech files**, download the speech files you need (for Auto-detect: `sensevoice` and `omni`), then **Install speech file** for each.
3. In **Settings**, choose **Auto-detect**, set up your voice, and start listening.

English works immediately without any speech files. Details: [speech files](docs/guides/MODEL_PACKS.md) and [trying it on a Pixel](docs/guides/TRY_IT.md). The release APK is development-signed, not a store build.

## Build from source

Requirements: Android Studio with **JDK 17** for Gradle, Android SDK platform **35** and build tools **35.0.0**, Python 3.11+.

```bash
python scripts/prepare_speech.py --compact
python scripts/prepare_speaker.py
python scripts/prepare_diarization.py
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Optional speech files are built with `prepare_sensevoice.py`, `prepare_omnilingual.py`, `prepare_accuracy_packs.py` and then `package_model_packs.py`; see [speech files](docs/guides/MODEL_PACKS.md). Device and benchmark tests use an isolated benchmark build so they never touch personal app data; see [validation](docs/evaluation/VALIDATION.md) and [phone tests](docs/guides/PHONE_TESTS.md).

## Repository layout

```text
app/        Android app: interface, listening service, speech engines, storage
core/       Platform-independent logic (extraction, routing, consent rules) and unit tests
scripts/    Model preparation, packaging, evaluation and phone-test scripts
docs/       Guides, design notes and evaluation reports (index: docs/README.md)
```

## Documentation

Start with the [documentation index](docs/README.md). Version history is in the [changelog](CHANGELOG.md); remaining work and known limitations are in the [roadmap](docs/design/ROADMAP.md).

## Status and limitations

SideKick is a capstone prototype targeting the Pixel 7a. It has been measured on public speech data and in focused device trials, not in long-term field use. All-day unplugged battery life, speaker matching in real rooms and a user study are the current focus. Voice matching is not consent, and the consent rules are an engineering approximation of US recording law, not legal advice.

## Team

Nicholas Beier, Bill Lee and Emma Huang.

## Third-party components

On-device models and libraries (whisper.cpp, sherpa-onnx, Silero VAD, SenseVoice, Omnilingual ASR, Parakeet, GigaAM and others) are used under their own licenses, which ship with the app in [`app/src/main/assets/licenses`](app/src/main/assets/licenses).
