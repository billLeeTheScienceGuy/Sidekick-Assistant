# Handoff

Current source: 0.10.0, branch `codex/android-foundation`.
Added local Korean/Chinese/French/German/Russian transcription and per-chunk auto
detection via shared Whisper tiny multilingual weights. Settings persists selection;
English default retains tiny.en. No paid/cloud API. Non-English event/date extraction
is not implemented; UI states this. Five public language clips, auto Korean/English,
model switching and settings UI passed on Pixel; 58 core tests and build/lint pass.
See LANGUAGES.md for sample quality measurements and the still-failed real short
correction trial. Do not report that speaker/microphone issue as resolved.
Short phrases (1.5–3s) can now match using independent strong evidence against both
enrollment and a recent confident long-phrase signature. The signature expires at
30 seconds, clears on attribution interruption, and is not renewed by short phrases.
No label inheritance or extra raw audio buffering. 57 core tests and three targeted
Pixel checks pass, including real-model short matching and draft end-time correction.
Real microphone validation still needed. See SPEAKER_ID.md.
User confirmed own-reading trial (six likely-user, eleven unassigned), then video
only (zero likely-user, 21 unassigned). Added a bounded balanced-score alternative
to tolerate uneven user half-window scores; no changes to short-phrase or OTHER
handling. See SPEAKER_ID.md. Fresh mixed-source validation is still required.
User's first combined trial did not pass: 18/18 chunks unassigned despite a saved
profile, no party suggestion, and a misrecognized venue. Fixed dotted AM/PM clock
parsing and sentence boundaries using a grounded regression; 55 core tests pass.
Added last-20-chunk metadata-only voice diagnostics in RAM, without weakening match
thresholds. A short new microphone trial is needed to diagnose unassigned voices;
the previous raw audio was not retained. Normal build/lint pass.
Local voice enrollment and conservative speaker matching are implemented. Settings
can enroll, disable or delete the private voice signature. Only likely-user chunks
create personal promises; other/unknown voices provide informational suggestions.
No profile means unassigned. See SPEAKER_ID.md for pinned model/runtime artifacts,
tests, conservative-threshold limitations and the combined required phone trial.
Planner tab adds searchable Review/Reminders/No reminder/History/All views, chronological
reminder ordering, passed-reminder labels, date/time pickers and same-minute reminder
warnings. Completed/dismissed items show Reopen to make reactivation explicit.
Optional read-only phone calendar checking is implemented. Activity start/end are distinct
from reminders and persist only after approval. Calendar results stay in memory. User
permission and a known real-calendar overlap/nonoverlap still need phone testing.
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
- Free local models only; no INTERNET permission. Diarization,
  continuous background LLM extraction and calendar writes remain future work.

Historical validation: 36 core tests; six earlier live-prototype device tests passed on Pixel 7a Android 17
(paced public audio through real VAD/Whisper, approval-only persistence, UI, reminders).
Normal and benchmark builds/lint pass. Automated tests never open the microphone.
Use the benchmark package for device tests to protect ordinary app data.
The later intent/live replay run passed three targeted tests in the same process.
The 0.4.1 future-plan regression passed five real-model cases in 65.112 seconds on
Pixel 7a. Both positive event examples yielded model-derived proposals; event-only,
negative and conditional examples yielded none. Normal/benchmark build and lint pass.
Planner UI/filter/search/editor and reminder tests passed (three targeted device tests,
4.225 seconds). Model/audio benchmarks were not rerun for this UI-only milestone.

User tried the live microphone prototype: somewhat works; transcription quality deferred.
Next: user tries short typed/edited Qwen notes via Find commitments, then planner improvements.
Complex corrections/cancellations and representative model accuracy remain incomplete.
Ask the user for meaningful product decisions; batch implementation and targeted checks,
keep updates brief, avoid repeated polling and unnecessary benchmark reruns.

Windows: Gradle JDK17 at `C:\Users\Patron\.jdks\jbr-17.0.14`; Android SDK under
`C:\Users\Patron\AppData\Local\Android\Sdk`. Prepare both bundled models with
`scripts/prepare-speech.ps1` or Python 3.11+ `scripts/prepare_speech.py`.

0.6 validation: normal/benchmark builds and benchmark lint passed; calendar synthetic
provider and Room 2-to-3 migration tests passed on Pixel after correcting the busy-status
query. Planner UI and reminder tests passed, including activity-time persistence and
invalid calendar input. No personal calendar data was accessed. Installed normal 0.6.0.

0.7 installed: Ask/Passive guidance settings, optional independent activity endpoints,
local cross-sentence timing suggestions, review-only updates to approved activities.
44 core and six targeted phone tests passed for 0.7.
0.8 adds switchable informational-event suggestions, optional place/address details,
automatic/manual address lookup, city/phone-location context, selected-message sharing
and opt-in notification scanning with selected apps. No paid API keys; online address
lookup uses the system geocoder and Maps is opened only on a tap. All inferred details
require approval. Room migration 3-to-4 preserves existing records and activity times.
48 core tests and twelve targeted phone checks passed across runs. Real geocoder,
location and notification-access behavior are the next required user phone checks.

Emulator follow-up: 14 service/UI/migration checks passed. Actual online city geocoding
returned candidates for The James/Madison and a public reference address. A simulated
network-location regression passed after fixing fused/network fallback and monotonic
cache freshness. See VALIDATION.md for timings and emulator location limitations.

2026-10-23 physical location check passed on Pixel 7a: non-mock fused fix
(200 seconds old, approximate accuracy 2 km), plus one address candidate with no
city fallback. Normal app permission is off; Settings phone-location choice requests
it. Test permission restored. Satellite GPS precision remains untested.

0.8.1: user enabled normal-app location permission, confirmed allowed. Repeat physical
lookup passed (real fused fix and one candidate). Added Settings readiness and manual
location check, plus venue-correction handling that clears old addresses and requires
approval before replacing saved details. 51 core tests and four targeted phone
regressions passed; normal/benchmark build+lint passed. Installed on phone/emulator.

0.8.2: physical paced speech/silence test passed (three resumptions, no Whisper calls
during long pauses); emulator AudioRecord/foreground service passed six minutes
screen-off, zero silent chunks, five-minute renewal and clean Finish. Added visible
speech/waiting status. Build+lint passed; installed both devices. Next required real
trial: own speech/room noise with screen off, then unplugged battery comparison.
See CONTINUOUS_LISTENING.md. No always-on hardware wake or speaker identity claim.
