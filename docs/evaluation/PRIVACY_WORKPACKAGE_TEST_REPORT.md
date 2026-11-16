# Privacy, consent and location work package: test report

Branch `privacy-consent-location` (from `main` at `35787d1`), November 16, 2026. Covers handoff tasks 1–9 and 11, plus task 10 option (a), which the team chose.
Tested on a laptop (Windows 11, JDK 21) and a Pixel 7a (Android API 37) over USB with AC power.
This is an engineering approximation of US law, not legal advice.

## Summary

| Area | Result |
| --- | --- |
| Handoff build command (`prepare_*.py`, then `:core:test :app:assembleDebug :app:lintDebug`) | **Passed**: BUILD SUCCESSFUL, lint 0 errors / 65 warnings |
| Core unit tests | **244 / 244 passed** (23 cases in new or rewritten consent test classes) |
| New on-device test `PrivacyConsentDeviceTest` | **4 / 4 passed** (after one real bug was found and fixed, see below) |
| Existing speaker / privacy device tests (9 classes) | **18 / 18 passed** |
| 60-minute `SustainedTurnsTest` (`-e sustainedSeconds 3600`) | **Completed and passed** on the phone: 1,079 segments, 0 of 360 party segments taken as the owner, work ratio 0.428, thermal 0 ([details](SUSTAINED_TURNS_60MIN.md)) |
| Debug APK on the phone, manual UI checks | **Installed over 0.34.0 without data loss**; settings, consent screen, start-of-session dialogs, live rule status, attested notice, notice record and *Finish* all checked (details below) |
| Task 8.2 recordings, task 12 IRB, legal confirmation | **Not done**: only people can do these (see the end) |

## What was implemented

