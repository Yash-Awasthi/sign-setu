<#
Runs the station brain with the settings setup.ps1 saved, in this window: it prints the ticket, token and LAN address
to pair phones, and stops with Ctrl+C. Extra arguments go to the brain, e.g.  .\start.ps1 --rotate-token
  .\start.ps1 -Service   starts the scheduled task instead (setup.ps1 -Service made it), in the background
  -Log                   used by the task: output goes to brain.log in the state folder
#>
param([switch]$Service, [switch]$Log)
# Native tools write progress to stderr, which "Stop" would treat as failure; exit codes are checked instead.
$ErrorActionPreference = "Continue"
. (Join-Path $PSScriptRoot "station.ps1")
$c = Get-StationConfig

if ($Service) {
    if (-not (Get-ScheduledTask -TaskName $StationTask -ErrorAction SilentlyContinue)) { throw "No scheduled task: run .\setup.ps1 -Asr $($c.asr) -Service -NoModels" }
    if (@(Get-BrainProcess).Count) { Write-Host "The brain is already running. .\status.ps1 shows it."; return }
    Start-ScheduledTask -TaskName $StationTask
    Write-Host "Starting. In a minute .\status.ps1 shows it and how to pair a phone."
    return
}
if (-not $Log -and @(Get-BrainProcess).Count) { throw "The brain is already running (.\status.ps1). Stop it first with .\stop.ps1." }

$env:HF_HOME = $c.models
if ($c.relay) { $env:BRAIN_RELAY = $c.relay }
if ($c.isl) { $env:BRAIN_ISL = $c.isl }
if ($c.inbox) { $env:BRAIN_INBOX = $c.inbox }
Set-Location $PSScriptRoot
$python = Join-Path $c.venv "Scripts\python.exe"
if ($Log) {
    New-Item -ItemType Directory -Force -Path $StationState | Out-Null
    if ((Test-Path $StationLog) -and (Get-Item $StationLog).Length -gt 10MB) { Move-Item -Force $StationLog "$StationLog.old" }
    # The brain writes the log itself, in UTF-8; a PowerShell redirect would write UTF-16.
    & $python -s -m brain --asr $c.asr --llm $c.llm --log $StationLog @args
} else {
    & $python -s -m brain --asr $c.asr --llm $c.llm @args
}
