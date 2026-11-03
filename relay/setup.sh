#!/usr/bin/env bash
# Sets up the department's iroh relay on a Linux server, once (docs/16-relay.md). Run as root:
#   1. the iroh-relay release binary, checked against its pinned SHA-256, into /usr/local/bin,
#   2. a system user iroh-relay, and /etc/iroh-relay/relay.toml from this folder's template with --hostname and --contact,
#   3. the systemd unit (iroh-relay.service here), enabled and started; it restarts the relay if it stops,
#   4. ufw rules for TCP 443, UDP 7842 and TCP 80, when ufw is active.
#
#   sudo ./setup.sh --hostname relay.example.gov.in --contact it-cell@example.gov.in [--tar FILE]
#   sudo ./setup.sh --remove     # stops it and removes the unit and binary; config and certificate stay
# --tar takes the release archive already downloaded, for a server that cannot reach GitHub.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
version=v1.2.0
sha256=de0093dbcb51b9dc17cd54fc31a60d27600535ae86617354f2fe0594e593abe5
hostname=; contact=; tar=; remove=0
while [ $# -gt 0 ]; do
  case "$1" in
    --hostname) hostname="${2:?--hostname needs a value}"; shift 2 ;;
    --contact) contact="${2:?--contact needs a value}"; shift 2 ;;
    --tar) tar="${2:?--tar needs a value}"; shift 2 ;;
    --remove) remove=1; shift ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
done
[ "$(id -u)" = 0 ] || { echo "Run this as root (sudo)." >&2; exit 1; }

if [ "$remove" = 1 ]; then
  systemctl disable --now iroh-relay 2>/dev/null || true
  rm -f /etc/systemd/system/iroh-relay.service /usr/local/bin/iroh-relay
  systemctl daemon-reload
  echo "Removed the relay service and binary. /etc/iroh-relay and /var/lib/iroh-relay (certificate) stay."
  exit 0
fi
[ -n "$hostname" ] && [ -n "$contact" ] || { echo "--hostname and --contact are required" >&2; exit 2; }
# Both go into relay.toml as strings; anything outside these shapes would break it or the sed below.
[[ "$hostname" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$ ]] || { echo "--hostname is a DNS name such as relay.example.gov.in" >&2; exit 2; }
[[ "$contact" =~ ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+$ ]] || { echo "--contact is an email address" >&2; exit 2; }

echo "1/4 iroh-relay $version"
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
if [ -z "$tar" ]; then
  tar="$work/iroh-relay.tar.gz"
  curl -fsSL -o "$tar" "https://github.com/n0-computer/iroh/releases/download/$version/iroh-relay-$version-x86_64-unknown-linux-musl.tar.gz"
fi
echo "$sha256  $tar" | sha256sum -c --quiet || { echo "$tar is not the iroh-relay $version release (SHA-256 differs)." >&2; exit 1; }
tar -xzf "$tar" -C "$work"
systemctl stop iroh-relay 2>/dev/null || true
install -m 755 "$work/iroh-relay" /usr/local/bin/iroh-relay
/usr/local/bin/iroh-relay --version

echo "2/4 /etc/iroh-relay/relay.toml"
id iroh-relay >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin iroh-relay
install -d /etc/iroh-relay
sed -e "s|^hostname = .*|hostname = \"$hostname\"|" -e "s|^contact = .*|contact = \"$contact\"|" "$here/relay.toml" > /etc/iroh-relay/relay.toml
chmod 644 /etc/iroh-relay/relay.toml

echo "3/4 systemd unit iroh-relay"
install -m 644 "$here/iroh-relay.service" /etc/systemd/system/iroh-relay.service
systemctl daemon-reload
systemctl enable --now iroh-relay

echo "4/4 Firewall"
if command -v ufw >/dev/null && ufw status | grep -q "Status: active"; then
  ufw allow 443/tcp >/dev/null; ufw allow 7842/udp >/dev/null; ufw allow 80/tcp >/dev/null
  echo "ufw: opened TCP 443, UDP 7842, TCP 80"
else
  echo "ufw is not active: open TCP 443, UDP 7842 and TCP 80 in this server's firewall and the network's."
fi
echo "Done. Within a minute 'journalctl -u iroh-relay' shows 'download certificate'; then https://$hostname/ping answers 200."
echo "Point each station at it: brain/setup.sh --asr MODEL --relay https://$hostname --service --no-models"