| Task | Where | What |
| --- | --- | --- |
| 1.1 data | `scripts/build_state_borders.py`, `core/src/main/resources/us-state-borders.txt` | Census `cb_2023_us_state_500k`, Douglas-Peucker at 100 m, islets under 0.25 km² dropped (they read as "outside the US", which is strictest). 56 areas, 1,126 rings, 120,671 points, 2,025,274 bytes (1.93 MiB). Two columns: `recording` = one/mixed/all, `biometric` = none/notice/strict (IL strict; TX, WA notice). Data-driven: add a state to the sets in the script. |
| 1.2–1.4 | `core/.../RecordingConsent.kt` | `Place(recording, biometric, forcedBy)`; `groupsOtherVoices = biometric != STRICT` (fixes the old bug where an unknown location or a point near Illinois still grouped voices). Strictest-of-nearby, taken independently per rule. Reasons such as "Within 2 km of Illinois: all-party rule, no voice grouping". Unknown, no permission, stale, abroad or a lookup exception give ALL_PARTY + STRICT. |
| 1.5 buffer | same | Precise: max(accuracy, 1 km) + 500 m. Coarse or unknown accuracy: (accuracy or 2 km) + 3 km + 500 m. **Deviation, toward stricter:** the handoff formula max(accuracy, floor) + margin gives 3.5 km for coarse, which contradicts its own test "coarse 2,000 m, 4 km from Illinois → STRICT" and its "~5 km with coarse location". Adding the floor makes both true; Android's approximate-location grid offset is not in the reported accuracy anyway. |
| 1.6 | Manifest, `MainActivity` | Optional `ACCESS_FINE_LOCATION`, asked once before listening ("More accurate state detection near borders"). Coarse-only still works. |
| 2.1 | `audio/JurisdictionLocator.follow` | `requestLocationUpdates` (500 m, 60 s) plus a 5-minute fallback poll while listening; every failure → strict. |
| 2.2 | `SpeakerTurnEngine.forgetOthers`, `TemporaryMemory.forgetVoiceNames` | Clears SessionVoices, session names, naming context and recent anchors. Called on every rule refresh where grouping is not allowed (STRICT, or NOTICE before notice), and at session end. |
| 2.3 | `ConsentGate.moveTo` | Clears the notice if the new place is stricter in either rule than where the notice was given. |
| 3 | `GuidanceSettings`, `ConsentRulesSection`, `ListeningService` | "Treat every place as the strictest state": no location requests at all; ALL_PARTY + STRICT. Turning it on mid-session stops the location follower at once. |
| 4.1–4.6 | `ConsentNotice`, `ConsentGate`, `ListeningService`, `MainActivity` | Base text plus the voice line where required (TX, WA, IL, unknown, or the setting). NOTICE places do not group until the voice line was spoken or attested. Buttons show when the recording notice or the voice notice is missing. Attestation reads "I told everyone (incl. voice analysis)" in NOTICE places. Illinois never groups. |
| 4.7 | `NoticeRecordEntity` (Room v7, `MIGRATION_6_7`), session details | Time, state codes, recording rule, voice rule, text version, includes-voice flag, method (SPOKEN / ATTESTED). No audio, no voice data, no position. Deleted on *Discard session*, *Delete all* and by the retention setting. |
| 5.1–5.5 | `VoiceEnrollmentActivity.VoiceConsentNotice`, `VoiceProfile`, `SidekickApplication`, `docs/PRIVACY_POLICY.md` | Notice (what, why, where, retention, deletion), name field, unticked "I consent and sign", optional signature box (the drawing is not stored, only the method), under-18 guardian box. "Agree & record" stays disabled until ticked. Record stored in voice preferences and deleted with the profile. Profiles that existed before this version: matching off until accepted, a dialog before the next session and a note in Settings. Retention: profile and record deleted on app start if unused for 12 months. Privacy policy linked from README and Settings. |
| 6 | `SessionVoices.name`, `SessionNaming`, `VoiceInvitations`, `TemporaryMemory`, `MainActivity` | Session-only names in memory. Allowed only when grouping is allowed and others' words may be kept. Tap a voice label to name it. A "<Name>?" chip when the owner says "Hey <Name>" and a different grouped voice speaks within 5 s; applied only on tap. Invitations from other voices become information-only "<Name or Voice N>: meet at 5". "I'll meet you at 5" right after a named voice becomes "Meet <Name> at 5". A short "sure" within 30 s goes to Review only (`review:` source, never saved automatically). |
| 7 | `VoiceMatch.Thresholds` | `STANDARD` (unchanged) and `BEFORE_NOTICE` (0.75 / 0.70 / 0.77, no short-phrase USER), used when recording is ALL_PARTY and notice is not given. |
| 8.1, 8.3 | `scripts/phone-tests/voice_tuning_report.py`, `docs/evaluation/SPEAKER_TUNING.md` | Script reads only the numeric fields of SidekickVoice lines and refuses anything else. The doc is a scaffold with the procedure; **no data yet**. |
| 9 | `ListeningService` | `AudioManager.mode` IN_CALL / IN_COMMUNICATION, checked every second → ALL_PARTY + STRICT for the call, status "Call detected: strictest rule"; the located place is restored after the call. |
| 10 (a) | `VoiceMatch.useOnce`, `SpeakerTurnEngine.classify` | The in-memory signature is zeroed right after its one comparison, also on error. Tested. |
| 11 | `scripts/summarize_model_bench.py`, `docs/evaluation/ENGLISH_BENCHMARK.md`, `MODEL_BENCH_SUMMARY.md` | Generated from the existing ModelBench results in `.deps/bench/`. They reproduce the slide numbers exactly (see below). |

Also changed:
- **Pre-existing bug fixed.** The old 10-minute location loop was a child of the session's `coroutineScope` and never ended. After *Finish*, the session waited on it, so a session could stay in "Finishing" until the loop's next wake-up or forever. Location, settings and call watchers are now cancelled when the speech worker finishes. Verified on the phone (see below).
- SidekickVoice log lines no longer include the "Voice N" label. They now carry speaker, kept, both rules, notice, grouping, call and scores only.

## Test results (real output)

### Handoff build command

