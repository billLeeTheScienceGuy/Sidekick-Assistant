param([Parameter(Mandatory)][string[]]$Sets, [Parameter(Mandatory)][string]$Models, [int]$Workers = 4, [int]$Threads = 3, [Parameter(Mandatory)][string]$Out)
# Candidate-model benchmark (see ModelBench.java). Needs .deps/sherpa-win: sherpa-onnx 1.13.8 jars, candidates/, models/ (app models).
$root = Resolve-Path "$PSScriptRoot\..\.."
$deps = Join-Path $root '.deps\sherpa-win'
$java = Join-Path $env:USERPROFILE '.jdks\jbr-21.0.11\bin\java.exe'
$cp = @((Join-Path $deps 'sherpa-onnx-jvm-1.13.8.jar'), (Join-Path $deps 'sherpa-onnx-native-lib-win-x64-1.13.8.jar')) -join ';'
$setArg = ($Sets | % { (Resolve-Path $_).Path }) -join ';'
& $java -Xmx8g -cp $cp (Join-Path $PSScriptRoot 'ModelBench.java') $setArg (Join-Path $deps 'candidates') (Join-Path $deps 'models') $Models $Workers $Threads $Out
