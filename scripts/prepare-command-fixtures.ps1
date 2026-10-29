$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$destination = Join-Path (Split-Path -Parent $PSScriptRoot) '.deps/quality-fixtures'
New-Item -ItemType Directory -Force $destination | Out-Null
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
$lines = @('Cancel it.', 'Move it to nine P M.', 'It ends at midnight.', "Do not cancel it.")
$fixtures = @()
try {
    $voices = @($synth.GetInstalledVoices() | Select-Object -First 2)
    for ($v = 0; $v -lt $voices.Count; $v++) {
        $synth.SelectVoice($voices[$v].VoiceInfo.Name)
        $synth.Rate = 2
        for ($i = 0; $i -lt $lines.Count; $i++) {
            $name = "command-$v-$i.wav"
            $synth.SetOutputToWaveFile((Join-Path $destination $name), $format)
            $synth.Speak($lines[$i]); $synth.SetOutputToNull()
            $fixtures += @{ file = $name; reference = $lines[$i] }
        }
    }
    ConvertTo-Json -InputObject @($fixtures) | Set-Content (Join-Path $destination 'commands.json') -Encoding UTF8
    Write-Output "Generated $($fixtures.Count) synthetic short-command fixtures. No microphone used."
} finally { $synth.Dispose() }