```
Verified speech model already present.
Verified speech detector ready for APK packaging.
Compact inputs ready; optional Whisper packs were not downloaded.
Verified campplus-advanced.onnx
Verified sherpa-onnx-1.13.8.aar
Verified voxceleb_resnet34_LM.onnx
Verified offline diarization model: 1,540,506 bytes, MIT (CNRS/pyannote).
...
> Task :app:lintDebug
BUILD SUCCESSFUL in 1m 14s
59 actionable tasks: 22 executed, 37 up-to-date
lint-results-debug.txt: 0 errors, 65 warnings
```

Python 3.12.10 was installed with winget for the prepare scripts. Gradle needs JDK 17 or 21 (`JAVA_HOME=%USERPROFILE%\.jdks\jbr-21.0.11`); Android Studio's bundled JBR 25 fails with Gradle 8.12. The new warnings in changed files are `UseKtx` style hints, the same style the existing code uses.

### Core unit tests: 244 / 244

New cases: `RecordingConsentTest` (14 cases, with every handoff case), `PrivacyWorkPackageTest` (7), `TemporarySignatureTest` (1), `InvitationDatingTest` (1).

Handoff cases, all passing (coarse location unless noted):

| Case | Point | Result |
| --- | --- | --- |
| Deep in Ohio | Columbus | ONE / NONE |
| Deep in Illinois | Springfield | ALL / STRICT |
| Wisconsin 2 km from Illinois | 42.5131, −89.0 | ALL / STRICT, groups = false, forcedBy = [IL], "Within 2 km of Illinois: all-party rule, no voice grouping" |
| Texas 2 km from New Mexico | 33.5, −103.0 | ONE / NOTICE |
| Oregon 2 km from Washington | 45.982, −118.0 | ALL / NOTICE, forcedBy = [WA] |
| Nevada 2 km from California | 40.5, −119.976 | ALL / NONE |
| No fix | – | ALL / STRICT |
| Coarse 2,000 m accuracy, 4 km from Illinois | 42.5311, −89.0 | ALL / STRICT |
| (extra) Wisconsin 2 km from Illinois, precise 50 m | | ONE / NONE (1.5 km buffer) |
| (extra) Wisconsin 1 km from Illinois, precise | | ALL / STRICT |

ConsentGate: notice reset Ohio → Washington; kept on Washington → Oregon (looser), back to Washington (equal) and Ohio (looser); reset on a move into Illinois. Texas: buttons shown although one-party; no grouping or naming before the voice notice; the base-only notice in Washington keeps words but not grouping; Illinois never groups after notice. Thresholds: 0.72/0.72 is USER under STANDARD and UNKNOWN under BEFORE_NOTICE; short phrases are never USER before notice.

### On-device: `PrivacyConsentDeviceTest` (compacttest package, isolated data)

```
com.sidekick.app.PrivacyConsentDeviceTest:....
Time: 1.031
OK (4 tests)
```

- `noticeRecordTableMigratesFromVersion6`: Room 6 → 7 migration validates against the exported schema; saved plans survive.
- `sessionVoiceNamesStayInMemory`: "Hey Zebulon" → grouped voice within 2 s → chip; named voice "Meet me at 5 tomorrow." → information-only event "Zebulon: meet at 5 tomorrow" with no reminder; owner "Sure." → owner event "Meet Zebulon at 5 tomorrow" in Review (`review:` source). After saving to a Room database: the name is in no column other than title/evidence/review note, notice records hold no name or voice key, the JSON export has no `spk_`, no `sourceKey` and no "Voice 1", and no SharedPreferences file holds the name or the voice key. `forgetVoiceNames()` leaves no nameable label.
- `ownerVoiceprintNeedsConsentAndExpires`: an upgraded profile without a record → `consentNeeded`, no matching references; after consent → matching; purge after 12 months + 1 day; *Delete my voice setup* removes the record.
- `jurisdictionRulesOnDevice`: the 1.93 MiB border file loads on the phone; Wisconsin near Illinois → ALL / STRICT, Houston precise → ONE / NOTICE.

**Bug found by this test and fixed:** the first run failed (`NoSuchElementException`): "meet me at 5" made no event, because the existing activity rules do not date a bare hour without am/pm. Fix (`VoiceInvitations.propose`): 1–7 o'clock reads as pm, 8–12 as the next occurrence, and the item is always flagged "Time read as the next 5 o'clock; check am or pm." Covered by `InvitationDatingTest`. Rerun: 4/4.

