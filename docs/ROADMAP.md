# Remaining prototype work

## Current foundation

Local continuous speech detection/transcription, seven selected languages,
user/other/unknown voice matching, per-language enrollment, event/planner storage,
automatic saves/corrections with Undo, reminders, optional message notifications
and shared text, place lookup, optional calendar conflict reads, and default-assistant
recovery are implemented. They remain prototype features with limited field testing.

## Priorities

1. **End-to-end reliability.** Exercise a real event from conversation/message to
   saved plan, correction/cancellation, notification and Undo. Then measure hours
   of unplugged, screen-off listening: battery/hour, heat, memory, queue overload,
   missed speech, calls, Bluetooth changes, permissions and system restarts. A
   20-minute trial and individual reboot success do not establish all-day reliability.
   Version 0.22 adds automated receiver/failure/Undo coverage and a one-tap field
   check. User confirmed the reminder alert worked after the channel fix;
   longer listening remains deferred at the user's request.
2. **Voice ownership.** Calibrate short, noisy and multilingual phrases against
   other people, overlapping speakers and TV audio. Current user/other/unknown
   matching is not full diarization or named-speaker identification. A few successes
   do not establish reliable personal-reminder attribution.
3. **Event understanding.** Move beyond the limited live rules for names, places,
   relative dates, multiple activities and corrections across longer conversations.
   Optional Qwen currently assists manual analysis; it is not a general live planner.
   Preserve source, uncertainty, ownership and reversible changes when expanding it.
   Version 0.23 adds grounded split date/time assembly in seven languages and
   English attendance/named-detail context. General multilingual reference
   resolution and longer conversations remain limited.
4. **Real integrations.** Validate actual messaging apps, grouped notifications,
   location/venue/address updates and reminder delivery under Doze. Calendar access
   currently checks conflicts; calendar writing/sync is a separate product decision.
5. **Release readiness.** Reduce the large bundled-model package using model packs,
   simplify onboarding/settings, check accessibility, add retention/export controls,
   prepare a signed build and run remote CI before sharing a release.

## Known quality limits

The real Korean eight-pm phrase remains inconsistent. Version 0.21.2 adds one
bounded guard extension for the captured mixed-script `열8시` form and repeated
polite ending. It is not a claim that Korean number recognition is solved, and no
further isolated number trial is requested. Arabic/dialect validation remains
limited to public/synthetic recordings. Automatic language switching needs broader
evaluation; selecting a language is currently more predictable.

Next milestone: a reliable end-to-end prototype across capture, ownership, event
updates and reminders, followed by sustained screen-off reliability measurements.
