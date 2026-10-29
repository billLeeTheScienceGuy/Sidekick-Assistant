# Handoff

Current source: 0.23.1/code40. User requested the accumulated work be committed
and pushed with simple wording. Grouped incoming messages are ordered by their
timestamps; unseen delayed messages cannot rewind the current context. Long local
notes validate a bounded recent window, retaining an inherited quote marker.
Normal 0.23.1/code40 installed with install -r, package version verified and the
main activity opened. Existing app data was preserved.

122 core tests pass; debug and benchmark builds/lint pass. A broader 34-test device
run identified four outdated UI test failures (Review-mode state and lazy-list
scrolling); the remaining checks passed. The final targeted run passed 40 of 41,
then the last scrolling fixture was repaired and its test passed (7.152s). Covers
Foundation, Planner, messages, automatic events and corrections. Additional checks
covered calendar filtering, shared text, uncertain updates, voice duplicates,
language profile isolation and assistant registration. Emulator-only assistant
recovery was skipped; no real microphone was opened. See VALIDATION.md.

Previous 0.23.0:

Current source: 0.23.0/code39, branch `codex/android-foundation`.
Conversation context is implemented; see CONVERSATION_CONTEXT.md. Split date/time
assembly supports seven languages; English pronoun attendance and named end/place
details remain bounded and grounded. Untimed plans and later explicit dates update
the same saved event, with reminder changes and Undo. Messages stay informational
and separated by conversation. Voice reference context resets after a >120-second
gap, speaker change or local date change; listening itself is not limited.

121 core tests pass. Both Android variants build/lint pass. 46 distinct targeted
Pixel instrumentation tests passed across the final runs: full 46-test run had
one replay regression, fixed and rechecked in all 31 AutomaticMemory/EventChange
tests (25.815s); the other 15 passed in the full run. Includes synthetic audio,
message boundaries/isolation, attendance, withdrawal, inferred-date refinement,
reschedule/cancellation replay protection, reminders, fault rollback and UI Undo.
One UI test now clears its own diagnostic reminder before clicking the test button
so stale fixture state cannot satisfy its asynchronous wait.

Normal 0.23.0/code39 installed successfully with install -r and MainActivity opened.
Package version verified on the Pixel. No real microphone
session or real-message access was started. User confirmed reminder alert worked;
long listening trial remains explicitly deferred. No commit or push requested.

Previous 0.22.1:

Current source: 0.22.1/code38, branch `codex/android-foundation`.
User did not perceive the reminder. Real DB shows the diagnostic was delivered
14.037s after due; Android NotificationRecord is still active, importance DEFAULT,
not DND-intercepted. Phone ringer is SILENT, notification stream muted; channel
vibration and pop-up were off. This is observed delivery with inadequate alerting,
not a missing alarm. Enabled vibration/pop-up for this app's reminder channel via
Android UI; verified both switches on. Global Silent mode was left unchanged.

App now reports delivery enabled separately from sound/mute, phone ringer mode,
channel vibration, pop-up and DND state; deep-links the specific channel settings.
New channels default HIGH+vibration, existing user channel preferences remain under
Android control. Added VIBRATE permission and REMINDER notification category.
Need user to choose Ring/Vibrate before retrying audible/vibration behavior.
Both variants build/lint pass; 5 targeted reminder/UI/receiver tests passed in
6.517s, including VIBRATE permission and REMINDER category. Normal 0.22.1/code38
installed successfully; VIBRATE granted. Existing phone Silent mode remains unchanged.

Previous 0.22.0:
Reliability work: durable last-five-event Undo in Planner; rollback event/journal/
alarm state on failed Undo; preserve old automatic notification until mutation
succeeds; rearm pending reminders when app resumes; blocked-alert notice/settings
link; stable only-alert-once reminder notifications. Added Settings one-tap two-minute
reminder check (fixed diagnostic ID, no microphone, reversible).

