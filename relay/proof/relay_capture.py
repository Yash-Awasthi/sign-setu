"""Explains capture.txt: the connections the relay accepted, the plaintext HTTP fields,
and the relay-protocol frames it decoded, with sizes and byte counts.

  python relay_capture.py capture.txt > fields.txt
"""
import collections
import hashlib
import sys

NAMES = {
    0x00: "server hello (17 B)",
    0x01: "client hello: 32 B endpoint id + 64 B signature",
    0x02: "auth result (1 B)",
    0x03: "close (2 B code)",
    0x04: "datagram: 32 B destination id + QUIC packet",
    0x06: "datagram: 32 B source id + QUIC packet",
    0x08: "peer-gone notice: 32 B endpoint id",
    0x09: "ping (8 B nonce)",
    0x0A: "pong (8 B nonce)",
}


def read(path):
    lines = open(path, encoding="ascii").read().splitlines()
    conns = collections.OrderedDict()
    for i in range(0, len(lines), 2):
        ts, tag, _ = lines[i].split(" ")
        conns.setdefault(tag, {"bytes": 0, "first": ts, "last": ts, "data": bytearray()})
        c = conns[tag]
        c["bytes"] += len(lines[i + 1]) // 2
        c["last"] = ts
        c["data"].extend(bytes.fromhex(lines[i + 1]))
    return conns


def frames(buf):
    i = 0
    out = []
    while i + 2 <= len(buf):
        b1 = buf[i + 1]
        ln = b1 & 0x7F
        hdr = 2
        if ln == 126:
            ln = int.from_bytes(buf[i + 2:i + 4], "big")
            hdr = 4
        elif ln == 127:
            ln = int.from_bytes(buf[i + 2:i + 10], "big")
            hdr = 10
        if b1 & 0x80:
            mask = buf[i + hdr:i + hdr + 4]
            payload = bytes(x ^ mask[j % 4] for j, x in enumerate(buf[i + hdr + 4:i + hdr + 4 + ln]))
            i += hdr + 4 + ln
        else:
            payload = buf[i + hdr:i + hdr + ln]
            i += hdr + ln
        out.append(payload)
    return out


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "capture.txt"
    raw = open(path, "rb").read()
    conns = read(path)
    print("RELAY CAPTURE SUMMARY")
    print(f"file {path}, {len(raw)} bytes, sha256 {hashlib.sha256(raw).hexdigest()}")
    print()
    print("TCP connections the relay accepted (from the tee at its socket):")
    print(f"  {'conn':5} {'C->R':>7} {'R->C':>7}  request line")
    http = {}
    for tag in conns:
        if tag.endswith("C->R"):
            name = tag.split(":")[0]
            req = conns[tag]["data"].split(b"\r\n")[0].decode("latin1")
            http[name] = req
    for name in http:
        c, r = conns[name + ":C->R"], conns[name + ":R->C"]
        print(f"  {name:5} {c['bytes']:7} {r['bytes']:7}  {http[name]}")
    print()
    for name, req in http.items():
        if req != "GET /relay HTTP/1.1":
            continue
        c, r = conns[name + ":C->R"], conns[name + ":R->C"]
        head = c["data"].split(b"\r\n\r\n")[0].decode("latin1")
        print(f"{name} plaintext TCP request, as the relay reads it:")
        for line in head.split("\r\n"):
            print(f"  {line}")
        cf = frames(c["data"][c["data"].index(b"\r\n\r\n") + 4:])
        rf = frames(r["data"][r["data"].index(b"\r\n\r\n") + 4:])
        ids = []
        for p in cf:
            if p[0] == 1:
                ids.append(("hello", p[1:33].hex()))
            elif p[0] == 4:
                ids.append(("send to", p[1:33].hex()))
        print(f"  frames client->relay: {len(cf)}, relay->client: {len(rf)}")
        print(f"  first hello endpoint id: {ids[0][1] if ids else 'none'}")
        dst = sorted({i for kind, i in ids if kind == 'send to'})
        print(f"  destination ids in send frames: {', '.join(dst) if dst else 'none'}")
        print(f"  time on the relay: {c['first']} .. {c['last']}")
        census = collections.Counter(p[0] for p in cf + rf)
        for tag, n in sorted(census.items()):
            print(f"    frame 0x{tag:02x} x{n}: {NAMES.get(tag, 'unknown')}")
        quic = [p for p in cf if p[0] == 4]
        sizes = sorted(len(p) - 33 for p in quic)
        if sizes:
            print(f"  QUIC datagrams forwarded: {len(sizes)}, "
                  f"{sizes[0]}..{sizes[-1]} bytes, {sum(sizes)} bytes total")
        print()
    allbytes = b"".join(bytes(c["data"]) for c in conns.values())
    brain = bytes.fromhex("79b5562e8fe654f94078b112e8a98ba7901f853ae695bed7e0e3910bad049664")
    phone = bytes.fromhex("da29e95b02e00ffa15645775fb1d2ba222a1943395eea06b94e2c057b7be69d0")
    print("Plaintext identifier check over every captured byte:")
    print(f"  brain endpoint id in the clear: {allbytes.count(brain)} times")
    print(f"  phone endpoint id in the clear: {allbytes.count(phone)} times")
    print(f"  application sentence in the clear: {b'SIGN-SETU-CANARY' in allbytes}")
    print(f"  reply in the clear: {b'REPLY-d7a1' in allbytes}")
    return 0


sys.exit(main())
