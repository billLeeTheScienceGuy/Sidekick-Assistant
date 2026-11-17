# Hands-off plans: what saves, what waits (unreleased)

Hands-off mode should rarely need Review. The rules live in `core` (`HandsOffPlans.kt`, tested by
`HandsOffPlansTest`); the app applies them in `TemporaryMemory`, `AutomaticMemory` and `ReminderCoordinator`.

| Heard or typed | What happens |
|---|---|
| Your plan with a clear day and time | Saved, with the kind's default reminder |
| Your plan with a clear day, no time ("dentist on Friday") | Saved as an all-day plan, marked "Assumed: all day (time assumed)" |
| A clock but no day ("meeting at 3") | Saved for the next occurrence, marked "(day assumed)" |
| "Next Friday", "the 1st", a bare "at 5" | Saved with the reading shown, e.g. "Fri 27 Nov (assumed from 'next friday')", "5 pm (am/pm assumed)" |
| Someone else's plan ("Mom lands at 5", another voice, a message) | Saved as information only, no reminder |
| "Maybe", "if", "might", "I think" + a plan | Review only ("Not saved automatically: it sounded uncertain") |
| Two different times for the same plan ("5 or 6", or 3pm then 4pm for the dentist) | The later one waits in Review |
| Negations, questions, quotes, reported speech, past plans | Nothing |
| You: "the dentist appointment is cancelled" (one matching upcoming plan) | Marked CANCELLED, reminder removed, Undo in the notification |
| You: "that's cancelled" with nothing just discussed | Nothing |
| Another voice or a message: "X is cancelled" | Plan unchanged; a "Cancel: X" Review item. Accepting it cancels (with Undo) |
| The same plan again in other words | Merged into the saved plan |
| A plan overlapping another saved plan | Saved anyway, marked "May overlap with ‘X’ (saved anyway)" |
| A Review item whose date has passed | Cleared automatically (undated conflicts after 7 days) |

**Assumptions stay visible.** Every guess is written on the saved plan after "Assumed:". When the real time,
day, am/pm or length is said later (same or a later conversation), the plan with that assumption is updated in
place and the marker disappears. A plan you edited by hand has no marker and is never overwritten.

**Plan kinds and defaults** (only these defaults are ever added; people, places and times come from what was said):

| Kind | Default |
|---|---|
| Birthday, anniversary | All day; reminder the day before at 9:00; "Repeats yearly" noted |
| Day off, holiday, vacation ("off Monday to Wednesday") | All day, multi-day; no reminder. Spans over 7 days block the first day and note the end |
| Flight | Departure time, flight number ("UA 123"), from/to places, arrival as end; reminder 3 hours before ("leave for the airport") |
| Train, bus, ferry | Reminder 1 hour before |
| Doctor, dentist, clinic | 1 hour long; reminders the day before and 1 hour before (all day: the day before at 9:00) |
| Meeting / call | 1 hour / 30 minutes long; reminder at the start |
| Deadline ("report due Friday", "submit by the 3rd") | All day; reminder 9:00 that morning |
| Bill, rent | Reminder 2 days before at 9:00; rent notes "Repeats monthly (assumed)" |
| Pickup, drop-off | Reminder 15 minutes before |
| Restaurant table | Title "Table for 4 at Luigi's"; reminder at the start |
| Repair, delivery window ("between 9 and 11") | Window start and end; reminder at the start |
| Other all-day plans | Reminder 9:00 that morning |
| Repeats ("every Tuesday", "first Monday of the month") | First date saved; "Repeats ... only the first date is saved" noted |

**Limits.** There is no recurrence in the database: repeats are noted, not scheduled. Each item has one reminder
at a time; the second medical reminder is re-armed when the first is delivered (that re-arm ends Undo for the
item). Kinds and all-day plans are English-first; other languages get all-day plans only through the existing
event words and day words (Korean, Chinese, French, German, Russian). No database schema change.

# 0.16.0 additions

A clear same-source announcement and later personal attendance at the same time can
become one personal event with a reminder and Undo. Matching preserves names and
venue differences. Older automatic announcement duplicates can move to History with
Undo; manual edits and ambiguous matches stay protected. Smart analysis failures
retain local results. See [VALIDATION.md](../evaluation/VALIDATION.md) for scope and tests.

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
[EVENT_CHANGES.md](../design/EVENT_CHANGES.md). Fuzzy event matching, arbitrary corrections
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
