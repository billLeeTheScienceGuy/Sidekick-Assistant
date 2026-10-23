# Version 0.2 validation

## Version 0.10.0 multilingual transcription

58 core tests and both builds/lint pass. The physical Pixel transcribed one public
FLEURS validation clip each in Korean, Chinese, French, German and Russian, tested
automatic Korean/English, switched back to tiny.en, and passed the language-setting
UI check (56.461 seconds). A paced Korean live/auto test passed separately in
15.93 seconds with two chunks and max processing latency 6,753 ms, no queue overflow.
See LANGUAGES.md for per-sample errors, script-variant caveats and limits. No private
microphone audio was captured by the automated tests. Multilingual event extraction
and cross-language speaker accuracy are not validated or claimed.

## Version 0.9.3 short corrections

57 core tests pass. Three physical Pixel regressions passed in 3.104 seconds,
including real-model 2.1-second matching against a recent long-phrase signature,
expiry/interruption rejection, and short-correction draft refinement. The positive
fixture overlaps enrollment, so this is integration evidence rather than held-out
speaker accuracy. No physical microphone was opened; the user's profile was untouched.
Both build variants and lint passed. See SPEAKER_ID.md for thresholds and limits.

## Version 0.9.0 speaker identification

54 core tests passed. Eight targeted physical Pixel checks passed in 3.287 seconds,
including real-model enrollment lifecycle, attribution isolation, and event updates.
A combined paced Silero/speaker/Whisper check passed in 16.1 seconds without queue
overflow. Conservative classification left three of four fixture chunks unassigned;
this is not a representative speaker-accuracy evaluation. See SPEAKER_ID.md for
artifact provenance, the initially rejected incompatible model and required user trial.

## Version 0.8.2 continuous-listening checks

Two opt-in timed tests passed. On the physical Pixel, real VAD/Whisper processed three
paced public-speech bursts with long pauses; transcription counts rose 4/8/12 with no
new calls during each following 15 seconds of silence. No physical microphone was
opened. The Android 14 emulator exercised real AudioRecord and microphone foreground
service with host mic disabled: six minutes with screen off, zero transcription chunks,
continued frame capture across the five-minute wake-lock renewal, and clean Finish.
The tests took 118.623 and 367.416 seconds respectively. See CONTINUOUS_LISTENING.md
for CPU/memory observations and scope. Both build variants and lint passed. 0.8.2 was
installed on phone/emulator with visible Speech detected / Waiting for speech status.

Still unmeasured: real room-noise VAD error rate, microphone accuracy in conversations,
unplugged idle-versus-listening battery drain and all-day physical reliability. These
results do not establish hardware sound-trigger wake-up, speaker identity or reboot
restart. Starting listening remains explicit and the microphone remains active while
VAD gates transcription. No audio files were recorded by either live capture path.

## Version 0.8.1 reliability update

After the user enabled normal-app location permission, dumpsys confirmed it remained
allowed. The physical foreground check passed again in 2.408 seconds: a real fused
fix, 494 seconds old with approximate 2 km accuracy, and one address candidate without
a configured-city fallback. The separate test package's temporary permission was
revoked; normal-app permission remained enabled.

51 core tests pass. Four targeted physical-phone regressions passed in 5.646 seconds:
activity follow-up details, approval-only venue corrections, optional unknown times,
and Settings/guidance readiness. Normal and benchmark builds/lint pass without errors.
Changing a venue now invalidates its previous address in inferred drafts, proposed
updates and manual edits. Saved records change only after approval; a later address
can then fill the corrected venue. Settings shows actual permission, Android Location
and notification-listener readiness, refreshing on resume, with an on-demand location
check that prints no coordinates and saves no fix. Version 0.8.1 installed on phone
and emulator with user settings/model/memory retained.

## Physical Pixel location verification (2026-10-23)

At the user's explicit request, the physical Pixel 7a ran the production foreground
location path in the separate benchmark package. The test passed in 1.703 seconds.
Android supplied a non-mock fused fix, 200 seconds old, with 2,000-metre approximate
accuracy. A location-biased query for The James returned one candidate with no city
fallback. Coordinates and returned addresses were neither printed nor persisted.
This verifies approximate phone-location acquisition and lookup, not a new satellite
GPS fix or precise navigation accuracy. The normal app's coarse-location permission
was off and remains unchanged; choosing phone location in Settings requests it.
The benchmark package's temporary coarse permission was revoked after testing.

PhysicalLocationTest requires explicit instrumentation opt-in
`-e verifyPhysicalLocation true` as well as a real device and benchmark package.
General test suites skip it. No saved memory, normal app settings or model was changed.

## Emulator service and location checks

The Android 14 Pixel_7a x86_64 emulator passed a 14-test suite in 72.023 seconds,
including actual Android notification-listener binding/delivery, synthetic sender
messages, follow-up address/end updates, disabled scanning, all existing planner/share
checks, reminders and database migrations. These tests grant access only in the
emulator benchmark package and never read personal phone messages.

Live geocoding initially returned no result during emulator startup, then returned one
candidate each for The James in Madison and a known public Mountain View address.
The no-result/unknown-address path also worked. A foreground test with an injected
Madison location returned an address candidate. A separate regression deliberately
left fused location empty and provided a network location: it passed after production
lookup was improved to examine fresh fixes from both providers and try each provider
before falling back to the configured city. Freshness now uses monotonic elapsed time.
The final two targeted location checks passed in 29.313 seconds; builds/lint passed.

