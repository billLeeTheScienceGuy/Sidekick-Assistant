$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'prepare-vad.ps1')
& (Join-Path $PSScriptRoot 'prepare-multilingual.ps1')
$root = Split-Path -Parent $PSScriptRoot
$destination = Join-Path $root 'app/src/main/assets/models'
New-Item -ItemType Directory -Force $destination | Out-Null
$file = Join-Path $destination 'ggml-tiny.en.bin'
$expected = '921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f'
if ((Test-Path -LiteralPath $file) -and ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -eq $expected)) {
    Write-Output 'Verified speech model already present.'
    exit 0
}
$temporary = "$file.download"
Invoke-WebRequest -Uri 'https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin' -OutFile $temporary
if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash -ne $expected) {
    throw 'Speech model checksum mismatch. The temporary file was not installed.'
}
Move-Item -LiteralPath $temporary -Destination $file -Force
Write-Output 'Installed verified Whisper tiny.en model for APK packaging (77,704,715 bytes).'
