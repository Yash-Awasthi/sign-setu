"""The brain's HTTP API. Every route but /healthz needs the pairing token; only private and loopback clients are served."""
import time

from fastapi import FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from . import gloss as glossing
from .models import Asr, Llm, Translator
from .security import RateLimit, Secrets, bearer_ok, client_allowed

VERSION = "1"
MAX_BODY = 8 * 1024 * 1024  # about 4 minutes of 16 kHz mono WAV
MAX_TEXT = 2000


class TranslateIn(BaseModel):
    texts: list[str] = Field(min_length=1, max_length=16)
    src: str
    tgt: str


class ChatIn(BaseModel):
    messages: list[dict] = Field(min_length=1, max_length=24)
    temperature: float = 0.2
    max_tokens: int = Field(256, le=1024)


class GlossIn(BaseModel):
    text: str = Field(max_length=MAX_TEXT)
    lang: str = "en"
    vocabulary: list[str] | None = Field(None, max_length=6000)


def create(secrets: Secrets, asr: Asr, translator: Translator, llm: Llm, limit: RateLimit | None = None) -> FastAPI:
    app = FastAPI(title="Sign Setu brain", version=VERSION, docs_url=None, redoc_url=None, openapi_url=None)
    limit = limit or RateLimit(240, 60.0)
    # Wrong tokens are counted apart: ten a minute is plenty for a typo, far too few to guess 256 bits.
    guesses = RateLimit(10, 60.0)

    @app.middleware("http")
    async def guard(request: Request, call_next):
        host = request.client.host if request.client else None
        if not client_allowed(host):
            return JSONResponse({"error": "not on the station network"}, status_code=403)
        if request.url.path != "/healthz":
            if not limit.allow(host):
                return JSONResponse({"error": "too many requests"}, status_code=429)
            if not bearer_ok(request.headers.get("authorization"), secrets.token):
                if not guesses.allow(host):
                    return JSONResponse({"error": "too many requests"}, status_code=429)
                return JSONResponse({"error": "pairing token missing or wrong"}, status_code=401)
        size = request.headers.get("content-length")
        if size and int(size) > MAX_BODY:
            return JSONResponse({"error": "request too large"}, status_code=413)
        t0 = time.perf_counter()
        response = await call_next(request)
        response.headers["Server-Timing"] = f"total;dur={(time.perf_counter() - t0) * 1000:.0f}"
        response.headers["Cache-Control"] = "no-store"
        return response

    @app.get("/healthz")
    def healthz():
        return {"ok": True, "version": VERSION}

    @app.get("/v1/status")
    def status():
        return {"asr": asr.loaded, "translation": translator.loaded, "llm": llm.available(), "llm_model": llm.model}

    @app.post("/v1/asr")
    async def recognise(audio: UploadFile = File(...), lang: str | None = Form(None)):
        data = await audio.read(MAX_BODY + 1)
        if len(data) > MAX_BODY:
            raise HTTPException(413, "audio too long")
        # The audio lives only in this request's memory: nothing is written to disk or kept.
        try:
            return asr.transcribe(data, lang)
        finally:
            del data

    @app.post("/v1/translate")
    def translate(body: TranslateIn):
        if any(len(t) > MAX_TEXT for t in body.texts):
            raise HTTPException(413, "text too long")
        try:
            out, engine = translator.translate(body.texts, body.src, body.tgt)
        except ValueError as e:
            raise HTTPException(422, str(e))
        return {"texts": out, "engine": engine}

    @app.post("/v1/chat")
    def chat(body: ChatIn):
        if not llm.available():
            raise HTTPException(503, "no local language model")
        return {"text": llm.chat(body.messages, body.temperature, body.max_tokens), "model": llm.model}

    @app.post("/v1/gloss")
    def gloss(body: GlossIn):
        text = body.text
        if body.lang != "en":
            try:
                text = translator.translate([text], body.lang, "en")[0][0]
            except ValueError as e:
                raise HTTPException(422, str(e))
        out = glossing.expand(text, body.vocabulary, llm if llm.available() else None)
        out["english"] = text
        return out

    return app
