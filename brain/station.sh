# Names and helpers the station scripts share (setup, start, stop, status, remove); sourced, never run alone.
# shellcheck shell=bash
STATION_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATION_CONFIG="$STATION_DIR/brain.env"
STATION_UNIT=signsetu-brain
STATION_UNIT_FILE="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user/$STATION_UNIT.service"
STATION_STATE="${BRAIN_STATE:-$HOME/.signsetu-brain}"

station_config() {
  [ -f "$STATION_CONFIG" ] || { echo "Not set up yet: run ./setup.sh --asr MODEL first." >&2; exit 1; }
  # shellcheck disable=SC1090
  . "$STATION_CONFIG"
}

station_has_unit() { [ -f "$STATION_UNIT_FILE" ]; }

# PIDs of this user's brain processes; the pattern needs "-m brain" as a whole word, so brain.fetch is not one.
station_pids() { pgrep -u "$(id -u)" -f -- '-m brain( |$)' || true; }

station_stop() {
  local was=0
  # The service first, or systemd would start the brain again.
  if station_has_unit && systemctl --user is-active --quiet "$STATION_UNIT"; then systemctl --user stop "$STATION_UNIT"; was=1; fi
  local pids; pids="$(station_pids)"
  if [ -n "$pids" ]; then
    # shellcheck disable=SC2086
    kill $pids 2>/dev/null || true
    sleep 2
    # shellcheck disable=SC2086
    kill -9 $pids 2>/dev/null || true
    was=1
  fi
  if [ "${1:-}" != quiet ]; then
    if [ "$was" = 1 ]; then echo "Stopped the brain."; else echo "The brain was not running."; fi
  fi
}
