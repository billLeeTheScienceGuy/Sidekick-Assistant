#!/bin/sh
# Processor time SideKick (test build) uses while listening with the screen off, over MINUTES minutes.
# Usage: sh scripts/phone-tests/idle-cpu.sh <label> [minutes]   (phone on USB; reads only /proc counters)
export MSYS_NO_PATHCONV=1
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
PKG=com.sidekick.app.benchmark
MIN=${2:-5}
"$ADB" shell input keyevent KEYCODE_WAKEUP
"$ADB" shell am start -n $PKG/com.sidekick.app.MainActivity >/dev/null; sleep 4
"$ADB" shell am start-foreground-service -n $PKG/com.sidekick.app.audio.ListeningService >/dev/null; sleep 25
PID=$("$ADB" shell pidof $PKG | tr -d '\r')
ticks() { "$ADB" shell cat /proc/$PID/stat | awk '{print $14+$15}'; }
"$ADB" shell input keyevent KEYCODE_SLEEP; sleep 5
A=$(ticks); T0=$(date +%s)
sleep $((MIN * 60))
B=$(ticks); T1=$(date +%s)
"$ADB" shell input keyevent KEYCODE_WAKEUP
"$ADB" shell am start-foreground-service -n $PKG/com.sidekick.app.audio.ListeningService -a com.sidekick.FINISH >/dev/null
STATE=$("$ADB" shell dumpsys activity services $PKG | grep -c ListeningService)
echo "$1: $(( (B - A) )) CPU ticks (1/100 s) in $((T1 - T0)) s = $(awk "BEGIN{printf \"%.2f\", ($B-$A)/100/($T1-$T0)*60}") CPU-seconds per minute (service entries: $STATE)"
