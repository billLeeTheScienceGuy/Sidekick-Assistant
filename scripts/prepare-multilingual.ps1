$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$destination = Join-Path $root 'app/src/main/assets/models'
New-Item -ItemType Directory -Force $destination | Out-Null
$base = Join-Path $destination 'ggml-base.bin'
$baseHash = '60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe'
if (!(Test-Path -LiteralPath $base) -or ((Get-FileHash -LiteralPath $base -Algorithm SHA256).Hash -ne $baseHash)) {
    $baseTemporary = "$base.download"
    try {
        Invoke-WebRequest 'https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base.bin' -OutFile $baseTemporary
        if ((Get-FileHash -LiteralPath $baseTemporary -Algorithm SHA256).Hash -ne $baseHash) { throw 'Base model checksum mismatch' }
        Move-Item -LiteralPath $baseTemporary -Destination $base -Force
    } finally { if (Test-Path -LiteralPath $baseTemporary) { Remove-Item -LiteralPath $baseTemporary } }
}
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
