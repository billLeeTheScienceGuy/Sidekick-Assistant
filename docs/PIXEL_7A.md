# Pixel 7a target and verification

The team uses Pixel 7a phones. Google specifies Tensor G2 and 8 GB LPDDR5 RAM: https://support.google.com/pixelphone/answer/7158570 . Treat 8 GB as shared system RAM, not available model memory.

## Decisions

- Native inference target: ARM64 (`arm64-v8a`); CPU baseline first. Do not assume Tensor G2 TPU access or Gemini Nano/AICore support. GPU acceleration is an experiment, not a dependency.
- Current speech model: Whisper tiny.en. Compare base.en after measuring errors and sustained temperature. The current implementation loads and frees one context per transcription to release RAM; context reuse is a future measured optimization.
- Initial intent model: Qwen3-0.6B Q4_K_M, non-thinking, 2,048-token context and 256-token output cap. The 1.7B candidate is opt-in benchmarking, not the default. Quantized weight size is not peak process RAM.
- Serialize ASR and LLM work; release a model context before opening the other if memory pressure warrants it. No parallel agents or concurrent large models.
- Start with two CPU inference threads and measure two versus four in a release build. More threads may increase heat and worsen sustained performance.
- Live notes: 16 kHz mono PCM, explicit start, no session timer, Silero speech detection and five-second chunks with 0.512-second boundary overlap. Two queued chunks maximum, one Whisper worker; capture continues concurrently.
- No network permission, remote inference, paid APIs, or silent cloud fallback. Missing models must produce a visible setup state.

The live pipeline uses two Whisper CPU threads and a 512-position encoder window; one-shot WAV imports retain their 30-second input limit. LLM settings in DeviceProfile remain future defaults. Screen-off capture uses a renewable partial wake lock; unplugged battery impact remains unmeasured.

## What has and has not been established

Version 0.2 includes native ARM64/x86_64 libraries and a bundled, verified tiny.en model. Seven instrumentation tests passed on a physical Pixel 7a running Android 17. Known-recording transcription took about 5.7 seconds in one test including setup/loading. This is not a sustained-performance or accuracy benchmark. Real microphone, screen-off, Doze, reboot and battery testing remain necessary. See VALIDATION.md.

## Physical-phone checklist

1. Enable developer options and USB debugging, connect the phone, and run `adb devices`. Verify the selected serial belongs to the intended Pixel 7a.
2. Install `app/build/outputs/apk/debug/app-debug.apk` with `adb -s SERIAL install -r PATH`. Open SideKick on the phone.
3. Test example -> consent -> analyze -> accept/dismiss -> close/reopen -> delete. No microphone permission should be required for the text flow.
4. Start **Start live notes** from the visible activity. Verify the level, progressive text, **Finish listening**, Discard, and continued capture past 30 seconds. Deny/revoke permissions and verify a recoverable error. Check screen-off listening and transcript accuracy. PCM is transient; only approved commitments are saved.
5. Run `gradlew.bat :app:connectedDebugAndroidTest` on Windows (or `bash ./gradlew :app:connectedDebugAndroidTest` elsewhere) with only the intended device connected. Tests exercise consent/UI and Room persistence. They use sample data, not the microphone.
6. Repeat inference in airplane mode. Record model SHA-256, runtime commit, build type, OS version and device temperature. Measure cold start and 20 consecutive utterances to reveal thermal degradation. Automated tests do not change the phone's network settings.

Proposed acceptance targets (not results): no OOM/ANR, peak app PSS below 1.5 GB, transcription real-time factor below 1 on 10-second clips, extraction p95 under 5 seconds, and no growing audio backlog. Adjust the model/scope if these fail; do not imply background listening is viable based only on a short demo. Battery budget should be agreed after a one-hour idle-listening baseline and a matched speech workload.
