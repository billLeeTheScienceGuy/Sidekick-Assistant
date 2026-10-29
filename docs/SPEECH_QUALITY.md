# 0.17.0 speaker-model evaluation — 2026-10-27

User chose following the phone timezone, with clear labels. Capture now names the
phone zone; saved activity ranges also name their recorded zone. No timezone setting,
existing timestamp or phone clock is changed.

Evaluated three models directly on Pixel with the same sherpa-onnx runtime: current
WeSpeaker ResNet34-LM, 3D-Speaker CAM++ English VoxCeleb, and CAM++ Chinese/English
advanced. Two synthetic voices have separate natural enrollment/short-plan/end-time
utterances, with an additional public JFK disjoint enrollment/short pair and five
public language clips used as different-speaker comparisons. Original fixture audio
is public/synthetic, not a recording of the user. No physical microphone was opened.

Measured five same-speaker short similarities vs different-speaker maximum:

| Model | Same-speaker range | Different-speaker max | 13 embeddings |
|---|---|---|---|
| WeSpeaker | .776–.885 | .596 | 6.557s |
| CAM++ English | .264–.593 | .782 | 2.030s |
| CAM++ Chinese/English advanced | .651–.852 | .234 | 2.027s |

The English-only candidate is excluded from production. Advanced CAM++ is added as
a selectable, offline 28.3MB candidate. Its cosine scale differs: short enrollment
and recent-reference cutoffs are .60 for this model, with duration/expiry/turn checks
retained. Existing ResNet cutoffs stay .80. Long-turn matching remains conservative.
These small fixture results motivate a real-user trial, not calibrated population
false-accept rates, household-speaker accuracy or a guarantee of improvement.

CAM++ uses 192-dimensional profiles; legacy uses 256. Model hashes/dimensions are
validated, mixed dimensions rejected, and model-specific files preserve the previous
profile on switching. Existing installations retain their old model/profile; fresh
installations choose CAM++. Switching requires one voice setup for the new model.
Deleting voice profiles removes both models' signatures. No raw enrollment audio is
saved and no new network permission is added. Attribution/license is included.

92 core tests pass. Model comparison passed in 12.871s. Three existing speaker checks
passed in the integration run; final CAM++ integration passed in 5.593s, including
a trimmed held-out phrase (profile .61/recent mean .62), eight different-speaker
rejections, profile reload/switch-back, disable/delete and wrong-model rejection.
Initial switch-back test used an invalid zero float tolerance; changing to 1e-6
accounts for normalization/JSON roundoff and the corrected test passes. Both build
variants and lint pass. Real-user CAM++ enrollment/short-phrase testing is required.

Sources: official sherpa model release and 3D-Speaker model card/project:
https://github.com/k2-fsa/sherpa-onnx/releases/tag/speaker-recongition-models
https://modelscope.cn/models/iic/speech_campplus_sv_zh_en_16k-common_advanced
https://github.com/modelscope/3D-Speaker
ModelScope API identifies the advanced weights as Apache License 2.0.
CAM++ SHA256: aa3cfc16963a10586a9393f5035d6d6b57e98d358b347f80c2a30bf4f00ceba2

# 0.16.1 short speaker references — 2026-10-27

The preceding real lecture trial produced one correctly timed 7–9 pm event, but it
remained informational: short profile .83 / recent mean .77 at 24 seconds failed
identity; later short phrases lost their anchor. No personal reminder was scheduled.

Recent voice matching now keeps embeddings from up to two independently accepted
long turns (at most six windows and one mean per turn). A short phrase still needs
profile >= .80 and either the original mean >= .80 or at least two reference windows
>= .80. One favorable window cannot qualify. No additional embedding inference is
needed. No audio or new persistent biometric data is retained. Existing profile stays.

