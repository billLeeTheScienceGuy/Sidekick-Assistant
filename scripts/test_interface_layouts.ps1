param(
    [string]$DeviceSerial = 'emulator-5554',
    [string]$Python = 'python'
)
$ErrorActionPreference = 'Stop'
if ($DeviceSerial -notmatch '^emulator-\d+$') { throw 'Use an isolated emulator, never a personal phone.' }
$root = Split-Path -Parent $PSScriptRoot
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$package = 'com.sidekick.app.compacttest'
if (((& $adb -s $DeviceSerial shell getprop ro.hardware) -join '').Trim() -notin @('ranchu','goldfish')) { throw 'Emulator hardware required.' }
if ([int]((& $adb -s $DeviceSerial shell getprop ro.build.version.sdk) -join '') -lt 33) { throw 'The locale matrix requires an API 33+ emulator.' }
$sizeText = (& $adb -s $DeviceSerial shell wm size) -join "`n"
$densityText = (& $adb -s $DeviceSerial shell wm density) -join "`n"
$savedSize = if ($sizeText -match 'Override size: (\d+x\d+)') { $Matches[1] } else { 'reset' }
$savedDensity = if ($densityText -match 'Override density: (\d+)') { $Matches[1] } else { 'reset' }
$savedFont = ((& $adb -s $DeviceSerial shell settings get system font_scale) -join '').Trim()
$localeText = (& $adb -s $DeviceSerial shell cmd locale get-app-locales $package --user 0) -join ''
$savedLocale = if ($localeText -match '\[(.*?)\]') { $Matches[1] } else { throw 'Could not read isolated app locale.' }
$out = Join-Path $root '.deps\interface-validation'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$cases = @(
    @{ name='simple'; size='720x1600'; font='1.0'; dark='false'; locale='en'; design='CALM'; text='PHONE'; ui='PHONE'; home='true' },
    @{ name='small'; size='640x1280'; font='1.0'; dark='false'; locale='en'; design='CALM'; text='PHONE'; ui='PHONE'; home='false' },
    @{ name='large-text'; size='720x1600'; font='2.0'; dark='true'; locale='en'; design='CALM'; text='PHONE'; ui='LARGE'; home='true' },
    @{ name='landscape'; size='1600x720'; font='1.3'; dark='false'; locale='en'; design='CLASSIC'; text='LARGE'; ui='PHONE'; home='true' },
    @{ name='wide'; size='1920x1200'; font='1.0'; dark='true'; locale='en'; design='WARM'; text='LARGE'; ui='EXTRA_LARGE'; home='false' },
    @{ name='rtl'; size='720x1600'; font='1.3'; dark='true'; locale='ar'; design='CLASSIC'; text='LARGE'; ui='PHONE'; home='true' }
)
$copyPng = @'
import subprocess,sys,json
from pathlib import Path
raw=subprocess.check_output([sys.argv[1],'-s',sys.argv[2],'exec-out','run-as',sys.argv[3],'cat','files/interface-'+sys.argv[4]+'.png'])
assert raw.startswith(bytes([137,80,78,71,13,10,26,10])), 'Expected PNG'
Path(sys.argv[5]).write_bytes(raw)
report=subprocess.check_output([sys.argv[1],'-s',sys.argv[2],'exec-out','run-as',sys.argv[3],'cat','files/interface-'+sys.argv[4]+'.json'])
Path(sys.argv[5]).with_suffix('.json').write_bytes(report)
print(json.loads(report))
'@
try {
    foreach ($case in $cases) {
        & $adb -s $DeviceSerial shell am force-stop $package
        & $adb -s $DeviceSerial shell wm size $case.size
        & $adb -s $DeviceSerial shell wm density 320
        & $adb -s $DeviceSerial shell settings put system font_scale $case.font
        & $adb -s $DeviceSerial shell cmd locale set-app-locales $package --user 0 --locales $case.locale
        if ($LASTEXITCODE -ne 0) { throw 'Display/locale configuration failed.' }
        Start-Sleep -Milliseconds 700
        $rtl = if ($case.locale -eq 'ar') { 'true' } else { 'false' }
        $result = & $adb -s $DeviceSerial shell am instrument -w -e interfaceDesign $case.design -e interfaceTextSize $case.text -e interfaceSize $case.ui -e interfaceHome $case.home -e interfaceDark $case.dark -e interfaceRtl $rtl -e interfaceScreenshot $case.name -e class 'com.sidekick.app.ResponsiveInterfaceTest#actualWindowLayoutAndControlsRemainUsable' "$package.test/androidx.test.runner.AndroidJUnitRunner" 2>&1 | Out-String
        $result | Set-Content -LiteralPath (Join-Path $out "$($case.name).txt") -Encoding utf8
        if ($LASTEXITCODE -ne 0 -or $result -notmatch 'OK \(1 test\)' -or $result -match 'FAILURES!!!|INSTRUMENTATION_FAILED') { throw "Layout check failed: $($case.name). See $out." }
        & $Python -c $copyPng $adb $DeviceSerial $package $case.name (Join-Path $out "$($case.name).png")
        if ($LASTEXITCODE -ne 0) { throw 'Screenshot retrieval failed.' }
        Write-Output "Passed $($case.name): $($case.size), density 320, font $($case.font), locale $($case.locale)"
    }
} finally {
    & $adb -s $DeviceSerial shell am force-stop $package
    & $adb -s $DeviceSerial shell wm size $savedSize
    & $adb -s $DeviceSerial shell wm density $savedDensity
    if ($savedFont -eq 'null') { & $adb -s $DeviceSerial shell settings delete system font_scale }
    else { & $adb -s $DeviceSerial shell settings put system font_scale $savedFont }
    if ($savedLocale) { & $adb -s $DeviceSerial shell cmd locale set-app-locales $package --user 0 --locales $savedLocale }
    else { & $adb -s $DeviceSerial shell cmd locale set-app-locales $package --user 0 }
}
