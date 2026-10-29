# 0.18 multilingual correction examples

| Language | Move to tomorrow at 9 pm | Cancel |
|---|---|---|
| Korean | 회의를 내일 오후 9시로 변경해줘. | 회의를 취소해줘. |
| Chinese | 把会议改到明天晚上9点。 | 取消会议。 |
| French | Déplace la réunion à demain à 21h. | Annule la réunion. |
| German | Verschiebe das Treffen auf morgen um 21 Uhr. | Das Treffen ist abgesagt. |
| Russian | Перенеси встречу на завтра в 21:00. | Отмени встречу. |

These use the existing source/ownership/ambiguity checks, persistent Undo and reminder
updates. Times stay in the saved event's timezone. Native spelling/spacing variants
are bounded; unrecognized wording stays unresolved. See VALIDATION.md for tests.

# Event changes — 0.15.0

Hands-off mode handles explicit English statements such as:

- “Move the meeting to Friday at 9 pm.”
- “The party has been moved to tomorrow at 21:00.”
- “Reschedule it to Friday.” (preserves its local clock)
- “The party is cancelled.” / “Cancel the party.”

Corrections use deterministic local text parsing, with no extra model or service.
Questions, hypothetical/negated/reported changes, quoted requests, ambiguous bare
hours, multiple dates/times, invalid DST clocks and past start times do not apply.
Explicit correction grammar now covers six languages; see the current examples above.
Existing six-language event creation/transcription remain available. Arbitrary wording
and general multilingual reasoning are not supported. Review mode requires using the existing editor instead.

A named target must match exactly one upcoming accepted event in the same source.
Incoming messages are scoped to their app/conversation, voice to voice, shared text
to shared text, and typed input to typed input. Speaker USER is required to move or
cancel a personal plan. Unassigned/other speakers can change informational events
only. Pronouns require a recent single remembered event within two minutes; they
are not resolved across process restarts. Matching is literal, not semantic identity.
Ambiguity leaves saved events unchanged and appears in the automation status.

Rescheduling keeps ownership, venue/address and a known elapsed duration. A reminder
moves with its existing lead time, if its new time is still future. Missing reminders
stay missing. Titles lose their obsolete English date/time suffix. The new event time
is shown in the existing planner date fields. Fresh “It ends at midnight” or place
continuations can update a moved event during the same recent voice context.

Cancellation retains the event as CANCELLED in History, removes its alarm, and
rejects stale reminder deliveries. Reopen uses the existing picker/editor. Both
changes use the persistent one-step Undo journal and private notification action.
An older Undo token cannot reverse a later change or manual edit. The coordinator
compares the resolved record under its mutation lock to avoid overwriting a racing
manual edit. Replayed original event wording cannot restore the prior time or
recreate a cancelled event while its matching saved record remains present.

No raw audio/full conversation archive, calendar writes or extra phone permissions
were introduced. This does not establish speech recognition or speaker-ID accuracy.
