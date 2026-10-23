$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$destination = Join-Path $root 'app/src/main/assets/models'
New-Item -ItemType Directory -Force $destination | Out-Null
$file = Join-Path $destination 'ggml-tiny.bin'
$expected = 'be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21'
if ((Test-Path -LiteralPath $file) -and ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -eq $expected)) { return }
$temporary = "$file.download"
try {
    Invoke-WebRequest -Uri 'https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-tiny.bin' -OutFile $temporary
    if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash -ne $expected) { throw 'Multilingual speech model checksum mismatch.' }
    Move-Item -LiteralPath $temporary -Destination $file -Force
} finally { if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary } }
Write-Output 'Installed verified multilingual Whisper tiny model.'
