# Try SideKick 0.2 on Pixel 7a

Install the updated APK over the previous version; no uninstall is needed. Existing commitments are migrated. Keep the app open for the first tests. No cloud login or model download on the phone is required.

## 1. Real speech

Tap **Record my voice**, confirm, and grant microphone permission. Say: "I'll call Emma tomorrow at 5 pm." Tap **Finish & transcribe**. The mic should stop and the transcript should appear. Note how many seconds transcription takes and whether names/times are correct. Correct the text if needed, confirm it is your own speech, and tap **Find commitments**.

Try one quiet recording and one with ordinary room noise. Speaker identification is not implemented; don't record other people's conversations. Transcription can hallucinate on noise, so review the text.

## 2. A real reminder

Tap **Review & accept**. Grant notifications if prompted. Edit the title, tap **In 2 minutes**, and **Save & accept**. The editor displays the timezone. Leave the app and watch for the reminder. These alarms are intentionally inexact: Android may delay them, particularly when the phone is idle. Report the requested time and actual delivery time.

## 3. Recovery and persistence

Start a recording and tap **Discard recording**. Start another; it should work. Try cancelling while transcription runs. Verify that accepted items remain after reopening. Mark a scheduled item **Done**, or delete it; it should not notify later. Clear sensitive text and delete saved test commitments when finished.

## What to report

- Did microphone permission, recording and Finish work?
- What did you say, what text appeared, and roughly how long did it take? Use non-sensitive sample sentences.
- Did the reminder arrive, and how late was it?
- Any stuck progress, crash, unusual heat or repeated notification?

Automated tests use a bundled public speech fixture and do not activate your microphone. Those tests cannot establish accuracy for your voice, background recording behavior, or battery life.
