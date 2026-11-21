# SideKick privacy policy

SideKick is a student research prototype (Android). It runs on your phone; it has no server and no account.
This policy describes what the app stores, for how long, and how to delete it. It is an engineering summary of
how the app works, not legal advice. Last updated: November 16, 2026 (voiceprint notice version 1, spoken-notice text version 2).

## What is never stored

- **Audio.** Microphone audio exists only in short in-memory buffers while it is being transcribed, then it is overwritten. No recordings are kept.
- **Other people's voice data.** To tell speakers apart, SideKick turns a stretch of speech into a voice signature
  (a list of numbers) in memory, compares it once, then erases it. Signatures of other people, the anonymous
  "Voice 1 / Voice 2" groups (forgotten after 30 minutes without that voice) and any name you give a voice for a session are never written to the database,
  settings, logs, exports or backups. They are cleared when the session ends, and immediately when the rules
  where you are do not allow voice grouping.
- **Your location.** Location is used only to look up which state's rules apply, with state borders bundled in the app. The position is not saved, logged or sent anywhere.

## What is stored on this phone

| Data | Why | Where | How long | How to delete |
| --- | --- | --- | --- | --- |
| **Your voice signature** (numbers that describe your voice; no audio) | Recognize your own plans and reminders | App's no-backup storage, this phone only | Until you delete it, or automatically after **12 months unused** | Settings → Listening → *Delete my voice setup* |
| **Your “SideKick” signature** (optional SideKick voice check; numbers that describe how you say the name; no audio) | Speak answers to your own “SideKick, …” questions | App's no-backup storage, this phone only | Until you delete it or your voice setup, or automatically after **12 months unused** | Settings → Set up my voice → SideKick voice check → *Delete*, or *Delete my voice setup* |
| **Your voice consent record** (the name you typed, time, notice version, method) | Proof of your written consent before your voice signature was made | App settings, this phone only | Deleted together with your voice signature | Same as above |
| **Notice records** (time, state code(s), recording rule, voice rule, notice text version, spoken / spoken automatically / "I told everyone", volume, measured loudness, counted or not) | Shows when and how people were told about note-taking. No audio, no voice data | App database | Same as your saved plans (Settings → Privacy → retention); deleted with the session if you discard it | *Discard session*, retention setting, or *Delete all* |
| **Saved plans and events** (title, time, place; optional short text excerpt) | Your reminders and calendar items | App database | Until you delete them, or the retention period you choose | Planner, or Settings → Privacy |
| **Scam warning signs** (which kinds of sign were seen and when, e.g. “asked for a code”; never the words) | Warn you, answer “SideKick, is this a scam?”, summarize a call afterwards | Memory only | 10 minutes, or until the call ends | Turn off *Scam warnings* in Settings → Privacy |
| **Recent live text** | Review before saving | Memory only | Until the session ends or you clear it | *Discard session* |

Android backup is turned off for voice data; the voice signature lives in no-backup storage.

## Consent rules SideKick applies

SideKick follows the **stricter** of the rules of the state you are in and any state within the location error margin
(about 1.5–2 km with precise location, about 5.5 km with approximate location). If the location is unknown, stale,
outside the US, during a phone call, or set by a mock-location app, it uses the strictest rules. The city you enter in Settings is never used for these rules.

- **Recording rule** (whose words are kept). All-party states (CA, FL, IL, MD, MA, MT, NH, PA, WA, and CT, DE, MI, NV, OR, VT treated as all-party):
  other people's words are kept only after the spoken notice ("Just so you know, SideKick is taking notes on this conversation.
  It briefly analyzes voices to tell speakers apart. Nothing about anyone's voice is saved." Both lines are spoken in every state.)
  or after you tap *I told everyone*. One-party states: other people's words are kept as information while you take part in the conversation.
- **Voice rule** (whether voices may be grouped).
  - Illinois: other voices are never grouped or named.
  - Texas and Washington: voices are grouped only after the notice (or after you tap *I told everyone (incl. voice analysis)*).
  - Elsewhere: grouping is allowed unless you turn on *Group voices only after notice, in every state*.
- **Your own voice signature** needs your written consent in every state: a notice, your typed name and a ticked box
  ("I consent and sign"), before anything is recorded. Under 18: a parent or guardian must agree.

The spoken notice plays at **at least half volume** (the phone's media volume is raised for it and put back afterwards).
It is not played, and does not count, if sound would go to headphones or Bluetooth, if the proximity sensor shows the
phone is covered or in a pocket, or if the phone is muted. While it plays, the microphone measures only how loud it was
(a number, no audio) to confirm sound actually came out. None of this proves that everyone heard it. Each notice record stores the
volume, the measured loudness and whether it counted. *Announce automatically when a new conversation starts* (off by
default) plays the notice when another voice is first heard; that first phrase is not kept.

A notice covers **one conversation**: it ends after 30 minutes without other people speaking, or 2 hours after it was given,
whichever comes first, and then has to be given again. *Always show the Announce button* (on by default) lets you re-announce at any time.

Settings → Privacy → *Treat every place as the strictest state* turns off all location checks and applies the strictest rules everywhere.

## Your choices

- Turn off *Recognize my voice* at any time; voice matching stops but your setup is kept.
- *Delete my voice setup* removes every voice signature and the consent record at once.
- *Delete all* removes saved plans, notice records and temporary text.
- Uninstalling the app removes everything it stored.

## Contact

SideKick is a university course project. Questions: open an issue at
https://github.com/billLeeTheScienceGuy/Sidekick-Assistant/issues.
