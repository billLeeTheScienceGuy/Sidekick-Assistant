# One-time local intent model

Optional model: Qwen3-0.6B, Q4_K_M GGUF, Apache-2.0. Its separate 484,220,000-byte
file is not bundled with the app and remains installed across APK updates. No phone
network permission, account or inference subscription is required.

## Install

1. Run `scripts/prepare-intent.ps1` on the development computer. It downloads and
   verifies the exact model below into the ignored `.deps` directory.
2. Copy that GGUF to the phone, tap **Import local intent model (484 MB)** under
   **Edit or type notes**, and select it. Import verifies its size and SHA-256 before
   atomically installing it in app-private, no-backup storage. Wrong files are rejected.
3. After stopping listening, copy recent text into the editor (or type your own note),
   confirm that the words are yours, and tap **Find commitments**. Review each proposal.

For a USB-connected debuggable build, the developer can use:

```powershell
python scripts/install-intent-dev.py --adb <path-to-adb.exe>
```

This transfers to an app-private temporary file, verifies the device copy, then installs
it. Restart the app to detect it. Ordinary users use the Android file picker instead.
Uninstalling the app removes the model; deleting saved commitments does not remove it.
An invalid/cancelled import leaves a previous model installation intact.

## Runtime behavior

Smart analysis is requested after capture stops; live detection still uses the lighter
rules path to protect transcription throughput. The model accepts up to 4,000 characters
and 1,600 prompt tokens, whichever is reached first. Native context is capped at 2,048
tokens, output at 384 tokens, with two CPU threads and a 90-second app timeout.
Qwen's non-thinking prefix and a JSON grammar constrain generation. Context/model
memory is released after each request, and native cancellation is cooperative.

The app validates ownership/consent, exact source evidence, source-backed action words
and source-backed time phrases. Quoted, conditional and negated evidence is rejected
conservatively. Validation can reject legitimate paraphrases; this is intentionally an
initial conservative extractor. The model's dates never authorize scheduling. Ambiguous
wording and conflicts still require review; correction/cancellation handling remains
limited and needs a representative evaluation set.

Version 0.4.1 broadens extraction to definite personal plans, attendance, visits and
travel. It permits checked verb inflections (going/go, visiting/visit) and attendance
wording grounded in an explicit event, while retaining name/place/time checks. Explicit
future-plan rules supplement model misses and are labeled as rule-derived proposals.
Facts such as "Louie's birthday is tomorrow" do not establish the user's attendance.

No transcript or personal model response is logged by production code or sent off-device.
Only approved commitments persist. Without a model, typed analysis explicitly uses
basic rules. An analysis failure is shown instead of silently inventing suggestions.

## Provenance

- [Model quantization](https://huggingface.co/QuantFactory/Qwen3-0.6B-GGUF/tree/e7e05d713acaa2baccdfb52e967eaba8ba562ba8):
  `Qwen3-0.6B.Q4_K_M.gguf`.
- Model SHA-256: `7af3fdf842f87b24672f8a7f1dd50404043f0bfb71093ff91c31d2b49df4631d`.
- [Original model](https://huggingface.co/Qwen/Qwen3-0.6B), Apache-2.0 notice included
  in `app/src/main/assets/licenses/qwen3.txt`.
- llama.cpp b6000 commit `4762ad7316dcdec20016ab5985fb46a27902204d`, source ZIP
  SHA-256 `4832501f61c82c88acce0222b19c754b3d58b69e0c082d511f6e201222a46083`.
  MIT notice included. Built as a separate native library with hidden static dependency
  symbols so its GGML version does not collide with whisper.cpp's version.

The model file is outside the APK; preparing it is optional for builds and CI. Real
model instrumentation tests use the separate benchmark package and skip without a model.

## Initial physical-device validation

On Pixel 7a / Android 17, the real model produced a validated proposal for
"I'll water the plants tomorrow." The cold request took 9.101 seconds, including first
checksum verification and loading. This one observation is not a latency distribution
or an accuracy benchmark. Post-request process PSS was 119,623 KiB; peak RAM was not
measured. Model/context memory is released before that PSS observation.

The extraction/validation path rejected quoted and conditional fixtures, native
cancellation completed, the next request succeeded, and an invalid replacement file
was rejected without losing the installed model. The same process then passed the
real VAD/Whisper replay and approval-only persistence tests (three instrumentation
tests total, 52.295 seconds). Real speech quality, corrections and varied phrasing still
need a larger evaluation set and user testing. An initial missing C++ runtime packaging
issue was fixed before the successful run.
