# Planner prototype

Version 0.5 adds a Planner tab alongside Capture. Capture and transcription retain
their existing behavior; listening controls remain visible when Planner is open.

- **Review:** temporary proposals waiting for approval (plus older saved proposals).
- **Reminders:** accepted items with reminder times, sorted earliest first. Passed
  times remain visible until the task is completed, edited or deleted. A reminder time
  is not necessarily the activity's deadline.
- **No reminder:** accepted commitments without reminder times.
- **History:** completed and dismissed saved items.
- **All:** review items, scheduled items, unscheduled items, then history.

Search matches titles and source evidence within the selected view. Counts show the
whole view before search. Newly added manual commitments are temporary review items.

The editor supports picker-only **Choose date & time**, a one-hour shortcut, and the existing
two-minute/no-reminder actions. Both picker dialogs must be confirmed before the
chosen date/time replaces the previous value. Cancel leaves the prior value intact.
Reminder times use the item's displayed timezone and the existing strict DST validation.
Saving still requires a future reminder time and enabled notifications.

A warning lists other accepted reminders in the same instant minute. This only checks
SideKick reminder times; it does not establish that activities overlap.

Try the Planner tab, filter/search your saved commitments, and use the picker to edit
one reminder. Confirm that temporary proposals still need approval before persistence,
and that cancelling the editor does not save them.

## Optional phone calendar check (0.6)

In the commitment editor, use **Choose date & time** for **Activity start** and
**Activity end** in the shown timezone. Reminder times use the same picker; there
are no manually editable date fields. These describe the activity, independently of
its reminder. Both are independently optional. If both are set, end must follow start,
with a maximum seven-day window. DST gaps and ambiguous times are rejected.
Activity times persist with the approved commitment; cancelling saves nothing.

Tap **Check phone calendar** and allow Android's read-calendar permission if desired.
Permission is requested only after a valid interval and this explicit action. Saving
works without granting permission or checking. No calendar permission is requested
on startup. Revoke it through Android app settings at any time.

The query reads Calendar Provider Instances, including recurring occurrences, from
visible calendars stored on the phone. Cancelled, declined and free events are ignored;
tentative events count as possible conflicts. All-day dates occupy local calendar days,
including DST changes. Adjacent endpoints do not overlap. Results show at most 50
conflicts (first ten titles displayed), stay only in memory, and are invalidated when
activity times change or the editor closes. No calendar event titles are saved or logged.
Access errors and denial show unknown availability, never a false all-clear.

Calendar changes require another explicit check. Hidden or unsynced calendars may be
missing. Checks are advisory; saving does not reserve time, write calendar events,
or automatically reschedule commitments. Activity intervals in other SideKick notes
are not included unless they exist in the phone calendar.

Phone test: review a commitment, enter a known busy interval, tap the calendar check,
and grant permission. Confirm its title/overlap warning. Change to an adjacent free
interval and check again. Verify denying access still allows saving without a reminder,
and saved activity times survive reopening. These real-calendar tests are left to you;
automated tests use only synthetic calendar data.

Reference: https://developer.android.com/identity/providers/calendar-provider

Reminder shortcuts each have their own full-width row so No reminder stays on one line
in the phone dialog. Date/time changes apply only after both picker confirmations.

## Conversation timing and guidance (0.7)

Settings offers Passive (default) or Ask. Both run the same local timing inference.
Ask prompts only when reviewing an activity with no end; I don't know dismisses the
question, and saving remains available. Passive keeps optional timing/calendar controls
collapsed. All date changes still use pickers. Manual entry remains optional.

Bounded recent live text can connect a declared activity to a later explicit ending,
including evening-to-midnight rollover and numeric durations such as 1.5 hours.
Factual event statements produce clearly labeled suggestions without assuming attendance.
Unknown or ambiguous clock times remain unknown. An inferred next-occurrence date is
marked for review. Different activity topics interrupt pronoun association.

Unapproved drafts can gain an ending from later speech. If an activity was approved
during the same retained session, a later ending creates a separate timing-update
proposal; the saved record changes only when that proposal is approved. Manual changes
are respected and completed/deleted records are not revived. Associations are bounded
and temporary; they do not connect arbitrary conversations after restart.

See [places and messages](PLACES_AND_MESSAGES.md) for 0.8 settings, information-only
events, optional venue/address fields and message ingestion.
