"""Reads capture.txt and decrypts the QUIC Initial the phone sent, exactly as any relay
operator can: RFC 9001 derives the Initial keys from the destination connection id that
travels in the clear. It prints what that yields (TLS ClientHello, ALPN) and confirms the
application sentence is not there.

  python initial_hello.py capture.txt
"""
import collections
import hmac
import struct
import sys

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

SALT = bytes.fromhex("38762cf7f55934b34d179ae6a4c80cadccbb7f0a")
CANARY = b"SIGN-SETU-CANARY-4b9f2d"


def streams(path):
    lines = open(path, encoding="ascii").read().splitlines()
    out = collections.OrderedDict()
    for i in range(0, len(lines), 2):
        _, tag, _ = lines[i].split(" ")
        out.setdefault(tag, bytearray()).extend(bytes.fromhex(lines[i + 1]))
    return out


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


def varint(b, i):
    v = b[i]
    n = 1 << (v >> 6)
    v &= 0x3F
    for k in range(1, n):
        v = (v << 8) | b[i + k]
    return v, i + n


def expand_label(secret, label, length):
    full = b"tls13 " + label
    info = struct.pack(">H", length) + bytes([len(full)]) + full + b"\x00"
    out, block, i = b"", b"", 1
    while len(out) < length:
        block = hmac.new(secret, block + info + bytes([i]), "sha256").digest()
        out += block
        i += 1
    return out[:length]


def decrypt_initial(pkt):
    i = 5
    dcid_len = pkt[i]
    dcid = pkt[i + 1:i + 1 + dcid_len]
    i += 1 + dcid_len
    i += 1 + pkt[i]  # source connection id
    token_len, i = varint(pkt, i)
    i += token_len
    length, i = varint(pkt, i)
    pn_off = i
    secret = expand_label(hmac.new(SALT, dcid, "sha256").digest(), b"client in", 32)
    key = expand_label(secret, b"quic key", 16)
    iv = expand_label(secret, b"quic iv", 12)
    hp = expand_label(secret, b"quic hp", 16)
    sample = pkt[pn_off + 4:pn_off + 20]
    mask = Cipher(algorithms.AES(hp), modes.ECB()).encryptor().update(sample)
    first = pkt[0] ^ (mask[0] & 0x0F)
    pn_len = (first & 3) + 1
    pn = bytearray(pkt[pn_off:pn_off + pn_len])
    for j in range(pn_len):
        pn[j] ^= mask[1 + j]
    pn = int.from_bytes(pn, "big")
    aad = bytes([first]) + pkt[1:pn_off] + pn.to_bytes(pn_len, "big")
    ciphertext = pkt[pn_off + pn_len:pn_off + length]
    nonce = bytes(a ^ b for a, b in zip(iv, pn.to_bytes(12, "big")))
    return dcid, AESGCM(key).decrypt(nonce, ciphertext, aad)


def crypto_bytes(plaintext):
    out = b""
    i = 0
    while i < len(plaintext) and plaintext[i] == 0x06:
        _, i = varint(plaintext, i + 1)  # offset
        n, i = varint(plaintext, i)
        out += plaintext[i:i + n]
        i += n
    return out


def alpn(tls):
    if not tls or tls[0] != 1:
        return []
    i = 4 + 2 + 32
    i += 1 + tls[i]
    i += 2 + int.from_bytes(tls[i:i + 2], "big")
    i += 1 + tls[i]
    if i + 2 > len(tls):
        return []
    end = i + 2 + int.from_bytes(tls[i:i + 2], "big")
    i += 2
    found = []
    while i + 4 <= end:
        kind = int.from_bytes(tls[i:i + 2], "big")
        size = int.from_bytes(tls[i + 2:i + 4], "big")
        body = tls[i + 4:i + 4 + size]
        if kind == 0x0010 and len(body) >= 3:
            j = 2
            while j + 1 <= len(body):
                n = body[j]
                found.append(body[j + 1:j + 1 + n].decode("latin1"))
                j += 1 + n
        i += 4 + size
    return found


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "capture.txt"
    seen = 0
    for tag, buf in streams(path).items():
        if not tag.endswith("C->R") or b"\r\n\r\n" not in buf:
            continue
        if b"GET /relay" not in buf[:400]:
            continue
        for n, payload in enumerate(frames(buf[buf.index(b"\r\n\r\n") + 4:])):
            if payload[0] != 4:
                continue
            body = payload[33:]
            pos = body.find(b"\x00\x00\x00\x01")
            while pos > 0:
                start = pos - 1
                if 0xC0 <= body[start] <= 0xFF:
                    try:
                        dcid, plain = decrypt_initial(body[start:])
                    except Exception:
                        pos = body.find(b"\x00\x00\x00\x01", pos + 1)
                        continue
                    tls = crypto_bytes(plain)
                    names = alpn(tls)
                    if names:
                        seen += 1
                        print(f"{tag} frame {n}: QUIC v1 Initial, dcid {dcid.hex()}")
                        print(f"  TLS ClientHello ALPN: {', '.join(names)}")
                        print(f"  application canary in this Initial: {CANARY in plain}")
                    break
                pos = body.find(b"\x00\x00\x00\x01", pos + 1)
    print(f"decrypted Initials carrying a ClientHello: {seen}")
    return 0 if seen else 1


sys.exit(main())
