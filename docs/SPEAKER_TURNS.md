# Offline speaker turns

Version 0.28 adds real neural speaker-turn segmentation before transcription.
Settings → Separate speaker turns controls it; enabled by default. The 1.54 MB
int8 pyannote segmentation model runs through the existing sherpa-onnx runtime
on the CPU. Segmentation and embedding use one native thread. No paid API,
account, inference network connection or recording archive is required.

The live pipeline partitions each bounded ten-second audio chunk into at most
four complete regions. Short regions, conflicting/overlapping labels and
unexplained gaps remain unassigned. Every sample is retained for transcription;
this does not recover each person's separate audio during simultaneous speech.
Unusable segmentation falls back to an unassigned whole chunk.

Anonymous Voice 1/2 labels use a bounded bank of eight RAM-only centroids with
15-minute expiry. Session UUIDs prevent labels from crossing listening sessions.
They are not names, attendance evidence, ownership or consent. Only independent
matching against an explicitly enrolled profile establishes USER. Ambiguous
regions cannot borrow that identity. Profile matching expands continuous windows
around pauses to collect enough speech, preserving the thresholds and audio order.

Each anonymous voice has a small temporary conversation context and its own
correction anchor. Another speaker's “cancel it” cannot cancel that person's event.
Distinct confident other voices' otherwise identical party announcements are not
collapsed together. Unassigned commands do not mutate saved events. The optional
Show speaker turns view contains only the last twelve temporary excerpts; clearing
recent conversation clears them and the per-voice context. Saved event source IDs
contain a session key, not an embedding or named person, and are excluded from export.

## Preparation and reproducibility

Run `python scripts/prepare_diarization.py` after the existing speech/speaker
preparation scripts. It verifies the archive and model SHA-256 and extracts only
known members. The model is not checked into Git. Its MIT attribution is bundled
in `assets/licenses/diarization-MIT.txt`.

- [sherpa-onnx diarization documentation](https://k2-fsa.github.io/sherpa/onnx/speaker-diarization/models.html)
- [Pinned 1.13.3 API](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.3/sherpa-onnx/kotlin-api/OfflineSpeakerDiarization.kt)
- [Public segmentation model release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/speaker-segmentation-models)

The controlled Pixel trial is in SUSTAINED_TURNS.md. Recorded owner/second-voice
and equal-weight-overlap fixtures pass. This is not a diarization error-rate study,
an independently calibrated identity evaluation or proof of multilingual natural
conversation accuracy. Anonymous clustering and short turns can still be wrong.
