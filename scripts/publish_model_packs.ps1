# Publishes the app and verified speech packs as the GitHub release "speech-packs" (the app's "Get speech files"
# button opens it). Needs the GitHub CLI signed in (gh auth login). SideKick itself never downloads: the phone's browser
# does, then Install speech file verifies every size and SHA-256 from model-packs.tsv.
# The korean pack is left out: its training data (KsponSpeech, AI Hub) may not be re-shared without ETRI/NIA approval.
param([string[]]$Packs = @('whisper', 'sensevoice', 'omni', 'english', 'russian'), [switch]$SkipApk)
$ErrorActionPreference = 'Stop'
$root = Resolve-Path "$PSScriptRoot\.."; Set-Location $root
if ($Packs -contains 'korean') { throw 'The korean pack is not cleared for re-sharing (see app/src/main/assets/licenses/zipformer-korean.txt).' }
$gh = (Get-Command gh -ErrorAction SilentlyContinue).Source; if (-not $gh) { $gh = 'C:\Program Files\GitHub CLI\gh.exe' }
& $gh auth status *> $null; if ($LASTEXITCODE -ne 0) { throw 'Sign in first: gh auth login' }
$files = $Packs | ForEach-Object { ".deps\model-packs\$_.skmodels" }
$missing = $files | Where-Object { -not (Test-Path $_) }
if ($missing) { throw "Missing packs: $missing (run the prepare_*.py scripts, then python scripts/package_model_packs.py)" }
if (-not $SkipApk) {
    $meta = Get-Content app\build\outputs\apk\debug\output-metadata.json | ConvertFrom-Json
    $version = $meta.elements[0].versionName
    $apk = ".deps\model-packs\SideKick-$version.apk"
    Copy-Item app\build\outputs\apk\debug\app-debug.apk $apk -Force
    $files = @($apk) + $files
}
$notes = @"
SideKick for Android, for testing (development-signed, not a store release), plus its offline speech files.

1. On the phone, download SideKick-*.apk and install it (allow your browser to install apps when Android asks).
2. In SideKick: Get started -> Get speech files opens this page. Download the speech files you need:
   Auto-detect (Korean/English and more): sensevoice + omni. Optional: english (better English), russian. Explicit Whisper modes: whisper.
3. Back in SideKick: Install speech file -> pick each downloaded file. Each is checked against sizes and SHA-256 hashes built into the app.

Speech runs on the phone; SideKick has no network permission. Licenses are inside each file.
The optional Korean names & time-check file is not included (training-data terms); Korean speech works without it.
"@
& $gh release view speech-packs *> $null
if ($LASTEXITCODE -ne 0) { & $gh release create speech-packs --title "SideKick app and speech files" --notes $notes }
else { & $gh release edit speech-packs --notes $notes }
& $gh release upload speech-packs @files --clobber
& $gh release view speech-packs --json assets --jq '.assets[] | "\(.name)  \(.size)"'
