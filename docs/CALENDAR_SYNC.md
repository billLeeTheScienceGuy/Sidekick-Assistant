# Optional calendar mirroring

Settings → Calendar mirroring → Choose calendar requests read/write access only
when you ask. Choose a writable calendar, then explicitly confirm enabling sync.
It is off by default. Calendars attached to online accounts may upload event
titles, activity times and places. Source excerpts, transcripts, voice profiles,
attendees and invitations are never copied. No calendar alarm is created; local
SideKick reminders continue independently.

Only saved future activities with a known start and end are initially mirrored.
Unknown duration stays unknown. Optional information-only events are marked free;
personal activities are busy. The selected source timezone is retained. The
calendar's SideKick source link opens the matching saved event without executing
changes. Conflict checks exclude that event's own mirror, but include other busy events.

Supported rescheduling, cancellation, deletion and Undo reconcile an unchanged
mirror. A Room v6 link ledger stores provider IDs, hashes and operation state,
without a second plaintext event journal. A stable source marker and pending
hash recover a provider write interrupted before the ledger update. A lost
insert response does not create a duplicate. Guarded provider batches protect
concurrent edits. External changes, added guests, recurrence or deleted copies
freeze that link for manual attention instead of overwriting or recreating it.

Turning sync off pauses writes and leaves copies in the calendar. Changing the
destination applies to new mirrors; existing links keep their original calendar.
If permission is revoked, small ID/hash cleanup records remain until access
returns, so pending deletion can be retried. External edited copies are preserved.
Provider observation and a 15-minute retry operate while the process runs;
launch/resume also reconciles. This is one-way mirroring, not bidirectional editing
or a guarantee of immediate synchronization while Android has stopped the app.

Six fake-provider tests cover interruption recovery, permission loss, cancellation,
Undo, manual edit/guest/recurrence protection and no automatic duration inference.
An emulator-only test also creates, updates and deletes a synthetic LOCAL calendar
through the real CalendarProvider and protects a synthetic added guest. It never
uses a real account. Actual Google/Exchange account behavior needs field validation.
