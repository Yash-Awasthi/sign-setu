# 16. The department's iroh relay

Phones reach the station brain over iroh from any network (`brain/brain/link.py`, `app/.../net/IrohBrain.kt`). Where no direct path can be punched through the two networks' NATs (mobile data, CGNAT, strict firewalls), the traffic goes through a relay. The relay is the department's own: neither the brain nor the phone ever contacts a third-party relay or address lookup.

The relay forwards QUIC packets that are end-to-end encrypted to the brain's key. It sees the two IP addresses, the brain's public key, timing and byte counts, never content. A fake relay fails, because the phone checks proof of the brain's key. It is open to any endpoint; the brain's pairing token gates every request.

## 1. What it needs

- One Windows or Linux server with a public IP address: 1 vCPU and 512 MB of RAM are enough for a district. One relay can serve every station.
- A DNS name for it, for example `relay.example.gov.in`.
- Inbound TCP 443 (relay and certificate issuance) and UDP 7842 (QUIC address discovery, which helps hole punching). TCP 80 is optional: it answers phones' captive-portal check.
- Outbound HTTPS to Let's Encrypt, for the certificate. iroh trusts only the public web roots (Mozilla's list) for relays, so a certificate from a private or department CA is refused; Let's Encrypt is used for that reason.

## 2. Install

From a checkout of this repository on the server. The hostname must be a DNS name and the contact an email address; the scripts refuse anything else, since both go into `relay.toml`. The relay's version should match the `iroh` version pinned in `brain/requirements.txt` and `gradle/libs.versions.toml`.

### Windows

In PowerShell as administrator:

```powershell
.\relay\setup.ps1 -Hostname relay.example.gov.in -Contact it-cell@example.gov.in
```

It downloads the iroh-relay release, refuses it unless its SHA-256 matches the one pinned in the script, writes `relay.toml` from `relay/relay.toml`, adds inbound firewall rules for the relay program (TCP 443, UDP 7842, TCP 80), and registers the scheduled task `SignSetuRelay`. The task runs the relay as SYSTEM from boot and restarts it within 5 seconds if it stops. Everything lives in `C:\ProgramData\SignSetuRelay` (`-Dir` to change), with the log in `relay.log`. On a server that cannot reach GitHub, download the zip elsewhere and pass `-Zip <file>`. Running the script again upgrades in place; `.\relay\setup.ps1 -Remove` stops the relay and removes the task and rules.

### Linux

As root, from this repository: `sudo ./relay/setup.sh --hostname relay.example.gov.in --contact it-cell@example.gov.in`
(`--tar <file>` for a server that cannot reach GitHub; `--remove` to take it away). It does the steps below: the
binary checked against its SHA-256, the system user, `relay.toml`, the systemd unit, and ufw when it is active. By
hand:

```sh
V=v1.2.0
curl -LO https://github.com/n0-computer/iroh/releases/download/$V/iroh-relay-$V-x86_64-unknown-linux-musl.tar.gz
echo "de0093dbcb51b9dc17cd54fc31a60d27600535ae86617354f2fe0594e593abe5  iroh-relay-$V-x86_64-unknown-linux-musl.tar.gz" | sha256sum -c
tar xzf iroh-relay-$V-x86_64-unknown-linux-musl.tar.gz
sudo install -m 755 iroh-relay /usr/local/bin/iroh-relay
iroh-relay --version

sudo useradd --system --no-create-home --shell /usr/sbin/nologin iroh-relay
sudo install -d /etc/iroh-relay
sudo install -m 644 relay/relay.toml /etc/iroh-relay/relay.toml
sudoedit /etc/iroh-relay/relay.toml        # set hostname and contact
sudo install -m 644 relay/iroh-relay.service /etc/systemd/system/iroh-relay.service
sudo systemctl daemon-reload
sudo systemctl enable --now iroh-relay
```

Open the ports, for example with ufw:

```sh
sudo ufw allow 443/tcp
sudo ufw allow 7842/udp
sudo ufw allow 80/tcp
```

## 3. Check it

```sh
journalctl -u iroh-relay -f       # Linux: "download certificate" within a minute of the first start
curl -s -o /dev/null -w "%{http_code}\n" https://relay.example.gov.in/ping            # 200
curl -s -o /dev/null -w "%{http_code}\n" http://relay.example.gov.in/generate_204     # 204
```

