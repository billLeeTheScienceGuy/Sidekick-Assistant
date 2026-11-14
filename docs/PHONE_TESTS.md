# Phone tests that need a person

Everything else runs unattended (`scripts/laptop-eval`, `DeviceHealthTest`, `NaturalSpeechEvaluationTest`). These two
need real voices in a real room. Plug in the Pixel with USB debugging on, open PowerShell in the repo folder.
The script reads only the app's `SidekickVoice` log: voice-match scores and consent decisions, never words or location.

## 1. Voice setup and matching (do this first)

Why: your voice profile was made from audio that scores you at 0.59–0.71, just under the bar (other people ~0.10–0.39).
Your words are always kept, and in one-party states others' words are kept only while you're in the conversation,
so the match decides what consent covers.

1. In SideKick: Settings → **Update my voice profiles**. Choose **English**, read the 3 sentences at a normal pace
   (each at least 4 seconds), in a quiet room, holding the phone as you normally would.
2. Do the same again with **Korean** chosen.
3. Run the check, then start listening in SideKick:
   ```
   .\scripts\phone-tests\live-check.ps1 -Minutes 3
   ```
4. For the first 1½ minutes, talk alone: a few English sentences, a few Korean ones, one mixed (“I'm meeting Sarah
   at 8 tonight, 저녁 8시에 만나요”). Pause 2 seconds between sentences.
5. For the next 1½ minutes, have someone else talk (or play a podcast from another phone), with you silent.
6. When time is up, tap **Finish**.

**Pass:** `USER` covers most of your phrases with best voice scores mostly ≥ 0.70; the other voice shows as `OTHER` or
`UNKNOWN` with scores under ~0.45. **If your scores stay 0.60–0.70:** send me the summary; the bar can be tuned with
this live data (not from Recorder files). Never lower it without this check.

## 2. Consent behaviour

Location: the app checks the state from the phone's location every 10 minutes, offline. It shows the rule under the
record button, e.g. “Illinois: only your words are kept until you announce note-taking” or “Wisconsin: you're in the
conversation, others' words kept as information”. Unknown or stale location, abroad, or within the location's
accuracy circle of an all-party state always means all-party.

Run `.\scripts\phone-tests\live-check.ps1 -Minutes 4` and start listening, then:

1. **Before any notice** (1 min): you say one sentence, then the other person talks.
   - One-party state (e.g. Wisconsin): their phrases are **kept** while you spoke in the last 2 minutes.
   - All-party state (e.g. Illinois, California): their phrases are **dropped** (“N phrases from other voices dropped, never saved” rises)
     and never transcribed.
2. Tap **Announce note-taking**: the phone says “Just so you know, SideKick is taking notes on this conversation.”
   (Or tell people yourself and tap **I told everyone**.)
3. **After the notice** (2 min): both of you talk. Their phrases are now **kept** in any state.
4. Tap **Finish**.

**Pass:** the summary's “Notice given after phrase N” line shows others kept 0/x before the notice (all-party state)
and all/most kept after it; your own phrases are kept throughout. In Illinois, other voices are never grouped by voice
(no “Voice 2” labels).

Also check by eye: the notification has an **Announce** action while recording; the rule label changes if you drive
across a state line during a long session (it re-checks every 10 minutes).

## 3. Unclear Korean time (optional, 1 minute)

Say quickly: “제가 내일 저녁 8시에 Sarah 생일 파티에 갈 거예요.” In **Review** (Planner), an item marked **Unclear time ·
check the time before accepting** appears with the most likely time (저녁 8시) if the app could not settle it; Hands-off
mode never saves it automatically. With the Korean time-check pack installed it is usually settled to 8시 directly.

## 4. Names in Korean speech (optional, 1 minute)

Settings → **Names you often mention**: add `Sarah 세라` (one per line: the name, then how it sounds in Korean).
Needs the **Korean names & time check** speech file. Then say: “내일 저녁에 세라랑 같이 밥 먹기로 했어요.” The note should
read “Sarah랑”. Repeat with a sentence that has no name (“열쇠가 사라졌어요”): nothing should change.