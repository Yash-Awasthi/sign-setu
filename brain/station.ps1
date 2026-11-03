# Names and helpers the station scripts share (setup, start, stop, status, remove); dot-sourced, never run alone.
$StationTask = "SignSetuBrain"
$StationRule = "Sign Setu brain (LAN)"
$StationConfig = Join-Path $PSScriptRoot "brain.json"
$StationState = if ($env:BRAIN_STATE) { $env:BRAIN_STATE } else { Join-Path $env:USERPROFILE ".signsetu-brain" }
$StationLog = Join-Path $StationState "brain.log"

function Get-StationConfig {
    if (-not (Test-Path $StationConfig)) { throw "Not set up yet: run .\setup.ps1 -Asr <model> first." }
    Get-Content $StationConfig -Raw | ConvertFrom-Json
}

# The brain's Python processes: the venv launcher and the interpreter it starts both carry "-m brain".
function Get-BrainProcess {
    Get-CimInstance Win32_Process -Filter "Name = 'python.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match '\s-m\s+brain(\s|$)' }
}

function Stop-Station([switch]$Quiet) {
    $task = Get-ScheduledTask -TaskName $StationTask -ErrorAction SilentlyContinue
    # The task first, or it would start the brain again within a minute.
    if ($task -and $task.State -eq "Running") { Stop-ScheduledTask -TaskName $StationTask }
    $procs = @(Get-BrainProcess)
    foreach ($p in $procs) { Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }
    if (-not $Quiet) {
        if ($procs.Count) { Write-Host "Stopped the brain ($($procs.Count) process(es))." } else { Write-Host "The brain was not running." }
    }
}
