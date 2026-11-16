# Sustained controlled speaker-turn trial: 60 minutes

November 16, 2026 · physical Pixel 7a · Android API 37 · isolated compacttest app, built from branch `privacy-consent-location`.
Same method as the [10-minute trial](SUSTAINED_TURNS.md): public JFK speech (owner, enrolled from three windows in an
isolated temporary profile) alternating with synthetic party speech, paced in real time, with no microphone.
English Fast, CAMPPlus owner matching and neural speaker segmentation. AC power connected.

| Metric | Result |
|---|---:|
| Requested source audio | 3,600 seconds (60 minutes) |
| Test wall time | 4,124.4 seconds |
| Completed | yes |
| Emitted transcript segments | 1,079 |
| Aggregate chunk processing work | 1,540.2 seconds |
| Processing work / source audio | 0.428 |
| Final queue drain | 1.592 seconds |
| Party keyword segments | 360 |
| Party segments wrongly labelled owner | **0** |
| Process PSS, first → last sample | 343,852 → 403,375 KiB |
| Maximum process PSS | 410,359 KiB (400.7 MiB) |
| Maximum Android thermal status | 0 (none) |
| Observed battery temperature | 23.8 → 26.4 °C max |

Metrics JSON (no audio, no transcripts): [sustained-turns-3600.json](sustained-turns-3600.json).

Run note: the laptop lost its adb connection before the test finished, so `am instrument` returned exit code 255 with no
result line. The test itself finished on the phone, and its result file (written by the test's `finally` block, stamped
the same night, `completed: true`) was read back afterwards with `run-as`.

Slide wording: "60 minutes of continuous paced speech on a Pixel 7a: kept up in real time (43% of the time budget),
memory under about 400 MB (it rose from 336 to 394 MiB over the hour, so a longer run is needed before claiming it is flat), no overheating, 0 of 360 other-voice segments taken as the owner." Limits, as in the
10-minute trial: repeated public/synthetic fixtures over USB power, not a live microphone, a real conversation, or battery drain.

Reproduce:

```powershell
adb shell am instrument -w -e class com.sidekick.app.SustainedTurnsTest -e runSustained true -e sustainedSeconds 3600 com.sidekick.app.compacttest.test/androidx.test.runner.AndroidJUnitRunner
adb exec-out run-as com.sidekick.app.compacttest cat files/sustained-turns.json
```