Without injected provider data, this emulator still had no usable approximate-location
fix, even with internet and a foreground Activity. Internet geocoding and acquiring
coordinates are separate operations; the emulator does not reproduce real cellular/
Wi-Fi positioning. A configured city works without a location fix. Android permission,
provider and actual GPS accuracy on a physical phone remain device-specific. Address
candidates always require confirmation; a returned candidate is not proof of venue identity.

The emulator fixtures remove mock providers and restore notification settings on exit.
The corrected location lookup was built for the normal app and installed on the phone
and emulator. No new model inference or microphone benchmark was required.

## Version 0.8 places/messages update

48 core tests pass. Normal and benchmark builds/lint pass with no lint errors.
Twelve targeted Pixel tests passed across the final runs: three migration/calendar
provider tests, two message ingestion/settings tests, two activity detail/retention
tests, three planner/guidance/share UI tests and two reminder tests. Sharing's first
run passed functional checks but failed ActivityScenario teardown after relaunching
its own tracked Activity. The test now delivers onNewIntent through Instrumentation
and restores the scenario intent; its targeted rerun passed in 1.676 seconds.

Coverage includes Room 3-to-4 preservation, informational filtering, selected-app and
enablement timestamp gating, per-conversation isolation, message deduplication, later
address/end details, saved-record update proposals without automatic writes, shared
text confirmation/disable gating, source ownership and Maps query encoding. Builds
run schema-generating variants sequentially to avoid concurrent Room schema writes.
No personal message, calendar, address lookup or location permission was used by tests.

Required physical checks remain: actual geocoder result quality, approximate-location
permission/provider availability, the selected messaging app's notification payloads
and Android notification-access setup. See PLACES_AND_MESSAGES.md. No new model/audio
benchmarks were needed because these paths use bounded local parsing.

## Version 0.7 timing/guidance update

44 core tests pass. Normal/benchmark APK builds and benchmark lint pass (0 errors,
12 warnings). Six targeted Pixel tests passed in 9.901 seconds: cross-chunk ending
inference, approval-only timing revisions, saving/editing unknown durations, persisted
Ask/Passive settings, I don't know followed by approval, planner controls and reminders.
The earlier picker/planner test was also rerun successfully before this milestone.
Normal 0.7.0 was installed. No personal messages/calendars were read, and model/audio
benchmarks were not repeated. This is conservative English timing inference, not a
claim of general conversation understanding or representative speech accuracy.

## Version 0.6 calendar update

36 core tests pass, including interval boundaries, invalid durations and all-day DST
conversion. Normal/benchmark APK builds and benchmark lint pass (0 errors, 11 warnings).
On Pixel 7a, the planner UI test and two reminder tests passed; saved activity start/end
were checked independently of the reminder. Two calendar device tests passed after a
query correction: non-destructive Room 2-to-3 upgrade and a synthetic Calendar Provider
covering recurring occurrences, all-day events, touching boundaries, free/cancelled/
declined events, hidden calendars and a null cursor. The synthetic test caught SQLite
comparison-affinity behavior in an initial COALESCE filter; the corrected query compares
the typed columns directly and passed the same fixtures. No real phone calendar was
read, and no microphone/model benchmarks were rerun. Version 0.6.0 was installed and
opened in the normal app, preserving the separately installed model and saved memory.

Required user check: opt into calendar reading, verify a known real overlap and a
nonoverlap, and confirm denied permission still allows saving. See PLANNER.md.

## Version 0.5 planner update

33 core tests pass, including planner state partitioning, reminder ordering and same-minute
comparisons. The new planner UI test and two reminder tests passed in 4.225 seconds on
Pixel 7a using the separate benchmark package. UI coverage includes filter/search, editor
access, one-hour/no-reminder actions and cancellation without persisting a draft. Native
date/time picker interaction and usability still benefit from user testing. No calendar
permissions or calendar data were accessed, and no model/audio benchmark reruns were needed.

## Version 0.4 local intent update

Qwen3-0.6B Q4_K_M is now an optional separately imported model. On the physical Pixel,
real-model extraction found an action outside the rules vocabulary; validation rejected
quoted/conditional fixtures, cancellation/recovery passed, and a bad replacement import
left the old model intact. The VAD/Whisper pipeline and approval-only storage tests also
passed in the same process. Three targeted instrumentation tests passed in 52.295 s.
See INTENT_MODEL.md for model provenance, limits and the single cold timing observation.
Corrections, general accuracy, peak model RAM and user experience remain to be evaluated.

## Version 0.3 prototype update

Continuous live notes now connects Silero VAD, five-second overlapping chunks and
concurrent Whisper processing to the microphone foreground service. Only accepted
commitments are persisted; recent text and new proposals are temporary. Earlier
persisted proposals from version 0.2 remain available until the user deletes them.

Targeted tests passed on the physical Pixel 7a without activating the microphone:
two live-pipeline tests (real paced VAD/Whisper plus silence rejection, and approval-only
storage), two foundation/UI tests and two reminder tests. Core coverage adds boundary
overlap, overlap-only suppression and exact text-overlap handling. Benchmark and normal
build/lint checks pass. These checks do not establish microphone accuracy, screen-off
capture or unplugged battery use; those are the next required user tests in TRY_IT.md.

The original results below describe version 0.2.

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