Reference matching expires at 60 seconds rather than 30 to accommodate pauses.
Short results never renew time or add trusted embeddings. Borderline short evidence
with strong enrollment similarity stays UNKNOWN but can retain the trusted references;
weak/conflicting evidence, invalid/quiet input, long unassigned/other speech, expired
or reversed clocks clear support. Sub-1.5-second speech still cannot qualify. Diagnostics
show profile, mean, two-window support and age separately.

90 core tests pass. Three isolated Pixel checks passed in 20.83 seconds. Of five
sampled own-speaker clips at a 39-second gap, three matched; two trimmed below 1.5s
correctly stayed UNKNOWN. Eight different-speaker fixtures (synthetic voices and five
public language clips) produced no USER matches. Existing padded/mixed voice and
expired/interrupted identity checks pass. Both builds/lint pass. These small fixture
checks do not establish real-user accuracy; a fresh personal-plan voice trial is next.

# 0.15.2 speaker boundaries — 2026-10-27

The user's real Pixel 0.15.1 trial passed: live text was "There's a workshop tomorrow
at 8pm. Move it to 9pm. Cancel it." The corresponding voice event was CANCELLED with
a revised 9 pm start. Three chunks were all unassigned. Visible first-chunk diagnostics
were 3.3s captured, window/profile scores .74/.63 and consistency .59. That does not
meet identity thresholds and does not establish that the enrollment is bad.

Speaker-only preprocessing now excludes quiet leading/trailing capture padding using
10 ms DC-removed RMS frames, a bounded adaptive edge threshold, three consecutive
active frames, and 40 ms margins. Internal pauses remain intact; no speech from
separate turns is concatenated. Applies to enrollment embeddings and runtime matching,
not VAD segmentation, ASR buffers, timestamps or stored audio. Temporary selection
copies are wiped. Existing saved enrollment is retained.

Matching uses the remaining speech duration: silence cannot turn a short phrase into
a long qualifying phrase. Profile/consistency acceptance thresholds and short-phrase
anchor requirements are unchanged. No identity inheritance from adjacent speech.
The selected speech duration is visible in diagnostics alongside captured duration.
Short standalone phrases can therefore correctly remain unassigned until a confident
long phrase. This is a boundary/quality improvement, not a claim of verified real-user
identity accuracy.

82 core tests pass, including four edge-selection checks for quiet padding, internal
pauses, silence/DC/clicks/nonfinite input, and short-phrase duration. Three isolated
Pixel device checks passed in 11.144 seconds: padded same-voice recognition, no padded
short-phrase identity borrowing, other/mixed-voice rejection, profile reload/disable/
delete and expired/interrupted anchor behavior. Both builds/lint pass. No microphone
was opened during automated checks; normal enrollment/data were not modified. Real
speaker matching still needs the user's longer-speech trial.

# 0.15.1 short-speech quality — 2026-10-24

Inspected the user's Pixel trial: the voice event had moved to 9 pm, but "Cancel it"
was transcribed as "Cancellate". Copying live text for smart analysis lost source
identity and created another 8 pm event. Copied live notes now retain voice provenance
through editing and both rule/smart analysis, preserving ownership and matching the
original event. Command-only notes skip unnecessary Qwen inference. Conversational
"let's", "it's cancelled", "make it nine p m" variants are parsed without guessing
ambiguous clocks or treating "Cancellate" as a cancellation instruction.

Reproduced tiny's cancellation error with two synthetic Windows voices on Pixel.
Beam-3 decoding and full encoder windows did not repair it; full encoder cost about
5 seconds per clip. Silence padding with tiny did not repair it either. Base decoded
both cancellation voices correctly and retained "Do not cancel it". The adaptive
path produced exact normalized reference text for all eight short fixtures, taking
3.169–3.610 seconds per clip in the final run. Tiny's initial cancellation outputs
were incorrect for both voices. These are synthetic samples, not a general accuracy
or real-user voice benchmark.

