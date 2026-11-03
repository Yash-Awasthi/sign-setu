#!/usr/bin/env bash
# Shows whether the station brain runs, what it offers phones (the heartbeat's capabilities), how to pair a phone, and
# the end of its log.  ./status.sh [LINES]
set -uo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=station.sh
. "$here/station.sh"
lines="${1:-20}"

if station_has_unit; then echo "Service:  $STATION_UNIT, $(systemctl --user is-active "$STATION_UNIT" 2>/dev/null)"
else echo "Service:  none (setup.sh --service makes one)"; fi
pids="$(station_pids)"
if [ -n "$pids" ]; then echo "Brain:    running (process $(echo "$pids" | tail -1))"; else echo "Brain:    not running"; fi
if [ -f "$STATION_CONFIG" ]; then
  # shellcheck disable=SC1090
  . "$STATION_CONFIG"
  dir="${ISL:-$here/isl}"
  reader="$(ls "$dir"/*.json 2>/dev/null | head -1)"
  echo "Speech:   $ASR"
  echo "Relay:    ${RELAY:-none: phones reach it only by a direct path}"
  if [ -n "$reader" ]; then echo "Reader:   $(basename "$reader" .json) in $dir"; else echo "Reader:   none in $dir (phones read signs themselves)"; fi
else
  echo "Settings: none (run ./setup.sh --asr MODEL)"
fi

if [ -n "$pids" ] && [ -f "$STATION_STATE/token" ]; then
  token="$(tr -d '\r\n' < "$STATION_STATE/token")"
  # The brain's certificate is its own, pinned by phones; this local check skips verifying it. The header goes on
  # stdin, so the token never shows in the process list.
  out="$(printf 'Authorization: Bearer %s\n' "$token" | curl -sk --max-time 5 -H @- https://127.0.0.1:8000/v1/status 2>/dev/null)"
  if [ -n "$out" ]; then
    echo "Offers:   $("${VENV:-/usr}/bin/python3" -c 'import json,sys; c=json.load(sys.stdin)["caps"]; on=[k for k,v in c.items() if v]; off=[k for k,v in c.items() if not v]; print(", ".join(on) + ("   (not: " + ", ".join(off) + ")" if off else ""))' <<<"$out")"
  else
    echo "Offers:   not answering yet on port 8000 (it may still be starting)"
  fi
fi
inbox="${INBOX:-}"
[ -n "$inbox" ] || inbox="$(journalctl --user -u "$STATION_UNIT" --no-pager 2>/dev/null | grep -o 'Conversations from phones are saved in .*' | tail -1 | sed 's/^Conversations from phones are saved in //')"
[ -n "$inbox" ] && echo "Inbox:    $inbox"
[ -f "$STATION_STATE/pairing.txt" ] && { echo; cat "$STATION_STATE/pairing.txt"; }
if station_has_unit; then echo; echo "Last $lines lines of the log"; journalctl --user -u "$STATION_UNIT" -n "$lines" --no-pager 2>/dev/null; fi
