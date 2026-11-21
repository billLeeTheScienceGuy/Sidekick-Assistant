#!/bin/sh
# Full-day battery and heat test for SideKick listening (Pixel over USB, USB debugging on).
#
#   1. Charge the phone to 100 %, keep it plugged in, then run:    sh scripts/phone-tests/battery-day.sh start
#   2. Unplug, open SideKick and tap Start listening. Use the phone normally for 8 hours or more.
#      Do not plug it in during the test: charging resets the numbers.
#   3. Plug it back in and run right away:                          sh scripts/phone-tests/battery-day.sh finish
#
# Reads only Android's battery and temperature counters (no words, audio or location) and writes
# .deps/validation/battery-day.txt. Which app is measured: com.sidekick.app (your installed app).
export MSYS_NO_PATHCONV=1
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
PKG="${PKG:-com.sidekick.app}"
OUT=.deps/validation/battery-day.txt
mkdir -p .deps/validation
case "$1" in
start)
  "$ADB" shell dumpsys batterystats --reset >/dev/null
  "$ADB" shell dumpsys battery | grep -E "level|temperature" > .deps/validation/battery-day-start.txt
  date '+%Y-%m-%d %H:%M' >> .deps/validation/battery-day-start.txt
  echo "Battery statistics reset. Unplug now, start listening in SideKick, and run 'finish' after the day."
  cat .deps/validation/battery-day-start.txt ;;
finish)
  {
    echo "== start"; cat .deps/validation/battery-day-start.txt
    echo "== end"; "$ADB" shell dumpsys battery | grep -E "level|temperature"; date '+%Y-%m-%d %H:%M'
    echo "== estimated use by app (mAh) and screen-off time"
    "$ADB" shell dumpsys batterystats --charged | grep -E "Estimated power use|Capacity:|Computed drain|Uid u0a|Screen off|Total run time|Time on battery" | head -40
    echo "== SideKick's own entry"
    UID_=$("$ADB" shell dumpsys package "$PKG" | grep -m1 -o "userId=[0-9]*" | cut -d= -f2)
    "$ADB" shell dumpsys batterystats --charged "$PKG" | grep -E "Wake lock|Foreground service|Audio|Cpu|mAh|Total cpu" | head -40
    echo "uid=$UID_"
    echo "== temperature (tenths of a degree C) over the day"
    "$ADB" shell dumpsys batterystats --history | grep -o "temp=[0-9]*" | sort -t= -k2 -n | sed -n '1p;$p'
  } > "$OUT" 2>&1
  echo "Written to $OUT"; head -20 "$OUT" ;;
*) echo "Usage: sh scripts/phone-tests/battery-day.sh start|finish" ;;
esac
