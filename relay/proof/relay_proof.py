"""Proves what the department's iroh relay can read.

Runs the relay on loopback in its plain-HTTP --dev mode (started separately, see
README.md), puts a TCP tee in front of its listening socket, and drives it with the
same iroh 1.2.0 client binding the brain and the phone use. The tee logs every byte
the relay receives and sends, so the capture is the relay's socket boundary.

The phone dials the brain's endpoint id with no direct address, so the first path is
the relay. Both sides then exchange a known sentence; the capture must contain the two
32-byte endpoint ids in the clear and none of the sentence.

  python relay_proof.py <relay_port> <proxy_port> <capture.txt>
"""
import asyncio
import datetime
import itertools
import os
import sys

import iroh

ALPN = b"signsetu/brain/1"
SK_SERVER = bytes(range(1, 33))
SK_CLIENT = bytes(range(101, 133))
CANARY = b"SIGN-SETU-CANARY-4b9f2d"
SENTENCE = (CANARY + b": the station said this sentence, the relay must not read it. ")
PAYLOAD = (SENTENCE * 40)[:4096]
REPLY = b"REPLY-d7a1: the brain heard it."


async def tee(listen_host, listen_port, target_host, target_port, out):
    ids = itertools.count(1)

    async def pipe(reader, writer, tag):
        try:
            while True:
                chunk = await reader.read(65536)
                if not chunk:
                    break
                out.write(f"{datetime.datetime.now().isoformat(timespec='microseconds')} {tag} {len(chunk)}\n")
                out.write(chunk.hex() + "\n")
                out.flush()
                writer.write(chunk)
                await writer.drain()
        except Exception:
            pass
        finally:
            try:
                writer.close()
            except Exception:
                pass

    async def handle(cr, cw):
        n = next(ids)
        try:
            sr, sw = await asyncio.open_connection(target_host, target_port)
        except Exception as e:
            print(f"proxy: relay refused connection {n}: {e}", flush=True)
            cw.close()
            return
        print(f"proxy: connection {n} forwarded", flush=True)
        await asyncio.gather(pipe(cr, sw, f"c{n}:C->R"), pipe(sr, cw, f"c{n}:R->C"))

    server = await asyncio.start_server(handle, listen_host, listen_port)
    print(f"proxy: {listen_host}:{listen_port} -> {target_host}:{target_port}", flush=True)
    return server


async def main():
    if len(sys.argv) != 4:
        print("usage: relay_proof.py <relay_port> <proxy_port> <capture.txt>", file=sys.stderr)
        return 2
    relay_port, proxy_port, capture = int(sys.argv[1]), int(sys.argv[2]), sys.argv[3]
    iroh.set_log_level(iroh.LogLevel.OFF)
    out = open(capture, "w", encoding="ascii", newline="\n")
    proxy = await tee("127.0.0.1", proxy_port, "::1", relay_port, out)
    url = f"http://127.0.0.1:{proxy_port}"
    mode = iroh.RelayMode.custom_from_urls([url])

    server = await iroh.Endpoint.bind(iroh.EndpointOptions(
        preset=iroh.preset_minimal(), secret_key=SK_SERVER, alpns=[ALPN], relay_mode=mode))
    client = await iroh.Endpoint.bind(iroh.EndpointOptions(
        preset=iroh.preset_minimal(), secret_key=SK_CLIENT, relay_mode=mode))
    print("brain endpoint id", server.id(), flush=True)
    print("phone endpoint id", client.id(), flush=True)
    # The relay is only a fallback path here; wait for it, but a missed online check is not fatal.
    try:
        await asyncio.wait_for(server.online(), 10)
    except asyncio.TimeoutError:
        print("brain endpoint offline: relay registration still pending", flush=True)

    got = asyncio.get_running_loop().create_future()

    async def serve():
        incoming = await server.accept_next()
        conn = await (await incoming.accept()).connect()
        bi = await conn.accept_bi()
        data = await bi.recv().read_exact(len(PAYLOAD))
        print("brain received", len(data), "bytes; marker present:", CANARY in data, flush=True)
        await bi.send().write_all(REPLY)
        await bi.send().finish()
        got.set_result(True)

    task = asyncio.create_task(serve())
    # No direct address in the address: the dial has to go through the relay.
    conn = await asyncio.wait_for(client.connect(iroh.EndpointAddr(server.id(), url, []), ALPN), 20)
    bi = await conn.open_bi()
    await bi.send().write_all(PAYLOAD)
    reply = await bi.recv().read_exact(len(REPLY))
    print("phone received", len(reply), "bytes; marker present:", CANARY in reply, flush=True)
    await asyncio.wait_for(got, 10)
    paths = [p for p in conn.paths()]
    for p in paths:
        print("path:", p, flush=True)
    relay_bytes = sum(p.stats.udp_tx_bytes for p in paths if p.is_relay)
    print("bytes sent on the relay path:", relay_bytes, flush=True)
    conn.close(0, b"done")
    task.cancel()
    await asyncio.wait_for(client.close(), 5)
    await asyncio.wait_for(server.close(), 5)
    proxy.close()
    await proxy.wait_closed()
    out.close()
    print("capture:", capture, os.path.getsize(capture), "bytes", flush=True)
    return 0


sys.exit(asyncio.run(main()))
