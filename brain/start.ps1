<#
Starts the station brain with the settings setup.ps1 saved, and prints the ticket, token and LAN address to pair phones.
Any extra arguments go to the brain, e.g.  .\start.ps1 --no-iroh   or   .\start.ps1 --rotate-token
#>
# Native tools write progress to stderr, which "Stop" would treat as failure; exit codes are checked instead.
$ErrorActionPreference = "Continue"
$config = Join-Path $PSScriptRoot "brain.json"
if (-not (Test-Path $config)) { throw "Run setup.ps1 first." }
$c = Get-Content $config -Raw | ConvertFrom-Json
$env:HF_HOME = $c.models
Set-Location $PSScriptRoot
& (Join-Path $c.venv "Scripts\python.exe") -s -m brain --asr $c.asr --llm $c.llm @args
