# Station brain

An optional service for one computer on the station network. Paired phones use it for speech to text, translation, sign order (gloss) and a local language model. Without it the app works alone; with it unreachable, each feature falls back to what the phone does by itself (typing instead of speech, the phone's own sign order and drafts).

| Route | What it does | Engine |
| --- | --- | --- |
| `GET /healthz` | Liveness, no token | |
| `GET /v1/status` | Which models are loaded, whether the language model answers | |
| `POST /v1/asr` | WAV (16 kHz mono) to text, with the detected language | faster-whisper `small`, INT8 on the CPU |
| `POST /v1/translate` | Up to 16 sentences between the app's languages | IndicTrans2 distilled 200M (English and Indian languages, Indian to Indian through English); NLLB-200 distilled 600M for Chhattisgarhi and as the fallback |
| `POST /v1/gloss` | A sentence to draft ISL sign order, limited to the vocabulary the phone sends | Rules (time first, negation after the verb, question word last), refined by the language model when it answers; words outside the vocabulary are dropped |
| `POST /v1/chat` | One reply from the local model | Ollama (`gemma3:1b` by default) on the same computer |

## Security

- HTTPS only, with a self-signed P-256 certificate made on first start. The phone trusts that certificate's SHA-256 pin and nothing else.
- Every route but `/healthz` needs the bearer token (256 bits, `secrets.token_urlsafe`). Ten wrong tokens a minute from one address get 429; any address gets at most 240 requests a minute.
- Only private and loopback client addresses are served; the phone accepts only a private, link-local or `.local` address for the brain.
- Audio and text live only in the request's memory. Nothing is written to disk, and the server keeps no access log.
- Bodies over 8 MB (about 4 minutes of audio) and texts over 2,000 characters are refused.

## Run

```
uv venv --python 3.12 venvs/signsetu-brain
uv pip install --python venvs/signsetu-brain/Scripts/python.exe --extra-index-url https://download.pytorch.org/whl/cpu \
    torch fastapi "uvicorn[standard]" python-multipart httpx cryptography faster-whisper "transformers<5" sentencepiece sacremoses indic-nlp-library
ollama pull gemma3:1b
cd brain && python -m brain            # prints: Pair a phone: https://<ip>:8000  token …  pin …
```

`HF_HOME` sets where the speech and translation models are kept (downloaded on first use). Options: `--asr` (Whisper size), `--translator indictrans2|nllb`, `--llm` (Ollama model), `--state` (certificate and token), `--rotate-token` (paired phones must enter the new token).

On the phone: Settings, Local AI brain, Station computer: enter the address, token and pin.

## Check

`python check.py https://<ip>:8000 <token> <pin> [--audio speech.wav]` refuses plain HTTP and a wrong pin, expects 401 without or with a wrong token and 429 for a token guesser, and times every route (first call, which loads the model, then the median and worst of the rest). Exit 1 on any failed check.

## Licences

faster-whisper (MIT) and Whisper weights (MIT); IndicTrans2 (MIT); NLLB-200 weights (CC BY-NC 4.0: non-commercial use only, which the department must confirm); Gemma (Gemma Terms of Use); Ollama (MIT); FastAPI and Uvicorn (MIT/BSD).
