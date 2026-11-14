param([string[]]$Models = @('lid', 'sense2024', 'parakeet110', 'fc20k', 'parakeet3', 'canary', 'sense2025', 'funnano', 'zipko', 'gigaam3', 'firered2',
    'moonar', 'omni2', 'omni1', 'nemotron', 'qwen3'), [string]$Set = 'timing', [string]$OutDir = '.deps\phone-bench')
# Times each candidate model on the Pixel (PhoneModelBenchTest), one instrumentation per model so a crash
# (e.g. out of memory) loses only that model. Pulls bench-<model>.json into $OutDir.
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$root = Resolve-Path "$PSScriptRoot\..\.."; Set-Location $root
New-Item -ItemType Directory -Force $OutDir | Out-Null
foreach ($m in $Models) {
    if (Test-Path "$OutDir\bench-$m.json") { "$m already timed"; continue }
    $start = Get-Date
    $log = & $adb shell am instrument -w -e class com.sidekick.app.PhoneModelBenchTest -e phoneBench true -e set $Set -e models $m com.sidekick.app.test/androidx.test.runner.AndroidJUnitRunner 2>&1
    & $adb shell run-as com.sidekick.app cat "files/evaluation/$Set/bench-$m.json" > "$OutDir\bench-$m.json"
    $ok = ($log -join ' ') -match 'OK \('
    "{0}: {1} in {2:N1} min" -f $m, $(if ($ok) { 'ok' } else { 'FAILED ' + (($log | Select-String 'Error|Exception|crash' | Select-Object -First 1) -join '') }), ((Get-Date) - $start).TotalMinutes
    if (-not $ok) { Remove-Item "$OutDir\bench-$m.json" -ErrorAction SilentlyContinue }
}
