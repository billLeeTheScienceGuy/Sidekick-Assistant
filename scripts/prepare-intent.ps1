$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$modelPath = Join-Path $projectPath '.deps/Qwen3-0.6B.Q4_K_M.gguf'
$expectedHash = '7af3fdf842f87b24672f8a7f1dd50404043f0bfb71093ff91c31d2b49df4631d'
New-Item -ItemType Directory -Force (Split-Path $modelPath -Parent) | Out-Null
if (-not ((Test-Path -LiteralPath $modelPath) -and ((Get-FileHash -LiteralPath $modelPath -Algorithm SHA256).Hash -eq $expectedHash))) {
    $temporaryModel = "$modelPath.download"
    Invoke-WebRequest 'https://huggingface.co/QuantFactory/Qwen3-0.6B-GGUF/resolve/e7e05d713acaa2baccdfb52e967eaba8ba562ba8/Qwen3-0.6B.Q4_K_M.gguf' -OutFile $temporaryModel
    if ((Get-FileHash -LiteralPath $temporaryModel -Algorithm SHA256).Hash -ne $expectedHash) { throw 'Intent model checksum mismatch.' }
    Move-Item -LiteralPath $temporaryModel -Destination $modelPath -Force
}
Write-Output "Verified intent model ready for one-time import: $modelPath"
