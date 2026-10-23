$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$vadPath = Join-Path $projectPath 'app/src/main/assets/models/ggml-silero-v5.1.2.bin'
$vadHash = '29940d98d42b91fbd05ce489f3ecf7c72f0a42f027e4875919a28fb4c04ea2cf'
New-Item -ItemType Directory -Force (Split-Path $vadPath -Parent) | Out-Null
if (-not ((Test-Path -LiteralPath $vadPath) -and ((Get-FileHash -LiteralPath $vadPath -Algorithm SHA256).Hash -eq $vadHash))) {
    $vadTemp = "$vadPath.download"
    Invoke-WebRequest 'https://huggingface.co/ggml-org/whisper-vad/resolve/9ffd54a1e1ee413ddf265af9913beaf518d1639b/ggml-silero-v5.1.2.bin' -OutFile $vadTemp
    if ((Get-FileHash -LiteralPath $vadTemp -Algorithm SHA256).Hash -ne $vadHash) { throw 'Speech detector checksum mismatch.' }
    Move-Item -LiteralPath $vadTemp -Destination $vadPath -Force
}
Write-Output 'Verified speech detector ready.'
