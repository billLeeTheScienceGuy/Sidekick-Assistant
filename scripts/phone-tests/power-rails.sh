#!/bin/sh
# Real energy (Pixel power meters, via Perfetto; no settings changed) while the test build listens with the screen
# off, for each mode: legacy (old loop), new, lowprio (new + detector at background priority).
# Usage: sh scripts/phone-tests/power-rails.sh [minutes]   Traces go to .deps/power/power-<mode>.pftrace
export MSYS_NO_PATHCONV=1
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
P=com.sidekick.app.benchmark
MIN=${1:-5}
mkdir -p .deps/power
for m in legacy new lowprio; do
  printf 'buffers { size_kb: 65536 }\ndata_sources { config { name: "android.power" android_power_config { battery_poll_ms: 1000 collect_power_rails: true } } }\nduration_ms: %d\n' $(( (MIN * 60 + 60) * 1000 )) \
    | "$ADB" shell perfetto --txt -c - -o /data/misc/perfetto-traces/power-$m.pftrace --background >/dev/null
  "$ADB" shell am instrument -w -e batteryTest true -e mode $m -e minutes $MIN -e class com.sidekick.app.BatteryListeningTest#idleCpu $P.test/androidx.test.runner.AndroidJUnitRunner | grep -E "^OK|FAIL"
  sleep 40
  "$ADB" pull /data/misc/perfetto-traces/power-$m.pftrace .deps/power/ >/dev/null
  "$ADB" shell run-as $P cat files/evaluation/cpu-$m.txt | head -1
done
