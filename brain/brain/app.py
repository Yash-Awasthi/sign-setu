"""The brain's HTTP API. Every route but /healthz needs the pairing token; only private and loopback clients are served."""
import time

from fastapi import FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from . import gloss as glossing
from . import plan as planning
from .mask import guard as masked
from .inbox import MAX_PIECE, Inbox
from .live import MAX_CHUNK, Live
from .models import Asr, Llm, Translator
from .signs import MAX_FRAMES, Readers
from .security import PEER_HEADER, RateLimit, Secrets, bearer_ok, client_allowed

VERSION = "1"
MAX_BODY = 8 * 1024 * 1024  # about 4 minutes of 16 kHz mono WAV
MAX_TEXT = 2000
DOWN = 60.0  # seconds a failed service stays out of the heartbeat


class TranslateIn(BaseModel):
    texts: list[str] = Field(min_length=1, max_length=16)
    src: str
    tgt: str


class ChatIn(BaseModel):
    messages: list[dict] = Field(min_length=1, max_length=24)
    # What identifies the person, masked before a model behind an API sees the text.
    names: list[str] = Field(default_factory=list, max_length=20)
    details: list[str] = Field(default_factory=list, max_length=40)
    temperature: float = Field(0.2, ge=0, le=2)
    max_tokens: int = Field(256, ge=1, le=1024)


ROLES = ("system", "user", "assistant")
MAX_PROMPT = 32 * 1024


def _messages(raw: list[dict]) -> list[dict]:
    """Only a role and its text go on to the model: no images, tools or other fields a client might add."""
    out = []
    for m in raw:
        role, content = m.get("role"), m.get("content")
        if role not in ROLES or not isinstance(content, str) or len(content) > MAX_PROMPT:
            raise HTTPException(422, "each message needs a role (system, user or assistant) and text content")
        out.append({"role": role, "content": content})
    return out


class Turn(BaseModel):
    role: str = Field(max_length=16)
    text: str = Field(max_length=MAX_TEXT)


class Item(BaseModel):
    id: str = Field(min_length=1, max_length=40)
    text: str = Field(max_length=MAX_TEXT)
    cues: list[str] = Field(max_length=24)


class PlanIn(BaseModel):
    turns: list[Turn] = Field(max_length=40)
    items: list[Item] = Field(min_length=1, max_length=60)
    names: list[str] = Field(default_factory=list, max_length=20)
    details: list[str] = Field(default_factory=list, max_length=40)
    # The person's language: the station puts their answer into English for its model, and a written line back.
    lang: str = Field("en", max_length=8)


class LiveIn(BaseModel):
    lang: str | None = None


class SignIn(BaseModel):
    aspect: float = Field(gt=0.1, lt=10)
    frames: list[dict] = Field(min_length=1, max_length=MAX_FRAMES)
    top: int = Field(10, ge=1, le=50)


class ArchiveIn(BaseModel):
    name: str = Field(max_length=200)
    size: int
    sha256: str


class GlossIn(BaseModel):
    text: str = Field(max_length=MAX_TEXT)
    lang: str = "en"
    vocabulary: list[str] | None = Field(None, max_length=6000)