Adaptive is the new default when no quality preference was set. It uses bundled
base for isolated English phrases under three seconds and tiny for longer/queued
speech. Existing explicit Fast/Larger choices persist. Other languages retain tiny
in Adaptive because prior larger-model comparisons were mixed. No extra download,
cloud inference, phonetic destructive-command substitution or private audio files.
This trades several seconds of short-phrase latency for measured recognition gains;
all-day battery/thermal behavior is not established.

See [VALIDATION.md](VALIDATION.md) for the final automated checks.

# Speech context and model comparison — 0.12.0

## 0.12.1 duplicate prevention

The user reported the microphone party/midnight trial worked; the visible phone UI
confirmed the expected times and literal venue. Speaker attribution was still
unassigned, and repeated party cards were visible. Successful extraction does not
establish voice-ID accuracy.

Repeated live events across changing voice labels now keep the original item when
the normalized opening and start timestamp match and capture times are within two
minutes. Case, punctuation and Unicode compatibility forms are normalized. This is
strict matching, not fuzzy merging of similar events. Separate dates, different
wording/venues, older mentions, imported messages and ambiguous multiple matches
remain separate. An informational event is never promoted to personal attendance
because its repetition has a different voice label.

Repeated new details produce a separate explicit-confirmation update. Unchanged
source details do not undo manually edited fields. Accepted/dismissed repetitions
are suppressed using the bounded RAM review history; that history is cleared for
a new session and is not a permanent transcript or a search of the saved database.
Existing saved records are not consolidated automatically.

Seven duplicate checks (including all five added languages) and eleven existing
event/update/UI checks passed on the Pixel in 2.487 seconds. ASR and model code were
unchanged, so the earlier expensive model benchmarks were not repeated.

## 0.12.0 implementation and measurements

Live speech uses windows up to ten seconds, one second of overlap and about one
second of silence before flushing. Capture continues during inference; two pending
windows are allowed before a visible overload stop. No audio files are written.

Short phrases under three seconds can prepend up to four seconds of earlier audio
held in RAM, only when both chunks independently match the enrolled user and the
gap is at most fifteen seconds. Overlapping samples are excluded from that prefix.
The bounded prefix is wiped on the next chunk, attribution interruption or worker
exit; during silence it remains in RAM until the next chunk or session exit.
Identity is assessed on the current audio, before adding ASR context. Long turns
use overlapping three-second speaker windows; every adjacent pair must agree.
Mixed or weak matches remain unassigned. This is not full diarization.

An unassigned end/place correction within two minutes of one identified activity
can produce a separate **Possible update**. An unrelated topic, a new event, a
confidently different speaker or expiry interrupts the link. The original stays
unchanged. The editor displays the uncertainty and requires **Confirm & apply
update**. Dismissal suppresses the same suggestion; approval replaces one item.

## Model choice

Settings offers Fast (English tiny.en; multilingual tiny) and Larger model (base,
including English). Fast remains the default. Base is not reliably better on every
language and requires substantially more processing. Finish listening to switch.
All weights and inference remain local and free. Base adds 147,951,465 bytes to
the packaged assets; only one Whisper context is loaded at a time.

Base source is pinned to whisper.cpp model revision
5359861c739e955e79d9a303bcbc70fb988958b1, SHA-256
`60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe`.
Its MIT license is packaged in `licenses/whisper-base.txt`.

## Pixel 7a measurements, 2026-10-23

One public FLEURS validation clip per language, explicitly selected language,
two CPU threads, full encoder context. These are sample observations, not
representative language accuracy. CER normalizes case, Unicode compatibility,
spaces and punctuation; Chinese traditional/simplified differences still count.

| Language | Audio seconds | Tiny ms | Base ms | Tiny CER | Base CER |
|---|---:|---:|---:|---:|---:|
| Korean | 10.56 | 5967 | 13433 | 15.0% | 5.0% |
| Chinese | 18.38 | 5563 | 12668 | 27.3% | 27.3% |
| French | 14.46 | 5734 | 12970 | 10.4% | 6.4% |
| German | 12.96 | 5522 | 12537 | 13.3% | 15.6% |
| Russian | 13.68 | 5783 | 13019 | 4.3% | 9.9% |

