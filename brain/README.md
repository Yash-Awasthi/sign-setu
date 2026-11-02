# Station brain

An optional service for one computer at the station. Paired phones use it for speech to text, translation, sign order (gloss) and a local language model, either on the station network or from any network over iroh. Without it the app works alone; with it unreachable, each feature falls back to what the phone does by itself (typing instead of speech, the phone's own sign order and drafts).

| Route | What it does | Engine |
| --- | --- | --- |
| `GET /healthz` | Liveness, no token | |
| `GET /v1/status` | Which models are loaded, whether the language model answers | |
| `POST /v1/asr` | WAV (16 kHz mono) to text, with the detected language | faster-whisper `small`, INT8 on the CPU |
| `POST /v1/translate` | Up to 16 sentences between the app's languages | IndicTrans2 distilled 200M (English and Indian languages, Indian to Indian through English); NLLB-200 distilled 600M for Chhattisgarhi and as the fallback |
| `POST /v1/gloss` | A sentence to draft ISL sign order, limited to the vocabulary the phone sends | Rules (time first, negation after the verb, question word last), refined by the language model when it answers; words outside the vocabulary are dropped |
| `POST /v1/chat` | One reply from the local model | Ollama (`gemma3:1b` by default) on the same computer |

## Reaching the brain

- **On the station network:** `https://<ip>:8000`, with the token and the certificate pin.
- **From anywhere (iroh):** the brain also binds an iroh endpoint under a key kept in its state directory (`iroh.key`) and prints a ticket. The phone dials the ticket over QUIC, end-to-end encrypted to that key: directly where NAT hole punching works, otherwise through an iroh relay, which forwards ciphertext it cannot read. No port is opened at the station. Each request is one stream (`brain/link.py`), forwarded to the brain's own HTTPS API on the loopback address, so the token, size limits and rate limits apply as on the LAN. `--no-iroh` turns this off.

## Security

- HTTPS only, with a self-signed P-256 certificate made on first start. The phone trusts that certificate's SHA-256 pin and nothing else.
- Every route but `/healthz` needs the bearer token (256 bits, `secrets.token_urlsafe`). Ten wrong tokens a minute from one address get 429; any address gets at most 240 requests a minute.
- Only private and loopback client addresses are served on port 8000; the phone accepts only a private, link-local or `.local` address for the brain. Requests over iroh reach the API from the loopback address, after the QUIC handshake has proven the brain's key.
- Audio and text live only in the request's memory. Nothing is written to disk, and the server keeps no access log.
- Bodies over 8 MB (about 4 minutes of audio) and texts over 2,000 characters are refused.

## Set up

Any computer at the station that stays on: Windows 10 or 11, or Linux; 8 GB of RAM and about 8 GB of disk; no GPU needed. It needs the internet once, to download the models, and then runs without it.

1. Install [uv](https://docs.astral.sh/uv/) (`winget install astral-sh.uv` on Windows). Optional: [Ollama](https://ollama.com), for the local language model.
2. IndicTrans2 is gated on Hugging Face: with the department's account, accept the licence on the two model pages ([en to Indian](https://huggingface.co/ai4bharat/indictrans2-en-indic-dist-200M), [Indian to en](https://huggingface.co/ai4bharat/indictrans2-indic-en-dist-200M)), then run `hf auth login` once or set `HF_TOKEN`. Without it, NLLB-200 translates instead.
3. Run the setup from this folder:

| System | Command |
| --- | --- |
| Windows | `.\setup.ps1 -Service` (add `-Firewall`, as administrator, for phones on the station network) |
| Linux | `./setup.sh --service` |

It makes a Python environment with the pinned packages (`requirements.txt`), downloads the models (`python -m brain.fetch`; about 4.5 GB), pulls the Ollama model when Ollama is installed, saves its settings (`brain.json` or `brain.env`, not committed) and, with `-Service` or `--service`, starts the brain at every boot. Options: `-Venv`/`--venv`, `-Models`/`--models` (where the models go), `-Asr`/`--asr` (Whisper size), `-Translator`/`--translator` (`indictrans2`, `nllb` or `both`), `-Llm`/`--llm` (Ollama model).

4. Start it by hand with `.\start.ps1` or `./start.sh`. It prints two pairing lines:

```
Pair a phone: https://192.168.1.20:8000  token …  pin …
Pair a phone from anywhere: ticket endpoint…  token …
```

5. On each phone: Settings, Local AI brain, Station computer: paste the ticket and the token. The ticket works on the station Wi-Fi and from any other network, and survives the computer's address changing; the `https://` address with its pin works on the station network only.

`.\start.ps1 --rotate-token` (or `./start.sh --rotate-token`) issues a new token; every phone must enter it again. The state folder (`%USERPROFILE%\.signsetu-brain` or `~/.signsetu-brain`) holds the certificate, the token and the iroh key: copy it to keep the same ticket on a new computer, and keep it private.

## Run by hand

```
uv venv --python 3.12 .venv
uv pip install --python .venv/Scripts/python.exe --index-strategy unsafe-best-match --extra-index-url https://download.pytorch.org/whl/cpu -r requirements.txt
python -m brain.fetch
python -m brain                        # from this folder; prints the LAN address, token and pin, and the iroh ticket
```

`HF_HOME` sets where the speech and translation models are kept. Options: `--asr` (Whisper size), `--translator indictrans2|nllb`, `--llm` (Ollama model), `--state` (certificate, token and iroh key), `--rotate-token`, `--no-iroh` (station network only).

## Check

`python check.py https://<ip>:8000 <token> <pin> [--audio speech.wav]` refuses plain HTTP and a wrong pin, expects 401 without or with a wrong token and 429 for a token guesser, and times every route (first call, which loads the model, then the median and worst of the rest). Exit 1 on any failed check.

## Licences

iroh (MIT or Apache-2.0); faster-whisper (MIT) and Whisper weights (MIT); IndicTrans2 (MIT); NLLB-200 weights (CC BY-NC 4.0: non-commercial use only, which the department must confirm); Gemma (Gemma Terms of Use); Ollama (MIT); FastAPI and Uvicorn (MIT/BSD).
