# Live voice-match and consent check on the Pixel (USB debugging on). See docs/PHONE_TESTS.md for what to say and do.
# Reads only the app's "SidekickVoice" log lines: voice scores and consent decisions, never words or location.
#   .\scripts\phone-tests\live-check.ps1 -Minutes 3
param([double]$Minutes = 3)
$ErrorActionPreference = 'Stop'
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (& $adb get-state 2>$null)) { throw "Phone not connected. Plug in the Pixel and allow USB debugging." }
& $adb logcat -c
Write-Host "Start listening in SideKick now. You have $Minutes minute(s); follow the steps in docs/PHONE_TESTS.md." -ForegroundColor Cyan
$end = (Get-Date).AddMinutes($Minutes)
while ((Get-Date) -lt $end) { Write-Host -NoNewline ("`r{0:mm\:ss} left " -f ($end - (Get-Date))); Start-Sleep 1 }
Write-Host "`rTime's up. Tap Finish in SideKick, then wait a few seconds..."; Start-Sleep 8
$lines = @(& $adb logcat -d -s SidekickVoice:I | Where-Object { $_ -match 'speaker=' })
if (-not $lines.Count) { Write-Host "No phrases logged. Was SideKick listening (not voice setup), on this build?" -ForegroundColor Yellow; exit 1 }
$rows = foreach ($l in $lines) {
    $scores = if ($l -match 'profile ([\d\. /]+);') { $Matches[1] -split ' / ' | ForEach-Object { [double]$_ } } else { @() }
    [pscustomobject]@{
        Speaker = if ($l -match 'speaker=(\w+)') { $Matches[1] } else { '?' }
        Kept    = $l -match 'kept=true'
        Rule    = if ($l -match 'rule=(\w+)') { $Matches[1] } else { '?' }
        Notice  = $l -match 'notice=true'
        Best    = if ($scores.Count) { ($scores | Measure-Object -Maximum).Maximum } else { $null }
    }
}
Write-Host "`n== Phrases: $($rows.Count)  (rule: $(($rows | Select-Object -ExpandProperty Rule -Unique) -join ', '))"
$rows | Group-Object Speaker | ForEach-Object {
    $s = @($_.Group | Where-Object { $_.Best -ne $null } | ForEach-Object Best)
    "{0,-8} {1,3} phrases · kept {2,3} · dropped {3,3} · best voice score {4}" -f $_.Name, $_.Count, @($_.Group | Where-Object Kept).Count, @($_.Group | Where-Object { -not $_.Kept }).Count,
        $(if ($s.Count) { "min {0:N2} / median {1:N2} / max {2:N2}" -f ($s | Measure-Object -Minimum).Minimum, ($s | Sort-Object)[[int]($s.Count / 2)], ($s | Measure-Object -Maximum).Maximum } else { 'n/a' })
}
$before = @($rows | Where-Object { -not $_.Notice }); $after = @($rows | Where-Object Notice)
if ($after.Count) { "Notice given after phrase {0}: before it, others kept {1}/{2}; after it, others kept {3}/{4}" -f $before.Count,
    @($before | Where-Object { $_.Speaker -ne 'USER' -and $_.Kept }).Count, @($before | Where-Object { $_.Speaker -ne 'USER' }).Count,
    @($after | Where-Object { $_.Speaker -ne 'USER' -and $_.Kept }).Count, @($after | Where-Object { $_.Speaker -ne 'USER' }).Count }
$out = Join-Path $PSScriptRoot "..\..\.deps\phone-tests"; New-Item -ItemType Directory -Force $out | Out-Null
$file = Join-Path $out ("live-check-{0:yyyyMMdd-HHmm}.log" -f (Get-Date)); $lines | Set-Content $file -Encoding utf8
"Raw lines (scores only) saved to $file"
