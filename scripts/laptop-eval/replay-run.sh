#!/bin/sh
# Replay test: the laptop plays public voice clips out loud while the phone records them through its microphone
# (ReplayCaptureTest, benchmark package). The recording is pulled, deleted from the phone, cut into clips and embedded.
# Usage: scripts/laptop-eval/replay-run.sh <model.onnx> [<model.onnx> ...]
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
D=$ROOT/.deps; R=$D/speaker-public/replay
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
export MSYS_NO_PATHCONV=1
PKG=com.sidekick.app.benchmark
SECONDS_TOTAL=$(( $("$D/python312/python.exe" -c "import sys;sys.path.insert(0,r'$(cygpath -w "$D/audio-tools")');import soundfile as sf;print(int(sf.info(r'$(cygpath -w "$R/playlist.wav")').duration))") + 20 ))
"$ADB" shell am instrument -w -e class com.sidekick.app.ReplayCaptureTest -e replayCapture true -e seconds $SECONDS_TOTAL $PKG.test/androidx.test.runner.AndroidJUnitRunner > "$R/capture.log" 2>&1 &
CAPTURE=$!
sleep 10
powershell.exe -NoProfile -Command "(New-Object Media.SoundPlayer '$(cygpath -w "$R/playlist.wav")').PlaySync()"
wait $CAPTURE
cat "$R/capture.log" | tail -3
"$ADB" exec-out run-as $PKG cat cache/replay.wav > "$R/replay.wav"
"$ADB" shell run-as $PKG rm -f cache/replay.wav
"$D/python312/python.exe" -I "$(cygpath -w "$ROOT/scripts/laptop-eval/replay.py")" cut "$(cygpath -w "$R")" "$(cygpath -w "$R/replay.wav")"
STDLIB=$(cygpath -w "$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib" -name 'kotlin-stdlib-2.1.20.jar' | grep -v javadoc | head -1)")
W=$(cygpath -w "$D/sherpa-win")
CP="$(cygpath -w "$ROOT/core/build/libs/core.jar");$STDLIB;$W\\sherpa-onnx-v1.13.3.jar;$W\\sherpa-onnx-native-lib-win-x64-v1.13.3.jar"
for model in "$@"; do
  b=$(basename "$model" .onnx)
  /c/Users/Patron/.jdks/jbr-17.0.14/bin/java -Xmx1g -cp "$CP" "$(cygpath -w "$ROOT/scripts/laptop-eval/SpeakerEmbed.java")" "$(cygpath -w "$R/corpus")" "$(cygpath -w "$model")" "$(cygpath -w "$R/$b.bin")" 12 > "$R/$b.log" 2>&1
  echo "$b: $(tail -1 "$R/$b.log")"
done
