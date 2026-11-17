#!/bin/sh
# Final phone check in the isolated benchmark package (never the personal app): builds the test copy, installs it and
# runs the device suite plus the speaker tests. Output: .deps/validation/phone-suite.txt
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
export MSYS_NO_PATHCONV=1 JAVA_HOME=${JAVA_HOME_WIN:-C:/Users/Patron/.jdks/jbr-17.0.14}
PKG=com.sidekick.app.benchmark
cd "$ROOT" || exit 1
mkdir -p .deps/validation
./gradlew.bat "-Dorg.gradle.jvmargs=-Xmx6g" -PspeechBenchmark -x verifyOptionalSpeechModels :app:assembleBenchmark :app:assembleBenchmarkAndroidTest > .deps/validation/phone-build.log 2>&1 || { echo BUILD FAILED; grep -E "^e:|What went wrong" -A3 .deps/validation/phone-build.log | head -20; exit 1; }
./gradlew.bat --stop > /dev/null 2>&1
"$ADB" install -r app/build/outputs/apk/benchmark/app-benchmark.apk > /dev/null || exit 1
"$ADB" install -r app/build/outputs/apk/androidTest/benchmark/app-benchmark-androidTest.apk > /dev/null || exit 1
"$ADB" shell run-as $PKG rm -f no_backup/qwen3-0.6b-q4_k_m.gguf
"$ADB" shell pm grant $PKG android.permission.POST_NOTIFICATIONS
CLASSES="ActivityTimingTest MemoryPrivacyTest PrivacyScreenTest PrivacyRegionSettingsTest MessageRiskTest BundledOfflineTest CalendarMirrorTest CalendarTargetScreenTest TrackedVoiceChangesTest ResponsiveInterfaceTest InterfaceCustomizationTest ListeningSettingsTest RecognitionUncertaintyTest FoundationTest PlannerScreenTest SummaryScreenTest AutomaticScreenTest AutomaticMemoryTest EventChangeTest LocalizedChangesTest MessageInboxTest CopiedSpeechTest ReminderTest UndoPlannerTest UncertainUpdateTest UncertainUpdateScreenTest VoiceDuplicateTest LanguageVoiceProfileTest CamplusSpeakerTest NeuralDiarizationTest SpeakerTurnsTest SpeakerIdentityTest SpeakerPaddingTest"
LIST=$(for c in $CLASSES; do [ -f "app/src/androidTest/kotlin/com/sidekick/app/$c.kt" ] && printf "com.sidekick.app.%s," "$c"; done)
"$ADB" shell am instrument -w -e class "${LIST%,}" $PKG.test/androidx.test.runner.AndroidJUnitRunner > .deps/validation/phone-suite.txt 2>&1
grep -E "^OK|Tests run|FAILURES|Time:|INSTRUMENTATION_FAILED|Process crashed" .deps/validation/phone-suite.txt
grep -E "^[0-9]+\) " .deps/validation/phone-suite.txt | head -20
