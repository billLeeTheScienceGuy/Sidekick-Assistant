# End-to-end reliability — 0.22

0.22.1 field follow-up: the two-minute check actually posted 14.037s after due and
remained in Android's notification tray. The phone was in Silent mode with muted
notification audio; channel vibration and pop-up were off. Enabled those two channel
options through Android settings, preserving global Silent mode. Setup status now
separates posting permission from perceptible alerts and links directly to channel
controls. New channel defaults use HIGH/vibration; existing settings require Android
UI changes. VIBRATE permission and REMINDER category are declared. Ring/Vibrate mode
must be chosen by the user for the next sound/vibration check.

## Changes

- Planner shows the latest automatic change for up to five events, with Undo.
  Dismissing or blocking Android notifications no longer removes the in-app path.
  Manual edits and stale tokens remain protected by the existing snapshot checks.
- Undo now rolls back the saved event, Undo token and alarm state if restoring an
  alarm fails. Automatic-change notifications are cleared after successful writes,
  so an earlier Undo action survives an alarm-scheduling failure.
- Returning to the app re-arms pending reminders, including missed reminders after
  notifications were enabled again. Planner shows a blocked-alert notice and links
  to Android notification settings when necessary. Reminder notifications use a
  stable identity and only-alert-once behavior.
- Settings → Setup status includes **Test a reminder in 2 minutes**. It creates
  one clearly labelled test reminder, replaces any previous pending check, and
  records a reversible change. It does not open the microphone. Undo reverses the
  latest check; the first check can be removed entirely that way.

Existing saved events and voice profiles are preserved. The database remains v5;
new observation queries do not require an entity/schema migration. Recognition
models and speaker thresholds are unchanged.

## Automated coverage

Isolated Pixel tests cover event extraction from synthetic text → personal saved
event/reminder → reschedule → cancel → stale-Undo rejection → Undo → reminder edit
→ actual Android PendingIntent/ReminderReceiver → notification → completion.
Repeated delivery and late broadcasts after completion are ignored. This does not
capture the microphone or establish real AlarmManager timing under Doze: the test
can send the pending broadcast to exercise the receiver deterministically.

Fault-injection tests at the alarm-adapter boundary verify failed creation,
rescheduling and Undo preserve database/journal consistency and leave no ghost
scheduled reminder. A failed Undo remains retryable. Compose tests verify Planner
Undo after notification dismissal, and the Settings check creates a labelled
reminder with an undoable entry. Existing reminder/event/automation tests also run.

## Next phone check

1. Open **Settings**, tap **Test a reminder in 2 minutes**, then close Settings.
2. Unplug and lock the Pixel. No speaking or live listening is needed.
3. Check whether the reminder notification arrives, and note any delay. Android
   may defer the inexact alarm, so the displayed time is not an exact-delivery promise.
4. Open **Planner → Recent automatic changes** and use Undo on the reminder check.

Report arrival/absence, approximate delay, and whether Undo worked. This validates
the normal app's background notification path. Longer unplugged listening trials,
thermal/memory measurements, interruptions and broader permission recovery tests
remain later work. No additional isolated Korean number drill is requested.
