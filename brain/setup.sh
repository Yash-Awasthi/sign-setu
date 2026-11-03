#!/usr/bin/env bash
# Sets up the station brain on a Linux computer (docs/18-station.md): the Python environment, the models, Ollama's
# model when Ollama is installed, and with --service a systemd user service that runs it from boot, restarts it if it
# stops and logs to the journal; it starts at once.
# --asr is the speech model the department chose: a faster-whisper size (small, medium, large-v3) or a model folder.
# --relay is the department's iroh relay (docs/16-relay.md), for phones off the station network.
# --isl is the folder for the station's sign reader (default: isl/ here; put a reader's .json and .bin in it).
# --inbox is where conversations from phones are saved (default: Sign Setu in Documents).
# --no-models skips the downloads, to change settings or the service only.
# Run it again at any time with other settings; it replaces the earlier ones. Needs uv (https://docs.astral.sh/uv/).
# Afterwards: ./status.sh shows whether it runs and how to pair; ./stop.sh stops it; ./start.sh --service starts the
# service again; ./start.sh alone runs it in this terminal; ./remove.sh takes the service away.
#
#   ./setup.sh --asr MODEL [--relay URL] [--service] [--venv DIR] [--models DIR] [--translator indictrans2|nllb|both]
#              [--llm gemma3:1b] [--isl DIR] [--inbox DIR] [--no-models]
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=station.sh
. "$here/station.sh"
venv="$here/.venv"; models="${XDG_DATA_HOME:-$HOME/.local/share}/signsetu-brain/models"
asr=; translator=both; llm=gemma3:1b; relay=; isl=; inbox=; service=0; fetch=1
while [ $# -gt 0 ]; do
  case "$1" in
    --venv) venv="${2:?--venv needs a value}"; shift 2 ;;
    --models) models="${2:?--models needs a value}"; shift 2 ;;
    --asr) asr="${2:?--asr needs a value}"; shift 2 ;;
    --translator) translator="${2:?--translator needs a value}"; shift 2 ;;
    --llm) llm="${2:?--llm needs a value}"; shift 2 ;;
    --relay) relay="${2:?--relay needs a value}"; shift 2 ;;
    --isl) isl="${2:?--isl needs a value}"; shift 2 ;;
    --inbox) inbox="${2:?--inbox needs a value}"; shift 2 ;;
    --service) service=1; shift ;;
    --no-models) fetch=0; shift ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
done
[ -n "$asr" ] || { echo "--asr is required: the speech model, e.g. --asr small or --asr large-v3" >&2; exit 2; }
case "$translator" in indictrans2|nllb|both) ;; *) echo "--translator is indictrans2, nllb or both" >&2; exit 2 ;; esac
case "$relay" in ""|http://?*|https://?*) ;; *) echo "--relay is a URL such as https://relay.example.gov.in" >&2; exit 2 ;; esac
command -v uv >/dev/null || { echo "uv is not installed: curl -LsSf https://astral.sh/uv/install.sh | sh" >&2; exit 1; }

echo "1/4 Python environment in $venv"
[ -x "$venv/bin/python" ] || uv venv --python 3.12 "$venv"
uv pip install --python "$venv/bin/python" --index-strategy unsafe-best-match \
  --extra-index-url https://download.pytorch.org/whl/cpu -r "$here/requirements.txt"

if [ "$fetch" = 1 ]; then
  echo "2/4 Speech and translation models in $models (several GB, once)"
  mkdir -p "$models"
  ( cd "$here" && HF_HOME="$models" "$venv/bin/python" -s -m brain.fetch --asr "$asr" --translator "$translator" ) \
    || echo "warning: some models failed to download; IndicTrans2 needs a Hugging Face login with its licence accepted"
else
  echo "2/4 Models: skipped (--no-models)"
fi

# Settings start.sh reads, so the service and a hand start run the same brain.
printf 'VENV=%q\nMODELS=%q\nASR=%q\nLLM=%q\nRELAY=%q\nISL=%q\nINBOX=%q\n' "$venv" "$models" "$asr" "$llm" "$relay" "$isl" "$inbox" > "$STATION_CONFIG"
mkdir -p "${isl:-$here/isl}"

if [ "$fetch" = 1 ]; then
  echo "3/4 Local language model"
  if command -v ollama >/dev/null; then ollama pull "$llm"
  else echo "warning: Ollama is not installed: AI help and sign-order refinement stay off (https://ollama.com, then: ollama pull $llm)"; fi
else
  echo "3/4 Local language model: skipped (--no-models)"
fi

if [ "$service" = 1 ]; then
  echo "4/4 systemd user service $STATION_UNIT"
  station_stop quiet
  mkdir -p "$(dirname "$STATION_UNIT_FILE")"
  cat > "$STATION_UNIT_FILE" <<EOF
[Unit]
Description=Sign Setu station brain
After=network-online.target

[Service]
ExecStart="$here/start.sh"
Restart=always
RestartSec=10

[Install]
WantedBy=default.target
EOF
  systemctl --user daemon-reload
  systemctl --user enable --now "$STATION_UNIT"
  # Keeps the user's services running from boot with nobody signed in.
  loginctl enable-linger "$USER" 2>/dev/null || echo "note: 'sudo loginctl enable-linger $USER' lets it start at boot with nobody signed in"
  echo "Done. The brain is starting; in a minute ./status.sh shows it and how to pair a phone."
else
  echo "4/4 No service (add --service for one)"
  echo "Done. Run it with ./start.sh (in this terminal), or set up the service with --service."
fi
echo "Phones on the station network need TCP 8000 open (for example: sudo ufw allow from 192.168.0.0/16 to any port 8000 proto tcp); iroh needs no port."
