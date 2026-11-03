<#
Stops the station brain, whether the scheduled task or a hand start runs it. The task stays set up, so it starts
again at the next boot (or sign-in); .\start.ps1 -Service starts it now, .\remove.ps1 takes it away.
#>
$ErrorActionPreference = "Continue"
. (Join-Path $PSScriptRoot "station.ps1")
Stop-Station
