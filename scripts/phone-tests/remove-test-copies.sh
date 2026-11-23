#!/bin/sh
# Removes every SideKick test copy from the phone (the owner's com.sidekick.app is never touched). Called at the end of
# every phone-test script, so no copy with the same name and icon is left to take the microphone.
export MSYS_NO_PATHCONV=1
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
for p in com.sidekick.app.benchmark com.sidekick.app.benchmark.test com.sidekick.app.compacttest com.sidekick.app.compacttest.test com.sidekick.app.test; do
  "$ADB" shell pm list packages "$p" | grep -qx "package:$p" && "$ADB" shell pm uninstall "$p" >/dev/null && echo "removed $p"
done
"$ADB" shell pm list packages com.sidekick | grep -v "package:com.sidekick.app$" && echo "WARNING: a test copy is still installed" || true
