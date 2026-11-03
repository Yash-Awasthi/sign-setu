#!/usr/bin/env bash
# Stops the station brain and takes away its systemd user service. The environment, the models, the sign reader, the
# state folder (certificate, token, iroh key: the ticket phones were paired with) and the inbox stay; the paths are
# printed, to delete by hand once nothing in them is needed.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=station.sh
. "$here/station.sh"
station_stop
if station_has_unit; then
  systemctl --user disable "$STATION_UNIT" 2>/dev/null || true
  rm -f "$STATION_UNIT_FILE"
  systemctl --user daemon-reload
  echo "Removed the service $STATION_UNIT."
fi
echo "Kept:"
if [ -f "$STATION_CONFIG" ]; then
  # shellcheck disable=SC1090
  . "$STATION_CONFIG"
  echo "  environment  $VENV"
  echo "  models       $MODELS"
fi
echo "  state        $STATION_STATE   (ticket, token, certificate)"
echo "  settings     $STATION_CONFIG"
