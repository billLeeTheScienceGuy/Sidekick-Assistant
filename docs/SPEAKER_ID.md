# Local speaker identification — prototype 0.9.0

## Short corrections in 0.9.3

Live phrases from 1.5 to under 3 seconds now use a full-window embedding rather
than being automatically rejected. They must independently match both enrollment
and the recent long-phrase voice signature with cosine ≥0.80. The recent signature
comes only from a confidently USER-labelled long chunk, expires after 30 seconds,
and is cleared on an intervening unassigned/other chunk or disabled/missing profile.
Short matches do not extend its lifetime. No recent signature, weaker/poor-quality
audio, or less than 1.5 seconds remains UNKNOWN. There is no blind label carryover
or extra raw-audio history. The ephemeral signature is wiped when the session ends.

Three targeted Pixel checks passed in 3.104 seconds. The real-model public fixture
matched a 2.1-second phrase (profile 0.94, recent signature 0.92) after a 15-second
gap. Its classification allowed a short end-time correction to refine one draft.
Expired and interrupted signatures stayed unassigned; existing unknown/other-source
isolation and known-user correction tests passed. This fixture overlaps enrollment
audio and proves integration, not independent real-user accuracy. All 57 core tests
pass; both app variants build and lint passes. Real short-phrase and stranger-voice
checks on the user's microphone remain necessary before making reliability claims.

## Two-source comparison and 0.9.2

Fresh 0.9.2 mixed-source trial inspected afterward: seven likely-user chunks,
eleven unassigned, zero OTHER. Long chunks near the beginning matched the enrolled
profile (half-window scores mostly 0.75–0.87); later long chunks remained UNKNOWN
(roughly 0.45–0.70). This sequence is consistent with the requested own-reading then
video order. One asymmetric user-labelled chunk scored 0.66/0.79, consistency 0.78,
and passed the new balanced rule. Five displayed chunks were below three seconds
and remained unassigned. Exact source-switch timing and per-chunk source labels
were not captured, so do not report a measured false-acceptance/recognition rate.
No thresholds were changed after inspecting this trial. Short-utterance attribution
and its effect on cross-sentence event corrections remain unresolved.

User confirmed the entire first diagnostic trial was their own reading: the UI
reported six likely-user chunks and eleven unassigned. A subsequent video-only
trial reported zero likely-user, zero OTHER and 21 unassigned chunks. This shows
no false user matches in that one video trial, not a general false-acceptance rate.
Short phrases fail the minimum duration gate. Several longer user phrases showed
uneven half-window scores near the cutoff; video half-window scores remained below
the new acceptance rule in the displayed diagnostic sample.

0.9.2 retains the original both-halves ≥0.70 user rule and adds a bounded alternative:
both halves ≥0.65, their mean ≥0.72, and consistency ≥0.65. Short phrases, mixed
windows and non-finite scores remain unassigned. OTHER thresholds are unchanged.
One score-level regression covers representative rounded near-threshold user/video
observations plus inconsistent or severely asymmetric windows. No old audio or
embeddings were retained for exact rescoring. These trials informed the adjustment;
a fresh own-voice/video comparison is needed as a held-out check. Enrollment persists.

## First user trial and 0.9.1 follow-up

The first actual combined speaker/script/video trial produced 18 unassigned chunks,
zero likely-user/other matches, despite a saved profile. No party suggestion appeared.
The transcript preserved the party, end correction and address but misheard the
venue correction. This trial did not establish working real-user speaker matching.

Whisper's dotted `8 p.m.` spelling exposed an independent parser bug. Version 0.9.1
accepts dotted meridiems and preserves sentence/venue boundaries without rewriting
source evidence. A regression verifies tomorrow's start, corrected midnight end,
original venue and explicit address; all 55 core tests pass. It does not guess that
a misheard venue was The Sylvee.

0.9.1 adds a collapsible Voice diagnostics view: last 20 chunks' duration, rejection
reason or profile/half-consistency similarity scores. Only numeric/quality metadata
stays in RAM; no extra speech, embeddings or diagnostic file is saved. Thresholds
are unchanged. A short new voice trial is required because discarded old PCM cannot
be re-scored. Saved voice enrollment remains intact across installation.

Settings → My voice → Set up my voice opens an explicit microphone setup screen.
Speak alone in long natural phrases for about 20 seconds. Three qualifying speech
chunks are embedded; setup automatically stops at three or after 45 seconds. Only
a successful, consistent three-sample enrollment replaces the profile. Cancellation
or failure retains the previous profile. Enrollment is not transcribed. No raw
audio file is created. Embeddings live in app-private no-backup storage; Settings
can disable matching or delete the profile. Speaker matching is not consent or
authentication and does not detect replay attacks.