### On-device: existing tests

```
SpeakerTurnsTest, SpeakerIdentityTest, ShortVoiceReferencesTest, CamplusSpeakerTest, LanguageVoiceProfileTest,
TrackedVoiceChangesTest, MemoryPrivacyTest, BundledOfflineTest, SpeakerPaddingTest
Time: 12.495
OK (18 tests)
```

Some classes report 0 run tests because their methods are opt-in (`assume…`); the 18 that ran passed.

### On-device: 60-minute `SustainedTurnsTest`

```
{"device":"Pixel 7a","sdk":37,"requestedAudioSeconds":3600,"wallMs":4124421,"completed":true,"emittedSegments":1079,
 "processingWorkMs":1540206,"queueDrainMs":1592,"partyKeywordSegments":360,"falseOwnerPartySegments":0, ...}
361 observations · max PSS 410,359 KiB · max thermal status 0 · battery 23.8 → 26.4 °C · always plugged in
```

The laptop slept overnight before the run ended, so adb dropped (`am instrument` exit 255) and the phone briefly showed
as unauthorized until the adb server was restarted. The test still finished on the phone; its result file (written in the
test's `finally` block, stamped that night) was read back with `run-as`. Committed as `sustained-turns-3600.json`
(metrics only) with [SUSTAINED_TURNS_60MIN.md](SUSTAINED_TURNS_60MIN.md). The agent should have turned on keep-awake for the
whole run; next time, run it with the laptop kept awake.

### On the phone: debug app, manual checks (agent-driven over adb)

The debug APK (0.35.0 + this branch) installed over 0.34.0 with `adb install -r`: no uninstall, saved plans kept.

| Check | Result |
| --- | --- |
| Settings → Privacy: "Conversation rules", both toggles, "Privacy policy" link | Shown. Turning "strictest" on writes `strictest_everywhere=true` and disables the voice-line toggle; turned off again. |
| Your existing voice profile (3 files, saved before this version) | Flagged `legacy_needs_consent`. Settings shows "Voice consent needed. Voice matching is off until you agree." Live logs say `voice consent needed; matching off`. **Not signed by the agent.** |
| Voice setup consent screen | Notice, name field, "I consent and sign", "I'm under 18", signature box. "Agree & record" stays disabled with a name and no tick, and enables when ticked. Unticked and left without agreeing; no consent record saved (checked in preferences). |
| Bug found and fixed | A scroll swipe over the signature box drew a stroke outside it. The canvas is now clipped (`clipToBounds`). |
| Start listening | "Voice consent needed" dialog → *Listen without it* → "Precise location?" dialog (chose *Not now*) → listening started. |
| Live status (state name withheld in this public report) | "<state>: only your words are kept until you speak · <state>: one-party rule". No notice buttons, which is correct for one-party / voice-none. |
| With "voice-analysis line in every state" turned on during the session | Status added "· voice grouping after notice"; buttons "Announce note-taking" and "I told everyone (incl. voice analysis)" appeared. |
| Tapped "I told everyone (incl. voice analysis)" | Buttons gone; status "you're in the conversation, others' words kept as information". |
| Session details → Notice record | "09:55:20 · you told everyone · <ST> · one-party · voice none · incl. voice line · text v2" |
| SidekickVoice log lines | e.g. `speaker=UNKNOWN kept=true rule=ONE_PARTY biometric=NONE notice=true grouping=true call=false · 2.2s captured · 2.2s speech: voice consent needed; matching off`. No words, voice labels or names. |
| *Finish listening* | Back to "Start listening" in **3 s** (the old session-end hang is gone). |
| Crash buffer | 0 SideKick crash lines. |
| Settings restored | Both new toggles back to off (default). |

## Evidence for the presentation numbers (task 11)

| Claim | Source now committed | Result |
| --- | --- | --- |
| English "0.5% / 2%" | `docs/evaluation/ENGLISH_BENCHMARK.md` | Parakeet TDT 110M: 0.5% (39 clean clips), 2.0% (117 noisy/far clips); SenseVoice 2.1% / 3.2%. Character (letter) error. Long natural passages: 13.3%, so do not quote 0.5% for those. |
| "16 models, ~1,380 clips" | `docs/evaluation/MODEL_BENCH_SUMMARY.md` | 16 (15 recognizers + the language identifier), **1,377** distinct clips (ar 250, de 161, en 204, fr 184, ko 198, ru 199, zh 181). |
| "0 of 30 other speakers" | `speaker-trials.json`; `.deps/bench/REPORT.md` device health | Public trials: 0 of 28 other voices (121 tries). The 2026-11-14 device-health run did show `othersAccepted=0` of 30 other clips, **but also `ownerAccepted=0` of 8 owner clips.** Use "0 of 28 other voices (121 tries)" until task 8 is done, and mention that owner acceptance is not yet measured on real owners. |
| "60 minutes nonstop" | `docs/evaluation/SUSTAINED_TURNS_60MIN.md`, `sustained-turns-3600.json` | Done: 60 minutes of paced public speech on the Pixel 7a, completed. Not a live microphone or battery claim. |

## What failed, was skipped, or was not verified

- **Task 8.2 data, task 12 and the legal review:** not done; people only (below). `SPEAKER_TUNING.md` has no numbers.
- **The spoken notice was not played on the phone.** It was tested at night in an occupied home, so text-to-speech was not triggered. The attested path ("I told everyone") was tested. The spoken path uses the same `noticeGiven` code plus Android TTS.
- **Phone-call detection was not exercised.** A call cannot be placed from adb without a second phone. The code path is a 1-second `AudioManager.mode` check.
- **Moving across a border mid-session** was not tested physically. The rule logic is unit-tested; the location-update wiring was only checked as far as "a place was located at session start".
- **Precise location permission:** the one-time prompt appears, but I chose "Not now". Granting a location permission on your phone is your decision.
- **Your existing voice profile now needs your consent.** Voice matching on your phone is off until you open Settings → Listening → *Review voice consent* and sign. I did not sign for you. During UI testing I typed a test name and ticked the box to check that the button enables, then left without agreeing; nothing was saved.
- The 120,671-point border file makes the first lookup slower than before. Not measured separately; the device test that loads it ran in about 1 s in total.
- Persisted rows still carry the random per-session voice key in `sourceKey` (existing behaviour, used for same-voice event changes). It is random per session and never linked to a stored signature, and it is not in the export. If the team wants zero voice keys in Room, strip the track for rows from named voices.

## Only you can do

1. **Legal confirmation.** Ask the university's legal office to review `docs/PRIVACY_POLICY.md`, the rule table (`scripts/build_state_borders.py`: the all-party / mixed sets, IL strict, TX and WA notice), the notice wording (`ConsentNotice`), the voiceprint consent text (`VoiceprintPolicy`), the 12-month retention, and the border buffer.
2. **Task 10 sign-off.** You chose option (a) (temporary comparison, zeroed after use). Get the team's and legal's agreement in writing, since BIPA has a private right of action.
3. **IRB / user study (task 12).** Check whether IRB approval is needed, and prepare a written consent form covering recording and voice analysis.
4. **Participant recordings (task 8.2).** The owner plus 30 or more consenting people, in quiet, noisy and far settings, then run `voice_tuning_report.py` and fill in `SPEAKER_TUNING.md`. Note the existing 0 of 8 owner acceptance.
5. **Phone-only manual checks:** sign your own voice consent (Settings → Listening → *Review voice consent*); allow precise location if you want the smaller buffer; tap *Announce note-taking* once and listen for both lines in Texas or Washington mode (or with *Include the voice-analysis line in every state* on); make or receive a call while listening and check "Call detected: strictest rule"; if you can, drive across a state line while listening; in a real conversation, tap a "Voice N" label to name it, try "Hey <Name>", "meet me at 5" and "sure", then check Review.
6. **Review and merge** branch `privacy-consent-location` (not pushed to `main`; pushing or opening a PR is your call), and update the slides with the wording above.
