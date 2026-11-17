# Keeps the laptop from idle-sleeping until the unattended voice queue writes "queue DONE" (at most 24 hours).
# Uses the same per-program request a video player makes; it changes no power settings and ends with this script.
# A closed lid or choosing Sleep still sleeps the laptop.
param([string]$Report = "$PSScriptRoot\..\..\.deps\speaker-public\eval\OVERNIGHT_REPORT.txt")
Add-Type -Namespace Win32 -Name Power -MemberDefinition '[DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(uint flags);'
$ES_CONTINUOUS = [uint32]"0x80000000"; $ES_SYSTEM_REQUIRED = [uint32]1
[Win32.Power]::SetThreadExecutionState($ES_CONTINUOUS -bor $ES_SYSTEM_REQUIRED) | Out-Null
$deadline = (Get-Date).AddHours(24)
try {
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 60
        $last = Get-Content $Report -Tail 1 -ErrorAction SilentlyContinue
        if ($last -match "queue DONE") { break }
    }
} finally { [Win32.Power]::SetThreadExecutionState($ES_CONTINUOUS) | Out-Null }
