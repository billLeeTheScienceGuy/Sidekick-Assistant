#!/bin/sh
# Ideas 1 and 3: CAM++ signatures without / with noise removal (GTCRN 0.5 MB, DPDFNet2 10 MB) for every corpus, plus
# ERes2Net signatures for the babble corpus (model fusion). Results: .deps/speaker-public/eval/<variant>-<corpus>.bin
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
D=$ROOT/.deps; E=$D/speaker-public/eval; S=$D/speaker-public
JAVA=/c/Users/Patron/.jdks/jbr-17.0.14/bin/java
STDLIB=$(cygpath -w "$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib" -name 'kotlin-stdlib-2.1.20.jar' | grep -v javadoc | head -1)")
W=$(cygpath -w "$D/sherpa-win")
CP="$(cygpath -w "$ROOT/core/build/libs/core.jar");$STDLIB;$W\\sherpa-onnx-v1.13.3.jar;$W\\sherpa-onnx-native-lib-win-x64-v1.13.3.jar"
CAM=$(cygpath -w "$ROOT/app/src/main/assets/models/campplus-advanced.onnx")
ERES=$(cygpath -w "$S/models/3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx")
embed() { # name model denoise corpus workers
  out="$E/$1.bin"; [ -s "$out" ] && return
  DENOISE="$3" "$JAVA" -Xmx1g -cp "$CP" "$(cygpath -w "$ROOT/scripts/laptop-eval/SpeakerEmbed.java")" "$(cygpath -w "$S/$4")" "$2" "$(cygpath -w "$out")" "$5" > "$E/$1.log" 2>&1
  echo "$1: $(tail -1 "$E/$1.log")"
}
GT="gtcrn:$(cygpath -w "$D/sherpa-win/denoise/gtcrn_simple.onnx")"
DP="dpdfnet:$(cygpath -w "$D/sherpa-win/denoise/dpdfnet2.onnx")"
( embed campplus-advanced-3 "$CAM" "" corpus3 4; embed eres2net-3 "$ERES" "" corpus3 4 ) &
( for c in corpus3 corpus2 corpus; do embed "gtcrn-$c" "$CAM" "$GT" $c 6; done ) &
( for c in corpus3 corpus2 corpus; do embed "dpdfnet-$c" "$CAM" "$DP" $c 6; done ) &
wait
embed gtcrn-replay "$CAM" "$GT" replay/corpus 8 &
embed dpdfnet-replay "$CAM" "$DP" replay/corpus 8 &
wait
echo DENOISE QUEUE DONE
