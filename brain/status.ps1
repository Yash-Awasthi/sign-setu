<#
Shows whether the station brain runs, what it offers phones (the heartbeat's capabilities), how to pair a phone, and
the end of its log.  .\status.ps1 [-Lines 20]
#>
param([int]$Lines = 20)
$ErrorActionPreference = "Continue"
. (Join-Path $PSScriptRoot "station.ps1")

$task = Get-ScheduledTask -TaskName $StationTask -ErrorAction SilentlyContinue
Write-Host ("Service:  " + $(if ($task) { "$StationTask, $($task.State)" } else { "none (setup.ps1 -Service makes one)" }))
$procs = @(Get-BrainProcess)
Write-Host ("Brain:    " + $(if ($procs.Count) { "running (process $($procs[-1].ProcessId))" } else { "not running" }))
if (Test-Path $StationConfig) {
    $c = Get-StationConfig
    $isl = if ($c.isl) { $c.isl } else { Join-Path $PSScriptRoot "isl" }
    $reader = @(Get-ChildItem -Path $isl -File -ErrorAction SilentlyContinue | Where-Object { $_.Extension -in ".bin", ".tflite", ".onnx" })
    Write-Host "Speech:   $($c.asr)"
    Write-Host ("Relay:    " + $(if ($c.relay) { $c.relay } else { "none: phones reach it only by a direct path" }))
    Write-Host ("Reader:   " + $(if ($reader.Count) { "$(($reader | ForEach-Object Name) -join ", ") in $isl" } else { "none in $isl (phones read signs themselves)" }))
} else { Write-Host "Settings: none (run .\setup.ps1 -Asr <model>)" }

$tokenFile = Join-Path $StationState "token"
if ($procs.Count -and (Test-Path $tokenFile)) {
    $token = (Get-Content $tokenFile -Raw).Trim()
    # The brain's certificate is its own, pinned by phones; this local check skips verifying it. The header goes in a
    # BOM-free file, not the process list; piped to curl, PowerShell may prefix a BOM that breaks the header.
    $hdr = [IO.Path]::GetTempFileName()
    try {
        [IO.File]::WriteAllText($hdr, "Authorization: Bearer $token`n", (New-Object Text.UTF8Encoding $false))
        $out = & curl.exe -sk --max-time 5 -H "@$hdr" https://127.0.0.1:8000/v1/status 2>$null
    } finally { Remove-Item -Force $hdr -ErrorAction SilentlyContinue }
    if ($LASTEXITCODE -eq 0 -and $out) {
        $caps = ($out | ConvertFrom-Json).caps
        $on = @($caps.PSObject.Properties | Where-Object { $_.Value } | ForEach-Object { $_.Name })
        $off = @($caps.PSObject.Properties | Where-Object { -not $_.Value } | ForEach-Object { $_.Name })
        Write-Host "Offers:   $($on -join ', ')$(if ($off.Count) { "   (not: $($off -join ', '))" })"
    } else { Write-Host "Offers:   not answering yet on port 8000 (it may still be starting)" }
}
# Where received conversations go, as the brain said when it started.
$inbox = if ($c -and $c.inbox) { $null } elseif (Test-Path $StationLog) { Select-String -Path $StationLog -Pattern "Conversations from phones are saved in (.*)" | Select-Object -Last 1 } else { $null }
if ($c -and $c.inbox) { Write-Host "Inbox:    $($c.inbox)" } elseif ($inbox) { Write-Host "Inbox:    $($inbox.Matches[0].Groups[1].Value)" }
$pairing = Join-Path $StationState "pairing.txt"
if (Test-Path $pairing) { Write-Host ""; Get-Content $pairing | Write-Host }
if (Test-Path $StationLog) { Write-Host ""; Write-Host "Last $Lines lines of $StationLog"; Get-Content $StationLog -Tail $Lines | Write-Host }
