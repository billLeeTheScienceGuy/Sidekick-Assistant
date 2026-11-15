# Offline model packs

Version 0.26 separates optional speech weights from the normal APK. English Fast,
VAD and both voice-matching models remain bundled. The model catalog is pinned by
file size and SHA-256; licenses remain in the app and are included in each pack.
The full benchmark variant still bundles all weights for repeatable testing.

| Pack | Approximate file size | Use |
|---|---:|---|
| whisper.skmodels | 226 MB | Whisper multilingual Tiny and Base; larger English, explicit Whisper modes, short French/German/Russian and optional Automatic refinement |
| sensevoice.skmodels | 240 MB | Adaptive Korean/Chinese; optional short Automatic refinement |
| omni.skmodels | 366 MB | Omnilingual v2 (an installed v1 pack must be re-imported). Adaptive Automatic/Arabic and longer French/German/Russian; optional Korean clock retry |
| english.skmodels | 136 MB | Optional, Auto-detect: Parakeet TDT 110M re-reads plain English (most accurate English model benchmarked) |
| russian.skmodels | 229 MB | Optional, Auto-detect: GigaAM v3 re-reads plain Russian (no English words) |
| korean.skmodels | 77 MB | Optional: Korean Zipformer cross-checks unclear Korean times first, and finds names from Settings → Names you often mention in Korean speech (names right 29→41 of 50 on the Pixel, +0.3 s per Korean phrase). Not published: KsponSpeech/AI Hub terms need ETRI/NIA confirmation |

Adaptive French/German/Russian needs Whisper and Omni. Adaptive Automatic and
Arabic need Omni; installing SenseVoice and Whisper also improves short Automatic
commands. Adaptive English needs Whisper. The english and russian packs are optional:
Auto-detect uses them when installed and works the same without them. Korean/Chinese needs SenseVoice;
Whisper/Omni provide optional Korean clock retries. English Fast works immediately after a fresh install. Existing
quality choices and installed weights are retained on upgrade; a fresh install
starts with English Fast. Choosing a mode with missing weights disables Start
until the needed packs are installed. Text, Planner and Summary remain available.

## Build without optional weights

Normal debug/release and compactTest builds need only
`python scripts/prepare_speech.py --compact`, `prepare_speaker.py` and
`prepare_diarization.py`. The Gradle benchmark variant separately verifies all
optional weights. CI builds the compact app and cancels superseded runs on the
same branch. Full pack creation and benchmark builds still use the commands below.

## Create and install

Run the existing prepare_speech.py, prepare_speaker.py, prepare_diarization.py, prepare_sensevoice.py and
prepare_omnilingual.py scripts (and prepare_accuracy_packs.py for the optional English/Russian packs), then:

```
python scripts/package_model_packs.py
```

Verified archives appear in `.deps/model-packs/`. `scripts/publish_model_packs.ps1` uploads them to the
`speech-packs` GitHub release, which **Get speech files** opens in the phone's browser (SideKick itself
has no network permission). Or copy the desired files to the
phone, open **Get started → Install offline model pack** (also in Settings), and
choose each file. After installation the source archive can be removed from the
phone; the verified weights are stored privately, excluded from Android backup.
No model download or cloud inference happens automatically inside SideKick.

Imports stream through bounded buffers, reject unknown paths/files, duplicates,
oversized files, wrong hashes and incomplete packs, and commit only after all
weights verify. Cancellation cleans staging files; the next import removes
abandoned staging from a process interruption. A backup directory supports
recovery if replacement is interrupted. Imports serialize against speech decode.
Finishing listening before import is required by the UI.

Installing all packs still uses substantial storage. A smaller APK does not imply
lower inference memory, better accuracy or lower battery consumption. Speaker
profiles and the separate optional Qwen intent model are not part of these packs.

## Verification

Core tests cover catalog validation, traversal/oversize/incomplete imports,
checksum failures, preservation of an installed pack, cancellation and recovery.
The isolated `.compacttest` app verified a real Whisper pack import and actual
native Base inference with no Base weights bundled. `.benchmark` remains isolated
from the normal app. Never run connectedDebugAndroidTest against personal data.
