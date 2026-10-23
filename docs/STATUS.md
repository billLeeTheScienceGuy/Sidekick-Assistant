# Handoff

Current prototype: 0.4.1, branch `codex/android-foundation`.
Verified model is installed on the connected Pixel. The benchmark model copy has been
restored for regression tests and is retained during active development to avoid repeated
484 MB transfers. The normal app's model remains installed separately from its APK.

0.4.1 fixes missed attendance/future plans, including "I will be going to Louie's
birthday tomorrow". Prompt covers personal plans; validation supports grounded verb
forms and event attendance. Explicit-plan rules supplement model misses with a clear
rule-derived review note. Negative, conditional, third-party and mere event facts remain
excluded. Names, places and time phrases must remain grounded in source evidence.

- Optional Qwen3-0.6B Q4_K_M model imported once (484 MB, exact hash) into no-backup
  app storage. Model is separate from APK; no network permission. See INTENT_MODEL.md.
- Text analysis after listening stops uses llama.cpp b6000, two CPU threads, 2,048-token
  context, non-thinking prompt and constrained JSON. Evidence/ownership/action/time
  validation precedes temporary proposals. Only user-approved items persist.
- New real-model test passed on Pixel: new verb outside rule vocabulary, quoted and
  conditional rejection, cancellation/recovery and bad-import preservation. Cold positive
  request took 9.101 s (one fixture, no representative accuracy/latency claim).

- Continuous, explicitly started live notes; no session timer or audio files.
- Silero v5.1.2 via pinned whisper.cpp, rolling 1.024-second VAD context every
  0.256 seconds. Five-second chunks, 0.512-second overlap, two queued chunks.
- Whisper tiny.en: two CPU threads, 512-position encoder window, cached during
  the session. Finish drains the queue; Discard cancels and clears this session's
  temporary proposals. Interruptions/overload are visible.
- Recent text is bounded in RAM. New unapproved proposals remain in RAM; approval
  saves to Room and optionally schedules a reminder. Legacy saved data is retained.
- Start/Finish/Discard controls in app and notification. Microphone starts only
  from the user's explicit action. Renewable partial wake lock supports screen-off
  processing; all-day battery life is unverified.
- Free local models only; no INTERNET permission. Speaker matching, diarization,
  continuous background LLM extraction and calendar integration remain future work.

Validation: 30 core tests; six earlier live-prototype device tests passed on Pixel 7a Android 17
(paced public audio through real VAD/Whisper, approval-only persistence, UI, reminders).
Normal and benchmark builds/lint pass. Automated tests never open the microphone.
Use the benchmark package for device tests to protect ordinary app data.
The later intent/live replay run passed three targeted tests in the same process.
The 0.4.1 future-plan regression passed five real-model cases in 65.112 seconds on
Pixel 7a. Both positive event examples yielded model-derived proposals; event-only,
negative and conditional examples yielded none. Normal/benchmark build and lint pass.

User tried the live microphone prototype: somewhat works; transcription quality deferred.
Next: user tries short typed/edited Qwen notes via Find commitments, then planner improvements.
Complex corrections/cancellations and representative model accuracy remain incomplete.
Ask the user for meaningful product decisions; batch implementation and targeted checks,
keep updates brief, avoid repeated polling and unnecessary benchmark reruns.

Windows: Gradle JDK17 at `C:\Users\Patron\.jdks\jbr-17.0.14`; Android SDK under
`C:\Users\Patron\AppData\Local\Android\Sdk`. Prepare both bundled models with
`scripts/prepare-speech.ps1` or Python 3.11+ `scripts/prepare_speech.py`.