During normal live listening, Silero gates speech first. The single worker runs
WeSpeaker on two halves of each eligible chunk, then Whisper, and wipes PCM as
before. CPU provider, one speaker thread; the existing bounded queue still stops
visibly on overload. Less than three seconds or poor signal remains unassigned.
Both halves must resemble the enrolled profile by cosine ≥0.70 to label USER;
both ≤0.35 label OTHER. The halves must resemble each other by ≥0.60, otherwise
UNKNOWN. These are conservative prototype thresholds, not calibrated population
accuracy claims. They cannot guarantee separation of overlapping speakers.

Only likely-user chunks can generate personal promises. OTHER and UNKNOWN can
generate informational event suggestions when enabled in Settings. Turning matching
off or not enrolling makes all live speech unassigned, not implicitly user-owned.
No inference writes a saved event automatically. Context is reset on attribution
changes so ambiguous pronouns cannot silently update another speaker's activity.
This may miss a valid correction when a short continuation becomes UNKNOWN.
Typed text remains an explicit user-provided path, separate from live attribution.

The live status and aggregate per-session counts show likely-user/other/unassigned
chunks. They persist only in process memory, not a permanent transcript or audit log.
The existing single consent prompt covers the session; the user must stop for new
participants who have not agreed or withdrawal. Consent and applicable law cannot
be inferred from the voice signature, GPS, or absence of saved audio.

## Pinned dependencies

Run `python scripts/prepare_speaker.py` before building. CI runs the same script.

- Runtime: sherpa-onnx 1.13.3 Android AAR, Apache 2.0.
  https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.3/sherpa-onnx-1.13.3.aar
  SHA-256 `243ad797a3b6e75ebbeaf7a2ab4aec0777e7d71b730685abb762a120940b07b6`
- WeSpeaker VoxCeleb ResNet34-LM, CC BY 4.0, runtime metadata adapted by sherpa-onnx.
  https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/wespeaker_en_voxceleb_resnet34_LM.onnx
  26,530,550 bytes, 16 kHz mono, 256-dimensional embedding.
  SHA-256 `e9848563da86f263117134dfd7ad63c92355b37de492b55e325400c9d9c39012`
  Original source: https://huggingface.co/Wespeaker/wespeaker-voxceleb-resnet34-LM

The original Hugging Face ONNX lacks required runtime metadata and caused a native
initialization crash during the first benchmark test. It is not the shipped artifact.
Preparation and first use validate the compatible export's hash. Model attribution
is in Settings and packaged license notices. No runtime network permission is added.

## Validation on 2026-10-23

- 54 core tests passed, including invalid-vector and uncertain/mixed-window cases.
- Eight physical Pixel checks passed in 3.287 seconds: real model embedding,
  enrollment/reload/disable/delete and failed replacement preservation, source
  attribution isolation, and existing event-update/persistence regressions.
- Disjoint phrases from the public JFK fixture had cosine 0.86559635. A clean
  enrolled fixture matched USER. This does not estimate user-vs-stranger accuracy.
- One combined paced VAD → speaker → Whisper test passed in 16.1 seconds, with
  four transcription chunks, no queue overflow and the expected known speech.
  Pipeline elapsed 15,960 ms, process CPU 14,303 ms, max Whisper call 2,279 ms.
  Only one of four chunks matched USER; the others remained unassigned. Real
  enrollment, background noise and rejection of other speakers require phone testing.
- Both build variants and lint passed with no lint errors; existing warning-level
  dependency/target-SDK/style findings remain. No automated test captured physical
  microphone audio or enrolled the human user's voice.
- Two physical UI checks passed in 7.359 seconds: voice setup is reachable without
  opening the microphone, and existing guidance/review still works after the settings
  changes. Normal 0.9.0 installed on the Pixel; enrollment is intentionally left for
  the user, not performed with a public test voice.

## Combined user test

1. Finish any current live session. Set up your voice alone in Settings.
2. Start a fresh Live notes session and say the party/correction script from
   PLAYBACK_EVALUATION.md yourself, with pauses. Keep phrases natural and long.
3. Play the linked public video through a separate speaker. Those voices should
   be OTHER or UNKNOWN; no video dialogue should become a personal promise.
4. Include silence and screen-off operation. Finish and leave SideKick open for
   inspection of transcript, counts and suggestions. Nothing saves until approved.

Previous temporary playback text is not needed and may be cleared by installation.
Keep synthetic regression scripts and the public audio test fixture in development.
