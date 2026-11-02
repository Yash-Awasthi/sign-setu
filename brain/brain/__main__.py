"""Starts the brain: HTTPS on port 8000 on every interface, pairing details printed once at start.

    python -m brain [--state DIR] [--port 8000] [--asr small] [--translator indictrans2|nllb] [--llm gemma3:1b]
    python -m brain --rotate-token     # a new token; paired phones must enter it again
"""
import argparse
import os

# The brain never fetches from the internet while it serves: models come from HF_HOME as downloaded beforehand, and
# no model code is updated behind the operator's back.
os.environ.setdefault("HF_HUB_OFFLINE", "1")
from pathlib import Path

import uvicorn

from .app import create
from .models import Asr, Llm, Translator
from .security import Secrets, lan_addresses


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--state", default=os.environ.get("BRAIN_STATE", str(Path.home() / ".signsetu-brain")))
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--port", type=int, default=8000)
    ap.add_argument("--asr", default="small", help="faster-whisper size")
    ap.add_argument("--translator", default="indictrans2", choices=["indictrans2", "nllb"])
    ap.add_argument("--llm", default="gemma3:1b", help="Ollama model name")
    ap.add_argument("--ollama", default="http://127.0.0.1:11434")
    ap.add_argument("--rotate-token", action="store_true")
    ap.add_argument("--no-iroh", action="store_true", help="LAN only: no iroh endpoint")
    a = ap.parse_args()

    secrets = Secrets(Path(a.state))
    if a.rotate_token:
        secrets.rotate_token()
    for ip in lan_addresses() or ["127.0.0.1"]:
        print(f"Pair a phone: https://{ip}:{a.port}  token {secrets.token}  pin {secrets.pin}", flush=True)
    if not a.no_iroh:
        from .link import Link

        # Started before the API, so it is serving by the time the first phone dials; it forwards to the API below.
        ticket = Link(Path(a.state), f"https://127.0.0.1:{a.port}", secrets.cert).start()
        print(f"Pair a phone from anywhere: ticket {ticket}  token {secrets.token}", flush=True)
    app = create(secrets, Asr(a.asr), Translator(a.translator), Llm(a.ollama, a.llm))
    uvicorn.run(app, host=a.host, port=a.port, ssl_certfile=str(secrets.cert), ssl_keyfile=str(secrets.key),
                log_level="warning", access_log=False, server_header=False, date_header=False)


if __name__ == "__main__":
    main()
