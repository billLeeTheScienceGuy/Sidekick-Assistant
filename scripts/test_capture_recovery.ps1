param([string]$DeviceSerial = 'emulator-5554')
$ErrorActionPreference = 'Stop'
if ($DeviceSerial -notmatch '^emulator-\d+$') { throw 'This test only controls an isolated emulator.' }
$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$taskAdb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$package = 'com.sidekick.app.benchmark'
$component = "$package/com.sidekick.app.audio.ListeningService"
function Invoke-TestAdb([string[]]$Arguments) {
    $value = (& $taskAdb -s $DeviceSerial @Arguments 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $value" }
    return $value
}
if ((Invoke-TestAdb @('shell','getprop','ro.hardware')) -notin @('ranchu','goldfish')) { throw 'Not an Android emulator.' }
$emulatorProcesses = @(Get-CimInstance Win32_Process | Where-Object { $_.Name -in @('emulator.exe','qemu-system-x86_64.exe') })
if ($emulatorProcesses.Count -eq 0 -or @($emulatorProcesses | Where-Object { $_.CommandLine -notmatch '(^|\s)-no-audio(\s|$)' }).Count -ne 0) {
    throw 'Start the test emulator with -no-audio. This script must not capture host microphone input.'
}
function Capture-Active {
    $dump = Invoke-TestAdb @('shell','dumpsys','activity','services',$package)
    $section = @($dump -split '(?m)^  \* ServiceRecord' | Where-Object { $_ -match 'com\.sidekick\.app\.audio\.ListeningService' })
    return @($section | Where-Object { $_ -match 'isForeground=true' -and $_ -match 'app=ProcessRecord\{' -and $_ -notmatch 'destroying=true' }).Count -gt 0
}
function Recovery-Armed {
    $prefs = Invoke-TestAdb @('shell','run-as',$package,'cat','shared_prefs/assistant.xml')
    return $prefs -match 'name="session_armed" value="true"'
}
function Await-State([scriptblock]$Condition, [string]$Failure) {
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    do {
        if (& $Condition) { return $timer.ElapsedMilliseconds }
        Start-Sleep -Milliseconds 500
    } while ($timer.ElapsedMilliseconds -lt 30000)
    throw $Failure
}
function Start-TestCapture {
    $null = Invoke-TestAdb @('shell','am','start','-n',"$package/com.sidekick.app.MainActivity")
    # The private service is started as its own debug UID, never exported for testing.
    $null = Invoke-TestAdb @('shell','run-as',$package,'am','start-foreground-service','--user','0','-n',$component)
    $null = Await-State { (Capture-Active) -and (Recovery-Armed) } 'Test capture did not start.'
}
function Stop-TestCapture {
    if (Capture-Active) {
        $null = Invoke-TestAdb @('shell','run-as',$package,'am','startservice','--user','0','-n',$component,'-a','com.sidekick.FINISH')
        $null = Await-State { -not (Capture-Active) } 'Test capture did not stop.'
    }
}
$previousRole = Invoke-TestAdb @('shell','cmd','role','get-role-holders','android.app.role.ASSISTANT')
$oldAccess = (Invoke-TestAdb @('shell','dumpsys','package',$package)) -match 'android.permission.RECORD_AUDIO: granted=true'
$result = [ordered]@{ device = $DeviceSerial; package = $package; conditions = 'Silent emulator; no physical microphone; verified debug UID; not a force-stop bypass'; completed = $false }
try {
    $null = Invoke-TestAdb @('shell','pm','grant',$package,'android.permission.RECORD_AUDIO')
    $null = Invoke-TestAdb @('shell','pm','grant',$package,'android.permission.POST_NOTIFICATIONS')
    $null = Invoke-TestAdb @('shell','cmd','role','add-role-holder','--user','0','android.app.role.ASSISTANT',$package)
    $null = Invoke-TestAdb @('shell','input','keyevent','KEYCODE_WAKEUP')
    $null = Invoke-TestAdb @('shell','wm','dismiss-keyguard')
    Start-TestCapture
    $oldPid = Invoke-TestAdb @('shell','pidof',$package)
    if ($oldPid -notmatch '^\d+$') { throw 'Expected exactly one isolated process.' }
    $identity = (Invoke-TestAdb @('shell','run-as',$package,'cat',"/proc/$oldPid/cmdline")).Trim([char]0)
    if ($identity -ne $package) { throw 'Process identity mismatch; refusing to signal it.' }
    $null = Invoke-TestAdb @('shell','run-as',$package,'kill','-9',$oldPid)
    $result.processRecoveryObservedMs = Await-State {
        try { $newPid = Invoke-TestAdb @('shell','pidof',$package) } catch { return $false }
        $newPid -match '^\d+$' -and $newPid -ne $oldPid -and (Capture-Active)
    } 'Enabled capture did not recover after process death.'
    Stop-TestCapture
    if (Recovery-Armed) { throw 'Finish left recovery armed.' }
    Start-TestCapture
    $null = Invoke-TestAdb @('shell','pm','revoke',$package,'android.permission.RECORD_AUDIO')
    $result.revocationStoppedAndDisarmedMs = Await-State { -not (Capture-Active) -and -not (Recovery-Armed) } 'Revocation did not stop and disarm capture.'
    $null = Invoke-TestAdb @('shell','pm','grant',$package,'android.permission.RECORD_AUDIO')
    Start-Sleep -Seconds 2
    if ((Capture-Active) -or (Recovery-Armed)) { throw 'Permission restoration silently re-armed capture.' }
    $result.regrantDidNotRearm = $true
    $result.completed = $true
} finally {
    try { Stop-TestCapture } finally {
        if ($previousRole -and $previousRole -match '^[a-zA-Z0-9_.]+$') {
            $null = Invoke-TestAdb @('shell','cmd','role','add-role-holder','--user','0','android.app.role.ASSISTANT',$previousRole)
        } else { $null = Invoke-TestAdb @('shell','cmd','role','remove-role-holder','--user','0','android.app.role.ASSISTANT',$package) }
        if ($oldAccess) { $null = Invoke-TestAdb @('shell','pm','grant',$package,'android.permission.RECORD_AUDIO') }
        else { $null = Invoke-TestAdb @('shell','pm','revoke',$package,'android.permission.RECORD_AUDIO') }
    }
    $out = Join-Path (Split-Path -Parent $PSScriptRoot) '.deps/capture-recovery.json'
    $result | ConvertTo-Json | Set-Content -LiteralPath $out -Encoding utf8
}
$result | ConvertTo-Json