112 core tests and both variants build/lint pass. Initial 33 Pixel checks passed:
injected scheduling failures, real receiver workflow, Planner Undo, existing reminders,
event changes and automation. Final Settings-check/Planner Undo/receiver tests
also passed (3 tests, 6.199s; 34 distinct targeted device checks total).
See RELIABILITY.md for coverage and required locked/unplugged notification check.
Normal 0.22.0/code37 installed successfully with data preserved. Settings opened
for the user; no test reminder or microphone started on their behalf.
No new ASR/number drills; model weights, profiles and thresholds unchanged.

Previous 0.21.2:
User reported one of two real attempts worked and asked for one final fix, then
move on without more isolated number drills. Actual second output was
저는 내일 오후 열8시에 파티에 갈 거예요에요. Mixed-script 열8 bypassed the earlier
clock regex, so no retry ran. Added mixed-digit/Korean teen clocks and spelled-out
teens to the invalid-12-hour detector. Comparison tolerates only a duplicated polite
ending; replacement still changes only the clock. Captured-case regression and
changed-date negative check pass. 112 core tests plus normal build/lint pass.
This does not establish real number recognition is solved. Stop this test loop.
Both real chunks were UNKNOWN; the successful eight-pm event was informational.
Current priorities and remaining work are in ROADMAP.md. Normal 0.21.2/code36
installed successfully with data preserved. No further isolated number test requested.

Previous 0.21.1:
Real 0.21 trial still returned 18 twice for the user's eight-pm prompt. Original
retry did not persist its second read, so its exact failure cause is unknown.
Changed invalid-clock retry to full-context Whisper base first (better Korean
public accuracy), then Omnilingual if base cannot safely repair it. Only the clock
span changes; original surrounding wording/punctuation stays. Guards remain exact
for context, dates, negation, meridiem and minutes; no hard-coded 18-to-8 rewrite.
Added RAM-only last-five clock diagnostics to capture UI, showing each model's
clock and whether repair was accepted. No private transcript logging or audio files.
Rare retry may take ~15–20s; must verify actual user speech again after install.
111 core tests pass; isolated KoreanNumberReadingTest verified full-context base
recognized both digit/word eight-pm fixtures and repaired only the captured clock
to 20:00. AdaptiveRecognitionTest also passed (2 device tests, 70.854s). Both builds
and lint pass. Normal 0.21.1/code35 installed successfully, Korean/SenseVoice selected,
microphone off. Do not claim the real case fixed
until a fresh microphone trial; the new UI clock checks will expose each reading.

Previous 0.21.0:
Added Arabic selection/native Whisper allowlist, native-language voice setup and
MSA date/event/place/duration/correction grammar. Existing English/Korean voice
profiles are kept. Added spoken compound hours in French/German/Russian.

Evaluated Whisper tiny/base, full/reduced context, SenseVoice ITN on/off, small Q5,
Moonshine Arabic and Omnilingual int8 on actual Pixel using public/synthetic clips.
See ASR_EVALUATION.md for measurements and limits. Shipped Adaptive: SenseVoice KO/ZH;
Omnilingual Arabic and longer/backlogged FR/DE; short FR/RU base (clear queue), short
DE full-context tiny; English prior behavior; Auto prior Whisper detection.
Omnilingual assets add 365 MB, pinned SHA-256; preparation and CI updated. Small Q5
and Moonshine are test-only, not shipped. Arabic command accuracy remains incomplete
(5/8 in initial command run); broad dialect/native use has not been validated.

Korean invalid AM/PM numeric clocks now get one independent Omnilingual retry. It
may replace the clock only if all surrounding words, meridiem and minutes agree;
questions/quotes, changed identities/dates and valid clocks are never rewritten.
Guard has core tests; actual 18-vs-8 microphone reproduction remains unverified.
111 core tests, both builds/lint pass. 24 targeted device tests passed across
production recognition/model switching, negative commands, Arabic/full multilingual
event/reminder/Undo flow, existing automation and copied-note status. Final adaptive
smoke rerun after the clock guard addition also passed (1 test, 46.965s).
Installed normal 0.21.0/code34 successfully. Capture confirms Korean/SenseVoice,
Adaptive routing and microphone off. English/Korean CAM++ signature files retain
their previous sizes/timestamps. No reenrollment needed. Next real trial can target
the Korean invalid-clock retry; Arabic validation used public/synthetic speech.

