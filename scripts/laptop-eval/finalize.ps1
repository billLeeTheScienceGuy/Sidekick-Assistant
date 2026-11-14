# Unattended wrap-up: waits for the laptop benchmark and the phone health chain, re-times Qwen3 on the phone,
# then writes .deps\bench\REPORT.md with every result. Safe to re-run; each step skips work already done.
$root = Resolve-Path "$PSScriptRoot\..\.."; Set-Location $root
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$models = 'parakeet110', 'fc20k', 'parakeet3', 'canary', 'sense2024', 'sense2025', 'nemotron', 'qwen3', 'firered2', 'funnano', 'zipko', 'gigaam3', 'moonar', 'omni2', 'omni1', 'lid'
while (@($models | ? { -not (Test-Path ".deps\bench\$_.json") }).Count -gt 0) { Start-Sleep 60 }
while (-not ((& $adb shell "ls /data/local/tmp/health-finished 2>/dev/null") -match 'finished')) { Start-Sleep 60 }
New-Item -ItemType Directory -Force .deps\health | Out-Null
foreach ($p in 'speaker', 'memory', 'energy', 'falsetrigger', 'heat') { & $adb shell run-as com.sidekick.app cat "files/evaluation/health-$p.json" > ".deps\health\$p.json" }
if (-not (Test-Path .deps\phone-bench\bench-qwen3.json) -or (Get-Content .deps\phone-bench\bench-qwen3.json -Raw) -match 'ERROR') {
    Remove-Item .deps\phone-bench\bench-qwen3.json -ErrorAction SilentlyContinue
    & $adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk | Out-Null
    & "$PSScriptRoot\phone-bench.ps1" -Models qwen3
}

# ---------- report ----------
$rows = Get-ChildItem .deps\bench\*.json | % { Get-Content $_.FullName -Raw -Encoding utf8 | ConvertFrom-Json } | % { $_ }
$rows | % { $_ | Add-Member -Force -NotePropertyName cer -NotePropertyValue ($_.characterEdits / [Math]::Max(1, $_.referenceCharacters)) }
# Natural YouTube captions can be wrong or translated: score a natural clip only if some model came within 60% of it.
$best = @{}; $rows | ? { $_.mode -ne 'lid' } | Group-Object file | % { $best[$_.Name] = ($_.Group | Measure-Object cer -Minimum).Minimum }
$scored = $rows | ? { $_.mode -ne 'lid' -and ($_.set -ne 'passages' -and $_.set -ne 'mixed' -or $best[$_.file] -lt 0.6) }
$out = [System.Collections.Generic.List[string]]::new()
$out.Add("# SideKick speech benchmark report"); $out.Add(""); $out.Add("Generated $(Get-Date -Format 'yyyy-MM-dd HH:mm'). Character error rate (CER, %) per test set and language; lower is better. Natural sets count only clips whose caption some model matched within 60%.")
foreach ($set in ($scored | Select-Object -ExpandProperty set -Unique | Sort-Object)) {
    $out.Add(""); $out.Add("## Set: $set"); $out.Add("")
    $langs = $scored | ? set -eq $set | Select-Object -ExpandProperty language -Unique | Sort-Object
    $out.Add("| Model | " + ($langs -join ' | ') + " |"); $out.Add("| --- |" + (' --- |' * $langs.Count))
    foreach ($m in $models) {
        $cells = foreach ($l in $langs) { $g = @($scored | ? { $_.set -eq $set -and $_.language -eq $l -and $_.mode -eq $m }); if ($g.Count) { "{0:N1} ({1})" -f (100.0 * ($g | Measure-Object characterEdits -Sum).Sum / [Math]::Max(1, ($g | Measure-Object referenceCharacters -Sum).Sum)), $g.Count } else { '' } }
        if (($cells | ? { $_ }).Count) { $out.Add("| $m | " + ($cells -join ' | ') + " |") }
    }
}
$out.Add(""); $out.Add("## Language detector accuracy (Whisper tiny)"); $out.Add("")
$rows | ? mode -eq 'lid' | Group-Object language | Sort-Object Name | % { $out.Add("- $($_.Name): $(@($_.Group | ? { $_.recognized -eq $_.language }).Count) of $($_.Count)") }
$out.Add(""); $out.Add("## Phone speed (Pixel 7a, 4 threads)"); $out.Add(""); $out.Add("| Model | Median s/clip | Real-time factor | Load s | Errors |"); $out.Add("| --- | --- | --- | --- | --- |")
foreach ($f in Get-ChildItem .deps\phone-bench\bench-*.json) {
    $r = @(Get-Content $f.FullName -Raw -Encoding utf8 | ConvertFrom-Json); $ok = @($r | ? { $_.recognized -notlike 'ERROR*' })
    if (-not $ok.Count) { $out.Add("| $($f.BaseName -replace 'bench-','') | | | | all |"); continue }
    $ms = @($ok | % elapsedMs | Sort-Object); $rtf = @($ok | % { $_.elapsedMs / [Math]::Max(1, $_.audioMs) } | Sort-Object)
    $out.Add(("| {0} | {1:N2} | {2:N2} | {3:N1} | {4} |" -f ($f.BaseName -replace 'bench-',''), ($ms[[int]($ms.Count / 2)] / 1000), $rtf[[int]($rtf.Count / 2)], ($r[0].loadMs / 1000), ($r.Count - $ok.Count)))
}
$out.Add(""); $out.Add("## Device health (app's current Auto routing)"); $out.Add("")
foreach ($p in 'speaker', 'memory', 'energy', 'falsetrigger') { $raw = Get-Content ".deps\health\$p.json" -Raw -ErrorAction SilentlyContinue; if ($raw) { $j = $raw | ConvertFrom-Json; $j.PSObject.Properties.Remove('rows'); $out.Add("- **$p**: " + (($j.PSObject.Properties | % { "$($_.Name)=$($_.Value)" }) -join ', ')) } }
$heat = Get-Content .deps\health\heat.json -Raw -ErrorAction SilentlyContinue
if ($heat) { $s = @(($heat | ConvertFrom-Json).samples); if ($s.Count -ge 10) { $n = [int]($s.Count / 10); $first = ($s | Select-Object -First $n | Measure-Object realTimeFactor -Average).Average; $last = ($s | Select-Object -Last $n | Measure-Object realTimeFactor -Average).Average; $out.Add(("- **heat**: real-time factor first 10% {0:N3}, last 10% {1:N3} (slowdown {2:P0}); max thermal status {3}" -f $first, $last, (($last / $first) - 1), ($s | Measure-Object thermal -Maximum).Maximum)) } }
[IO.File]::WriteAllLines("$root\.deps\bench\REPORT.md", $out, (New-Object Text.UTF8Encoding $false))
"report written: .deps\bench\REPORT.md"
