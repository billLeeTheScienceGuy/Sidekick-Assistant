# SideKick prototype implementation and evaluation

Prepared November 9, 2026. Version 0.29.0. This describes a working Android
prototype and its measured limits; it is not a claim that the entire Phase 1
proposal or a production 24/7 assistant is complete.

Version 0.29 adds a frozen public-speech robustness evaluation, measured Automatic
and longer multilingual routing, private capture-interruption alerts, PCM-stall
detection and permission-revocation recovery protection. Owner context remains
stable across anonymous cluster changes; continuous Chinese/Korean overlap is
removed without altering numerals. See [broader quality evidence](ROBUSTNESS_EVALUATION.md).
Short Arabic clocks remain imperfect, and no all-day battery claim is made.

## Implemented system

SideKick captures speech during an explicitly started listening session, uses
local VAD and sequential offline ASR, associates speech with user/other/unknown
voice categories, and extracts events and commitments. It stores event evidence,
times, venues and current state locally. Hands-off mode applies supported saves
and corrections with Undo; Review mode is available. The phone timezone governs
date interpretation. Raw recordings are not saved; recent transcript text stays
temporary. Event evidence is retained with saved items.

The action layer includes reminders, optional incoming-message notifications,
shared text, venue lookup with address confirmation, calendar conflict reads and
explicit handoff to the calendar editor, plus opt-in one-way mirroring with
interruption recovery and external-edit protection. Daily summaries derive from saved facts;
conversation highlights are bounded source excerpts. These are not unrestricted
LLM narratives. Optional Qwen assists manual analysis; live extraction uses
bounded grammar and validated fields. Default-assistant recovery supports an
enabled session subject to Android restrictions and explicit Stop.

Version 0.27 adds opt-in 30/90/365-day retention, explicit JSON event export with
saved excerpts excluded by default, labelled settings controls and large-font
navigation checks. Pending reminders, future items and undated accepted plans are
protected from retention cleanup. Local English risk advisories cover a small set
of gift-card, private-code, remote-access and safe-account warning signs. They are
nonblocking, dismissible and disableable, and never copy credentials into alerts.
See [privacy and risk behavior](PRIVACY_AND_RISK.md), the
[architecture](ARCHITECTURE.md) and [acceptance checklist](CAPSTONE_CHECKLIST.md).

Version 0.28 adds neural speaker-turn segmentation and bounded RAM-only anonymous
voices with separate correction context. Owner attribution remains an independent
enrollment match. Overlap stays unassigned; this is not simultaneous source
separation. A controlled ten-minute Pixel replay passed with 180 transcript
segments, 60 party mentions and zero second-voice party segments assigned to the
owner. Aggregate processing work was 265.306 seconds and final queue drain 1.581
seconds; maximum process PSS was 399.3 MiB and reported thermal status stayed zero.
USB was connected and public/synthetic fixtures were repeated. This does not
measure microphone accuracy, battery drain or population speaker error rates.
See [speaker turns](SPEAKER_TURNS.md) and [the sustained trial](SUSTAINED_TURNS.md).

Calendar mirroring is separately confirmed and can expose event metadata through
the selected calendar account. It never copies excerpts or creates invitations.
A real emulator LOCAL-provider test validates its writes, corrections, cancellation,
recovery and guest protection without using personal calendars. Optional manually
refreshed coarse region guidance is informational; everyone-agrees consent remains
the app policy. A limited jurisdiction catalog is not a legal-compliance engine.

## Reproducible evaluation

The checked-in corpus contains 92 self-authored cases across English, Korean,
Chinese, French, German, Russian and Arabic, with expected subjects, times,
attendance, places and reminder behavior. It includes supported examples, names,
multiple events, unknown/alternative times, task deadlines, reported speech and
broader language challenges. It is a development corpus, partly based on known
functional examples, not a blind holdout, native-speaker audit or user study.

On this corpus, event-subject precision is 100%, recall 98.8%, matched-event exact
time accuracy 100% and matched-event attendance accuracy 100%. There are no false
or mistimed reminders in these cases; one expected reminder is missed. Failed language cases
remain in [the full report](ACCURACY_REPORT.md); the aggregate scores must not be
presented as real-world speech accuracy. Speaker labels were supplied, not inferred.
Optional Qwen and live action execution are not measured by this text benchmark.

The evaluation exposed unsafe guesses for alternative bare clocks, ambiguous
numeric dates and vague future periods. These now retain unknown times rather
than create an inferred next-occurrence reminder. Event titles preserve names;
spoken thirty-minute delays are supported, and historical “used to” statements
are excluded. Version 0.26.1 adds explicit spelled-out English clocks, heading-to
plans, Chinese tomorrow-evening timing and bounded appointment ownership forms.
Original evidence is retained; ambiguous clocks and third-person ownership remain
guarded. Version 0.27 handles explicit German evening attendance and fixes Arabic
twelve-at-night parsing; small ambiguous night hours stay unknown. The dialectal
Arabic challenge still fails. Broader wording remains limited. The original
labels were retained; a Russian subject-token
spelling was corrected to match the source's ё, without changing its semantics.

Core unit checks additionally cover parsing, Undo semantics, date boundaries,
buffering, profile logic, source ownership and model-pack safety. Emulator
instrumentation tests cover the app/database/notification/UI paths; see
[VALIDATION.md](VALIDATION.md) for counts and exact scope. A fresh compact install
successfully imported verified weights and ran native Whisper Base on a fixture.
These are software checks, not measurements of natural conversations or all-day
phone behavior.

## Setup and distribution

Get started provides compact language/quality selectors, model readiness, optional
voice setup and explicit Android permission controls. It never starts listening
automatically. Missing packs are explained, and typed workflows remain usable.
The normal APK is about 226 MB instead of about 905 MB (approximately 75% smaller).
Optional verified packs are installed once and reused locally. All packs together
still require substantial storage, and the inference workload remains unchanged.
The submission script packages a development-signed APK, this report, evaluation,
demo instructions and model-pack guidance with checksums. It is not a signed
Play Store release or a promise of universal device compatibility.

## Remaining work

- Natural-speech and multilingual speaker calibration, simultaneous-voice audio
  separation and independently measured diarization/identity error rates.
- Sustained screen-off battery, thermal, missed-speech and interruption testing
  on the physical Pixel 7a; emulator timing is not device performance evidence.
- Broader language understanding and independently audited evaluation data.
- Real messaging-app and calendar-account field testing, bidirectional calendar editing,
  release signing and fuller accessibility/user testing.
- General multilingual scam classification and automatic legal/participant-consent
  verification are not implemented. Local English advisories and coarse-region
  guidance are bounded baselines. Existing controls do not establish legal compliance.

The prototype supports a reproducible core-pipeline demonstration. Submission
claims should match these limits and the course rubric. No user-study outcomes,
24/7 reliability, perfect speaker recognition or clinical benefit are claimed.