Previous 0.20.0:
Fix copied-note correction replay: remember at most 64 live correction strings in
RAM per temporary session and suppress only unchanged corrections from voice:copied.
New live commands remain eligible, edited copied commands still work. Clear resets
the replay history. False status was reproduced after live success then manual
analysis of copied notes. Regression checks status and unchanged cancellation token.

Real voice diagnostics: 2.3s speech lacked a recent confident long phrase; 3.6s
speech scored 0.34/0.43 against enrollment, consistency 0.47. No threshold lowering.
Added model-and-language-specific local profiles for selected non-English languages;
English/Automatic retain original filenames. Switching does not delete signatures.
Application tracks speech-language selection; service verifies enrollment language
and model before saving. Enrollment provides native-language reading prompts and
explains profile scope. Deleting profiles explicitly deletes all models/languages.
Korean setup must be performed by user; microphone never started automatically.
Need fresh Korean enrollment, then a longer natural opening followed by short plans
to check real attribution. This is calibration infrastructure, not proof of accuracy.
103 core tests and 22 targeted Pixel checks pass: copied-note status/token stability,
new edited command and fresh-live eligibility, language/model storage isolation and
reload, CAM++ regressions and all automatic-event tests. Both variants build/lint pass.
Installed normal 0.20.0/code33 with data preserved. Verified original English CAM++
signature still has identical size and modification time (2398 bytes, Oct 27 23:47).
User completed Korean setup. Verified Korean CAM++ signature exists (2425 bytes,
Oct 28 14:39 phone time), original English signature unchanged, and capture screen
says voice setup complete. Live Korean speaker attribution remains to be tested.
Real post-enrollment follow-up: all 3 speech chunks classified USER, zero unknown/
other. Profile window scores .75/.75/.75, .71/.73 and .79/.70; consistency .85,
.65 and .68. This real sample establishes Korean setup improved this trial, not
general biometric accuracy. Party sentence recognized as 저는 내일 오후 18시에
파티에 갈 거예요. Contradictory 오후+18 clock was rejected; no party/reminder saved.
Speaker test passed on this example, full event/reminder trial did not. Numeric
ASR/ITN remains next issue; do not silently reinterpret 18 as intended 8.

Previous 0.19.2:
Real 0.19.2 Korean follow-up passed the event changes: same original clean meeting
id a88636c0-9165-44f3-94dc-585d548ef5c8 moved from 18:00 to 19:00 on Oct 29 in
America/New_York, then CANCELLED. Journal before snapshot confirms rescheduled
accepted state at 19:00; after snapshot confirms cancellation. Old bogus entries
unchanged, total records stayed 15. Transcript semantically correct with spacing
and one punctuation-only line. Ownership still UNKNOWN/informational; speaker ID
remains unresolved. UI still displayed 'Event change unclear' despite saved success;
status needs follow-up. No new voice test required for this same event-change path.

Previous failed trial and fix details:
Real 0.19.1 follow-up still failed: opening transcript was
내일 오후 6 시에 회 의가 있 어요. The split noun 회 의 prevented event detection,
so no recent anchor was set and old meeting stayed accepted at 18:00. Added
horizontal-spacing tolerance inside that noun, retaining evidence and newline
boundaries. Updated populated-device regression to the exact captured phrases,
including a previous clean meeting and two bogus old meetings.
103 core tests and 18 AutomaticMemoryTest device checks pass. Exact captured
spacing reuses the prior clean record, moves it to 19:00 and cancels it; old bogus
records remain unchanged. Both variants build/lint pass. Real 0.19.2 microphone
follow-up is still pending; previous trial's actual saved event was not modified.

Previous 0.19.1:
Real Korean 0.19 trial: create/move transcriptions correct, cancellation had extra
particle (회의의 를). Meeting saved informational at 18:00, but older bogus Korean
trials made generic meeting corrections ambiguous, so neither applied. Speaker
remained UNKNOWN; no personal reminder was created. This trial is not an end-to-end
pass. Fix: bare localized nouns prefer the matching recently discussed event in the
same channel (120s), keeping ownership, named-target and expired-context guards.
Normalize one extra 의 only when the remaining target is an exact bare event noun.
102 core tests and all 18 AutomaticMemoryTest Pixel checks pass, including the
captured Korean three-command flow with two older meetings left unchanged, and
ambiguity without recent context. Both variants build and lint successfully.
Normal 0.19.1/code31 installed preserving data. Korean remains selected. Need a
fresh real three-command test; previous real meeting remains accepted at 18:00,
and older trial entries were preserved. No new speaker setup performed.

