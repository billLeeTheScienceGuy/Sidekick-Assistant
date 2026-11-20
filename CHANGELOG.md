# Changelog

Version notes for SideKick, newest first. Links point to the measurements and limits behind each change.

## Unreleased (privacy, consent and location work package)

- Settings: Screen & text size is its own section, listed first.
- Optional SideKick voice check (off by default; Settings → Your voice, its own screen): say five "SideKick, ..." questions and SideKick
  recognizes you by how you say its name, so answers to your questions are spoken even when your voice was otherwise
  unclear. Never overrides another person's voice. Its stranger rate is not yet measured (docs/evaluation/SPEAKER_TUNING.md).
  Three other owner-recognition ideas were tested and not adopted (same document).
- Reliability fixes from a code review: muting the microphone pauses listening instead of stopping it; a phrase the
  recognizer fails on is retried or skipped without stopping; other errors restart listening (at most 5 times an hour);
  listening resumes by itself after an app update; reminders ring on time (exact alarms); faster start with large
  speech packs (docs/evaluation/TEST_HISTORY.md T87).
- Battery and reliability: listening's own power in silence is about 42 mW, down from 117 mW (speech detector once a
  second on the efficient cores, microphone read once a second, low-power location away from state borders, call
  events instead of polling). A full day of listening used 21% of the battery over 14 hours, SideKick itself about 4%
  (docs/evaluation/TEST_HISTORY.md T81, T86). Listening no longer stops when busy (a waiting line sized by free memory,
  faster recognition to catch up, nothing skipped), pauses while another app uses the microphone and carries on
  afterwards, and logs any stop reason. Optional "Pause when the phone is resting", suggested once after 3 hours.
- Korean: a lone hesitation sound the recognizer writes as its own word ("회의가 어 있어요") is dropped between Korean
  words; "어" at the start of a sentence ("yeah") is kept. Fixes the last failing phone test (124 of 124).
- Voice matching: the quiet-room bar now depends on how long and how clean the speech is (.58-.65): owner turns
  62% -> 69% at the base rule, 0 of 409,213 stranger turns (454 voices). A larger test (1,813 voices in all
  seven app languages) found 17 pairs of near-identical voices that can pass as each other (97 of about 6.6 million
  stranger turns), so a very similar voice, such as a close relative, can occasionally be taken for the owner. Your saved voice also slowly adapts
  across sessions from moments SideKick is very sure it is you, never far from your setup; the voice consent notice
  says so (version 2), so it asks again once.
