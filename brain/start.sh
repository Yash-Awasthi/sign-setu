#!/usr/bin/env bash
# Starts the station brain with the settings setup.sh saved, and prints the ticket, token and LAN address to pair
# phones. Extra arguments go to the brain, e.g.  ./start.sh --no-iroh   or   ./start.sh --rotate-token
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
[ -f "$here/brain.env" ] || { echo "Run setup.sh first." >&2; exit 1; }
# shellcheck disable=SC1091
. "$here/brain.env"
cd "$here"
HF_HOME="$MODELS" exec "$VENV/bin/python" -s -m brain --asr "$ASR" --llm "$LLM" "$@"
