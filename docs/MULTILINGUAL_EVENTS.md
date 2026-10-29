# Basic multilingual event understanding — 0.11.0

Transcription and event understanding are separate layers. Version 0.10.0 installed
multilingual Whisper, but the event grammar still expected English vocabulary. Version
0.11.0 adds a small local grammar for Korean, Chinese, French, German and Russian,
in addition to the existing English path. It preserves literal titles, evidence and
place names; it does not translate a conversation into invented English details.

Supported patterns include common party/meeting/appointment/concert/dinner/class/
birthday/wedding nouns, today/tomorrow, weekdays and explicit next-calendar-week
weekdays, ISO dates, explicit 24-hour clocks, selected spoken hour words with explicit
AM/PM context, noon/midnight, explicit durations and later end/place/address details.
Korean and Chinese clocks support afternoon/evening qualifiers. Chinese sentence
punctuation no longer requires whitespace. English and local end statements may be
mixed within the same attributed conversation context.

All inferred details stay review proposals. Explicit first-person attendance can
produce a personal plan only for the user speaker; others' statements remain
informational. Negation/uncertainty is conservative; ambiguous morning/evening,
contradictory dates, invalid dates and DST gaps/overlaps remain unknown or produce
no suggestion. Another event topic interrupts pronoun-based end association. Changing
the venue clears the previous address unless a new explicit address is available.

Typed/shared/notification and live attributed text use the same event extraction
path. Recognized local event statements have a fast path in smart analysis, so they
do not require loading or importing Qwen. Existing English model behavior remains
separate. Generic non-English promises, complex grammar, local month-name formats,
every number-word form, cancellation/rescheduling and arbitrary coreference are not
comprehensively supported. This is a bounded baseline, not general fluent reasoning.

## Equivalent synthetic examples

Each example means an informational party tomorrow at 20:00 at The James, ending
at the following midnight. Place names are literal test inputs, not verified venues.

- Korean: 내일 오후 8시에 The James에서 파티가 있어요. 자정에 끝나요.
- Chinese: 明天晚上八点在The James有派对。午夜结束。
- French: Il y a une fête demain à vingt heures au The James. Ça finit à minuit.
- German: Morgen ist eine Party um 20 Uhr im The James. Sie endet um Mitternacht.
- Russian: Завтра вечеринка в 20:00 в The James. Она закончится в полночь.

Use one example at a time in typed notes first, or speak it with the corresponding
speech language selected. Microphone transcription errors can still change or lose
words; successful typed parsing does not prove end-to-end speech accuracy. Voice
matching remains a separate gate and can still leave short or uncertain speech
unassigned. The prior English short-correction recording remains a failed test;
this grammar change does not restore its discarded audio or establish an ASR fix.

## Verification

65 core tests pass, including five-language equivalent events, native spoken clock
forms, negations, uncertain/ambiguous times, midnight rollover, ownership, literal
evidence/venue/address corrections, cross-language endings, durations, next-week
dates and DST/invalid-date rejection. Device regressions exercise live draft updates,
message ownership, no automatic persistence and a typed local event with no Qwen
model installed in its isolated test context. No automated test uses the physical mic.
Six Pixel event-flow tests passed in 0.281 seconds; both builds and lint passed.