On Windows the same line appears in `C:\ProgramData\SignSetuRelay\relay.log`, and the checks run in PowerShell as `curl.exe` with the same arguments. The certificate is kept in the certificate folder (`/var/lib/iroh-relay`, or `certs` under `-Dir`) and renewed by the relay itself.

## 4. Point the brain at it

```powershell
.\setup.ps1 -Asr <model> -Relay https://relay.example.gov.in -Service     # Windows; saved in brain.json
```
```sh
./setup.sh --asr <model> --relay https://relay.example.gov.in --service    # Linux; saved in brain.env
```

A hand start takes `--relay URL` or the `BRAIN_RELAY` environment variable. The brain prints a ticket that names the relay; phones paired with an older ticket must be paired again with the new one. The phone needs no setting: it uses the relay its ticket names. If a ticket names an n0 relay (a host under `iroh.link`), or a relay address that cannot be read, the phone uses no relay and dials direct only.

Without a relay the brain still binds iroh, and phones reach it only where a direct path works. If the relay is down when the brain starts, the brain waits 10 seconds, prints the ticket anyway and connects to the relay once it is back.

## 5. On the phone

- The phone keeps one iroh key for the install, encrypted with an Android Keystore key and stored in the app's no-backup folder. Its endpoint ID stays the same across launches, but a reinstall or a restore from backup makes a new one. A temporary Keystore error at boot does not reset it.
- The connection and endpoint are dropped, and dialled again when next needed, after a network change, when the station link is turned off, and when the app goes to the background.
- Timeouts: connecting 15 seconds; the heartbeat counts the station gone after 12 seconds without a beat and retries after 2 seconds, then at most every 10 seconds; checking a pairing takes up to 20 seconds over iroh and 5 on the station network.
- A ticket pasted with line breaks (for example copied from a wrapped terminal) is accepted.

## 6. Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| No "download certificate" line in the first minute | The name does not resolve to this server, TCP 443 is not open from the internet, or the server cannot reach Let's Encrypt. Fix DNS or the firewall and restart the relay |
| `/ping` fails with a certificate error | A private CA certificate: iroh trusts only public roots, so use Let's Encrypt |
| `setup` refuses the archive | Its SHA-256 differs from the pinned one; download the release for the pinned version again |
| Setup refuses the hostname or contact | Hostname: letters, digits, dots and dashes. Contact: an email address |
| Brain logs `iroh: relay ... not reachable yet` | The relay is down or the URL is wrong; the brain keeps going and joins the relay when it is back |
| Phone connects on the station Wi-Fi but not on mobile data | The brain has no `--relay`, the relay is down, or the phone holds an old ticket without the relay; set the relay, restart the brain, pair again with the new ticket |
| Hole punching never succeeds | UDP is blocked at the station; the relay still carries the traffic, and UDP 7842 helps discovery |
| Windows relay log is hard to read | `relay.log` is written as UTF-16 and is rotated only when the relay restarts |
| The relay version differs from the apps | Install the version pinned in `brain/requirements.txt` and `gradle/libs.versions.toml` |

## 7. How this was tested

On one computer, with the iroh-relay 1.2.0 release binary:

- The brain (`python -m brain --asr small --relay http://localhost:3340`) behind a relay in `--dev` mode: a client dialing the ticket with no direct address went through the relay, `/healthz` and `/v1/status` answered 200 with the token and 401 without, and the brain held no TCP connection outside the local network. The path then moved from the relay to a direct one, as iroh does after hole punching.
- Direct only (no relay), and a relay that is down at start: both give a ticket; the first connects directly.
- `relay.toml` as shipped, with only the hostname, ports and certificate directory changed, against Let's Encrypt's test server (pebble): the relay obtained its certificate by TLS-ALPN-01 and served `/ping` over HTTPS with a certificate that verifies.
- `relay/setup.ps1` on Windows 11: install, firewall rules, the SYSTEM task, a certificate from pebble on port 443, `/ping` 200 and `/generate_204` 204, the relay back within seconds after it was killed, a second run over the first, a wrong archive refused, and `-Remove`. Without the test server the task reached Let's Encrypt itself, which refused only because the name was `localhost`.

Not tested here: the systemd unit and ufw on a Linux server, a real Let's Encrypt certificate (it needs a public DNS name), a reboot, and the app on a phone (it builds; the Kotlin calls are the same bindings as the Python ones tested).
