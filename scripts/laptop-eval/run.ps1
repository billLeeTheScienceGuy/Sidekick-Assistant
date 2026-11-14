param([Parameter(Mandatory)][string]$Set, [string]$Modes = 'auto', [int]$Workers = 4, [int]$Threads = 3, [Parameter(Mandatory)][string]$Out)
# Laptop speech evaluation (see LaptopEval.java). Needs .deps/sherpa-win (sherpa-onnx 1.13.3 jars + models copied from the phone).
$root = Resolve-Path "$PSScriptRoot\..\.."
$deps = Join-Path $root '.deps\sherpa-win'
$java = Join-Path $env:USERPROFILE '.jdks\jbr-21.0.11\bin\java.exe'
$stdlib = Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\org.jetbrains.kotlin\kotlin-stdlib" -Recurse -Filter 'kotlin-stdlib-2.1.20.jar' | Select-Object -First 1
$cp = @((Join-Path $root 'core\build\libs\core.jar'), $stdlib.FullName, (Join-Path $deps 'sherpa-onnx-v1.13.3.jar'), (Join-Path $deps 'sherpa-onnx-native-lib-win-x64-v1.13.3.jar')) -join ';'
& $java -cp $cp (Join-Path $PSScriptRoot 'LaptopEval.java') (Resolve-Path $Set) (Join-Path $deps 'models') $Modes $Workers $Threads $Out
