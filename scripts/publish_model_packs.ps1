# Publishes verified speech packs as assets of the GitHub release "speech-packs" (the app's "Get speech files" button).
# Needs the GitHub CLI (winget install GitHub.cli; gh auth login). The app itself never downloads: the browser does,
# then Install speech file verifies every size and SHA-256 from model-packs.tsv.
# The korean pack is left out by default: its training-data terms (KsponSpeech, AI Hub) have not been reviewed.
param([string[]]$Packs = @('whisper', 'sensevoice', 'omni', 'english', 'russian'))
$ErrorActionPreference = 'Stop'
$root = Resolve-Path "$PSScriptRoot\.."; Set-Location $root
python scripts/prepare_omnilingual.py
python scripts/prepare_accuracy_packs.py
python scripts/package_model_packs.py
$files = $Packs | ForEach-Object { ".deps\model-packs\$_.skmodels" }
$missing = $files | Where-Object { -not (Test-Path $_) }
if ($missing) { throw "Missing packs: $missing (run prepare_speech.py and prepare_sensevoice.py first)" }
$notes = "Offline speech files for SideKick. Download on the phone, then Get started -> Install speech file. Each file is checked against the sizes and SHA-256 hashes built into the app. Licenses are inside each pack."
gh release view speech-packs *> $null
if ($LASTEXITCODE -ne 0) { gh release create speech-packs --title "Speech packs" --notes $notes }
gh release upload speech-packs @files --clobber
gh release view speech-packs --json assets --jq '.assets[] | "\(.name)  \(.size)"'
