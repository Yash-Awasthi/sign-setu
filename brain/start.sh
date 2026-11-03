#!/usr/bin/env bash
# Runs the station brain with the settings setup.sh saved, in this terminal: it prints the ticket, token and LAN
# address to pair phones, and stops with Ctrl+C. Extra arguments go to the brain, e.g.  ./start.sh --rotate-token
#   ./start.sh --service   starts the systemd user service instead (setup.sh --service made it), in the background
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=station.sh
. "$here/station.sh"
station_config
if [ "${1:-}" = --service ]; then
  station_has_unit || { echo "No service: run ./setup.sh --asr $ASR --service --no-models" >&2; exit 1; }
  systemctl --user start "$STATION_UNIT"
  echo "Starting. In a minute ./status.sh shows it and how to pair a phone."
  exit 0
fi
# Started by systemd, the service is the brain; by hand, a second brain would fight the first for port 8000.
if [ -z "${INVOCATION_ID:-}" ] && [ -n "$(station_pids)" ]; then
  echo "The brain is already running (./status.sh). Stop it first with ./stop.sh." >&2; exit 1
fi
cd "$here"
export PYTHONUNBUFFERED=1
BRAIN_RELAY="${RELAY:-}" BRAIN_ISL="${ISL:-}" BRAIN_INBOX="${INBOX:-}" HF_HOME="$MODELS" exec "$VENV/bin/python" -s -m brain --asr "$ASR" --llm "$LLM" "$@"
