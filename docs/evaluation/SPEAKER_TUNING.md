# Speaker threshold tuning (task 8)

**Status:** the participant study below (owner + 30 people) is **still not run**. On November 16, 2026 the thresholds
were tuned instead on public voices on the laptop and checked through the phone microphone; see
"Public-voice tuning (November 16, 2026)" at the end. The participant study remains the final check.

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


## Public-voice tuning (November 16, 2026)

### Method

- **Voices:** LibriSpeech test-clean (40 people, 30 readings each), test-other (33 people; their signatures form the
  stranger cohort and are never evaluated), code-switched FLEURS (about 10 readers), FLEURS (400 clips, 6 languages,
  stranger-only) and the owner's 8 voice memos. 381 stranger voices in total.
- **Rooms (one shared room per condition, like one café):** quiet; noisy (pink noise, 10 dB); babble (6 people
  talking, 5 dB); far (2–3 m: RT60 .4 s, direct sound +3 dB); echo (stress: direct sound 27 dB under the echo).
- **Laptop harness** (`scripts/laptop-eval/`): the same sherpa-onnx extractor, the same `SpeakerAudio` trimming and
  window layout as `SpeakerEngine`; a phone run of the same 188 clips gave identical signatures. Each clip is one
  captured chunk (at most 10 s). Owners are enrolled like `VoiceProfile.save` (3 samples, pairwise ≥ .6, averaged).
- **Rule for every bar:** at least .08 above the closest stranger seen in any room (including echo).
- **Replay on the phone:** the laptop played clips out loud and the phone recorded them (`ReplayCaptureTest`, same
  `MIC` source and format as `ListeningService`). Run 1: profiles from clean audio. Run 2: setup clips also replayed,
  as in the app (setup and listening through the same microphone).

### Results

Model comparison (each model at its own safe bar, one quiet-room setup):

| Model | Size | Closest stranger | Owner turns recognised |
| --- | --- | --- | --- |
| CAM++ (default) | 28 MB | .56 | **72%** |
| TitaNet-small | 40 MB | .54 | 69% (0% far and echo) |
| ERes2Net (centered) | 27 MB | .52 | 67% |
| ERes2NetV2 (centered) | 71 MB | .61 | 42% |
| ResNet34 (old "Standard") | 27 MB | .85–.93 | ~0%: strangers score like the owner; no longer offered |

CAM++, with ideas added one at a time (simulation over about 163,000 stranger turns; strangers accepted: **0**):

| Rule | Overall | Quiet | Noisy | Babble | Far | Echo |
| --- | --- | --- | --- | --- | --- | --- |
| Previous (one setup, .70) | 54% | 76% | 30% | – | 67% | 12% |
| Quiet .64 / other settings .71 | 73% | 89% | 56% | 18% | 83% | 49% |
| + session carry (.74) | 76% | 90% | 62% | 36% | 83% | 61% |
| + session-adapted profile (.67) | 78% | 91% | 64% | 36% | 83% | 63% |
| + second model ERes2Net (not shipped) | 79% | 92% | 65% | 36% | 83% | 64% |
| + neighbouring chunks (.72) | **82%** | 93% | 70% | 36% | 83% | 67% |

Overall excludes the echo stress room. Noise removal before matching made things worse (all ideas included: GTCRN 71%, DPDFNet 77%, vs 82% without; the
closest stranger to a session voice rose from .64 to .73–.75), so it is not used.

Phone microphone (replay):

| Setup | Owner recognised at .64 | Strangers |
| --- | --- | --- |
| Profile from clean audio (run 1) | 15.5% | 0 of 1,480 |
| Setup through the phone microphone (run 2) | **72.6%** | 0 of 1,460 (closest .50) |

### Chosen thresholds (CAM++, `Tuned.CAMPPLUS_STANDARD`)

Quiet-room profile .64; noise and table profiles .71; cohort check 2 deviations; session carry .74; session-adapted
profile .67 (weight .3); neighbouring chunks .72. Before notice in all-party places: unchanged (.75, no short phrases,
no session ideas), plus the cohort check.

### Limits

- Public read speech in simulated rooms; real conversation, accents, children and relatives are not covered.
- With 381 stranger voices, zero accepted means fewer than about 1 in 130 people could pass at 95% confidence; the
  .08 margin is the main safeguard. Recordings of the owner's voice played back are not detectable.
