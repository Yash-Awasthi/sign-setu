<#
Stops the station brain and takes away its scheduled task and firewall rule (the rule needs administrator rights).
The environment, the models, the sign reader, the state folder (certificate, token, iroh key: the ticket phones were
paired with) and the inbox (Sign Setu in Documents) stay; the paths are printed, to delete by hand once nothing in them is needed.
#>
$ErrorActionPreference = "Continue"
. (Join-Path $PSScriptRoot "station.ps1")
Stop-Station
if (Get-ScheduledTask -TaskName $StationTask -ErrorAction SilentlyContinue) {
    Unregister-ScheduledTask -TaskName $StationTask -Confirm:$false
    Write-Host "Removed the scheduled task $StationTask."
}
$rule = Get-NetFirewallRule -DisplayName "$StationRule*" -ErrorAction SilentlyContinue
if ($rule) {
    try { $rule | Remove-NetFirewallRule -ErrorAction Stop; Write-Host "Removed the firewall rules." }
    catch { Write-Warning "The firewall rule needs administrator rights to remove: run this again as administrator." }
}
Write-Host "Kept:"
if (Test-Path $StationConfig) {
    $c = Get-StationConfig
    Write-Host "  environment  $($c.venv)"
    Write-Host "  models       $($c.models)"
}
Write-Host "  state        $StationState   (ticket, token, certificate, log)"
Write-Host "  settings     $StationConfig"