def create(secrets: Secrets, asr: Asr, translator: Translator, llm: Llm, limit: RateLimit | None = None,
           isl: Readers | None = None, inbox: Inbox | None = None, plan_mode: str | None = None) -> FastAPI:
    # A model behind an API is large enough to write; a local one names words unless the operator says otherwise.
    plan_mode = plan_mode or ("write" if getattr(llm, "remote", False) else "words")
    app = FastAPI(title="Sign Setu brain", version=VERSION, docs_url=None, redoc_url=None, openapi_url=None)
    limit = limit or RateLimit(240, 60.0)
    # Wrong tokens are counted apart: ten a minute is plenty for a typo, far too few to guess 256 bits.
    guesses = RateLimit(10, 60.0)
    # A service that fails is taken out of the heartbeat for DOWN seconds, and the failing request gets 503 with
    # {"unavailable": service}: phones stop asking at once and wait for a heartbeat that offers it again.
    down: dict[str, float] = {}

    def mark(service: str, why: str) -> None:
        down[service] = time.monotonic() + DOWN
        print(f"{service} failed ({why}); not offered for {DOWN:.0f} s", flush=True)

    def fail(service: str, why: str):
        mark(service, why)
        raise HTTPException(503, detail={"error": why, "unavailable": service})

    def up(service: str) -> bool:
        return down.get(service, 0.0) <= time.monotonic()

    def translated(text: str, src: str, tgt: str) -> str | None:
        """[text] in [tgt]; None for a language the translator lacks or a translator that is down, never an error."""
        if not up("translate"):
            return None
        try:
            return translator.translate([text], src, tgt)[0][0]
        except ValueError:  # a language with no translation model, such as Gondi
            return None
        except Exception as e:
            mark("translate", f"{type(e).__name__}: {str(e)[:120]}")
            return None

    live = Live(asr, on_fail=lambda e: mark("live", f"{type(e).__name__}: {e}"))

    @app.middleware("http")
    async def guard(request: Request, call_next):
        host = request.client.host if request.client else None
        if not client_allowed(host):
            return JSONResponse({"error": "not on the station network"}, status_code=403)
        # Over iroh every phone arrives from loopback; the bridge names each, so one phone cannot use up another's limit.
        key = host
        if host in ("127.0.0.1", "::1") and request.headers.get(PEER_HEADER):
            key = f"iroh:{request.headers[PEER_HEADER][:64]}"
        if request.url.path != "/healthz":
            # Pieces of a live take come twice a second and are bounded by the take itself, not by this limit;
            # over iroh every phone shares the loopback address, so they would use it up.
            if not request.url.path.startswith(("/v1/live/", "/v1/archive/")) and not limit.allow(key):
                return JSONResponse({"error": "too many requests"}, status_code=429)
            if not bearer_ok(request.headers.get("authorization"), secrets.token):
                # Guesses count per address: a guesser over iroh cannot dodge the limit with fresh endpoint ids.
                if not guesses.allow(host):
                    return JSONResponse({"error": "too many requests"}, status_code=429)
                return JSONResponse({"error": "pairing token missing or wrong"}, status_code=401)
        size = request.headers.get("content-length")
        if size:
            if not size.strip().isdigit():
                return JSONResponse({"error": "bad content-length"}, status_code=400)
            if int(size) > MAX_BODY:
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
        # [caps] is what phones may ask for: they send nothing a station has not said it does (docs/18-station.md).
        caps = {"asr": asr.loaded and up("asr"), "live": asr.loaded and up("asr") and up("live"), "isl": bool(isl) and up("isl"),
                "llm": llm.available() and up("llm"), "gloss": up("gloss"), "plan": llm.available() and up("llm"), "archive": inbox is not None and up("archive")}
        return {"asr": asr.loaded, "translation": translator.loaded, "llm": caps["llm"], "llm_model": llm.model, "llm_remote": getattr(llm, "remote", False), "plan_mode": plan_mode, "refused": {"asr": asr.error, "llm": None if caps["llm"] else llm.error},
                "isl_model": isl.names[0] if isl else None,
                "isl": isl.status() if isl is not None else {"models": [], "refused": []}, "caps": caps}

    @app.post("/v1/sign")
    def read_sign(body: SignIn):
        """One sign's landmarks to the station reader's likeliest words."""
        if not isl:
            raise HTTPException(503, detail={"error": "no sign reader on this station", "unavailable": "isl"})
        try:
            return isl.read(body.frames, body.aspect, body.top)
        except ValueError as e:
            raise HTTPException(422, f"bad frames: {e}")
        except LookupError:
            raise HTTPException(503, detail={"error": "no working sign reader", "unavailable": "isl"})

    @app.post("/v1/archive")
    def archive_start(body: ArchiveIn):
        if inbox is None:
            raise HTTPException(503, "this station does not take conversations")
        try:
            return {"id": inbox.start(body.name, body.size, body.sha256)}
        except ValueError as e:
            raise HTTPException(422, str(e))
        except OSError as e:  # the inbox's disk is full or gone
            fail("archive", f"{type(e).__name__}: {e}")

    @app.post("/v1/archive/{tid}")
    async def archive_piece(tid: str, at: int, request: Request):
        if inbox is None:
            raise HTTPException(503, "this station does not take conversations")
        data = await request.body()
        if len(data) > MAX_PIECE:
            raise HTTPException(413, "piece too long")
        try:
            held = inbox.add(tid, at, data)
        except ValueError as e:
            raise HTTPException(409, str(e))
        except OSError as e:
            fail("archive", f"{type(e).__name__}: {e}")
        if held is None:
            raise HTTPException(404, "no such transfer")
        return {"held": held}

    @app.post("/v1/archive/{tid}/done")
    def archive_done(tid: str):
        if inbox is None:
            raise HTTPException(503, "this station does not take conversations")
        try:
            name = inbox.done(tid)
        except ValueError as e:
            raise HTTPException(422, str(e))
        except OSError as e:
            fail("archive", f"{type(e).__name__}: {e}")
        if name is None:
            raise HTTPException(404, "no such transfer")
        return {"saved": name, "path": str(inbox.folder / name)}

    @app.post("/v1/asr")
    async def recognise(audio: UploadFile = File(...), lang: str | None = Form(None), to: str | None = Form(None)):
        data = await audio.read(MAX_BODY + 1)
        if len(data) > MAX_BODY:
            raise HTTPException(413, "audio too long")
        # The audio lives only in this request's memory: nothing is written to disk or kept.
        try:
            out = asr.transcribe(data, lang)
        except ValueError as e:
            raise HTTPException(422, str(e))
        except Exception as e:
            fail("asr", f"{type(e).__name__}: {e}")
        finally:
            del data
        # With [to], the words are also given in that language, so the officer can read what was said.
        if to and out["text"] and out["lang"] != to:
            try:
                texts, engine = translator.translate([out["text"]], out["lang"], to)
                out["translation"] = {"lang": to, "text": texts[0], "engine": engine}
            except Exception:
                pass  # a language the translator does not cover, or a translator that failed: the words alone go back
        return out

    @app.post("/v1/live")
    def live_start(body: LiveIn):
        if not (asr.loaded and up("asr") and up("live")):
            raise HTTPException(503, detail={"error": "live speech is down", "unavailable": "live"})
        try:
            return {"id": live.start(body.lang)}
        except OverflowError:
            raise HTTPException(503, "too many live takes")

    @app.post("/v1/live/{tid}")
    async def live_feed(tid: str, request: Request):
        """A piece of 16 kHz mono 16-bit PCM; returns the words so far, and end once the person has stopped."""
        data = await request.body()
        if len(data) > MAX_CHUNK:
            raise HTTPException(413, "piece too long")
        if not up("live"):
            live.stop(tid)
            raise HTTPException(503, detail={"error": "live speech is down", "unavailable": "live"})
        out = live.feed(tid, data)
        if out is None:
            raise HTTPException(404, "no such take")
        return out

    @app.post("/v1/live/{tid}/stop")
    def live_stop(tid: str):
        out = live.stop(tid)
        if out is None:
            raise HTTPException(404, "no such take")
        return out

    @app.post("/v1/translate")
    def translate(body: TranslateIn):
        if any(len(t) > MAX_TEXT for t in body.texts):
            raise HTTPException(413, "text too long")
        try:
            out, engine = translator.translate(body.texts, body.src, body.tgt)
        except ValueError as e:
            raise HTTPException(422, str(e))
        except Exception as e:  # a model that is missing or will not load: the reason goes back, not a bare 500
            raise HTTPException(503, detail={"error": f"translation unavailable: {type(e).__name__}: {str(e)[:200]}", "unavailable": "translate"})
        return {"texts": out, "engine": engine}

    @app.post("/v1/chat")
    def chat(body: ChatIn):
        if not (llm.available() and up("llm")):
            raise HTTPException(503, detail={"error": "no local language model", "unavailable": "llm"})
        messages = _messages(body.messages)
        try:
            return {"text": masked(llm, body.names, body.details).chat(messages, body.temperature, body.max_tokens), "model": llm.model}
        except Exception as e:
            fail("llm", f"{type(e).__name__}: {e}")

    @app.post("/v1/plan")
    def plan_next(body: PlanIn):
        """The item among [items] that meets the person's last answer, or (write mode) a short sentence of the model's own
        for the phone to check; both null when nothing fits."""
        if not (llm.available() and up("llm")):
            raise HTTPException(503, detail={"error": "no local language model", "unavailable": "plan"})
        try:
            out = planning.plan(masked(llm, body.names, body.details), [t.model_dump() for t in body.turns], [i.model_dump() for i in body.items],
                                plan_mode, body.lang, translated)
        except Exception as e:
            fail("llm", f"{type(e).__name__}: {e}")
        return {**out, "model": llm.model}

    @app.post("/v1/gloss")
    def gloss(body: GlossIn):
        text = body.text
        if body.lang != "en":
            try:
                text = translator.translate([text], body.lang, "en")[0][0]
            except ValueError as e:
                raise HTTPException(422, str(e))
        try:
            out = glossing.expand(text, body.vocabulary, masked(llm) if llm.available() and up("llm") else None)
        except Exception as e:
            # The model's refinement failed: the rules alone still give an order.
            mark("llm", f"{type(e).__name__}: {e}")
            try:
                out = glossing.expand(text, body.vocabulary, None)
            except Exception as e2:
                fail("gloss", f"{type(e2).__name__}: {e2}")
        out["english"] = text
        return out

    return app
