#!/bin/sh
# Queue: wait for each model's main embeddings, embed the extra corpus, then analyse raw and centered.
# Results: .deps/speaker-public/eval/<model>[--center].json and summary.txt
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
D=$ROOT/.deps; E=$D/speaker-public/eval
JAVA=/c/Users/Patron/.jdks/jbr-17.0.14/bin/java
STDLIB=$(cygpath -w "$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib" -name 'kotlin-stdlib-2.1.20.jar' | grep -v javadoc | head -1)")
W=$(cygpath -w "$D/sherpa-win")
CP="$(cygpath -w "$ROOT/core/build/libs/core.jar");$STDLIB;$W\\sherpa-onnx-v1.13.3.jar;$W\\sherpa-onnx-native-lib-win-x64-v1.13.3.jar"
model_path() { case $1 in voxceleb_resnet34_LM|campplus-advanced) echo "$ROOT/app/src/main/assets/models/$1.onnx";; *) echo "$D/speaker-public/models/$1.onnx";; esac; }
for m in voxceleb_resnet34_LM nemo_en_titanet_small 3dspeaker_speech_eres2net_sv_en_voxceleb_16k 3dspeaker_speech_eres2netv2_sv_zh-cn_16k-common campplus-advanced; do
  until [ -s "$E/$m.bin" ]; do sleep 30; done
  [ -s "$E/$m-2.bin" ] || "$JAVA" -Xmx1g -cp "$CP" "$(cygpath -w "$ROOT/scripts/laptop-eval/SpeakerEmbed.java")" "$(cygpath -w "$D/speaker-public/corpus2")" "$(cygpath -w "$(model_path $m)")" "$(cygpath -w "$E/$m-2.bin")" 8 > "$E/$m-2.log" 2>&1
  for c in "" "--center"; do
    "$D/python312/python.exe" -I "$(cygpath -w "$ROOT/scripts/laptop-eval/speaker_analysis.py")" "$(cygpath -w "$E/$m$c.json")" $c --cohort-out "$(cygpath -w "$E/cohort-$m$c.bin")" \
      "$(cygpath -w "$D/speaker-public/corpus")" "$(cygpath -w "$E/$m.bin")" "$(cygpath -w "$D/speaker-public/corpus2")" "$(cygpath -w "$E/$m-2.bin")" > "$E/$m$c.txt" 2>&1
    echo "== $m $c" >> "$E/summary.txt"; cat "$E/$m$c.txt" >> "$E/summary.txt"
  done
done
echo QUEUE DONE >> "$E/summary.txt"
