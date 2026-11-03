# Station brain

An optional service for one computer at the station. Paired phones use it for speech to text, translation, sign order (gloss) and a local language model, either on the station network or from any network over iroh. Without it the app works alone; with it unreachable, each feature falls back to what the phone does by itself (typing instead of speech, the phone's own sign order and drafts).

| Route | What it does | Engine |
| --- | --- | --- |
| `GET /healthz` | Liveness, no token | |
| `GET /v1/status` | Which models are loaded, and `caps`: what phones may ask for ([docs/18-station.md](../docs/18-station.md)) | |
| `GET /v1/beat` (iroh only) | The heartbeat: one stream that carries the status every 5 seconds until the phone closes it | `link.py` |
| `POST /v1/asr` | WAV (16 kHz mono) to text, with the detected language | faster-whisper, the model chosen at setup (`-Asr`), INT8 on the CPU |
| `POST /v1/live`, `/v1/live/{id}`, `/v1/live/{id}/stop` | Live speech to text: the phone sends 16 kHz PCM in pieces while the person talks and reads back the words so far, settled and tentative, and when they stopped (`live.py`) | The same model, one greedy pass about every half second; a word settles once two passes agree ([docs/17-live-speech.md](../docs/17-live-speech.md)) |
| `POST /v1/sign` | One sign's landmarks to the station reader's likeliest words | Every reader in `--isl` (`signs.py`); the first by `priority` answers, and its top is marked `disputed` when another is 0.3 or more sure of a different word; none without one |
| `POST /v1/archive`, `/v1/archive/{id}?at=`, `/v1/archive/{id}/done` | A whole conversation from a phone, as one zip in pieces, saved in the inbox once its SHA-256 matches | `inbox.py`; the inbox is `--inbox`, or `Sign Setu` in Documents, OneDrive's first |
| `POST /v1/translate` | Up to 16 sentences between the app's languages | IndicTrans2 distilled 200M (English and Indian languages, Indian to Indian through English); NLLB-200 distilled 600M for Chhattisgarhi and as the fallback |
| `POST /v1/gloss` | A sentence to draft ISL sign order, limited to the vocabulary the phone sends | Rules (time first, negation after the verb, question word last), refined by the language model when it answers; words outside the vocabulary are dropped |
| `POST /v1/plan` | Auto mode's next step: an allowed bank item that meets the person's last answer, or (write mode) one short sentence for the phone to check | `--plan words`: the model names one of the items' cue words; `--plan write`: it picks an item or writes a sentence (`plan.py`). Anything else is no pick and the phone keeps the bank's order |
| `POST /v1/chat` | One reply from the station's model | Ollama (`gemma3:1b` by default) on the same computer, or a model behind an API key (`--llm-api openai\|anthropic`, `--llm-url`, key in `llm.key`), with names, details and numbers masked before text leaves |

## Reaching the brain

- **On the station network:** `https://<ip>:8000`, with the token and the certificate pin.
- **From anywhere (iroh):** the brain also binds an iroh endpoint under a key kept in its state directory (`iroh.key`) and prints a ticket. The phone dials the ticket over QUIC, end-to-end encrypted to that key: directly where NAT hole punching works, otherwise through the department's own iroh relay (`--relay`, [docs/16-relay.md](../docs/16-relay.md)), which forwards ciphertext it cannot read; no third-party relay is used, and without one only direct paths work. No port is opened at the station. Each request is one stream (`brain/link.py`), forwarded to the brain's own HTTPS API on the loopback address, so the token, size limits and rate limits apply as on the LAN. `--no-iroh` turns this off.

## Security

- HTTPS only, with a self-signed P-256 certificate made on first start. The phone trusts that certificate's SHA-256 pin and nothing else.
- Every route but `/healthz` needs the bearer token (256 bits, `secrets.token_urlsafe`). Ten wrong tokens a minute from one address get 429. Otherwise at most 240 requests a minute are served per client: per address on the station network, and per phone over iroh (the bridge passes the phone's endpoint id in `x-signsetu-peer`), so several phones do not share one allowance.
- Only private and loopback client addresses are served on port 8000; the phone accepts only a private, link-local or `.local` address for the brain. Requests over iroh reach the API from the loopback address, after the QUIC handshake has proven the brain's key.
- Audio and text sent for transcription, translation or the model live only in the request's memory, and the server keeps no access log. The one thing written to disk is a conversation an officer sends to the inbox, saved as it came; protecting that folder is the department's.
- Bodies over 8 MB (about 4 minutes of audio) and texts over 2,000 characters are refused; inbox pieces are at most 4 MB and a conversation at most 1 GB. A non-numeric `content-length` gets 400.
- `/v1/chat` passes only `role` and `content` to Ollama. The role must be `system`, `user` or `assistant`, content a string of at most 32 KB, `temperature` 0 to 2 and `max_tokens` at least 1; anything else gets 422. Audio with a damaged WAV header gets 422.
- The certificate key, token, `pairing.txt` and `iroh.key` are written owner-only (mode 0600) and replaced in one step, so a crash cannot leave a half-written file. An empty or damaged token file gets a new token. Requests over iroh may only name plain paths: encoded dots, backslashes and control characters are refused.
- A conversation zip read back from the inbox is capped at 256 MB per file, and names such as `CON`, `NUL` and `COM1` get a `_` prefix on Windows.

## Set up

Any computer at the station that stays on: Windows 10 or 11, or Linux; 8 GB of RAM and about 8 GB of disk; no GPU needed. It needs the internet once, to download the models, and then runs without it.

1. Install [uv](https://docs.astral.sh/uv/) (`winget install astral-sh.uv` on Windows). Optional: [Ollama](https://ollama.com), for the local language model.
2. IndicTrans2 is gated on Hugging Face: with the department's account, accept the licence on the two model pages ([en to Indian](https://huggingface.co/ai4bharat/indictrans2-en-indic-dist-200M), [Indian to en](https://huggingface.co/ai4bharat/indictrans2-indic-en-dist-200M)), then run `hf auth login` once or set `HF_TOKEN`. Without it, NLLB-200 translates instead.
3. Run the setup from this folder:

| System | Command |
| --- | --- |
| Windows | `.\setup.ps1 -Asr <model> -Service` (add `-Firewall`, as administrator, for phones on the station network) |
| Linux | `./setup.sh --asr <model> --service` |

It makes a Python environment with the pinned packages (`requirements.txt`), downloads the models (`python -m brain.fetch`; about 4.5 GB), pulls the Ollama model when Ollama is installed, saves its settings (`brain.json` or `brain.env`, not committed) and, with `-Service` or `--service`, starts the brain at every boot. Options: `-Venv`/`--venv`, `-Models`/`--models` (where the models go), `-Asr`/`--asr` (required: the speech model, a faster-whisper size such as `small`, `medium` or `large-v3`, or a model folder; larger models read more languages well and run slower on a CPU), `-Translator`/`--translator` (`indictrans2`, `nllb` or `both`), `-Llm`/`--llm` (Ollama model), `-Relay`/`--relay` (the department's relay), `-Isl`/`--isl` and `-Inbox`/`--inbox` ([docs/18-station.md](../docs/18-station.md)).

4. With `-Service` it runs already, and restarts by itself if it stops. Day to day:

| What | Windows | Linux |
| --- | --- | --- |
| Is it running, what does it offer, how to pair | `.\status.ps1` | `./status.sh` |
| Stop it | `.\stop.ps1` | `./stop.sh` |
| Start the service again | `.\start.ps1 -Service` | `./start.sh --service` |
| Run it in this window instead (Ctrl+C stops) | `.\start.ps1` | `./start.sh` |
| Change a setting without downloading again | `.\setup.ps1 ... -NoModels` | `./setup.sh ... --no-models` |
| Take the service away (environment, models and state stay) | `.\remove.ps1` | `./remove.sh` |

To add a sign reader, put its model and its `.json` spec in `isl\` (or `isl/`) here: `.bin` (format 1), `.tflite` (LiteRT) or `.onnx`. It loads within seconds, no restart. A bad or unreadable file is refused with a reason (in `/v1/status` under `isl.refused`) and the reader it would replace keeps running. A `.pt` is refused: export it to ONNX. The spec needs `labels`, `frames`, `temperature` and `input` (`shoulder98` or `raw218`); `--words` names the app's `sign_words.json` so labels it lacks are reported. On Windows the service starts at boot when `setup.ps1` runs as administrator, otherwise at sign-in; its log is `brain.log` in the state folder. On Linux it is a systemd user service, logged in the journal. `-Firewall` (`setup.ps1`, as administrator) opens TCP 8000 and iroh's direct path on private networks only: a Wi-Fi Windows calls Public gets neither, and phones on it need the relay.

Started by hand, it prints two pairing lines; `status` shows the same lines while it runs as a service:

```
Pair a phone: https://192.168.1.20:8000  token …  pin …
Pair a phone from anywhere: ticket endpoint…  token …
```

5. On each phone: Settings, Online, Station computer: paste the ticket and the token. The ticket works on the station Wi-Fi and from any other network, and survives the computer's address changing; the `https://` address with its pin works on the station network only.

`.\start.ps1 --rotate-token` (or `./start.sh --rotate-token`) issues a new token; every phone must enter it again. The state folder (`%USERPROFILE%\.signsetu-brain` or `~/.signsetu-brain`) holds the certificate, the token and the iroh key: copy it to keep the same ticket on a new computer, and keep it private.

## Run by hand

```
uv venv --python 3.12 .venv
uv pip install --python .venv/Scripts/python.exe --index-strategy unsafe-best-match --extra-index-url https://download.pytorch.org/whl/cpu -r requirements.txt
python -m brain.fetch --asr <model>
python -m brain --asr <model>                        # from this folder; prints the LAN address, token and pin, and the iroh ticket
```

`HF_HOME` sets where the speech and translation models are kept. Options: `--asr` (required, as above), `--translator indictrans2|nllb`, `--llm` (Ollama model), `--ollama` (its address), `--state` (certificate, token and iroh key; `BRAIN_STATE`), `--host`, `--port` (1 to 65535, default 8000), `--relay` (an `http(s)://host` URL; `BRAIN_RELAY`), `--isl` (the station's sign reader; `BRAIN_ISL`), `--inbox` (where conversations from phones go; `BRAIN_INBOX`), `--log` (append all output to a UTF-8 file), `--rotate-token`, `--no-iroh` (station network only).

## What the brain does when something is missing

- No `--relay`: iroh uses no relay and no n0 service; phones reach the brain only by a direct path.
- The relay is down at start: the brain logs `iroh: relay ... not reachable yet`, keeps going, and the ticket still names the relay.
- iroh cannot bind: the brain logs `iroh unavailable (...)` and serves the station network only; `pairing.txt` then has no ticket line.
- The sign reader in `--isl` is broken: the brain logs `Sign reader in ... not loaded (...)` and phones read signs themselves. A reader spec needs `frames` of at least 2 and a positive `temperature`.
- The inbox defaults to `Sign Setu` in Documents (OneDrive's Documents first, which syncs to Microsoft; set `--inbox` to keep it in-house).
- `status` shows the Inbox line from the saved settings when `--inbox` was given, otherwise from the log. It passes the token to the local check on standard input, so it never appears in the process list.

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `setup` says uv is not installed | `winget install astral-sh.uv`, then open a new shell |
| Translation is by NLLB, not IndicTrans2 | The IndicTrans2 licences are not accepted or `hf auth login` was not run; do that, then `setup -NoModels` |
| `The brain is already running` | Another brain holds port 8000; `.\status.ps1` or `./status.sh`, then `.\stop.ps1` or `./stop.sh` |
| `status` shows not running right after `-Service` | Models load first; wait a minute and run `status` again. The log is `brain.log` in the state folder (Linux: `journalctl --user -u` the service) |
| Phone on the station Wi-Fi cannot connect | Windows called the Wi-Fi Public, or no firewall rule: run `setup.ps1 -Firewall -NoModels` as administrator, or use the ticket and a relay. On Android 17 allow access to devices on this network |
| Phone says the pin or certificate is wrong | The state folder was replaced or deleted, which made a new certificate; pair again with the new pin |
| Every phone gets 401 | The token was rotated; enter the new one on each phone |
| 429 from a phone | Ten wrong tokens in a minute from one address, or more than 240 requests a minute from one phone; wait a minute |
| Ticket works only on the station Wi-Fi | No relay is set or it is unreachable; see [docs/16-relay.md](../docs/16-relay.md) |
| A phone paired before the relay was set no longer connects | Its ticket names no relay; copy the new ticket from `status` |
| `/v1/status` lacks `isl`, `llm` or `live` | No reader in `isl\`, Ollama is not running or lacks the model, or the speech model has not finished loading |
| `setup.ps1` rejects `-Relay` | It must start with `http://` or `https://` and hold no spaces |
| Hindi names print as `?` in a console | A legacy code page; the log file (`--log`) is UTF-8 |

## Check

`python check.py <token> --url https://<ip>:8000 --pin <pin> [--ticket <ticket>] [--audio speech.wav]` (`--ticket` alone checks only the iroh path through the relay: connect, heartbeat, then every service: asr, live, sign, translate, gloss, chat, archive; a service the station does not offer is skipped, not failed). On the LAN it refuses plain HTTP and a wrong pin, expects 401 without or with a wrong token and 429 for a token guesser, and times every route (first call, which loads the model, then the median and worst of the rest). Exit 1 on any failed check.

## Licences

iroh (MIT or Apache-2.0); faster-whisper (MIT) and Whisper weights (MIT); IndicTrans2 (MIT); NLLB-200 weights (CC BY-NC 4.0: non-commercial use only, which the department must confirm); Gemma (Gemma Terms of Use); Ollama (MIT); FastAPI and Uvicorn (MIT/BSD).
