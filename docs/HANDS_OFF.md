# 0.16.0 additions

A clear same-source announcement and later personal attendance at the same time can
become one personal event with a reminder and Undo. Matching preserves names and
venue differences. Older automatic announcement duplicates can move to History with
Undo; manual edits and ambiguous matches stay protected. Smart analysis failures
retain local results. See [VALIDATION.md](VALIDATION.md) for scope and tests.

# Hands-off automation — 0.13.0

The user requested automatic saving and corrections without approval prompts.
Hands-off mode is now the normal app's default. Settings can switch back to Review
mode. The isolated benchmark package defaults to Review to preserve test isolation.
This supersedes older documentation saying only manually approved items persist.

After permissions, voice setup if wanted, and starting a consenting listening
session, SideKick can operate with its screen closed. Detected events save to its
local planner automatically. Matching end/place corrections apply automatically,
including the previously review-only unassigned-speaker corrections. Activity end
time, place and duration remain optional. Missing details do not trigger questions.
Unknown/other voices remain information-only and never imply personal attendance.

Personal plans with an unambiguous future time automatically get a reminder at the
activity start (or an explicitly resolved commitment time). Information-only events
are saved without reminders. Android notification permission is necessary for
delivery; inexact alarms can be delayed. SideKick does not write the phone calendar.

Opted-in new incoming message notifications use the same automatic flow while the
app is closed. Android notification access and selected messaging apps remain
required. Sharing text into SideKick also saves detected events without an analysis
approval dialog when sharing and Hands-off are enabled. Raw/full messages are not
archived; only extracted event evidence is saved, limited to 1,200 characters.
Raw audio and rolling live transcripts are still temporary.

## Corrections and duplicates

Voice association and literal event/date validation still select the target. The
app does not guess between multiple matching events, invent a missing time, or
convert another speaker's plans into yours. Exactly matching saved event openings
and start timestamps can reconnect across sessions without creating a duplicate;
message origins remain separate. Automatic ownership is retained for these updates;
manually edited saved items are protected from replay overwrites. Old venue addresses
clear when the venue changes. Unresolved venues stay as spoken; online address
suggestions still are not automatically treated as verified addresses.

Version 0.15 adds bounded explicit English rescheduling/cancellation; see
[EVENT_CHANGES.md](EVENT_CHANGES.md). Fuzzy event matching, arbitrary corrections
and general multilingual reasoning remain unimplemented. Ambiguous references may remain unresolved instead of changing a
possibly unrelated event. More automation does not establish recognition accuracy.

## Undo without opening the app

A quiet, private notification accompanies each automatic save/update, with an
**Undo** button. Undo reverses the latest automatic change for that item: a new
event is deleted, or an update restores its previous fields and reminder. It also
suppresses immediate reapplication within the current session. Manual edits,
completion and deletion invalidate stale Undo actions. Older tokens cannot overwrite
a later automatic update. Reminder delivery alone does not invalidate Undo.

Room v5 adds an atomic change journal with before/after event snapshots and a unique
token. It survives process restarts, is capped at 100 items, and is cleared with
deleted memory. At most five recent Undo notifications restore on app/boot/update
recovery. This is one-step Undo per item, not a permanent transcript or unlimited
revision history. Existing Room v4 records migrate without deletion.

Stopping or clearing temporary text keeps already saved events; use Undo to reverse
a save. Review mode preserves the earlier approval-based behavior. Manually created
blank items still require editing instead of being saved automatically.

## Android boundary

Listening is initially started explicitly, with its foreground notification and Stop
controls. Version 0.14 adds an optional [default-assistant route](ASSISTANT_MODE.md)
that can resume a previously enabled session across system restarts and reboots.
Selection alone does not activate the microphone; Stop disarms recovery. Force-stop
is not overridden. VAD suppresses transcription during silence inside an active
session. Capture and automatic event processing do not require editing or approval.

## Validation

Tests use isolated benchmark data and public/synthetic audio; no test opens the
physical microphone or reads personal messages. Coverage includes automatic saves,
unassigned corrections, personal reminder scheduling, information-only ownership,
Review mode, invalid-item failure, cross-session duplicates, manual-edit protection,
venue/address correction, persisted Undo, real notification-action Undo, incoming
message callbacks, v4-to-v5 migration, and real VAD/Whisper → event → database flow.
Share UI and Review UI have separate mode checks. Existing expensive language/model
comparisons were not rerun because their inference implementation did not change.

Final Pixel run: 29 targeted device checks passed in 7.684 seconds. Both build
variants and lint passed. Earlier UI harness failures were fixed by launching the
share activity with its original share intent, scrolling lazy items into composition,
and explicitly selecting Review mode for the older approval test. Actual continuous
microphone behavior and long-session battery/thermal performance still need use;
the preceding user microphone trial validated the speech layer, not these new saves.
