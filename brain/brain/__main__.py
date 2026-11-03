"""Starts the brain: HTTPS on port 8000 on every interface, pairing details printed once at start.

    python -m brain [--state DIR] [--port 8000] --asr MODEL [--translator indictrans2|nllb] [--llm gemma3:1b]
                   [--relay https://relay.example.gov.in] [--isl DIR] [--inbox DIR]
    python -m brain --asr MODEL --rotate-token     # a new token; paired phones must enter it again
"""
import argparse
import os
import sys

# The brain never fetches from the internet while it serves: models come from HF_HOME as downloaded beforehand, and
# no model code is updated behind the operator's back.
os.environ.setdefault("HF_HUB_OFFLINE", "1")
from pathlib import Path

import uvicorn

from .app import create
from .models import Asr, Llm, Translator
from .security import Secrets, lan_addresses, write_private


def default_inbox() -> Path:
    """Sign Setu in the user's Documents: OneDrive's Documents when there is one (the department's choice: it syncs to
    Microsoft), else the local one."""
    home = Path.home()
    for docs in (home / "OneDrive" / "Documents", home / "Documents"):
        if docs.is_dir():
            return docs / "Sign Setu"
    return home / "Documents" / "Sign Setu"


def relay_url(value: str) -> str:
    """An http(s) URL with a host and nothing after it but a path; argparse reports anything else."""
    from urllib.parse import urlsplit

    u = urlsplit(value.strip())
    if u.scheme not in ("http", "https") or not u.hostname or u.query or u.fragment:
        raise argparse.ArgumentTypeError(f"not a relay URL: {value!r} (expected https://relay.example.gov.in)")
    return value.strip()


def port(value: str) -> int:
    n = int(value)
    if not 1 <= n <= 65535:
        raise argparse.ArgumentTypeError(f"port {n} out of range")
    return n


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--state", default=os.environ.get("BRAIN_STATE", str(Path.home() / ".signsetu-brain")))
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--port", type=port, default=8000)
    ap.add_argument("--asr", required=True, help="faster-whisper model: a size (small, medium, large-v3) or a model folder; the department chooses")
    ap.add_argument("--translator", default="indictrans2", choices=["indictrans2", "nllb"])
    ap.add_argument("--llm", default="gemma3:1b", help="Ollama model name")
    ap.add_argument("--ollama", default="http://127.0.0.1:11434")
    ap.add_argument("--rotate-token", action="store_true")
    ap.add_argument("--no-iroh", action="store_true", help="LAN only: no iroh endpoint")
    ap.add_argument("--relay", type=relay_url, default=os.environ.get("BRAIN_RELAY") or None,
                    help="the department's iroh relay URL; without it phones reach the brain only by a direct path")
    ap.add_argument("--isl", default=os.environ.get("BRAIN_ISL") or str(Path(__file__).resolve().parent.parent / "isl"),
                    help="folder with the station's sign reader (format 1 .json and .bin; default: isl/ beside this "
                         "package); while it holds none, no signs are read here")
    ap.add_argument("--inbox", default=os.environ.get("BRAIN_INBOX") or None,
                    help="folder where conversations sent from phones are saved (default: Sign Setu in Documents, OneDrive's first)")
    ap.add_argument("--log", default=None, help="write all output to this file (UTF-8, appended) instead of the console")
    a = ap.parse_args()
    # A console in a legacy code page cannot show every path or language: such a line prints with ? rather than crash.
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(errors="replace")
    if a.log:
        # Set before anything prints or uvicorn sets up its logging, so every line lands in the file.
        Path(a.log).parent.mkdir(parents=True, exist_ok=True)
        sys.stdout = sys.stderr = open(a.log, "a", encoding="utf-8", buffering=1)
        import time

        print(f"--- started {time.strftime('%Y-%m-%d %H:%M:%S')}", flush=True)

    secrets = Secrets(Path(a.state))
    if a.rotate_token:
        secrets.rotate_token()
    lines = [f"Pair a phone: https://{ip}:{a.port}  token {secrets.token}  pin {secrets.pin}" for ip in lan_addresses() or ["127.0.0.1"]]
    if not a.no_iroh:
        from .link import Link

        # Started before the API, so it is serving by the time the first phone dials; it forwards to the API below.
        try:
            ticket = Link(Path(a.state), f"https://127.0.0.1:{a.port}", secrets.cert, a.relay).start()
            lines.append(f"Pair a phone from anywhere: ticket {ticket}  token {secrets.token}")
        except Exception as e:  # iroh cannot bind here: the station still serves phones on its own network
            print(f"iroh unavailable ({type(e).__name__}: {e}); phones reach this brain only on the station network", flush=True)
    print("\n".join(lines), flush=True)
    # The status scripts show these again while the brain runs as a service, where nobody sees its console.
    pairing = Path(a.state) / "pairing.txt"
    write_private(pairing, ("\n".join(lines) + "\n").encode("utf-8"))
    isl = None
    isl_dir = Path(a.isl)
    if any(isl_dir.glob("*.json")):
        from .signs import IslReader

        try:
            isl = IslReader(isl_dir)
            print(f"Sign reader: {isl.name}, {len(isl.labels)} words", flush=True)
        except (OSError, ValueError, KeyError, TypeError) as e:  # a broken reader must not keep the rest of the station down
            print(f"Sign reader in {isl_dir} not loaded ({type(e).__name__}: {e}); phones read signs themselves", flush=True)
    else:
        isl_dir.mkdir(parents=True, exist_ok=True)
        print(f"No sign reader in {isl_dir}: phones read signs themselves. Put a reader's .json and .bin there and restart.", flush=True)
    from .inbox import Inbox

    inbox = Inbox(Path(a.inbox) if a.inbox else default_inbox())
    print(f"Conversations from phones are saved in {inbox.folder}", flush=True)
    asr = Asr(a.asr)
    # Offered to phones only once loaded (status "caps"), so no recording is sent to a station that cannot read it.
    asr.warm()
    app = create(secrets, asr, Translator(a.translator), Llm(a.ollama, a.llm), isl=isl, inbox=inbox)
    # Keep-alive well past the 5 s heartbeat, so the iroh bridge's pooled connection is not closed between beats.
    uvicorn.run(app, host=a.host, port=a.port, ssl_certfile=str(secrets.cert), ssl_keyfile=str(secrets.key), timeout_keep_alive=120,
                log_level="warning", access_log=False, server_header=False, date_header=False)


if __name__ == "__main__":
    main()
