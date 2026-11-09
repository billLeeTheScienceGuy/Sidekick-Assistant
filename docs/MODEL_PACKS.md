# Offline model packs

Version 0.26 separates optional speech weights from the normal APK. English Fast,
VAD and both voice-matching models remain bundled. The model catalog is pinned by
file size and SHA-256; licenses remain in the app and are included in each pack.
The full benchmark variant still bundles all weights for repeatable testing.

| Pack | Approximate file size | Use |
|---|---:|---|
| whisper.skmodels | 226 MB | Whisper multilingual Tiny and Base; larger English, explicit Whisper modes, short French/German/Russian and optional Automatic refinement |
| sensevoice.skmodels | 240 MB | Adaptive Korean/Chinese; optional short Automatic refinement |
| omni.skmodels | 365 MB | Adaptive Automatic/Arabic and longer French/German/Russian; optional Korean clock retry |

Adaptive French/German/Russian needs Whisper and Omni. Adaptive Automatic and
Arabic need Omni; installing SenseVoice and Whisper also improves short Automatic
commands. Adaptive English needs Whisper. Korean/Chinese needs SenseVoice;
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
prepare_omnilingual.py scripts, then:

```
python scripts/package_model_packs.py
```

Verified archives appear in `.deps/model-packs/`. Copy the desired files to the
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
