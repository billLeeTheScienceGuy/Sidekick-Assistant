# 0.29 broader speech and recovery validation

November 9, 2026. All 186 core tests pass. Normal debug, benchmark and compactTest
builds/lint pass. The final isolated emulator suite passes **117 checks in
325.575 seconds**; the compact-package suite passes **27 in 54.461 seconds**.
Fresh isolated installs now explicitly prepare notification permission so Android's
permission sheet cannot obstruct Compose checks. An initial unprepared full run
failed five notification/UI checks; the harness was fixed and the entire suite rerun.

The final isolated physical Pixel run passes **nine checks in 403.939 seconds**:
Automatic command safety, combined seven-language paced VAD/turns/ASR, selected
Adaptive commands, padded/repeated negations, Korean quiet-boundary/action flows
and neural speaker context. No physical microphone or personal app instrumentation
was used. Automatic short commands meet the measured **31/34** baseline; three
Arabic clock cases remain unresolved. All 34 reject wrong times/actions. This
baseline records a newly measured limitation, not a claim of complete recognition.

The broader recorded evaluation adds 28 public FLEURS readings, selected-language
clean/noisy trials, paired model candidates, 112 Automatic raw-model comparisons
and 87 owner/unfamiliar/overlap audio trials. Zero unfamiliar/overlap regions were
assigned USER; noisy owner recall remains weak. These are development recordings,
not population accuracy or a native-speaker audit. Report, raw public outputs,
provenance and reproduction are in ROBUSTNESS_EVALUATION.md and evaluation/.

A six-minute silent screen-off emulator check passed (367.673 seconds suite time),
with zero ASR chunks. Global mute stopped/disarmed capture with a generic private
notification (one check, 8.863 seconds). A verified host-driven process kill recovered
an armed assistant session after 2.074 seconds; mic revocation stopped/disarmed it
after 2.757 seconds and regrant did not rearm it. Digital silence is valid PCM;
no-read stalls and native errors explicitly stop. No host microphone was enabled.

The seven-language combined physical replay emitted eight regions and drained its
final backlog in 0.864 seconds. USB remained connected. No unplugged battery,
within-sentence code-switching or natural-conversation performance is claimed.

Normal 0.29.0/code47 was installed with `install -r` on the physical Pixel;
Android package version and successful MainActivity launch were verified. No
personal app data was cleared or instrumented. The normal APK is 226,238,474 bytes;
optional speech packs remain separate. The submission includes raw public fixture
evidence and recursively checksummed artifacts.

Previous validation (historical):

# 0.28 integrated speaker turns, calendar mirrors and region guidance

