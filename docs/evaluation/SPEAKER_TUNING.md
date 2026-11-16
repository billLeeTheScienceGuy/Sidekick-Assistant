# Speaker threshold tuning (task 8)

**Status: not yet run.** The recordings in "Data" below need consenting participants. No numbers on this page are
measured yet; do not quote any until the tables are filled from real runs.

## What is being tuned

Owner matching (`VoiceMatch.Thresholds` in `core`) has two sets:

| Set | When | Both windows | Balanced: min / mean | Consistency | Short phrases (1.5–3 s) |
| --- | --- | --- | --- | --- | --- |
| `STANDARD` | one-party places, or after notice | ≥ 0.70 | ≥ 0.65 / ≥ 0.72 | ≥ 0.65 | allowed (profile and recent voice ≥ model short threshold) |
| `BEFORE_NOTICE` | all-party place, notice not yet given | ≥ 0.75 | ≥ 0.70 / ≥ 0.77 | ≥ 0.65 | never USER |

`BEFORE_NOTICE` matters most: before notice in an all-party state, a stranger wrongly accepted as the owner would have
their words kept. Goal: **stranger accept = 0** in every condition, with owner accept as high as possible.

Existing evidence (public data, not a substitute): `speaker-trials.json`, 28 other-person FLEURS recordings, 121 tries,
0 accepted as owner (53 OTHER, 68 UNKNOWN); owner side one public enrollment, 2 of 3 matched. The 2026-11-14 device
health run (`.deps/bench/REPORT.md`) recorded `othersAccepted=0` of 30 other clips and `ownerAccepted=0` of 8 owner clips.
That 0 of 8 owner result is a warning sign: the current thresholds may already be too strict for real owners.

## Data (participants, written consent)

- The phone owner plus **at least 30 different people**.
- Conditions: **quiet** (same room, about 0.5 m), **noisy** (café or played crowd noise), **far** (about 2–3 m).
- Each participant signs the study consent form (recording and voice analysis; see task 12 / IRB) **before** any recording.
- Each participant reads the same short script and speaks freely for about a minute per condition.
- The owner enrolls once in quiet (Settings → Set up my voice) and repeats the same conditions.

## Procedure

1. Install the debug build on the Pixel 7a and enroll the owner.
2. For each run: `adb logcat -c`, start listening, have one person speak, Finish, then
   `adb logcat -d -s SidekickVoice:I > runs/<role>-<condition>-<id>.txt` (role: owner or stranger).
   SidekickVoice lines hold only the speaker decision, keep/drop, the consent rule and scores. No words, location, voice labels or names.
3. Do the runs once in a one-party place (STANDARD) and once with *Treat every place as the strictest state* on
   (BEFORE_NOTICE). The log line says which set was used (`thresholds standard|before-notice`).
4. Report:

   ```bash
   python scripts/phone-tests/voice_tuning_report.py owner:quiet:runs/owner-quiet-1.txt stranger:quiet:runs/stranger-quiet-p01.txt ... --out docs/evaluation/SPEAKER_TUNING_RESULTS.md
   ```

5. Pick the lowest thresholds where stranger accept stays 0 in every condition. Change `VoiceMatch.Thresholds`,
   update `PrivacyWorkPackageTest`, and record the choice below.
6. Do not commit raw logs or audio. Commit only the generated report.

## Results

| Condition | Owner accept (STANDARD) | Stranger accept (STANDARD) | Owner accept (BEFORE_NOTICE) | Stranger accept (BEFORE_NOTICE) |
| --- | --- | --- | --- | --- |
| quiet | – | – | – | – |
| noisy | – | – | – | – |
| far | – | – | – | – |

## Chosen thresholds

Not chosen yet. Current values are the starting point in the table at the top.
