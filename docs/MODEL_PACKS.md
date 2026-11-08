# Offline model packs

Version 0.26 separates optional speech weights from the normal APK. English Fast,
VAD and both voice-matching models remain bundled. The model catalog is pinned by
file size and SHA-256; licenses remain in the app and are included in each pack.
The full benchmark variant still bundles all weights for repeatable testing.

| Pack | Approximate file size | Use |
|---|---:|---|
| whisper.skmodels | 226 MB | Whisper multilingual Tiny and Base; larger English, Auto, explicit Whisper modes, French/German/Russian paths |
| sensevoice.skmodels | 240 MB | Adaptive Korean/Chinese |
| omni.skmodels | 365 MB | Adaptive Arabic and longer French/German; optional Korean clock retry |

Adaptive French/German needs Whisper and Omni. Adaptive Russian and English need
Whisper. Korean/Chinese needs SenseVoice; Whisper/Omni provide optional Korean
clock retries. English Fast works immediately after a fresh install. Existing
quality choices and installed weights are retained on upgrade; a fresh install
starts with English Fast. Choosing a mode with missing weights disables Start
until the needed packs are installed. Text, Planner and Summary remain available.

## Create and install

Run the existing prepare_speech.py, prepare_speaker.py, prepare_sensevoice.py and
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
