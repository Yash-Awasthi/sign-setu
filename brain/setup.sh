#!/usr/bin/env bash
# Sets up the station brain on a Linux computer, once: the Python environment, the models, Ollama's model when Ollama
# is installed, and with --service a systemd user service that starts it at boot. Afterwards ./start.sh runs it.
# Needs uv (https://docs.astral.sh/uv/).
#
#   ./setup.sh [--venv DIR] [--models DIR] [--asr small] [--translator indictrans2|nllb|both] [--llm gemma3:1b] [--service]
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
venv="$here/.venv"; models="${XDG_DATA_HOME:-$HOME/.local/share}/signsetu-brain/models"
asr=small; translator=both; llm=gemma3:1b; service=0
while [ $# -gt 0 ]; do
  case "$1" in
    --venv) venv="$2"; shift 2 ;;
    --models) models="$2"; shift 2 ;;
    --asr) asr="$2"; shift 2 ;;
    --translator) translator="$2"; shift 2 ;;
    --llm) llm="$2"; shift 2 ;;
    --service) service=1; shift ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
done
command -v uv >/dev/null || { echo "uv is not installed: curl -LsSf https://astral.sh/uv/install.sh | sh" >&2; exit 1; }

echo "1/4 Python environment in $venv"
[ -x "$venv/bin/python" ] || uv venv --python 3.12 "$venv"
uv pip install --python "$venv/bin/python" --index-strategy unsafe-best-match \
  --extra-index-url https://download.pytorch.org/whl/cpu -r "$here/requirements.txt"

echo "2/4 Speech and translation models in $models (several GB, once)"
mkdir -p "$models"
( cd "$here" && HF_HOME="$models" "$venv/bin/python" -s -m brain.fetch --asr "$asr" --translator "$translator" ) \
  || echo "warning: some models failed to download; IndicTrans2 needs a Hugging Face login with its licence accepted"

# Settings start.sh reads, so the service and a hand start run the same brain.
printf 'VENV=%q\nMODELS=%q\nASR=%q\nLLM=%q\n' "$venv" "$models" "$asr" "$llm" > "$here/brain.env"

echo "3/4 Local language model"
if command -v ollama >/dev/null; then ollama pull "$llm"; else echo "warning: Ollama is not installed: sign order uses rules only and chat is off (https://ollama.com, then: ollama pull $llm)"; fi

if [ "$service" = 1 ]; then
  echo "4/4 systemd user service signsetu-brain"
  mkdir -p "$HOME/.config/systemd/user"
  cat > "$HOME/.config/systemd/user/signsetu-brain.service" <<EOF
[Unit]
Description=Sign Setu station brain
After=network-online.target

[Service]
ExecStart=$here/start.sh
Restart=on-failure
RestartSec=10

[Install]
WantedBy=default.target
EOF
  systemctl --user daemon-reload
  systemctl --user enable --now signsetu-brain
  # Keeps the user's services running with nobody logged in.
  loginctl enable-linger "$USER" || true
else
  echo "4/4 No service (add --service for one)"
fi
echo "Done. Start it with: ./start.sh   (the station network needs TCP 8000 open; iroh needs no port)"
