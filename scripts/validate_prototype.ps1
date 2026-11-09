param(
    [string]$DeviceSerial = 'emulator-5554',
    [ValidateSet('Compact','Full')][string]$Suite = 'Compact',
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if ($DeviceSerial -notmatch '^emulator-\d+$') { throw 'Use an emulator serial; this script never instruments the personal app or a physical phone.' }
    $sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
    $variant = if ($Suite -eq 'Full') { 'Benchmark' } else { 'CompactTest' }
    $variantDir = if ($Suite -eq 'Full') { 'benchmark' } else { 'compactTest' }
    $property = if ($Suite -eq 'Full') { '-PspeechBenchmark' } else { '-PcompactTest' }
    $package = if ($Suite -eq 'Full') { 'com.sidekick.app.benchmark' } else { 'com.sidekick.app.compacttest' }
    if (-not $SkipBuild) {
        & .\gradlew.bat $property ':core:test' ":app:assemble$variant" ":app:assemble${variant}AndroidTest" ":app:lint$variant"
        if ($LASTEXITCODE -ne 0) { throw 'Build or core tests failed.' }
    }
    if ((& $adb -s $DeviceSerial shell getprop sys.boot_completed) -notmatch '1') { throw 'Start and unlock the emulator first.' }
    & $adb -s $DeviceSerial install -r "app/build/outputs/apk/$variantDir/app-$variantDir.apk"
    if ($LASTEXITCODE -ne 0) { throw 'Isolated app installation failed.' }
    & $adb -s $DeviceSerial install -r "app/build/outputs/apk/androidTest/$variantDir/app-$variantDir-androidTest.apk"
    if ($LASTEXITCODE -ne 0) { throw 'Test installation failed.' }
    $classes = @('ActivityTimingTest','MemoryPrivacyTest','PrivacyScreenTest','MessageRiskTest','BundledOfflineTest')
    if ($Suite -eq 'Compact') { $classes += 'CompactSetupTest' }
    else { $classes += @('FoundationTest','PlannerScreenTest','SummaryScreenTest','CalendarDraftTest','AutomaticScreenTest','CalendarTest','AutomaticMemoryTest','EventChangeTest','LocalizedChangesTest','MessageInboxTest','CopiedSpeechTest','ReminderTest','ReminderRecoveryTest','EventReminderJourneyTest','UndoPlannerTest','UncertainUpdateTest','UncertainUpdateScreenTest','VoiceDuplicateTest','LanguageVoiceProfileTest','EmulatorIntegrationTest') }
    $qualified = ($classes | ForEach-Object { "com.sidekick.app.$_" }) -join ','
    $arguments = @('-s',$DeviceSerial,'shell','am','instrument','-w','-e','class',$qualified)
    $arguments += "$package.test/androidx.test.runner.AndroidJUnitRunner"
    $output = & $adb @arguments 2>&1 | Out-String
    $exitCode = $LASTEXITCODE
    New-Item -ItemType Directory -Force -Path '.deps/validation' | Out-Null
    $output | Set-Content -LiteralPath ".deps/validation/$Suite.txt" -Encoding utf8
    Write-Output $output
    if ($exitCode -ne 0 -or $output -notmatch 'OK \(\d+ tests?\)' -or $output -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') { throw 'Instrumentation failed; see .deps/validation.' }
} finally { Pop-Location }
