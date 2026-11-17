#!/bin/sh
# Runs the remaining voice-matching evaluations without supervision and writes one report:
#   .deps/speaker-public/eval/OVERNIGHT_REPORT.txt
# 1. waits for the LibriSpeech dev/train downloads, unpacks them, builds corpus4 (about 324 more people)
# 2. CAM++ signatures with chunk quality for every corpus (skips files that already exist)
# 3. quality-aware bars, learning across sessions, all ideas together, on every corpus
# 4. VoxCeleb1 too, if zip files are in .deps/speaker-public/data/voxceleb/
# Start detached: powershell Start-Process with this script (see the hand-over note).
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
D=$ROOT/.deps; S=$D/speaker-public; E=$S/eval; R=$E/OVERNIGHT_REPORT.txt
PY="$D/python312/python.exe"; JAVA=/c/Users/Patron/.jdks/jbr-17.0.14/bin/java
w() { cygpath -w "$1"; }
log() { echo "[$(date '+%H:%M')] $*" >> "$R"; }
STDLIB=$(w "$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib" -name 'kotlin-stdlib-2.1.20.jar' | grep -v javadoc | head -1)")
CP="$(w "$ROOT/core/build/libs/core.jar");$STDLIB;$(w "$D/sherpa-win")\\sherpa-onnx-v1.13.3.jar;$(w "$D/sherpa-win")\\sherpa-onnx-native-lib-win-x64-v1.13.3.jar"
CAM=$(w "$ROOT/app/src/main/assets/models/campplus-advanced.onnx")
embed() { [ -s "$E/$1.bin" ] || "$JAVA" -Xmx1500m -cp "$CP" "$(w "$ROOT/scripts/laptop-eval/SpeakerEmbed.java")" "$(w "$S/$2")" "$CAM" "$(w "$E/$1.bin")" 14 > "$E/$1.log" 2>&1; log "embedded $1: $(tail -1 "$E/$1.log")"; }
log "overnight voice queue started"

# 1. downloads (resumes partial files)
cd "$S/data" || exit 1
for f in ${LIBRI_SETS:-dev-clean dev-other}; do
  [ -d "LibriSpeech/$f" ] && continue
  for attempt in 1 2 3 4 5; do curl -sSL -C - -o $f.tar.gz https://www.openslr.org/resources/12/$f.tar.gz && break; sleep 60; done
  tar xzf $f.tar.gz && log "unpacked $f" || log "FAILED to unpack $f"
done
cd "$ROOT"
[ -f "$S/corpus4/manifest.tsv" ] || "$PY" -I scripts/laptop-eval/speaker_corpus_more.py "$(w "$D")" "$(w "$S/corpus4")" >> "$R" 2>&1

# 2. signatures with chunk quality
embed campq-corpus corpus; embed campq-corpus2 corpus2; embed campq-corpus3 corpus3; embed campq-replay2-corpus replay2/corpus
embed campq-corpus4 corpus4
VOX=""
if ls "$S/data/voxceleb/"*.zip >/dev/null 2>&1; then
  [ -f "$S/corpus5/manifest.tsv" ] || "$PY" -I scripts/laptop-eval/speaker_corpus_voxceleb.py "$(w "$D")" "$(w "$S/corpus5")" >> "$R" 2>&1
  embed campq-corpus5 corpus5 && VOX="$(w "$S/corpus5") $(w "$E/campq-corpus5.bin")"
fi
ALL="$(w "$S/corpus") $(w "$E/campq-corpus.bin") $(w "$S/corpus2") $(w "$E/campq-corpus2.bin") $(w "$S/corpus3") $(w "$E/campq-corpus3.bin") $(w "$S/corpus4") $(w "$E/campq-corpus4.bin") $VOX"

# 3. analyses
log "== closest strangers with all voices (sets the safe bars)"
"$PY" -I scripts/laptop-eval/speaker_analysis.py "$(w "$E/all-voices.json")" $ALL >> "$R" 2>&1
log "== quality-aware bars (global .64)"
"$PY" -I scripts/laptop-eval/speaker_quality.py 0.64 $ALL >> "$R" 2>&1
log "== learning across sessions"
"$PY" -I scripts/laptop-eval/speaker_crosssession.py 0.64 $ALL >> "$R" 2>&1
log "== all ideas together (bars .64/.71/.74)"
"$PY" -I scripts/laptop-eval/speaker_ideas.py 0.64 0.71 0.74 --a $ALL >> "$R" 2>&1
log "== phone-microphone replay with setup on the phone"
"$PY" -I scripts/laptop-eval/replay_enroll_analysis.py "$(w "$S/replay2")" "$(w "$E/campq-replay2-corpus.bin")" >> "$R" 2>&1
log "overnight voice queue DONE"
