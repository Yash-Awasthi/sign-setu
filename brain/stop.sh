#!/usr/bin/env bash
# Stops the station brain, whether the service or a hand start runs it. The service stays set up, so it starts again
# at the next boot; ./start.sh --service starts it now, ./remove.sh takes it away.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=station.sh
. "$here/station.sh"
station_stop