- Hands-off saving needs Review far less. Your own plan with a clear day but no time ("I have a dentist appointment
  on Friday") now saves as an all-day plan instead of being missed. Every guess is written on the saved plan
  ("Assumed: all day (time assumed)", "Fri 27 Nov (assumed from 'next friday')", "1 hour (length assumed)"), and
  when the real time or day is said later the plan is updated and the "assumed" note disappears. Common plans get
  sensible defaults: birthdays and anniversaries (day-before reminder, yearly repeat noted), days off and vacations
  (multi-day, no reminder), flights (number, cities, arrival; reminder 3 hours before), trains/buses/ferries (1 hour
  before), doctor and dentist (1 hour long; reminders the day before and 1 hour before), meetings (1 hour) and calls
  (30 minutes), deadlines and bills (morning reminder; bills 2 days before), pickups (15 minutes before), restaurant
  tables (party size and place) and repair or delivery windows ("between 9 and 11"). Repeats ("every Tuesday",
  "first Monday of the month") save the first date and say so. Other people's plans ("Mom lands at 5") are saved as
  information only. Only real conflicts go to Review: "maybe"/"if" plans and two different times for the same plan.
  Your own voice naming a plan ("the dentist appointment is cancelled") cancels it with Undo; a bare "that's
  cancelled" with nothing to point at does nothing; another voice or a message saying something is cancelled only
  adds a Review item, never removes a plan. The same plan said twice merges, a clash is saved and flagged ("May
  overlap with ..."), and Review items whose date has passed clear themselves. No database change. See
  `docs/guides/HANDS_OFF.md`.
- Simple home is now the default, for older adults and people new to smartphones: Today (one large Start/Stop
  listening button, a Listening/Not listening line, the rules line, and the next plans as large cards with Change and
  Undo/Remove buttons), Plans, and a labelled More (summary, notes and listening details, setup, detailed view). A
  "plans to check" button appears only when something needs checking. Settings now open with Simple/Detailed and text
  size, then four sections: Listening, Your voice, Reminders & calendar, Privacy. Recommended choices are labelled and
  jargon (session, model, profile, chunk, diarization, attribution) is replaced with plain words. The detailed view
  stays available in Settings; consent and privacy texts are unchanged. See `docs/guides/TRY_IT.md`.
- Removed the optional Qwen3-0.6B model ("smart analysis" for typed notes), its llama.cpp native build and import
  screen. On the phone over the 92-case corpus it found 19 of 83 events (rules: 82), took 14.4 s per sentence (rules:
  7 ms) and found nothing the rules missed. The app deletes a previously imported model file (484 MB) on start. Typed
  notes and live speech now use the same local rules. See `docs/guides/INTENT_MODEL.md`.
- Owner voice matching retuned on 381 public stranger voices (about 160,000 stranger turns in quiet, noisy, far and
  echo rooms; laptop evaluation in `scripts/laptop-eval/`, checked against the phone's own library). Owner turns
  recognised: 82% (was 54%); strangers accepted: 0. Through the phone microphone with setup on the phone: 73% at the
  quiet bar, 0 of 1,460 stranger turns. Every bar sits at least .08 above the closest stranger seen.
  Before notice in all-party places nothing is loosened. See `docs/evaluation/SPEAKER_TUNING.md`.
- Your voice from earlier in the same session counts: once a long turn matches your saved profile, later chunks that
  sound like those turns (.74) count as you; never overrides "someone else", off before notice in all-party places.
- During a session, your profile is nudged toward how you sound today (memory only; bar .67), and an unassigned
  chunk between two of your chunks that sounds like both counts as you (.72). Such a chunk's text appears once the
  next chunk is decided (a few seconds). Both are off before notice in all-party places.
- Noise removal before voice matching (GTCRN, DPDFNet) was tested and not adopted: it brought strangers closer.
- Voice setup can be recorded in a quiet room, with background noise, or with the phone on a table; each is its own
  profile. The quiet-room profile uses a bar of .64, the others .71.
- A stranger cohort (198 public LibriSpeech signatures, 150 KB, CC BY 4.0) adds a check that every matching window is
  well above how strangers score. It never rejected an owner turn in the evaluation and guards against outliers.
- The ResNet ("Standard") voice model is no longer offered: in the evaluation strangers scored as high as the owner.
  A saved ResNet choice moves to CAM++ and needs voice setup again. TitaNet-small, ERes2Net and ERes2NetV2 were
  compared and did not beat CAM++ at the same safety margin.
- Two independent rules per state: recording (one-party / all-party) and voice (none / notice in TX and WA / strict in IL).
  SideKick takes the strictest of the containing state and every state within the location error margin; anything
  unknown (no fix, stale fix, abroad, lookup error, phone call) is all-party + strict. Borders updated to Census 1:500k.
- Rules follow you during a session (location updates every 500 m / 60 s plus a 5-minute check). Moving somewhere
  stricter asks for a new notice; entering a strict place forgets every other voice at once.
- The spoken notice now always includes a voice-analysis line, in every state; each notice is recorded per session
  (time, state, rules, text version, spoken or attested), with no audio or voice data.
- A notice covers one conversation: it ends after 30 minutes without other voices, or after 2 hours. Voice groups now last
  30 minutes without that voice (was 15). Optional "Always show the Announce button" (on by default).
- The spoken notice plays at least at half volume (raised and restored) and counts only if the microphone measured it
  as loud enough; volume, loudness and the result go into the notice record (Room v8). Optional "Announce automatically
  when a new conversation starts" (off by default).
- A location from a mock-location app (Developer options) gets the strictest rules; the city in Settings never sets them.
  The "Listening & privacy" panel now says what SideKick does while listening, and links the Wisconsin statute (968.31).
- Settings: "Treat every place as the strictest state", "Group voices only after notice, in every state", privacy policy link.
- Owner voice setup asks for written consent first (name, checkbox, optional signature; guardian for under-18s);
  profiles without a consent record stop matching until accepted; profiles unused for 12 months are deleted.
- Name a voice for one session only; invitations from named voices ("meet me at 5") become information-only events;
  a short "sure" from you goes to Review.
- Stricter owner matching before notice in all-party places; threshold-tuning script and scaffold
  ([speaker tuning](docs/evaluation/SPEAKER_TUNING.md)). Test report: [privacy work package](docs/evaluation/PRIVACY_WORKPACKAGE_TEST_REPORT.md).
- Fix: a finished session no longer waits on the background location loop.
- Fix: recognizer loops ("thank you" ×12) keep one copy (RepeatFilter; no change to about 20,000 human transcripts,
  34 real loops caught in saved recognizer output). Whisper decodes a looping reading once more with a little randomness
  and caps output length; ordinary phrases are decoded exactly once, as before.

## 0.35.0

- Auto-detect routing rebuilt around a benchmark of 16 open speech models: SenseVoice tags the language first,
  Parakeet re-reads plain English (two English readings must agree), Omnilingual v2 handles other languages,
  GigaAM re-reads plain Russian. Optional English, Russian and Korean speech packs.
- Recording-consent gate: offline US state detection from bundled Census borders, strict on any uncertainty,
  spoken notice for all-party states; speech that may not be kept is dropped before transcription.
- Korean time cross-check (Korean Zipformer), unclear times sent to Review instead of being saved,
  a name list for names in Korean speech, and a fix for spoken "AM." being read as PM.
- Music and noise scraps filtered, per-voice language memory, idle models unloaded.
- Published as a downloadable release with the app and its speech files; see [model packs](docs/guides/MODEL_PACKS.md)
  and [phone tests](docs/guides/PHONE_TESTS.md).

## 0.34.0

- Explicit `change … to …` corrections, spoken meridiem (`nine p m`), separate time and venue corrections,
  Korean keyword spacing, and stricter voice setup (at least four seconds per sample, validated windows).
  See [recognition follow-up](docs/evaluation/RECOGNITION_034.md).

## Earlier versions

0.33 improves speech onset/pauses, quiet-speech detection and short CJK automatic
recognition. Conflicting acoustic readings stay as temporary text instead of
automatically changing plans. See the [recorded Pixel evaluation and limits](docs/evaluation/RECOGNITION_033.md).

0.32 simplifies Settings with plain-language labels and optional details, keeps
privacy information beside each action, and improves short bilingual recognition
by avoiding retries that force mixed speech into one language.
See the [paired Pixel measurements and limits](docs/evaluation/RECOGNITION_NEXT_STEPS.md).
The six-category settings, Auto-detect switch and optional start tips introduced
in 0.31 remain available.

0.30 adds an adaptive Android interface with **Simple / Detailed** home layouts,
independent text and interface sizes, Calm / Classic / Warm design presets,
Light / Dark / System appearance and optional manual note-taking.
Use **Settings** to personalize it. Persistent navigation adapts from a bottom bar
to a side rail. See the [interface benchmark](docs/evaluation/INTERFACE_BENCHMARK.md) for
comparisons, supported devices and the reproducible layout checks.

0.29 improves Automatic and longer multilingual speech using measured local-model
routing, adds explicit microphone-stall/interruption handling, and keeps owner
context stable across anonymous speaker clusters. See the [broader evaluation](docs/evaluation/ROBUSTNESS_EVALUATION.md)
for noisy public speech, short Arabic limits and recovery measurements.

0.28 adds [offline speaker-turn segmentation](docs/design/SPEAKER_TURNS.md), session-only
anonymous voices, [opt-in calendar mirroring](docs/guides/CALENDAR_SYNC.md) with edit
protection, and optional [region privacy guidance](docs/design/PRIVACY_AND_RISK.md).
A [ten-minute controlled Pixel trial](docs/evaluation/SUSTAINED_TURNS.md) passed without
queue overflow or second-voice owner matches; it is not a battery/field study.

0.27 adds opt-in memory retention, private event export, local English risk
advisories and labelled settings controls. Start with the
[capstone checklist](docs/project/CAPSTONE_CHECKLIST.md), [architecture](docs/design/ARCHITECTURE.md)
and [privacy behavior](docs/design/PRIVACY_AND_RISK.md). Reproduce the emulator checks with
`scripts/validate_prototype.ps1`; the submission bundle includes dated evidence
and known limitations.

0.26 adds a [92-case text evaluation](docs/evaluation/ACCURACY_REPORT.md), a Get started checklist, [verified offline model packs](docs/guides/MODEL_PACKS.md), and a compact APK about 75% smaller. 0.26.1 fixes eight of the corpus's ten missed-reminder cases while preserving ambiguity and ownership checks. A [prototype submission report](docs/project/SUBMISSION_REPORT.md) and `scripts/prepare_submission.py` provide reproducible demo deliverables. The evaluation is curated text coverage, not real-world speech accuracy.

0.25 adds [daily summaries and temporary conversation highlights](docs/design/SUMMARIES.md), explicit calendar-editor handoff, and stronger named/multilingual attendance updates. A [prototype demo checklist](docs/guides/DEMO.md) describes the supported scope.

0.24 integrates [broader English dates](docs/design/DATE_UNDERSTANDING.md), preserves exact relative reminder delays, and rejects ambiguous times across the supported grammars. Ambiguous “next weekday” suggestions do not trigger automatic reminders.

0.23.1 prevents delayed message notifications from rewinding conversation context and handles long notes within validation limits. Expanded device checks cover Planner editing, sharing, calendar filtering, uncertain-speaker updates and profile isolation.

0.23 adds [conversation context](docs/design/CONVERSATION_CONTEXT.md): split date/time details in seven languages, stable updates to untimed plans, and English attendance changes with reminder updates and Undo. Ambiguous references stay unapplied.

0.22.1 separates notification delivery from audible/visible alert settings, adds a direct reminder-channel settings link, and enables vibration/pop-up defaults for new channels. Existing Android channel preferences are preserved.

0.22 adds [end-to-end reliability improvements](docs/design/RELIABILITY.md): Undo in Planner, recovery from scheduling failures, reminder retry when returning to the app, and a one-tap notification check in Settings.

0.21.2 closes a Korean mixed-script clock retry gap. See the [current roadmap](docs/design/ROADMAP.md) for remaining prototype work; speech accuracy remains a known limitation.

0.21 adds Arabic and improves Adaptive recognition across the seven selected languages using measured on-device model choices. It also retries invalid Korean 12-hour clocks with a second offline model under strict context guards. See [quality measurements and limits](docs/evaluation/ASR_EVALUATION.md) and [Arabic support](docs/design/ARABIC.md).

0.20 prevents replaying already processed corrections when analyzing copied live notes, and keeps separate voice signatures for selected languages. Korean setup preserves the existing English signature; matching still needs real-device verification.

0.19.1 fixes Korean corrections when older matching events exist by using the recently discussed matching event, and handles an extra recognized Korean particle in generic cancellation commands.

0.19 uses offline SenseVoice int8 for Korean in Adaptive speech mode, preserves quiet-pause boundaries, and rejects mixed fragments that would borrow a later sentence’s clock. Other speech modes remain available.

0.18 extends explicit event rescheduling/cancellation to Korean, Chinese, French, German and Russian, with the same Undo and reminder handling.

0.17 adds an evaluated CAM++ speaker-model option with separate voice setup, and explicit phone/activity timezone labels.

0.16 links matching announcements and personal attendance, archives automatic duplicates with Undo, and keeps local results when smart analysis fails.

0.15.1 adds adaptive short English speech recognition and fixes duplicates when reanalyzing copied live notes.

0.15 adds [automatic rescheduling and cancellation](docs/design/EVENT_CHANGES.md) with Undo.
Pixel reboot recovery has been verified after a user-started listening session.

An Android, local-first memory assistant for spoken commitments. Version 0.13 adds [Hands-off mode](docs/guides/HANDS_OFF.md): detected events and matching corrections save automatically, with Undo in notifications. Personal plans with clear times get reminders; other voices stay information-only. Review mode is available in Settings. Audio and full transcripts stay temporary. See [the plan](docs/design/PLAN.md), [model shortlist](docs/design/MODELS.md), and [phone test guide](docs/guides/TRY_IT.md).

Version 0.4 adds optional local Qwen3-0.6B smart extraction for edited/typed notes after
listening stops. [Install its model once](docs/guides/INTENT_MODEL.md); the 484 MB model stays
outside the APK and survives app updates. Live capture keeps using the lighter rules
path. Every model suggestion requires review and approval.

Version 0.5 adds a [Planner tab](docs/guides/PLANNER.md) with search, reminder ordering,
review/history filters, date/time pickers and same-minute reminder warnings. Calendar
access is optional in version 0.6: choose activity start/end times in the editor and tap **Check phone calendar**. Read permission is requested only then; no calendar writes occur.

Version 0.8 adds [optional places and messages](docs/design/PLACES_AND_MESSAGES.md):
Ask/Passive guidance, activity end-time suggestions, information-only events, literal
venue names and confirmed address suggestions, manual/automatic lookup, chosen-city/
phone-location context, shared-message import and opt-in notification scanning.
Speech and intent stay local; optional address lookup can use an online system provider.

Version 0.8.1 adds Settings readiness/location checks and prevents changed venues from retaining old addresses.

Version 0.8.2 adds speech/waiting status and [continuous-listening validation](docs/design/CONTINUOUS_LISTENING.md).

Primary device: **Pixel 7a (Tensor G2, 8 GB RAM)**. Whisper tiny.en and optional Qwen3-0.6B CPU inference have passed focused fixture tests on a physical Pixel 7a running Android 17. See [validation](docs/evaluation/VALIDATION.md) for the scope of testing.

- Version 0.14.0 adds an optional [default-assistant mode](docs/guides/ASSISTANT_MODE.md).
  After selection and one user-started session, enabled listening can recover from
  system process restarts and reboots. Stop disarms it. Emulator recovery is verified;
  the Pixel is configured and still needs its user-started recovery trial.

- Version 0.13.0: automatic event saving, matching corrections, personal reminders,
  incoming/shared-message processing and persistent notification Undo. No editor or
  approval is required in Hands-off mode after setup and starting listening.

- Version 0.12.0 adds ten-second live windows, bounded RAM speech context,
  independent speaker windows, optional Whisper base and review-only uncertain
  corrections. See [model comparison and limits](docs/evaluation/SPEECH_QUALITY.md).
  Version 0.12.1 prevents matching repeated speech across voice-label changes from
  creating duplicate events; changed details remain subject to review.

- Version 0.10.0: local Korean, Chinese, French, German and Russian transcription,
  plus Automatic language detection in Settings. A shared multilingual Whisper tiny
  model is bundled; English keeps tiny.en. Transcripts retain the spoken language.
  Version 0.11.0 adds basic dated-event, end-time and place rules in all five languages;
  complex grammar remains limited. See [language validation](docs/design/LANGUAGES.md) and
  [event grammar](docs/design/MULTILINGUAL_EVENTS.md).

Version 0.9.0 adds optional local voice enrollment in Settings. Three speech samples
produce a private, deletable voice signature; raw enrollment audio is discarded and
never transcribed. WeSpeaker ResNet34-LM through sherpa-onnx matches two windows of
each speech chunk. Only confident user matches can create personal plans; other or
uncertain voices produce information-only event suggestions. Thresholds are a
conservative prototype, not representative accuracy claims. No profile or disabled
matching means all live speakers are unassigned. Voice identity is not consent.
