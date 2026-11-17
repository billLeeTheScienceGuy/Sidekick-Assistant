# Try SideKick on Pixel 7a

Current 0.28 setup: use **Get started** for language, model readiness, voice setup
and permissions. English Fast is bundled; other speech modes use separately
installed verified packs (MODEL_PACKS.md). Planner, text and Summary work without
microphone permission. Use DEMO.md and CAPSTONE_CHECKLIST.md for the current demo.
Settings → Memory & privacy provides opt-in retention, JSON export and local
English risk advisories. Speaker turns are enabled by default; anonymous labels
are temporary and do not prove identity or consent. Calendar mirroring is off
until you select a calendar and confirm. Optional region guidance is under
Listening privacy & region guidance; it never authorizes listening.
Earlier version-specific notes below are historical.

## Simple home (default)

SideKick opens on the simple layout, made for people who are new to smartphones.
The bottom bar has three buttons: **Today**, **Plans** and **More**.

- **Today** shows one large **Start listening** button (it turns red and reads
  **Stop listening** while the microphone is on), a status line (**Listening** or
  **Not listening**) and, under the button, the rules line ("Rules follow your
  state …"). While listening, **Announce note-taking** and the "I told everyone"
  button appear when a notice is needed (or always, if that setting is on), and
  **Stop and clear what was heard** stops and forgets the recent words.
- If something needs checking, a **1 plan to check** button appears; it opens Plans
  on the plans waiting for you. Nothing appears when nothing needs checking.
- **Your next plans** lists up to five upcoming plans as large cards: what, when
  ("Tomorrow at 3:00 PM") and where. Each card has **Change** and either **Undo**
  (for something SideKick saved by itself) or **Remove** (asks first; removed plans
  can be brought back from Plans → Past).
- **Plans** is the full list with search and the filters To check, With reminder,
  No reminder, Past and All. Every action is a visible button; nothing needs a swipe.
- **More** opens: Summary of your day; What was heard & notes (recent words, typing a
  note, listening and voice details); Set up speech and your voice; Settings; and
  **Use the detailed screen**.

To go back to the earlier layout, choose **Detailed** under Screen at the top of
Settings (or More → Use the detailed screen). Choose **Simple** to return. The text
size choice is next to it; colors, light/dark, buttons & spacing and Type notes are
under **More screen options**. The other settings are in four sections: Listening,
Your voice, Reminders & calendar, and Privacy.

## Typed notes

Stop listening, copy recent text into **Edit or type notes** (or type your own),
confirm your words, and tap **Find commitments**. Typed notes use the same local rules
as live speech. Review evidence and time wording, then approve to save. Negated or
quoted promises should produce no new proposals. (The optional Qwen model from 0.4 was
removed in November 2026; see INTENT_MODEL.md.)

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
