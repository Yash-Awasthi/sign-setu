# What the relay can read

This folder holds a recorded run of the department's relay on loopback, at the socket the
relay listens on, driven by the same iroh 1.2.0 client binding the brain and the phone use.

- `relay_proof.py` starts a TCP tee in front of the relay, binds two endpoints with the
  brain's and the phone's ALPN and keys, dials the brain's endpoint id with no direct
  address, and has the phone send a 3400-byte sentence and the brain answer.
- `capture.txt` is every byte the tee passed in both directions: one timestamp, direction,
  length line, then the bytes as hex, per TCP read. It is the relay's socket boundary.
- `fields.txt` decodes that capture: connections, HTTP headers, relay frames, sizes and the
  plaintext identifier check. `python relay_capture.py capture.txt > fields.txt`.
- `initial_hello.py` decrypts the QUIC Initial the phone sent with the RFC 9001 initial
  keys, which are public, and prints the one handshake field that yields: the ALPN.
- `relay.log.txt` is the relay's own debug log for the same run; it records the peer
  address, the endpoint id prefix and a connection id.
- `run.txt` is the harness output, including the path snapshot and the relay byte count.

## How to reproduce

The relay is the release binary `setup.ps1` installs, version 1.2.0. On Windows, in
PowerShell, with Python 3.14 and these packages (`pip install iroh==1.2.0 cryptography`):

```powershell
# terminal 1: the relay, plain HTTP, port 3340
$env:RUST_LOG = "debug"
& "$env:ProgramData\SignSetuRelay\iroh-relay.exe" --dev

# terminal 2: the harness, its tee on 3341, capture into this folder
python relay\proof\relay_proof.py 3340 3341 relay\proof\capture.txt
python relay\proof\relay_capture.py relay\proof\capture.txt > relay\proof\fields.txt
python relay\proof\initial_hello.py relay\proof\capture.txt > relay\proof\initial_hello.txt
```

`relay_proof.py` exits 0 after printing the two endpoint ids, the sentence received, the
reply received and the path snapshot; it exits non-zero if a step times out.
`relay_capture.py` exits 0 and prints the table below. `initial_hello.py` exits 0 when it
decrypted a ClientHello. The capture is from the run whose `fields.txt` sha256 is
`44d7e55b63b1bef2f74895c68beab23b72b6d17b6bae8fdb56e820a50c9de14f`.

The relay in `--dev` binds `[::]:3340`, so the tee forwards to `::1`; the endpoints dial the
tee at `127.0.0.1:3341`. The keys are fixed so the ids in `fields.txt` match this folder.

## Fields the relay reads

| Field | Where | In this capture |
| --- | --- | --- |
| Client address and port | TCP accept, relay log | `[::1]:51227` etc., one line per connection |
| Connection times and duration | TCP accept/close | c8: 00:45:29.173 to 00:45:30.204 |
| Request line and headers | HTTP upgrade, plaintext | `GET /relay HTTP/1.1`, `Host`, `Upgrade: websocket`, `Sec-WebSocket-Key`, `sec-websocket-protocol: iroh-relay-v2, iroh-relay-v1` |
| Bearer token, if configured | `Authorization` header | absent here; the relay does not see the brain's pairing token, which travels inside QUIC |
| Phone endpoint public key | 0x01 hello, then 32 B in every 0x04 frame | `da29e95b...69d0`, 12 times in the clear |
| Brain endpoint public key | 0x01 hello, then 32 B in every 0x04 frame | `79b5562e...9664`, 23 times in the clear |
| Datagram destination | 0x04 frame, 32 B before the QUIC packet | phone sends to the brain id, brain sends to the phone id |
| Datagram size and timing | every frame | 11 phone datagrams, 74..1453 B, 12960 B total; 23 brain datagrams, 31..1421 B, 9112 B total |
| Frame counts, ping/pong, close | 0x09, 0x0a, 0x03, 0x08 | one each per connection; presence and time |
| QUIC version and header | first bytes of each datagram | version 1, long/short header, connection id lengths and values, packet lengths |
| TLS ClientHello and ALPN | QUIC Initial, decrypted with RFC 9001 keys | `signsetu/brain/1` |
| Application sentence and reply | QUIC 1-RTT payload | absent: `SIGN-SETU-CANARY` and `REPLY-d7a1` are in none of the 104021 captured bytes |

The relay's own log confirms its view: `connection opened from [::1]:51227`,
`remote_endpoint=da29e95b02 connection_id=1`, and the same for the brain.

## What this does not show

- The application data is encrypted end to end. iroh's QUIC 1-RTT keys exist only in the
  two endpoints; the 3400-byte sentence is not in the capture, so the relay forwards it
  without being able to read or alter it undetected.
- The QUIC Initial is not confidential by design (RFC 9001 section 5.2): its keys come from
  the destination connection id that travels in the clear. That is why the ALPN above is
  readable by a relay operator who chooses to derive them. It is metadata about the
  protocol, not station content, and the brain's token is not in it.
- iroh moved the same connection to a direct path after hole punching on this one host
  (the snapshot in `run.txt` shows `is_relay=True` first and a direct path later), as it
  does on a station LAN. The relay carried 22546 bytes from the phone before the switch;
  those bytes are the capture. On mobile data, where no direct path exists, it carries all
  of it, with the same visibility.
- An allowlist (`[access]` in `relay.toml`) changes which endpoint ids may connect, not
  what a connected relay can read.
