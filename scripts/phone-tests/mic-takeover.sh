#!/bin/sh
# Another app takes the microphone for 12 s while the owner's SideKick listens; shows SideKick's notification and
# Android's microphone log every 2 s, then removes the test copy. The owner's app is only observed, never instrumented.
trap 'sh "$(dirname "$0")/remove-test-copies.sh"' EXIT  # never leave a test copy on the phone
export MSYS_NO_PATHCONV=1
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
"$ADB" install -r app/build/outputs/apk/benchmark/app-benchmark.apk >/dev/null
"$ADB" install -r app/build/outputs/apk/androidTest/benchmark/app-benchmark-androidTest.apk >/dev/null
state() {
  t=$("$ADB" shell dumpsys notification --noredact | grep -A40 "pkg=com.sidekick.app user=UserHandle{0} id=1 tag=null" | grep -m1 "android.title" | sed 's/.*(\(.*\))/\1/')
  m=$("$ADB" shell dumpsys audio | grep -A4 "RecordActivityMonitor" | grep "pack:com.sidekick.app " | grep -o "silenced:[a-z]*")
  echo "$(date +%H:%M:%S) notification: $t | your mic: ${m:-not recording}"
}
state
"$ADB" shell am instrument -w -e micTakeover true -e seconds 12 -e class com.sidekick.app.MicTakeoverTest com.sidekick.app.benchmark.test/androidx.test.runner.AndroidJUnitRunner > /dev/null &
for i in 1 2 3 4 5 6 7 8 9 10 11 12; do sleep 2; state; done
wait
"$ADB" shell dumpsys activity services com.sidekick.app | grep -q "ListeningService" && echo "your SideKick: still running" || echo "your SideKick: STOPPED"
