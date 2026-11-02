<#
Sets up the station brain on a Windows computer, once:
  1. a Python 3.12 environment with the pinned packages (requirements.txt),
  2. the speech and translation models, downloaded into -Models,
  3. Ollama's local language model, when Ollama is installed,
  4. with -Service, a scheduled task that starts the brain at every boot,
  5. with -Firewall (run as administrator), an inbound rule for port 8000 on private networks.
Afterwards start.ps1 runs the brain and prints how to pair a phone. Needs uv (https://docs.astral.sh/uv/).

  .\setup.ps1 [-Venv .venv] [-Models <folder>] [-Asr small] [-Translator both] [-Llm gemma3:1b] [-Service] [-Firewall]
#>
param(
    [string]$Venv = (Join-Path $PSScriptRoot ".venv"),
    [string]$Models = (Join-Path $env:LOCALAPPDATA "signsetu-brain\models"),
    [string]$Asr = "small",
    [ValidateSet("indictrans2", "nllb", "both")][string]$Translator = "both",
    [string]$Llm = "gemma3:1b",
    [switch]$Service,
    [switch]$Firewall
)
# Native tools write progress to stderr, which "Stop" would treat as failure; exit codes are checked instead.
$ErrorActionPreference = "Continue"

if (-not (Get-Command uv -ErrorAction SilentlyContinue)) {
    throw "uv is not installed. Install it with: winget install astral-sh.uv   (then open a new PowerShell)"
}
$python = Join-Path $Venv "Scripts\python.exe"

Write-Host "1/5 Python environment in $Venv"
if (-not (Test-Path $python)) { uv venv --python 3.12 $Venv; if ($LASTEXITCODE) { throw "uv venv failed" } }
uv pip install --python $python --index-strategy unsafe-best-match --extra-index-url https://download.pytorch.org/whl/cpu -r (Join-Path $PSScriptRoot "requirements.txt")
if ($LASTEXITCODE) { throw "package install failed" }

Write-Host "2/5 Speech and translation models in $Models (several GB, once)"
New-Item -ItemType Directory -Force -Path $Models | Out-Null
$env:HF_HOME = $Models
Push-Location $PSScriptRoot
try { & $python -s -m brain.fetch --asr $Asr --translator $Translator } finally { Pop-Location }
if ($LASTEXITCODE) { Write-Warning "$LASTEXITCODE model(s) failed to download; see FAIL above. IndicTrans2 needs a Hugging Face login with its licence accepted; NLLB-200 serves without it." }

# Settings start.ps1 reads, so the task and a hand start run the same brain.
@{ venv = $Venv; models = $Models; asr = $Asr; llm = $Llm } | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot "brain.json")

Write-Host "3/5 Local language model"
$ollama = Get-Command ollama -ErrorAction SilentlyContinue
if (-not $ollama) { $ollama = Get-Command (Join-Path $env:LOCALAPPDATA "Programs\Ollama\ollama.exe") -ErrorAction SilentlyContinue }
if ($ollama) {
    # A pull with no server running starts one that would hold this console open; start it on its own first.
    try { Invoke-RestMethod -TimeoutSec 2 http://127.0.0.1:11434/api/tags | Out-Null }
    catch { Start-Process -WindowStyle Hidden -FilePath $ollama.Source -ArgumentList "serve"; Start-Sleep -Seconds 5 }
    Start-Process -NoNewWindow -Wait -FilePath $ollama.Source -ArgumentList "pull", $Llm
} else { Write-Warning "Ollama is not installed: sign order uses rules only and chat is off. Install it from https://ollama.com, then run: ollama pull $Llm" }

if ($Service) {
    Write-Host "4/5 Scheduled task SignSetuBrain (starts at boot)"
    $start = Join-Path $PSScriptRoot "start.ps1"
    $action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$start`""
    $trigger = New-ScheduledTaskTrigger -AtStartup
    $settings = New-ScheduledTaskSettingsSet -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero)
    Register-ScheduledTask -TaskName "SignSetuBrain" -Action $action -Trigger $trigger -Settings $settings -User $env:USERNAME -RunLevel Limited -Force | Out-Null
} else { Write-Host "4/5 No scheduled task (add -Service for one)" }

if ($Firewall) {
    Write-Host "5/5 Firewall: port 8000 on private networks only"
    New-NetFirewallRule -DisplayName "Sign Setu brain (LAN)" -Direction Inbound -Protocol TCP -LocalPort 8000 -Profile Private -Action Allow | Out-Null
} else { Write-Host "5/5 No firewall rule (add -Firewall as administrator for phones on the station network; iroh needs none)" }

Write-Host "Done. Start it with: .\start.ps1"