Previous 0.19.0:
The user's Korean microphone trial failed ASR badly and saved bogus fragments.
Adaptive Korean now uses offline SenseVoice int8. Pixel direct recognition checks
passed eight synthetic command clips; the paced VAD/ASR/automatic-event pipeline
passed create, negative cancellation, reschedule and cancellation for two voices.
Three targeted device tests and 102 core tests pass; both variants build/lint pass.
Quiet pauses now clear prior audio context and preserve text boundaries; an earlier
unresolved Korean clock cannot borrow a later clock. Explicit Whisper modes remain.
Real user Korean microphone verification is still required. Keep CAM++ profile;
no new enrollment needed. Details and model limitations: SPEECH_QUALITY.md.
Normal 0.19.0/code30 installed with data preserved; screen verified Korean,
SenseVoice int8 and microphone off. English live-event/end-time regression passes.

Previous 0.18.0:
Real CAM++ personal dinner/reminder trial passed. Added explicit corrections in all
five added languages, shared localized timing validation, generic cross-language
subject matching, post-reschedule native continuations and neutral localized captions.
98 core tests, 19 initial + 11 correction Pixel checks, both builds/lint pass.
User chose Korean for the next real spoken test. Set transcription to Korean for
this trial, leave microphone off; previous language English should be restored after
verification unless the user asks to retain Korean. Existing CAM++ profile is kept.

Previous 0.17.0:
ResNet short real-user trial still failed (.75 profile/.72 reference), so compared
three speaker models on Pixel. Advanced Chinese/English CAM++ separated fixture
speakers more clearly/faster; English-only CAM++ performed poorly and is not shipped.
Added CAM++ option, separate model-specific profiles, 192/256-dimension guards,
model-specific short cutoffs and preserved legacy profile/model. Normal version 0.17.0/code28 is installed. Selected CAM++ via Settings and opened
its enrollment screen, microphone OFF; verified legacy voice-profile.json still exists.
Need the user to tap Start voice setup, then run a fresh short personal-plan test. 92 core tests, legacy speaker regressions
and final CAM++ device check pass, both builds/lint pass. User chose phone timezone;
capture and saved activity ranges now show zone explicitly, no time changes.

Previous 0.16.1:
Actual lecture test timed/merged correctly but remained informational due to short
speaker rejection (.83 profile/.77 recent mean). Recent reference bank now supports
two-window agreement plus unchanged enrollment/score thresholds; TTL60s, no renewal
from short results. Existing profile preserved. 90 core/three Pixel checks pass,
both builds/lint pass. Need fresh personal-plan test of the user's short phrases.

Previous 0.16.0:
User replaced profile; actual trial matched 6 USER/1 UNKNOWN and personal workshop
has a reminder. New work: known-plan model bypass, unresolved-only smart input with
full-source validation, graceful token/model fallback, English report guards, literal
same-event attendance consolidation/reminders, reversible old announcement cleanup.
86 core tests, final 24 targeted Pixel checks, both builds/lint pass. Need combined
spoken announcement -> attendance -> end-time trial. Existing profile/data preserved.

Previous 0.15.2:
User's own workshop/move/cancel trial passed on 0.15.1; voice event is cancelled
at revised 9 pm. All voices unassigned, so next work addressed speaker boundaries.
Speaker embeddings now trim only quiet edges, retain internal pauses, use selected
speech duration, and keep all acceptance/anchor thresholds unchanged. Existing normal
voice profile preserved. 82 core tests and three targeted Pixel model checks pass;
both builds/lint pass. Need longer own-voice trial, not another event grammar trial.

