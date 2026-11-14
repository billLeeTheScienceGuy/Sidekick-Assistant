# Recognition and correction follow-up: 0.34.0

## Observed failure

The 0.33.1 real microphone trial saved one informational party for tomorrow at 8 p.m. at The James, with no end time. English correction wording was largely present in the temporary transcript, but the final event was not updated. All four chunks were speaker-unassigned despite checking both enrolled profiles. Parser defects and owner verification are distinct problems.

## Changes

- Recognize explicit `change ... to ...` commands, including today's/tomorrow's named activity. A possessive relative day must agree with the event date before rescheduling.
- Normalize spaced spoken English meridiem for parsing (`nine p m`, `9 p m`), leaving original evidence intact.
- Split an unpunctuated explicit time correction from a following named venue correction. Do not perform this split for negated, reported or otherwise unsupported commands.
- Preserve the new venue before `instead of`, instead of including the replaced venue in its name.
- Tolerate spaces inside Korean party, tomorrow, noon and midnight keywords. Allow a matching Korean end-time continuation to refine an English activity. Different event kinds remain separate. Original wording remains evidence.
- Voice setup requires at least four seconds of trimmed speech and validates independent matching windows against the candidate signature. Rejected samples provide visible retry guidance while keeping good samples. The previous saved profile is retained if setup does not complete.
- Existing thresholds and uncertain-speaker restrictions remain. Checking setup windows cannot guarantee future matching at other distances or in other languages. No automatic voice enrollment or raw audio persistence.

## Validation

Build, unit tests, isolated Pixel correction/enrollment regressions and installed-model comparisons are in progress. Actual owner matching requires a new user-initiated setup and microphone trial after installation. Nothing here claims perfect natural bilingual recognition.
