<#
Sets up the department's iroh relay on a Windows server, once (docs/16-relay.md). Run as administrator:
  1. the iroh-relay release binary, checked against its pinned SHA-256, into -Dir,
  2. relay.toml from this folder's template (or the existing one), with -Hostname, -Contact and the certificate folder,
  3. inbound firewall rules: TCP 443, UDP 7842 and TCP 80, for the relay program only,
  4. a scheduled task SignSetuRelay that runs it as SYSTEM from boot and restarts it if it stops.

  .\setup.ps1 -Hostname relay.example.gov.in -Contact it-cell@example.gov.in [-Dir C:\ProgramData\SignSetuRelay] [-Zip <file>]
-Zip takes the release zip already downloaded, for a server that cannot reach GitHub.
  .\setup.ps1 -Remove      # stops the relay and removes the task and firewall rules; -Dir is left in place
#>
param(
    [Parameter(Mandatory, ParameterSetName = "Install")][ValidatePattern("^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$")][string]$Hostname,
    [Parameter(Mandatory, ParameterSetName = "Install")][ValidatePattern("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+$")][string]$Contact,
    [string]$Dir = (Join-Path $env:ProgramData "SignSetuRelay"),
    [Parameter(ParameterSetName = "Install")][string]$Zip = "",
    [Parameter(Mandatory, ParameterSetName = "Remove")][switch]$Remove
)
$ErrorActionPreference = "Stop"
$Version = "v1.2.0"
$Sha256 = "31df0f028453a12abe4b55f9ab50580d5b00d5e5ab3596d0371c6f808e383f90"
$Task = "SignSetuRelay"

$me = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $me.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw "Run this as administrator." }
$exe = Join-Path $Dir "iroh-relay.exe"

function Stop-Relay {
    # The task must be stopped first, or its loop starts the relay again.
    if (Get-ScheduledTask -TaskName $Task -ErrorAction SilentlyContinue) { Stop-ScheduledTask -TaskName $Task }
    Get-Process iroh-relay -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $exe } | Stop-Process -Force
}

if ($Remove) {
    Stop-Relay
    Unregister-ScheduledTask -TaskName $Task -Confirm:$false -ErrorAction SilentlyContinue
    Get-NetFirewallRule -Group $Task -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    Write-Host "Removed the task and firewall rules. Binary, config, certificate and log stay in $Dir."
    return
}
New-Item -ItemType Directory -Force -Path $Dir, (Join-Path $Dir "certs") | Out-Null
# ProgramData lets any user add files here, and SYSTEM runs what is in it: only administrators and SYSTEM may hold it.
icacls $Dir /setowner *S-1-5-32-544 /T /C /Q | Out-Null; if ($LASTEXITCODE) { throw "could not lock down $Dir" }
icacls $Dir /reset /T /C /Q | Out-Null; if ($LASTEXITCODE) { throw "could not lock down $Dir" }
icacls $Dir /inheritance:r /grant:r "*S-1-5-18:(OI)(CI)F" "*S-1-5-32-544:(OI)(CI)F" /C /Q | Out-Null; if ($LASTEXITCODE) { throw "could not lock down $Dir" }
$config = Join-Path $Dir "relay.toml"

Write-Host "1/4 iroh-relay $Version in $Dir"
$local = Join-Path $Dir "iroh-relay-$Version.zip"
if (-not $Zip) {
    $Zip = $local
    $url = "https://github.com/n0-computer/iroh/releases/download/$Version/iroh-relay-$Version-x86_64-pc-windows-msvc.zip"
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $Zip
}
# Hashed and unpacked from the locked folder, so the checked file is the one unpacked.
if ((Resolve-Path $Zip).Path -ne $local) { Copy-Item -Force $Zip $local }
$Zip = $local
if ((Get-FileHash -Algorithm SHA256 $Zip).Hash -ne $Sha256) { throw "$Zip is not the iroh-relay $Version release (SHA-256 differs)." }
Stop-Relay
Expand-Archive -Force -Path $Zip -DestinationPath $Dir
& $exe --version

Write-Host "2/4 $config"
# A re-run edits the existing file, so an [access] allowlist and other changes stay.
$src = if (Test-Path $config) { $config } else { Join-Path $PSScriptRoot "relay.toml" }
$toml = (Get-Content -Raw $src) `
    -replace '(?m)^hostname = .*$', "hostname = `"$Hostname`"" `
    -replace '(?m)^contact = .*$', "contact = `"$Contact`"" `
    -replace '(?m)^cert_dir = .*$', "cert_dir = '$(Join-Path $Dir "certs")'"
# TOML rejects a byte-order mark, which Set-Content would write in UTF-8.
[IO.File]::WriteAllText($config, $toml, (New-Object Text.UTF8Encoding $false))

Write-Host "3/4 Firewall rules"
Get-NetFirewallRule -Group $Task -ErrorAction SilentlyContinue | Remove-NetFirewallRule
foreach ($r in @(@("TCP", 443, "relay and certificate"), @("UDP", 7842, "QUIC address discovery"), @("TCP", 80, "captive-portal check"))) {
    New-NetFirewallRule -Group $Task -DisplayName "Sign Setu relay $($r[0]) $($r[1]) ($($r[2]))" -Direction Inbound -Action Allow `
        -Protocol $r[0] -LocalPort $r[1] -Program $exe -Profile Any | Out-Null
}

Write-Host "4/4 Scheduled task $Task (SYSTEM, from boot)"
# iroh-relay is not a Windows service, so a loop keeps it running and its log beside it.
$run = Join-Path $Dir "run.ps1"
$log = Join-Path $Dir "relay.log"
@"
`$env:RUST_LOG = "info"
while (`$true) {
    if ((Test-Path "$log") -and (Get-Item "$log").Length -gt 10MB) { Move-Item -Force "$log" "$log.old" }
    & "$exe" --config-path "$config" *>> "$log"
    Start-Sleep -Seconds 5
}
"@ | Set-Content -Encoding utf8 $run
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$run`""
$trigger = New-ScheduledTaskTrigger -AtStartup
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1)
$principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
Register-ScheduledTask -TaskName $Task -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Force | Out-Null
Start-ScheduledTask -TaskName $Task

Write-Host "Done. The relay asks Let's Encrypt for its certificate now; within a minute $log shows 'download certificate'."
Write-Host "Check: https://$Hostname/ping answers 200. Then point the brain at it: brain\setup.ps1 -Asr <model> -Relay https://$Hostname"