Previous 0.15.1:
Adaptive English short-phrase decoding (base for isolated <3s phrases, tiny for longer
or queued speech) is default unless an explicit model preference exists. Eight short
synthetic clips match references; actual paced VAD/ASR create/move/negation/cancel and
copied-note ViewModel regressions pass on Pixel. 78 core tests, 21 active Pixel checks,
both builds/lint pass. Copied voice provenance prevents the duplicate seen by the user.
Own-voice quality still needs a real trial; no general accuracy/battery claim.

Previous 0.15.0:
Explicit English event moves/cancellations now run in Hands-off mode across live,
typed/shared and opted-in message sources. Source isolation, ambiguity rejection,
future/DST time validation, preserved duration/reminder lead, CANCELLED History and
persistent notification Undo are implemented. See EVENT_CHANGES.md.
Physical Pixel recovery was verified after the user started live notes and rebooted:
RESUME_ASSISTANT microphone FGS active, AudioRecord active at 16 kHz and not silenced,
SideKick still the selected assistant. This verifies recovery/microphone access,
not transcription accuracy or all-day reliability.

Previous 0.14.0:
User authorized the default-assistant route. Pixel's role holder/active voice service
are now SideKick, screen/screenshot context disabled (mask 3). Recovery is enabled
but unarmed until the user starts listening once. Stop disarms; enrollment never
resumes. System-bound assistant + sticky microphone service + boot/unlock receiver
implement recovery without root. Three targeted emulator checks, host process-kill
recovery and full reboot recovery passed with emulator host mic off. Google restored
as emulator default afterward. Both builds/lint pass. Pixel microphone/reboot recovery
was subsequently verified on 2026-10-24 after the user rebooted and unlocked. See ASSISTANT_MODE.md.

Previous 0.13.0:
User explicitly requested automatic persistence/corrections without per-item approval.
Hands-off defaults on in the normal app; Review remains an opt-out in Settings.
Live, opted-in incoming messages and shared text invoke a serialized automatic saver.
Matching corrections apply without opening an editor; personal clear times get reminders.
Other/unknown voices remain information-only. Room v5 adds bounded persistent Undo;
notification actions can revert a save/update without opening the app. Existing records
migrate without deletion. See HANDS_OFF.md for limits and current persistence policy.
This supersedes the older approval-only requirements below. Audio/full live text stay
temporary. Android still requires user-started listening; no microphone auto-start.
29 targeted Pixel checks passed in 7.684 seconds, including real VAD/Whisper-to-save,
notification Undo without an Activity, persisted journal, migration and both UI modes.
Normal/benchmark builds and lint pass. Inference model comparisons were not repeated.

Previous 0.12.1:
User reported the 0.12.0 microphone trial worked. Phone UI confirmed the expected
start, midnight rollover and venue; attribution was still unassigned and repeated
party cards were visible. This validates that example's extraction, not speaker accuracy.
Added conservative duplicate prevention across voice-label changes: same normalized
opening, start timestamp and capture times within two minutes. Original ownership
and manual edits stay unchanged; new details require explicit update confirmation.
Saved/dismissed repeats are suppressed within the current RAM review history.
Seven duplicate checks and eleven existing update/event/UI regressions passed on
the Pixel in 2.487 seconds. No speech/model benchmarks were repeated for this text
change. Ambiguous or different events stay separate; see SPEECH_QUALITY.md.

Previous 0.12.0:
Ten-second ASR windows, one-second overlap, bounded independently attributed RAM
context, overlapping speaker windows and review-only uncertain updates are implemented.
Tiny stays default; optional base improved two of five public language samples,
tied one and worsened two, with about 2.3x processing cost. See SPEECH_QUALITY.md.
68 core tests and 18 targeted Pixel checks pass, including 44 seconds of paced tiny
speech without queue overflow and the explicit update confirmation screen. Prior entries below
are historical and do not override current behavior or establish microphone accuracy.

Previous 0.11.0:
Basic local event/date/place grammar now supports the five added languages, preserving
original evidence and ownership. Includes explicit clocks, tomorrow/weekdays/next week,
midnight/duration endings and cross-language continuations. Recognized typed events
do not need Qwen. 65 core tests pass. See MULTILINGUAL_EVENTS.md for scope and tests.
This does not claim general multilingual reasoning or fix the prior real ASR/voice
failure. Updated UI describes the bounded grammar instead of claiming English-only.
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
