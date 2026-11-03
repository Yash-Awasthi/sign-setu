<#
Sets up the station brain on a Windows computer (docs/18-station.md):
  1. a Python 3.12 environment with the pinned packages (requirements.txt),
  2. the speech and translation models, downloaded into -Models,
  3. Ollama's local language model, when Ollama is installed,
  4. with -Service, a scheduled task that runs the brain from boot (or from sign-in, without administrator rights),
     restarts it if it stops, and logs to brain.log in the state folder; it starts at once,
  5. with -Firewall (run as administrator), inbound rules on private networks only: TCP 8000, and the brain's
     Python for iroh's direct path, so a phone on the station Wi-Fi needs no relay.
-Asr is the speech model the department chose: a faster-whisper size (small, medium, large-v3) or a model folder.
-Relay is the department's iroh relay (docs/16-relay.md), for phones off the station network.
-Isl is the folder for the station's sign reader (default: isl\ here; put a reader's .json and .bin in it).
-Inbox is where conversations from phones are saved (default: Sign Setu in Documents, OneDrive's first).
-NoModels skips steps 2 and 3, to change settings or the service without downloading again.
Run it again at any time with other settings; it replaces the earlier ones. Needs uv (https://docs.astral.sh/uv/).
Afterwards: .\status.ps1 shows whether it runs and how to pair; .\stop.ps1 stops it; .\start.ps1 -Service starts the
task again; .\start.ps1 alone runs it in this window; .\remove.ps1 takes the task and firewall rule away.

  .\setup.ps1 -Asr <model> [-Relay https://relay.example.gov.in] [-Service] [-Firewall] [-Venv .venv] [-Models <folder>]
              [-Translator both] [-Llm gemma3:1b] [-Isl <folder>] [-Inbox <folder>] [-NoModels]
#>
param(
    [Parameter(Mandatory)][string]$Asr,
    [string]$Venv = (Join-Path $PSScriptRoot ".venv"),
    [string]$Models = (Join-Path $env:LOCALAPPDATA "signsetu-brain\models"),
    [ValidateSet("indictrans2", "nllb", "both")][string]$Translator = "both",
    [string]$Llm = "gemma3:1b",
    [ValidatePattern("^(https?://\S+)?$")][string]$Relay = "",
    [string]$Isl = "",
    [string]$Inbox = "",
    [switch]$Service,
    [switch]$Firewall,
    [switch]$NoModels
)
# Native tools write progress to stderr, which "Stop" would treat as failure; exit codes are checked instead.
$ErrorActionPreference = "Continue"
. (Join-Path $PSScriptRoot "station.ps1")

if (-not (Get-Command uv -ErrorAction SilentlyContinue)) {
    throw "uv is not installed. Install it with: winget install astral-sh.uv   (then open a new PowerShell)"
}
$python = Join-Path $Venv "Scripts\python.exe"
$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

Write-Host "1/5 Python environment in $Venv"
if (-not (Test-Path $python)) { uv venv --python 3.12 $Venv; if ($LASTEXITCODE) { throw "uv venv failed" } }
uv pip install --python $python --index-strategy unsafe-best-match --extra-index-url https://download.pytorch.org/whl/cpu -r (Join-Path $PSScriptRoot "requirements.txt")
if ($LASTEXITCODE) { throw "package install failed" }

if ($NoModels) {
    Write-Host "2/5 Models: skipped (-NoModels)"
} else {
    Write-Host "2/5 Speech and translation models in $Models (several GB, once)"
    New-Item -ItemType Directory -Force -Path $Models | Out-Null
    $env:HF_HOME = $Models
    Push-Location $PSScriptRoot
    try { & $python -s -m brain.fetch --asr $Asr --translator $Translator } finally { Pop-Location }
    if ($LASTEXITCODE) { Write-Warning "$LASTEXITCODE model(s) failed to download; see FAIL above. IndicTrans2 needs a Hugging Face login with its licence accepted; NLLB-200 serves without it." }
}

# Settings start.ps1 reads, so the task and a hand start run the same brain.
@{ venv = $Venv; models = $Models; asr = $Asr; llm = $Llm; relay = $Relay; isl = $Isl; inbox = $Inbox } | ConvertTo-Json | Set-Content -Encoding utf8 $StationConfig
New-Item -ItemType Directory -Force -Path $(if ($Isl) { $Isl } else { Join-Path $PSScriptRoot "isl" }) | Out-Null

if ($NoModels) {
    Write-Host "3/5 Local language model: skipped (-NoModels)"
} else {
    Write-Host "3/5 Local language model"
    $ollama = Get-Command ollama -ErrorAction SilentlyContinue
    if (-not $ollama) { $ollama = Get-Command (Join-Path $env:LOCALAPPDATA "Programs\Ollama\ollama.exe") -ErrorAction SilentlyContinue }
    if ($ollama) {
        # A pull with no server running starts one that would hold this console open; start it on its own first.
        try { Invoke-RestMethod -TimeoutSec 2 http://127.0.0.1:11434/api/tags | Out-Null }
        catch { Start-Process -WindowStyle Hidden -FilePath $ollama.Source -ArgumentList "serve"; Start-Sleep -Seconds 5 }
        Start-Process -NoNewWindow -Wait -FilePath $ollama.Source -ArgumentList "pull", $Llm
    } else { Write-Warning "Ollama is not installed: AI help and sign-order refinement stay off. Install it from https://ollama.com, then run: ollama pull $Llm" }
}

if ($Service) {
    Stop-Station -Quiet
    $start = Join-Path $PSScriptRoot "start.ps1"
    $action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$start`" -Log"
    # Restarted within a minute whenever it stops; no time limit on how long it runs.
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew
    if ($admin) {
        # S4U runs it at boot under this account with nobody signed in, and stores no password.
        Write-Host "4/5 Scheduled task $StationTask (from boot, as $env:USERNAME)"
        $principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType S4U -RunLevel Limited
        $trigger = New-ScheduledTaskTrigger -AtStartup
    } else {
        Write-Host "4/5 Scheduled task $StationTask (from sign-in as $env:USERNAME; run this as administrator to start it at boot instead)"
        $principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType Interactive -RunLevel Limited
        $trigger = New-ScheduledTaskTrigger -AtLogOn -User "$env:USERDOMAIN\$env:USERNAME"
    }
    Register-ScheduledTask -TaskName $StationTask -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Force | Out-Null
    Start-ScheduledTask -TaskName $StationTask
} else { Write-Host "4/5 No scheduled task (add -Service for one)" }

if ($Firewall) {
    if (-not $admin) { Write-Warning "5/5 -Firewall needs administrator rights; no rule added" }
    else {
        Write-Host "5/5 Firewall: TCP 8000 and iroh's direct path, private networks only"
        Get-NetFirewallRule -DisplayName "$StationRule*" -ErrorAction SilentlyContinue | Remove-NetFirewallRule
        New-NetFirewallRule -DisplayName $StationRule -Direction Inbound -Protocol TCP -LocalPort 8000 -Profile Private -Action Allow | Out-Null
        # The venv's python.exe only launches the base interpreter, which is the process that holds iroh's UDP port.
        $base = (& $python -c "import sys; print(sys._base_executable)").Trim()
        New-NetFirewallRule -DisplayName "$StationRule, iroh" -Direction Inbound -Protocol UDP -Program $base -Profile Private -Action Allow | Out-Null
    }
} else { Write-Host "5/5 No firewall rule (add -Firewall as administrator for phones on the station network; iroh needs none)" }

if ($Service) {
    Write-Host "Done. The brain is starting; in a minute .\status.ps1 shows it and how to pair a phone."
} else {
    Write-Host "Done. Run it with .\start.ps1 (in this window), or set up the service with -Service."
}
