$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$root = Split-Path -Parent $PSScriptRoot
$destination = Join-Path $root '.deps/quality-fixtures'
New-Item -ItemType Directory -Force $destination | Out-Null
$voice = New-Object System.Speech.Synthesis.SpeechSynthesizer
$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
try {
    $voice.Rate = 0
    $lines = @{
        'quality-short' = 'It ends at midnight.'
        'quality-party' = 'Tomorrow I will go to the party at eight P M at The James.'
    }
    foreach ($name in $lines.Keys) {
        $voice.SetOutputToWaveFile((Join-Path $destination "$name.wav"), $format)
        $voice.Speak($lines[$name])
        $voice.SetOutputToNull()
    }
    Write-Output 'Generated original synthetic test speech; no microphone used.'
} finally { $voice.Dispose() }
