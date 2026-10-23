# Try SideKick 0.3 on Pixel 7a

## Version 0.4 smart analysis

With the separate Qwen model installed, stop listening, copy recent text into
**Edit or type notes**, confirm your words, and tap **Find commitments**. Or type
"I'll water the plants tomorrow." This action is outside the original rules vocabulary.
Review evidence and time wording, then approve to save. Analysis can take several
seconds; **Cancel current operation** should stop it. Negated or quoted promises should
produce no new proposals. Start with short notes, under 4,000 characters; check dates
manually. Report missed or invented tasks as well as the delay.

Install the debug APK over the previous version. Existing saved commitments remain.
Start from the visible app. Models are bundled; the phone needs no account or download.

## Live notes

1. Tap **Start live notes**, confirm, and allow notifications and microphone access.
2. Stay quiet for ten seconds. No words or commitments should appear from silence.
3. Say: "I'll call Emma tomorrow at 5 pm." Pause, then speak another short note.
4. Watch **Recent live text** and the temporary proposals below. Expect several seconds
   of delay. Speech near a chunk boundary may repeat or lose words; report examples.
5. Continue past 30 seconds. Listening should continue through speech and silence.
6. Tap **Finish listening**. The microphone should turn off while pending speech finishes.
7. Review a proposal, edit the wording and time, and approve it to save it.

Only approved commitments persist. Recent live text is a rolling view (6,000 characters
in RAM; the screen displays the latest 3,000). Older words leave the view after rules
have attempted commitment detection. This narrow detector may miss commitments. Up to
200 unapproved proposals stay in memory until accepted, dismissed or the app process
ends. Reaching that bound stops listening with a visible message. Dismissing a session
does not delete previously approved commitments or earlier pending proposals.

## Background and recovery

Start another session and turn the screen off for about a minute while speaking your
own non-sensitive notes. Reopen the app and check the text. Try **Finish** and
**Discard session** from the notification. Start another session afterward. Report
missing audio, stuck progress or the microphone remaining active after stopping.

Android interruptions, another app taking the microphone, or processing overload can
stop listening; the app shows an interruption instead of silently dropping queued audio.
It does not start listening automatically after reboot or process death.

## Reminder

Approve a proposal using **In 2 minutes**. Leave the app and note when its reminder
arrives. Android may delay inexact alarms. Complete or delete it and confirm that a
cancelled reminder does not appear later.

## Report

- Your sample sentence, recognized words and approximate delay.
- Whether silence, Finish, Discard, restart and screen-off listening worked.
- Any missing/repeated boundary words, crash or unexpected heat.
- Requested and actual reminder delivery times.

Use only your own speech; speaker matching is not implemented. Automated tests use
public fixture audio without opening the microphone. They do not establish real-world
accuracy or all-day battery life. An active listening session holds a renewable partial
wake lock so capture/processing can continue when the screen is off; battery impact
requires separate unplugged testing.
