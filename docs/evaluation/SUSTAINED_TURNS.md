# Sustained controlled speaker-turn trial

November 8, 2026 · physical Pixel 7a · Android 17/API 37 · isolated compacttest app.
This used public JFK speech and synthetic party speech, not the microphone or
private conversations. English Fast, CAMPPlus owner matching and neural speaker
segmentation ran sequentially. Three distinct windows of the same public voice
were enrolled in an isolated temporary profile. USB remained connected.

| Metric | Result |
|---|---:|
| Requested source audio | 600 seconds |
| Test wall time | 686.709 seconds |
| Emitted transcript segments | 180 |
| Aggregate chunk processing work | 265.306 seconds |
| Processing work / source audio | 0.442 |
| Final queue drain | 1.581 seconds |
| Party keyword segments | 60 |
| Party segments wrongly labelled owner | 0 |
| Maximum process PSS | 408,888 KiB (399.3 MiB) |
| Maximum Android thermal status | 0 (none) |
| Observed battery temperature | 24.5 → 25.7 °C |

The real-time-paced test passed its bounded queue, transcript/turn-row bounds,
identity exclusion, final drain, memory and thermal guards. No raw audio or
transcripts are written by the test; its result JSON contains metrics only.
These repeated fixtures do not measure population accuracy, missed microphone
speech, noisy conversation, multilingual quality, 24-hour operation or battery
drain. USB and repeated known voices limit the conclusion.

To reproduce, build/install only the isolated compactTest app and its test APK,
then run the following against a connected test device. Do not instrument the
personal `com.sidekick.app` package. Use a suitable device and leave USB connected.

```powershell
adb -s DEVICE_SERIAL shell am instrument -w -e class com.sidekick.app.SustainedTurnsTest -e runSustained true -e sustainedSeconds 600 com.sidekick.app.compacttest.test/androidx.test.runner.AndroidJUnitRunner
adb -s DEVICE_SERIAL exec-out run-as com.sidekick.app.compacttest cat files/sustained-turns.json
```

The trial is opt-in and excluded from the quick regression suite. It opens no
microphone, uses a bounded wake lock, aborts on severe thermal state/unplugging,
and deletes its temporary isolated enrollment. SustainedTurnsTest records the
conditions and outcome even when an assertion fails.
