param(
    [ValidateRange(1, 100)][int]$Rounds = 3,
    [ValidateRange(1, 4)][int]$Threads = 2,
    [ValidateRange(0, 1500)][int]$AudioContext = 0,
    [ValidateSet('3,5,10', '3,5', '3', '5', '10')][string]$Sizes = '3,5,10',
    [switch]$RetainModel,
    [switch]$Paced,
    [string]$Serial
)
$ErrorActionPreference = 'Stop'
$sdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adbPath = Join-Path $sdkPath 'platform-tools\adb.exe'
$deviceArgs = if ($Serial) { @('-s', $Serial) } else { @() }
$repoPath = Split-Path $PSScriptRoot -Parent
$runId = [guid]::NewGuid().ToString('N')
$retainValue = $RetainModel.IsPresent.ToString().ToLowerInvariant()
$pacedValue = $Paced.IsPresent.ToString().ToLowerInvariant()
if ($AudioContext -gt 0 -and (($Sizes.Split(',') | ForEach-Object { [int]$_ } | Measure-Object -Maximum).Maximum * 50) -gt $AudioContext) {
    throw 'AudioContext must cover the longest audio chunk (50 context positions per second).'
}
Push-Location $repoPath
try {
    & .\gradlew.bat -PspeechBenchmark :app:assembleBenchmark :app:assembleBenchmarkAndroidTest
    if ($LASTEXITCODE -ne 0) { throw 'Benchmark build failed.' }
    foreach ($apk in @('app/build/outputs/apk/benchmark/app-benchmark.apk', 'app/build/outputs/apk/androidTest/benchmark/app-benchmark-androidTest.apk')) {
        & $adbPath @deviceArgs install -r $apk
        if ($LASTEXITCODE -ne 0) { throw 'Benchmark installation failed.' }
    }
    $result = & $adbPath @deviceArgs shell am instrument -w -e class com.sidekick.app.SpeechBenchmarkTest -e runId $runId -e rounds $Rounds -e threads $Threads -e audioContext $AudioContext -e sizes $Sizes -e retain $retainValue -e paced $pacedValue com.sidekick.app.benchmark.test/androidx.test.runner.AndroidJUnitRunner
    $result | Write-Output
    if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'OK \(1 test\)') { throw 'Benchmark did not complete successfully.' }
    $rows = & $adbPath @deviceArgs logcat -d -s 'SidekickBenchmark:I' '*:S' | ForEach-Object {
        $index = $_.IndexOf('{')
        if ($index -ge 0) {
            $json = $_.Substring($index)
            if (($json | ConvertFrom-Json).runId -eq $runId) { $json }
        }
    }
    $expected = $Rounds * $Sizes.Split(',').Count
    if (@($rows).Count -ne $expected) { throw "Expected $expected measurements; found $(@($rows).Count)." }
    New-Item -ItemType Directory -Force 'app/build/benchmarks' | Out-Null
    $outputPath = Join-Path $repoPath "app/build/benchmarks/$runId.jsonl"
    $rows | Set-Content -Encoding utf8 $outputPath
    Write-Output "Results: $outputPath"
} finally { Pop-Location }
