param([string[]]$Models = @('parakeet110', 'fc20k', 'parakeet3', 'canary', 'sense2024', 'sense2025', 'nemotron', 'qwen3', 'firered2', 'funnano',
    'zipko', 'gigaam3', 'moonar', 'omni2', 'omni1', 'lid'), [string]$OutDir = '.deps\bench')
# Every candidate on every test set, one process per model (a crash loses only that model). Skips finished models.
$root = Resolve-Path "$PSScriptRoot\..\.."
Set-Location $root
New-Item -ItemType Directory -Force $OutDir | Out-Null
$sets = '.deps\fleurs', '.deps\yodas\passages', '.deps\my-voice\wav', '.deps\scenarios', '.deps\cs-fleurs', '.deps\yodas\mixed', '.deps\codeswitch-tts', '.deps\lebanese'
foreach ($m in $Models) {
    $out = Join-Path $OutDir "$m.json"
    if (Test-Path $out) { "$m already done"; continue }
    $t = Measure-Command { & "$PSScriptRoot\bench.ps1" -Sets $sets -Models $m -Workers 4 -Threads 3 -Out "$out.partial" 2>&1 | Select-String 'clips in|unknown model' | % Line }
    if (Test-Path "$out.partial") { Move-Item "$out.partial" $out -Force }
    "{0} finished in {1:N1} min at {2:HH:mm}" -f $m, $t.TotalMinutes, (Get-Date)
}