Final remote checks confirmed November 9, 2026: code commit `ade3797` passed
both fresh Linux workflows, [main](https://github.com/billLeeTheScienceGuy/Sidekick-Assistant/actions/runs/42549902341)
and [working branch](https://github.com/billLeeTheScienceGuy/Sidekick-Assistant/actions/runs/42549902361).
The submission's 17 checksum entries and bundled segmentation model hash/license
were independently verified; its build metadata records a clean source revision.

November 8, 2026. All 175 core tests pass. Debug, benchmark and compactTest
build/lint checks pass. The broader isolated emulator run passes all 116 checks
in 311.917 seconds: the previous pipeline/UI/notification/location/reminder
coverage plus real neural segmentation, recorded owner/second-voice/overlap,
short/padded ownership, sustained paced native inference, separate anonymous
context, same-voice cancellation, calendar recovery/protection, real LOCAL
CalendarProvider writes, v1→v6 migration and opt-in region privacy controls.

Eight final isolated physical Pixel checks pass in 12.314 seconds: native speaker
turns and ownership, anonymous correction isolation, opt-in coarse-region cache
and URI validation, bundled offline ASR/input cleanup and full v1→v6 migration.
No microphone or personal app instrumentation was used.

All 27 compact-package checks pass in 46.896 seconds, including cold-start calendar
source links, rejection of action-like link parameters, missing-pack setup,
native imported-pack inference, 160% settings navigation and opt-in privacy controls.
The link test restores its original launch Intent for ActivityScenario cleanup;
changing it through onNewIntent had initially caused teardown failures despite
passing the event-opening and no-write assertions. That harness issue is resolved.

The separate ten-minute controlled physical replay passed in 686.933 seconds of
test-suite time (686.709 seconds measured trial wall time). It emitted 180 segments,
recognized all 60 party keyword segments and assigned none to the enrolled public
owner voice. Processing work was 265.306 seconds; final drain 1.581 seconds.
Maximum PSS was 408,888 KiB, thermal status remained zero, and observed battery
temperature rose from 24.5 to 25.7 °C. USB was connected. Repeated public/synthetic
audio does not establish natural conversation accuracy, microphone reliability,
battery drain or 24/7 performance. See SUSTAINED_TURNS.md for reproduction/scope.

The normal APK is 226,618,054 bytes, including the verified 1.54 MB segmentation
weight and MIT notice. Optional speech packs remain separate. The unchanged
authored text corpus retains 98.8% recall and one missed Arabic-dialect reminder;
these metrics do not measure ASR or inferred-speaker accuracy.

Normal 0.28.0/code46 was installed with install -r on the connected Pixel, package
version verified and MainActivity opened. This upgrade did not instrument or clear
the personal app. No app-process Room or AndroidRuntime errors were reported at
the launch check. Calendar sync and phone-region lookup remain opt-in.

# 0.27 capstone validation

163 core tests pass. New retention cases check opt-in defaults, strict cutoff,
latest evidence/activity/reminder timestamp, future items, undated accepted items,
pending reminders and terminal states. Local risk-rule cases check requests,
defensive advice, ordinary gifts, deduplication and absence of credential copying.
Additional language regressions cover German evening attendance, third/unknown
speakers, optional Uhr, midnight ambiguity and Arabic twelve-at-night behavior.

The unchanged 92-case text corpus now reports 100% precision, 98.8% recall and
one missed reminder; no false/mistimed reminders in these authored cases.
This is a development text corpus, not a representative ASR or user study.

13 compact emulator checks passed in 38.542 seconds, including JSON export's
Unicode/null/schema behavior and privacy exclusions; retention removing both
rows and Undo evidence; explicit destructive-cleanup confirmation; 160% text
settings navigation; dismiss/disable/rate-limit behavior; and selected-app/new
message gating. Setup never opens the microphone and blocks missing-pack modes.

Four isolated benchmark-package checks on the physical Pixel passed in 5.763
seconds: native bundled English transcription with zeroed input, full v1→v5
database migration preserving source data, export privacy, and retention cleanup.
They use synthetic/public test files and temporary test databases; no personal
app instrumentation or microphone capture occurred. The total is suite duration,
not a per-model latency, peak memory or battery measurement.

The broader run initially passed 94/95 and exposed a notification ingestion
timeout. Listener connection state/rebind handling was added, and that test now
explicitly selects Review mode and awaits the real Android binding callback.
All 12 targeted notification/location/activity/localized checks then passed in
34.457 seconds. Debug and benchmark build/lint pass. Previous main dd80165 also
passed both remote workflows (42533675565 and 42533675514).

The final broader rerun passed all 95 checks in 205.305 seconds, including real
notification ingestion, network-location fallback/geocoder behavior, recorded
VAD/ASR corrections, native bundled inference, migration, Planner/Summary,
calendar drafts, alarm receiver delivery, Undo/rollback, privacy and settings.
Normal 0.27.0/code45 was installed with install -r on the physical Pixel, its
version verified and MainActivity opened. Field testing remains limited as
described in CAPSTONE_CHECKLIST.md; no all-day or perfect-speaker claim.

The final compact build/lint also passed, followed by all 15 compact-package
checks in 42.805 seconds, including native English inference and the full database
migration. Retention/export layout was visually inspected on the emulator. The
distribution manifest and every SHA-256 entry were independently verified after
packaging; source metadata recorded a clean checkout and the exact commit.

# 0.26.1 text-understanding fixes

157 core tests pass. Four new regression groups cover explicit spelled-out clocks
and midnight endings, split timing with unchanged evidence, ambiguous clock choices,
uncertain heading-to plans, multilingual appointment ownership, other/unknown
speakers and third-person counterexamples. Parsing normalizes explicit clock words
internally; original conversation evidence is retained.

The unchanged 92-case corpus improves from 10 to two missed reminders: 100%
precision, 97.6% recall and no false/mistimed reminders. Matched-event exact time
and attendance are 100% on this corpus. Colloquial German and dialectal Arabic
cases remain failures. These are authored text checks, not new ASR measurements.

CompactTest build/lint and normal debug build/lint pass. Seven targeted emulator
checks passed in 12.88 seconds across ActivityTimingTest and CompactSetupTest.
The build is 0.26.1/code44; the submission bundle and evaluation report are updated.
No real microphone, speaker calibration or battery testing was performed.

# 0.26 compact setup, model packs and evaluation

153 core tests pass. New coverage checks streamed model-pack validation,
incomplete/corrupt/unsafe archives, cancellation, staging cleanup and recovery.
The 92-case authored multilingual text evaluation is reproduced by the core test
task and documented with its failures in ACCURACY_REPORT.md. It measures local
event parsing, not transcription or speaker recognition. No false or mistimed
reminders appeared in this corpus; 10 expected reminders were missed.

83 benchmark emulator tests passed in 129.95 seconds, including paced recorded
VAD/ASR corrections. Two additional compact-install tests passed in 56.022 seconds:
language/quality setup disables listening when required packs are missing;
a real verified Whisper pack imports and transcribes the recorded fixture;
a corrupt replacement is rejected while preserving the installed pack.
The final collapsed-pack onboarding layout was checked again separately.

Debug, benchmark and compactTest builds/lint pass. The normal APK excludes optional
speech weights and is about 225 MB rather than 905 MB. The submission preparation
script checks the APK contents and writes a bundle manifest with SHA-256 hashes.
Existing Pixel Korean model files were checked before upgrade; no fresh live
speech, speaker calibration or all-day battery trial was performed.
Normal 0.26.0/code43 was installed with install -r on the connected Pixel and its
package version verified. The final setup recheck passed in 8.086 seconds, and
the compact checklist was visually inspected on the emulator.

# 0.25 summaries, attendance and delayed corrections

147 core tests pass. Added coverage for phone-zone daily boundaries, a 25-hour
DST day, overnight overlap, reminder versus activity dates, unknown dates, current
history states, draft exclusion and exact temporary excerpts. Attendance cases
cover named subjects, multiple events, withdrawal/rejoining and affirmative,
negative, quoted and reported phrases in all seven selected languages.

Both debug and benchmark builds/lint pass. The full emulator run passed 82 tests
in 131.312s. New integration checks cover summary editing and clearing without
deleting saved events, no summary cards for temporary drafts, calendar-editor
intent fields without private evidence, rejection of invalid exports, one-event
named attendance/rejoining/Undo, and corrections delayed relative to capture.

The broader run exposed a real bug: paced speech was recognized correctly, but
processing-wall-time anchors could discard later pronoun commands. Anchors,
correction context and bounded transient observations now use capture timestamps.
The paced real-VAD/adaptive-ASR move/negation/cancellation trial then passed.
An uncertain-update UI assertion now waits for both database save and draft
cleanup rather than racing the asynchronous operation.

The final grouped-message improvement processes creation/move/cancellation in
chronological order. Eight targeted message/localized tests passed in 1.377s,
including the new reversed-input grouped case. Across the final runs, 83 distinct
emulator checks passed. No fresh physical microphone trial or battery claim is
included. Calendar tests inspect drafts; they do not save to a real account.

# 0.24 phone-free validation

Integrated Yi Huang's English date changes from 6f9cff4 without rewriting the
commit. GitHub Actions success verified for that commit (41921614060) and the
previous main commit 9b9d650 (41940141857).

140 core tests pass. New cases cover alternative clocks and relative delays,
compound-duration rejection, exact seconds in elapsed reminders, DST boundaries,
midnight, alternative multilingual clocks, conflicting ISO/relative local dates
and conflicting Arabic end dates. Debug and benchmark builds/lint both pass.

77 instrumentation checks passed on the headless Pixel_7a emulator in 109.27s:
Foundation, Planner, automatic sharing, synthetic calendar reads, activity timing,
automatic memory, event changes, multilingual changes, messages, copied speech,
reminders, scheduling rollback, reminder journey, Undo, uncertain updates,
duplicate protection and language profile isolation. The unchanged native
VAD/Whisper inference test was excluded from this batch. New integration checks
exercise month/day creation, relative rescheduling with one event and Undo, and
withholding an automatic reminder for ambiguous “next Friday.”

Three assistant checks also passed in 6.159s after selecting the benchmark
assistant on the emulator: recovery with explicit Stop protection, competing
recognition capture and silence timeout, and service registration. These used
the silent virtual microphone, with assistant settings restored afterward.

Total: 80 emulator checks. The physical Pixel was unavailable. Emulator success
does not establish real-world ASR, speaker accuracy, battery use, OEM recovery or
real messaging-app behavior. The normal 0.24 APK was built for later phone use.

# 0.23.1 broader self-test pass

122 core tests pass. Both variants build and lint successfully. New regressions
cover long notes and quoted text, out-of-order grouped messages, and stale unseen
notifications. Notifications are processed chronologically and cannot rewind the
current conversation context.

Expanded physical Pixel checks covered Foundation, Planner, sharing, calendar
filtering, activity timing, uncertain-speaker updates, voice duplicates, language
profile isolation and assistant registration. Four UI test failures were stale
fixtures: they inherited Hands-off state or searched for uncomposed lazy-list
content. Review tests now explicitly establish/restore their mode and scroll the
outer lazy list; microphone status is checked from service state. A final targeted
41-test run passed 40, with one remaining lazy-list fixture fixed and verified by
its passing 7.152s rerun. Message, automatic-event and correction regressions passed.

The emulator-only assistant recovery test was skipped on the physical Pixel.
No new live microphone, long battery trial, real-account integration or model
accuracy claim is included. Existing 0.23 device coverage is recorded below.

# 0.23 conversation context

121 core tests pass, including split date/time in English, Korean, Chinese,
French, German, Russian and Arabic, long bounded context, ambiguous clocks/dates,
named and generic references across multiple events, and attendance ownership.
Both debug and isolated benchmark variants build and lint successfully.

46 distinct physical Pixel 7a tests passed across final runs. A full run covered
AutomaticMemory, EventChange, LocalizedChanges, CopiedSpeech, EventReminderJourney,
MessageInbox, Reminder, ReminderRecovery and UndoPlanner. Its one remaining replay
regression was fixed and all 31 AutomaticMemory/EventChange tests then passed in
25.815s; the other 15 had passed. Earlier failures exposed missing attendance
reminders, lost recent-event anchors and duplicate inferred-date refinements;
those paths now have passing coverage. The UI diagnostic fixture clears its own
previous check before waiting for the button's asynchronous save.

Tests use the isolated .benchmark package, in-memory databases or their own
synthetic records, and stored synthetic audio. They do not activate the real mic,
read real messages or establish real-world multilingual ASR/voice accuracy.
Long unplugged listening remains deferred by user choice. No fresh phone trial
is required to validate these deterministic changes.

# 0.22.1 perceived reminder failure

Read-only physical inspection showed the diagnostic due at 1793250568073 and
deliveredAt 1793250582110: 14.037s late. Android still held its notification; it
was not DND-intercepted. POST_NOTIFICATIONS and lock-screen notifications were
enabled. Ringer mode was SILENT, notification stream muted, channel vibration off
and importance DEFAULT (no pop-up). Enabled channel vibration/pop-up through
Android UI and verified both switches; global Silent mode was left unchanged.

Setup status now distinguishes enabled delivery from sound/ringer, vibration,
pop-up and DND settings and links the exact channel controls. Added VIBRATE normal
permission, REMINDER category, HIGH/vibration defaults for newly created channels.
Existing user channel settings remain controlled by Android; no new channel ID or
global unmute is used to override them. See the
[Android channel documentation](https://developer.android.com/develop/ui/compose/notifications/channels).
Both variants build/lint pass. Five targeted physical-device tests passed in
6.517s, covering VIBRATE grant, REMINDER category, delivery/cleanup, settings UI,
Planner Undo and the real receiver workflow. A new sound/vibration field check
still requires the user to select Ring/Vibrate mode.

# 0.22.0 reminder/Undo reliability

112 core tests and both variants' builds/lint pass. 33 targeted Pixel checks passed
in 30.467s, covering alarm-adapter failure rollback, Planner Undo after notification
dismissal, event/correction/cancel/Undo/reminder/complete workflow through a real
PendingIntent/ReminderReceiver and NotificationManager, plus prior reminder, event
change and automatic-save regressions. After adding one-tap Setup status check,
3 UI/receiver checks passed in 6.199s (34 distinct targeted cases overall).

Only isolated benchmark package data was used. Scheduler-failure cases inject at
the platform adapter; broadcast journey can explicitly send the real pending
broadcast, so it does not verify AlarmManager timing or Doze. The remaining normal
app locked/unplugged notification trial is described in RELIABILITY.md. No additional
speech test was requested and no microphone was opened by these tests.

# 0.21.2 final bounded Korean repair — 2026-10-28

User reported one success and one failure, then requested moving on. UI showed
correct 오후 8시 followed by hybrid 오후 열8시 and a duplicated polite ending.
The hybrid bypassed retry detection. Detector now covers mixed-script and spelled
Korean teens; exact comparison tolerates a duplicated final 예요/에요 only. Only
the clock span changes. Captured-form regression and changed-date rejection pass.
112 core tests, normal build and lint pass. No new live number trial requested;
the remaining recognition limit is documented rather than declared resolved.

# 0.21.1 repeated Korean number failure — 2026-10-28

The user's real trial on 0.21 still displayed two separate 오후 18시 readings for
the eight-pm prompt. Previous second-model results were not retained, so they
cannot be inspected retroactively. Invalid-clock retry now tries full-context
Whisper base first, then Omnilingual only if needed. Only the clock span is
replaced; punctuation and surrounding source wording remain untouched. Existing
guards remain. Bounded clock-only diagnostics live in RAM and appear in capture;
they show model clocks, unresolved/accepted result, and availability failures.
No private transcript logging or saved audio was added. Real success still needs
a new trial; the added isolated test checks two synthetic eight-pm phrases using
the actual full-context base model and the captured invalid-clock text.
Both eight-pm fixture paths passed with an asserted 20:00 result and unchanged
context. Shared adaptive recognition/model switching also passed: 2 device tests
in 70.854s. 111 core tests and both builds/lint pass. This does not establish that
the user's repeated real microphone failure is resolved.

# 0.21.0 Arabic and language quality — 2026-10-28

See ASR_EVALUATION.md for the public/synthetic physical Pixel comparisons, including
clear limits. Arabic public CER: tiny46.6%, base38.4%, Omnilingual9.6%; Chinese
SenseVoice0% versus Whisper27.3% on one reading. These are not representative corpus
benchmarks. Arabic commands still miss some moves, and dialects need native speakers.

111 core tests pass, including Arabic extraction/timing/end/location, digit/diacritic
normalization, ambiguity/negation/ownership guards, cross-language corrections,
compound spoken hours, backend routing and conservative Korean clock repair.
Both variants build/lint. 24 targeted Pixel checks pass: production adaptive event
creation in seven languages with backend switching/audio wiping, four negative
commands, long FR/DE transcription, multilingual event/reschedule/end/cancel/Undo
(now including Arabic), prior automation and copied-note status. A final recognition
smoke rerun after the guarded Korean retry addition passed (1 test, 46.965s).
No private mic was opened.

Korean retry uses a second actual acoustic model only for invalid 12-hour clocks;
the earlier user's discarded audio cannot be replayed. Core selection tests prevent
rewriting valid clocks, questions/quotes, identities, dates, negations or minutes.
The original real-world number failure still needs a fresh microphone check.

# 0.20.0 copied status and language voice setup — 2026-10-28

Real Korean post-enrollment trial: UI counted 3 USER, 0 OTHER, 0 UNKNOWN chunks.
Window profile scores .75/.75/.75, .71/.73 and .79/.70 with minimum consistencies
.85/.65/.68. Recognition included 저는 내일 오후 18시에 파티에 갈 거예요.
No new saved party was present. Grammar rejected contradictory meridiem + 18;
this test passes attribution only, not time recognition or personal reminder saving.
Intended prompt was 오후 8시; actual discarded audio cannot be replayed to isolate
acoustic decoding versus inverse-text normalization. Do not guess 8 from 18.

103 core tests and 22 targeted Pixel tests pass (11.608s instrumentation): copied
live move/cancel commands can be analyzed repeatedly without changing the Undo token
or replacing success with ambiguity, while edited corrections and fresh live commands
remain eligible. Language-profile tests verify English/Korean persistence across
reload, language/model isolation, missing-language state, and complete deletion.
Existing CAM++ checks and 18 automatic-event checks also pass. Both variants build
and lint pass. No physical microphone was opened; real Korean enrollment and subsequent
attribution remain unverified. No thresholds were relaxed.

# 0.18.0 multilingual event changes — 2026-10-27

The user's real CAM++ dinner trial passed: "I will attend dinner tomorrow at 6 p.m."
was saved as a personal accepted event with a 6 pm reminder, in phone zone
America/New_York. This is one successful real example, not broad biometric accuracy.
User chose Korean for the next spoken correction test.

Explicit rescheduling/cancellation now supports Korean, Chinese, French, German and
Russian in addition to English. Supported examples are in EVENT_CHANGES.md. Native
commands enter the same serialized source-scoped event matcher, reversible reminder
coordinator and Undo journal. Original names and command evidence are retained.
Korean/Chinese particles, French/German articles and limited Russian meeting/party
case forms are handled. Generic event types can match an event in another language;
named targets still require literal matching. Earliest event noun determines a generic
subject, so "meeting about a party" is not targeted as a party. Multiple eligible
matches remain unresolved; other/unknown speech cannot cancel a personal event.

Date/clock resolution reuses localized grammar but requires every timing character to
belong to a recognized date, clock, connector or punctuation. Ambiguous bare clocks,
multiple clocks, alternatives, negation, questions, reported/quoted requests, unknown
words, past starts and invalid/ambiguous DST clocks do not apply. Missing dates retain
the event date; date-only moves retain the clock. This is bounded grammar, not general
multilingual reasoning or free-form event identity resolution. Substantial morphology
and arbitrary reformulations remain unsupported.

A localized reschedule uses a neutral event caption with current times in date fields;
original wording stays in evidence and can still match later named corrections. Fresh
localized midnight/end/place continuations can attach to the rescheduled event using
a localized reference opening. Known duration and reminder lead time remain preserved.

98 core tests pass. Initial 19 Pixel checks passed in 6.037s: five complete localized
create/move/end/cancel/Undo flows plus conversation-scoped Chinese notification text,
automatic-memory and copied-note regressions. Eleven additional localized/English
correction checks passed in 20.656s; final core checks also cover generic cross-language
subjects, names/modifiers, Korean spacing, unsupported times and DST. Both builds
and lint pass. These device checks used synthetic text and isolated benchmark data,
not the physical microphone or private messages. A final three-check native flow run
also passed in .946s, including protection for manually corrected event identity
against stale original evidence. Real Korean ASR/correction trial is
next; transcription will be temporarily set to Korean for that test. The prior language
was English; restore it after the trial unless the user requests otherwise.

# 0.17.0 speaker-model evaluation — 2026-10-27

User chose following the phone timezone, with clear labels. Capture now names the
phone zone; saved activity ranges also name their recorded zone. No timezone setting,
existing timestamp or phone clock is changed.

Evaluated three models directly on Pixel with the same sherpa-onnx runtime: current
WeSpeaker ResNet34-LM, 3D-Speaker CAM++ English VoxCeleb, and CAM++ Chinese/English
advanced. Two synthetic voices have separate natural enrollment/short-plan/end-time
utterances, with an additional public JFK disjoint enrollment/short pair and five
public language clips used as different-speaker comparisons. Original fixture audio
is public/synthetic, not a recording of the user. No physical microphone was opened.

Measured five same-speaker short similarities vs different-speaker maximum:

| Model | Same-speaker range | Different-speaker max | 13 embeddings |
|---|---|---|---|
| WeSpeaker | .776–.885 | .596 | 6.557s |
| CAM++ English | .264–.593 | .782 | 2.030s |
| CAM++ Chinese/English advanced | .651–.852 | .234 | 2.027s |

The English-only candidate is excluded from production. Advanced CAM++ is added as
a selectable, offline 28.3MB candidate. Its cosine scale differs: short enrollment
and recent-reference cutoffs are .60 for this model, with duration/expiry/turn checks
retained. Existing ResNet cutoffs stay .80. Long-turn matching remains conservative.
These small fixture results motivate a real-user trial, not calibrated population
false-accept rates, household-speaker accuracy or a guarantee of improvement.

CAM++ uses 192-dimensional profiles; legacy uses 256. Model hashes/dimensions are
validated, mixed dimensions rejected, and model-specific files preserve the previous
profile on switching. Existing installations retain their old model/profile; fresh
installations choose CAM++. Switching requires one voice setup for the new model.
Deleting voice profiles removes both models' signatures. No raw enrollment audio is
saved and no new network permission is added. Attribution/license is included.

92 core tests pass. Model comparison passed in 12.871s. Three existing speaker checks
passed in the integration run; final CAM++ integration passed in 5.593s, including
a trimmed held-out phrase (profile .61/recent mean .62), eight different-speaker
rejections, profile reload/switch-back, disable/delete and wrong-model rejection.
Initial switch-back test used an invalid zero float tolerance; changing to 1e-6
accounts for normalization/JSON roundoff and the corrected test passes. Both build
variants and lint pass. Installed normal 0.17.0/code28, selected CAM++ through the app UI and opened its
enrollment screen with microphone off. Legacy signature file remains present.
Real-user CAM++ enrollment/short-phrase testing is required.

Sources: official sherpa model release and 3D-Speaker model card/project:
https://github.com/k2-fsa/sherpa-onnx/releases/tag/speaker-recongition-models
https://modelscope.cn/models/iic/speech_campplus_sv_zh_en_16k-common_advanced
https://github.com/modelscope/3D-Speaker
ModelScope API identifies the advanced weights as Apache License 2.0.
CAM++ SHA256: aa3cfc16963a10586a9393f5035d6d6b57e98d358b347f80c2a30bf4f00ceba2

# 0.16.1 short speaker references — 2026-10-27

The preceding real lecture trial produced one correctly timed 7–9 pm event, but it
remained informational: short profile .83 / recent mean .77 at 24 seconds failed
identity; later short phrases lost their anchor. No personal reminder was scheduled.

Recent voice matching now keeps embeddings from up to two independently accepted
long turns (at most six windows and one mean per turn). A short phrase still needs
profile >= .80 and either the original mean >= .80 or at least two reference windows
>= .80. One favorable window cannot qualify. No additional embedding inference is
needed. No audio or new persistent biometric data is retained. Existing profile stays.

Reference matching expires at 60 seconds rather than 30 to accommodate pauses.
Short results never renew time or add trusted embeddings. Borderline short evidence
with strong enrollment similarity stays UNKNOWN but can retain the trusted references;
weak/conflicting evidence, invalid/quiet input, long unassigned/other speech, expired
or reversed clocks clear support. Sub-1.5-second speech still cannot qualify. Diagnostics
show profile, mean, two-window support and age separately.

90 core tests pass. Three isolated Pixel checks passed in 20.83 seconds. Of five
sampled own-speaker clips at a 39-second gap, three matched; two trimmed below 1.5s
correctly stayed UNKNOWN. Eight different-speaker fixtures (synthetic voices and five
public language clips) produced no USER matches. Existing padded/mixed voice and
expired/interrupted identity checks pass. Both builds/lint pass. These small fixture
checks do not establish real-user accuracy; a fresh personal-plan voice trial is next.

# 0.16.0 event consolidation and graceful analysis — 2026-10-27

After replacing enrollment, the user's actual voice trial matched 6 chunks as USER
and left 1 unassigned. Their explicit workshop attendance was saved as personal with
a start-time reminder. Do not characterize speaker ID as universally fixed. Manual
smart analysis still hit its token budget and informational duplicates remained.

Known local plans now bypass Qwen. Only unresolved first-person sentences are sent
to the optional local model; returned candidates are still validated against the full
original context, including quotation/reporting prefixes. Token/JSON/model failures
retain grounded local results and show a fallback notice instead of dropping the
whole analysis. User cancellation propagates. Timeouts also fall back in the view
model. Explicit report/test instructions such as "repeat I'll attend...", "keep your
9pm workshop" and transcript descriptions are excluded from English event creation.
This is a bounded context guard, not general quoted-conversation understanding.

A literal English subject key can link an announcement with later explicit attendance
at the exact same start instant and source. Names/modifiers and conflicting venues or
addresses stay distinct. More than one matching saved target remains unresolved in
Review. Attendance can upgrade only a fresh personal proposal from USER (or owned
typed input), never a replay with a different speaker label. Existing manual edits
remain protected. The upgraded event gets a start-time reminder with persistent Undo;
replays cannot downgrade its personal ownership.

Existing automatic informational announcements matching exactly one personal event
in the same source/time/subject and within two capture-hours can move to DISMISSED
History, with Undo, rather than clutter active events. Manual edits/Undo invalidate
cleanup ownership. Different names/venues and ambiguous personal matches are retained.
No records are deleted by cleanup. The initial scan runs once per process and after
successful event writes, not on every speech chunk; personal targets are indexed by
source/time/subject. Existing voice profile, models and database schema are preserved.

86 core tests pass. Initial integration run passed 32 Pixel checks in 24.725 seconds;
final run after full-context validation and reversible cleanup passed 24 targeted
Pixel checks in 5.211 seconds. Covers creation/attendance/reminder/Undo, identities
with different people/places, forced token failure, cancellation propagation, full
report context, reversible legacy cleanup, copied-note flows and voice-label replay.
Both build variants and lint pass. Physical test microphone was not opened by these
automated checks. The user's combined spoken announcement/attendance/end-time flow
is the next phone test. No speech-model or battery benchmarks were repeated.

# 0.15.2 speaker boundaries — 2026-10-27

The user's real Pixel 0.15.1 trial passed: live text was "There's a workshop tomorrow
at 8pm. Move it to 9pm. Cancel it." The corresponding voice event was CANCELLED with
a revised 9 pm start. Three chunks were all unassigned. Visible first-chunk diagnostics
were 3.3s captured, window/profile scores .74/.63 and consistency .59. That does not
meet identity thresholds and does not establish that the enrollment is bad.

Speaker-only preprocessing now excludes quiet leading/trailing capture padding using
10 ms DC-removed RMS frames, a bounded adaptive edge threshold, three consecutive
active frames, and 40 ms margins. Internal pauses remain intact; no speech from
separate turns is concatenated. Applies to enrollment embeddings and runtime matching,
not VAD segmentation, ASR buffers, timestamps or stored audio. Temporary selection
copies are wiped. Existing saved enrollment is retained.

Matching uses the remaining speech duration: silence cannot turn a short phrase into
a long qualifying phrase. Profile/consistency acceptance thresholds and short-phrase
anchor requirements are unchanged. No identity inheritance from adjacent speech.
The selected speech duration is visible in diagnostics alongside captured duration.
Short standalone phrases can therefore correctly remain unassigned until a confident
long phrase. This is a boundary/quality improvement, not a claim of verified real-user
identity accuracy.

82 core tests pass, including four edge-selection checks for quiet padding, internal
pauses, silence/DC/clicks/nonfinite input, and short-phrase duration. Three isolated
Pixel device checks passed in 11.144 seconds: padded same-voice recognition, no padded
short-phrase identity borrowing, other/mixed-voice rejection, profile reload/disable/
delete and expired/interrupted anchor behavior. Both builds/lint pass. No microphone
was opened during automated checks; normal enrollment/data were not modified. Real
speaker matching still needs the user's longer-speech trial.

# 0.15.1 short-speech quality — 2026-10-24

Inspected the user's Pixel trial: the voice event had moved to 9 pm, but "Cancel it"
was transcribed as "Cancellate". Copying live text for smart analysis lost source
identity and created another 8 pm event. Copied live notes now retain voice provenance
through editing and both rule/smart analysis, preserving ownership and matching the
original event. Command-only notes skip unnecessary Qwen inference. Conversational
"let's", "it's cancelled", "make it nine p m" variants are parsed without guessing
ambiguous clocks or treating "Cancellate" as a cancellation instruction.

Reproduced tiny's cancellation error with two synthetic Windows voices on Pixel.
Beam-3 decoding and full encoder windows did not repair it; full encoder cost about
5 seconds per clip. Silence padding with tiny did not repair it either. Base decoded
both cancellation voices correctly and retained "Do not cancel it". The adaptive
path produced exact normalized reference text for all eight short fixtures, taking
3.169–3.610 seconds per clip in the final run. Tiny's initial cancellation outputs
were incorrect for both voices. These are synthetic samples, not a general accuracy
or real-user voice benchmark.

Adaptive is the new default when no quality preference was set. It uses bundled
base for isolated English phrases under three seconds and tiny for longer/queued
speech. Existing explicit Fast/Larger choices persist. Other languages retain tiny
in Adaptive because prior larger-model comparisons were mixed. No extra download,
cloud inference, phonetic destructive-command substitution or private audio files.
This trades several seconds of short-phrase latency for measured recognition gains;
all-day battery/thermal behavior is not established.

78 core tests pass. Pixel instrumentation: 21 active checks passed in an 81.285-second
run, with two opt-in comparison methods skipped. Includes all eight short references,
actual paced VAD-to-adaptive-ASR-to-event create/move/negation/cancel, a real ViewModel
copy/reanalysis/corrected cancellation flow without duplicate or attendance promotion,
and existing automatic-memory/event-change regressions. Paced stream processing was
1.976/3.619/3.213/3.196 seconds for the four segments and had no queue overflow.
Both build variants and lint pass. Normal app test data was not injected: isolated
benchmark package used synthetic text/audio. The accidental unchanged automatic
8 pm copy on the normal app was removed using its existing notification Undo after
checking its creation snapshot and matching voice original. The rescheduled original
was retained. Real-user microphone quality still needs the user's next trial.

# 0.15.0 event changes and Pixel recovery — 2026-10-24

75 core tests pass, including seven new correction/date/DST/History checks.
29 targeted emulator checks pass in 6.365 seconds: eight event-change flows,
ten automatic-memory regressions, seven duplicate and four uncertain-update checks.
The eight event-change checks also passed on the physical Pixel in 1.124 seconds
using isolated benchmark storage. Both normal/benchmark builds and lint pass. Emulator host microphone remains off.
The event tests use synthetic text and isolated benchmark storage, not the user's
planner or private messages. No language benchmarks or battery trials were repeated.

The user started live notes, rebooted the physical Pixel, then unlocked it.
ADB verified the RESUME_ASSISTANT microphone foreground service, selected SideKick
voice service and active 16 kHz AudioRecord with silenced:false. This establishes
physical recovery and microphone access; it is not an ASR quality measurement.
Installed normal version 0.15.0/code 23 with data-preserving update. Its listening
service resumed after package replacement; AudioRecord remained active, not silenced.

# 0.13.0 automation validation — 2026-10-23

29 targeted Pixel checks passed in 7.684 seconds. Ten automation checks cover local
automatic creation/corrections/reminder scheduling, unknown-voice ownership, disabled
mode and invalid-input failure, saved-event association across sessions, message
callbacks, persistent Undo after database reopen, real notification-action Undo,
v4-to-v5 migration preserving existing data, actual VAD/Whisper synthetic audio to
saved event/end time, and corrected venue/address replay behavior. Share UI saves
without a confirmation dialog; Review UI still requires approval when selected.
Seven duplicate and ten existing event/update checks passed in the same run.

Both build variants and lint pass. Earlier UI test failures were harness problems
(activity intent/lifecycle matching, lazy-list scrolling and text matcher settings);
the final isolated run passes. No physical microphone was opened, no personal
messages were read, and no language/model comparisons or long battery trials rerun.
See HANDS_OFF.md for the user-authorized replacement of approval-only persistence.

# 0.12.1 duplicate validation — 2026-10-23

Seven new duplicate checks plus eleven event/update/UI regressions passed on the
Pixel in 2.487 seconds. Covers voice changes without ownership promotion, changed
details requiring review, accepted/dismissed repeats, Unicode/case/punctuation,
preserved manual edits, distinct events/message sources and all five added languages.
User reported the preceding microphone trial worked; UI confirmed times/venue but
still showed unassigned attribution and repeated cards. No broad accuracy claim.

# 0.12.0 speech/update validation — 2026-10-23

68 core tests pass. Ten targeted Pixel event/update tests passed: five-language event
flow and uncertain correction separation, dismissal, explicit saved-item replacement,
draft replacement without duplicates and interrupted/expired reference rejection.
The five-language tiny/base comparison and five speaker regressions passed together
in 144.441 seconds using isolated benchmark storage, without microphone access.

Base's paced public JFK test produced three chunks, one confidently matched user
chunk and no queue overflow. Total elapsed was 34,853 ms; process CPU 59,576 ms;
maximum combined speaker/ASR chunk processing was 15,364 ms. This short input plus
drain does **not** establish sustained realtime base performance. Tiny stays default.
Both models correctly handled the synthetic midnight phrase alone and with RAM
context; base improved two public language samples, tied one and worsened two.
Detailed sample timings, CER, memory snapshots and reproduction: SPEECH_QUALITY.md.

Default tiny also passed 44 seconds of paced repeated public speech: nine chunks,
seven user matches, no queue overflow, 7,062 ms maximum combined chunk processing,
55,243 ms total test time including setup/drain/different-voice check and 72,153 ms
process CPU. The confirmation UI test passed in 2.023 seconds after correcting its
selector to distinguish the editor warning from the identical list-card warning.
This brings the current targeted device checks to 18. Both builds and lint pass.

Earlier results below are historical; prior five-second-window measurements do not
describe the current ten-second pipeline. Real microphone accuracy remains unproven.

# Version 0.2 validation

## Version 0.11.0 multilingual event rules

65 core tests pass. Six targeted Pixel ActivityTimingTest checks passed in 0.281
seconds, including Korean live draft refinement, Chinese message ownership,
approval-only persistence and recognized German typed events without a Qwen model.
Existing English revision/save behavior passed. Both build variants and lint pass.
See MULTILINGUAL_EVENTS.md for supported grammar and limits; this does not establish
microphone accuracy or resolve the prior failed short-phrase recording.

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
# 0.19.0 Korean speech quality — 2026-10-28

Real 0.19.2 follow-up succeeded: transcript conveyed creation at 18:00, move to
19:00, and cancellation. Read-only DB inspection confirmed the original clean
meeting id retained, start 2026-10-29 19:00 America/New_York and state CANCELLED.
Undo journal before snapshot proves it was rescheduled/ACCEPTED at 19:00 before
cancellation. Two older bogus entries were unchanged; record count stayed 15.
Speaker remained UNKNOWN, so this validates informational event changes only.
The screen still showed 'Event change unclear' despite the successful persisted
changes; status feedback remains inconsistent and requires correction.

0.19.1 real follow-up also failed because SenseVoice emitted 회 의 inside the
opening statement. No new event/anchor was detected; the prior clean meeting stayed
accepted at 18:00. 0.19.2 tolerates spaces/tabs within 회의 without joining across
newlines or altering original evidence. Regression now uses the actual captured
spacing and seeds both older bogus entries and a prior clean meeting to verify
deduplication and context recovery against realistic saved state.
103 core tests and all 18 AutomaticMemoryTest Pixel checks passed (6.059s for
instrumentation). Exact captured spacing successfully reuses the clean record,
reschedules to 19:00, then cancels it without changing the old bad records.
Both variants build and lint pass. This verifies replayed text, not a fresh live
microphone trial, and does not resolve UNKNOWN speaker attribution.

Real microphone follow-up: first two phrases correctly transcribed; cancellation
was "회의의 를 취소해줘." New meeting was saved at 18:00 as informational (speaker
UNKNOWN), but older bad trial entries made generic corrections ambiguous. No move
or cancellation was applied. Therefore the real workflow did not pass. 0.19.1
adds scoped recent-event association for bare localized targets and narrow Korean
duplicated-particle normalization, with populated-database regression checks.
All 102 core and 18 AutomaticMemoryTest device checks pass after this fix.
The populated case uses the actual three captured phrases, leaves two old meetings
unchanged, moves the current event to 19:00, then cancels it. A separate case proves
two matching meetings without a recent anchor remain unchanged. Both builds/lint pass.

The real 0.18 Korean trial failed transcription; multilingual text correctness
was insufficient. Compared Whisper tiny/base and SenseVoice int8 on the physical
Pixel 7a. Exact command and public-speech results, limitations and model provenance
are in SPEECH_QUALITY.md. Adaptive Korean now uses SenseVoice; other language and
explicit Whisper modes keep their prior backend.

102 core tests pass, including quiet-pause boundary preservation, continuation
flags, Korean spacing, backend routing and rejection of the captured garbled clock
sequence. Three isolated KoreanRecognitionTest device checks pass in 47.718s:
eight direct synthetic commands, two paced full VAD-to-event sequences, and the
captured bad-fragment regression. The paced sequence verifies negative cancellation
leaves an event accepted, moving changes the start to 19:00, cancellation retains
one history record, and unknown voices remain informational with no reminder.
No physical microphone is opened by instrumentation. Normal and isolated builds
and lint pass; model preparation verifies hashes. Real microphone quality and
sustained battery/thermal behavior of the new backend remain unverified.
The existing English VAD/Whisper automatic-event and end-correction test also
passed (one device check, 3.59s). Installed normal 0.19.0/code30 without clearing
data; capture screen confirms Korean SenseVoice backend and microphone off.