Process CPU per clip was 10.8–11.5 seconds for tiny, 24.7–25.8 seconds for base.
Post-call total process PSS was about 288–293 MiB for tiny and 402–407 MiB for
base. These are snapshots, not peak memory or model-only allocations. Model setup
and cold loading can affect timings. The later live test uses a reduced encoder
context, so these full-context latencies are not live-window timings.

Both models transcribed synthetic “It ends at midnight.” correctly alone and with
prior party context. That easy Windows TTS sample does not reproduce the user's
failed microphone recording; no copy of that discarded recording exists.

The paced default-tiny pipeline processed 44 seconds of repeated public JFK speech
into nine chunks, with seven user matches, no queue overflow and maximum combined
speaker/ASR chunk processing of 7,062 ms. Total test time was 55,243 ms including
enrollment/setup, capture pacing, drain and a synthetic different-voice rejection;
process CPU was 72,153 ms. Base's shorter one-clip trial produced three chunks,
one user match, no overflow and a 15,364 ms maximum combined chunk. These tests
do not establish hours of continuous input or representative speaker accuracy.

An isolated Compose screen test also confirmed the warning is visible inside the
editor and the explicit confirmation saves one replacement, removing both temporary
drafts. All 68 core and 18 targeted device checks passed. Both app variants build
and lint successfully. No automated test captures the physical microphone.

Reproduce with `scripts/prepare_language_fixtures.py` and
`scripts/prepare-quality-fixtures.ps1`, then run isolated benchmark instrumentation
`SpeechComparisonTest` with `verifySpeechComparison=true`. Public/synthetic audio
is test-only; tests never open the physical microphone. Real room acoustics,
cross-language identity accuracy, sustained thermals and battery still need trials.
# Korean recognition: 0.19

0.20 follow-up: the real Korean event-change flow passed on 0.19.2. Speaker
attribution did not: one 2.3s phrase lacked a confident recent reference, and a
3.6s phrase had profile scores 0.34/0.43 and window consistency 0.47. This is not
evidence for lowering thresholds. Voice setup now stores separate signatures for
each explicitly selected non-English language; the original English/general file
and model-specific profiles are preserved. Korean enrollment has a Korean reading
prompt. This requires a fresh user enrollment and real validation; it does not
establish Korean speaker-model accuracy or remove the longer-phrase prerequisite.

The real Korean microphone trial on 0.18 produced garbled creation, rescheduling
and cancellation phrases. Earlier multilingual text tests did not validate ASR.
Adaptive mode now selects local SenseVoice Small int8 when Korean is selected;
Auto and other languages retain their existing Whisper paths. Explicit Fast and
Accurate still select Whisper tiny and base. No audio is uploaded or stored.

On the physical Pixel 7a, eight synthetic Korean command clips (two voices,
create/move/cancel/do-not-cancel) matched the reference words after punctuation,
spacing and number normalization. SenseVoice decoded each in 139–218 ms;
Whisper tiny took 1.35–4.10 seconds and base 3.24–3.80 seconds, with command errors.
On a separate 10.56-second public Korean sample, base had lower character error
(5.0%) than SenseVoice (13.3%) or tiny (15.0%). These small samples establish no
general accuracy guarantee. The real user's microphone trial remains required.

SenseVoice model data adds about 239 MB plus tokens. Observed post-decode process
PSS was about 403–428 MB, not a peak or a battery measurement. Weights and tokens
are hash-verified, prepared with `python scripts/prepare_sensevoice.py`; provenance
and license are in `app/src/main/assets/licenses/sensevoice.txt`.

After a quiet pause, the live pipeline no longer prepends the preceding utterance's
audio, and temporary text preserves a newline boundary. Continuous window splits
retain overlap handling. Korean parsing tolerates recognized morpheme spacing and
rejects an unresolved earlier clock rather than attaching a later clock to it.
