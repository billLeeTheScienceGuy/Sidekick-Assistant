$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$destination = Join-Path (Split-Path -Parent $PSScriptRoot) '.deps/speaker-evaluation'
New-Item -ItemType Directory -Force $destination | Out-Null
$models = @(
    @('3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx', '357a834f702b80161e5b981182c038e18553c1f2ca752ed6cec2052365d4129b'),
    @('3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx', 'aa3cfc16963a10586a9393f5035d6d6b57e98d358b347f80c2a30bf4f00ceba2')
)
foreach ($model in $models) {
    $target = Join-Path $destination $model[0]
    if ((Test-Path -LiteralPath $target) -and (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -eq $model[1]) { continue }
    $temporary = "$target.download"
    Invoke-WebRequest -Uri "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/$($model[0])" -OutFile $temporary
    if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash -ne $model[1]) { throw 'Speaker comparison model checksum mismatch.' }
    Move-Item -LiteralPath $temporary -Destination $target -Force
}
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
try {
    $voices = @($synth.GetInstalledVoices() | Select-Object -First 2)
    for ($v = 0; $v -lt $voices.Count; $v++) {
        $synth.SelectVoice($voices[$v].VoiceInfo.Name)
        $lines = @('This is my natural speaking voice. I enjoy walking outdoors and talking about ordinary things. The weather changes throughout the year.', "I'll attend a concert tomorrow at six P M.", 'It ends at eight P M.')
        for ($i = 0; $i -lt $lines.Count; $i++) {
            $synth.Rate = if ($i -eq 0) { 0 } else { 2 }
            $synth.SetOutputToWaveFile((Join-Path $destination "speaker-$v-$i.wav"), $format)
            $synth.Speak($lines[$i]); $synth.SetOutputToNull()
        }
    }
    Write-Output 'Generated disjoint enrollment and short-phrase speech for two synthetic voices.'
} finally { $synth.Dispose() }