- The owner's own data is 8 short memos; 10–20 more clips in different places would turn the owner estimate into a
  measurement. The participant study above is still the final check.

## Follow-up (November 17, 2026): more voices, quality-aware bars, learning across sessions

- **More voices:** LibriSpeech dev-clean and dev-other added 73 people (454 stranger voices, 409,213 stranger turns
  under the full rule). Strangers accepted: still 0; the closest stranger moved from .558 to .563. Owner recognition
  with every idea: 81.9%. (train-clean-100 was skipped: the download ran at about 0.2 MB/s; VoxCeleb1 is prepared in
  `scripts/laptop-eval/speaker_corpus_voxceleb.py` and is added by `overnight-voice.sh` when its zip files are present.)
- **Quality-aware bars** (`VoiceMatch.QualityBars`, `SpeakerAudio.quality`): the quiet-room bar depends on the chunk's
  speech length and signal-to-noise ratio; each group is .08 above its closest stranger, never below .58, and groups
  with fewer than 20,000 stranger turns keep .64. Owner recognition at the base rule 62.2% -> 69.3%; strangers 0.

| Speech | SNR < 15 dB | 15–25 dB | 25 dB + |
| --- | --- | --- | --- |
| under 5 s | .64 | .61 | .65 |
| 5–8 s | .64 | .64 | .62 |
| 8 s or more | .58 | .58 | .62 |

- **Learning across sessions** (`VoiceprintPolicy.learned`): after a session the saved quiet-room signature moves 15%
  toward that session's very confident turns (>= .78, at least 2), never below .90 similarity to the original setup.
  Simulated: 72.0% -> 72.7%, strangers 0, the profile stayed .992 similar to the setup. It cannot bridge a large
  setup/microphone mismatch (no turn is confident enough there); setting up through the phone does (15.5% -> 72.6%).
  Consent notice version 2 mentions it, so earlier consent is asked again.

## Larger test (November 17, 2026): 1,813 stranger voices in all seven app languages

Added LibriSpeech train-clean-100 (251 readers) and Mozilla Common Voice (CC0): Korean, Arabic, Russian and Chinese
(Taiwan) Scripted 27.0, English, French and German Spontaneous 5.0, up to 300 speakers per language with at least two
clips each (1,459 people after joining one person who appeared in two languages). 962 people were set up as owners;
about 6.6 million stranger turns were checked. Results are in `.deps/speaker-public/eval/OVERNIGHT_REPORT.txt` (run 3).

**Result: the "0 strangers" result above does not hold at this size.** Under the shipped rule, 97 stranger turns
were accepted, all from **17 pairs of people** whose voices the model scores as almost the same person:

| Source | Pairs | Notes |
| --- | --- | --- |
| Common Voice Korean | 11 | small volunteer pool; several pairs look like one person under two anonymous ids |
| Common Voice Russian | 3 | |
| Common Voice English | 2 | one pair shares age, gender and accent (40s, female, Australian) |
| LibriSpeech train-clean-100 | 1 | readers 4267 and 8226 (different names; 28 of the 97 turns) |
| Chinese, Arabic, French, German | 0 | none |

- All accepted turns were clean recordings (plus the LibriSpeech pair's simulated noisy copies). No stranger passed in
  babble, far or echo rooms, and still 0 of 1,460 through the phone microphone with a phone setup.
- 6 of the 17 pairs are mutual and ≥ .90 alike (centroids), the pattern of one person recorded under two ids. Common
  Voice gives a new anonymous id per browser or device, so this is likely for several pairs but cannot be proven.
- These pairs cannot be removed by raising the bar: rejecting all of them needs a bar of about .86 with cohort z 5,
  where the owner is recognized about 4% of the time. Recomputing the quality-aware bars on this set gives bars above
  .9 (owner 0.6%), so the quality bars stay as tuned on the 454-voice set.
- Owner recognition on this set: 78.9% with every idea (81.9% on the smaller set); learning across sessions
  77.3% -> 77.7%.

**Decision:** keep the shipped rule. The honest claim is that a stranger is very rarely taken for the owner (17 pairs
among about 2,150 speakers, none outside very similar-sounding pairs), not that it can never happen. Voices that sound
nearly identical to the model, such as close relatives or the same person on another device, can occasionally be
taken for the owner. The earlier confidence statement ("fewer than about 1 in 130 people") is superseded by this test.
