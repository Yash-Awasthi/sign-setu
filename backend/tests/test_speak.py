import hashlib

from fastapi.testclient import TestClient

from signsetu_gateway.app import create_app, speech_provider
from signsetu_gateway.config import Settings
from signsetu_gateway.db import Audit

CODE = "enrol-code-1234"


def client(**extra):
    s = Settings(database_url="sqlite://", enroll_hashes=frozenset({hashlib.sha256(CODE.encode()).hexdigest()}), **extra)

    async def speak(text, lang):
        return f"{lang}:{text}".encode()
    return TestClient(create_app(s, speak=speak))


def token(c):
    return {"Authorization": f"Bearer {c.post('/v1/enroll', json={'code': CODE}).json()['token']}"}


def test_speak_returns_audio_for_an_enrolled_device_and_stores_no_text():
    with client() as c:
        assert c.post("/v1/speak", json={"text": "पानी", "lang": "hi"}).status_code == 401
        r = c.post("/v1/speak", json={"text": "पानी", "lang": "hi"}, headers=token(c))
        assert r.status_code == 200
        assert r.headers["content-type"] == "audio/mpeg"
        assert r.content == "hi:पानी".encode()
        with c.app.state.sessions() as db:
            rows = db.query(Audit).filter(Audit.action == "speak").all()
            assert len(rows) == 1 and "पानी" not in repr(vars(rows[0]))


def test_speak_refuses_long_text_and_odd_language_codes():
    with client() as c:
        h = token(c)
        assert c.post("/v1/speak", json={"text": "x" * 501, "lang": "en"}, headers=h).status_code == 422
        assert c.post("/v1/speak", json={"text": "hi", "lang": "../en"}, headers=h).status_code == 422


def test_english_and_hindi_use_elevenlabs_and_others_cartesia_where_it_has_the_language():
    assert speech_provider("en") == speech_provider("hi") == "elevenlabs"
    assert speech_provider("ta") == speech_provider("hne") == "cartesia"
    assert speech_provider("as") is None


def test_speak_without_provider_keys_answers_503():
    s = Settings(database_url="sqlite://", enroll_hashes=frozenset({hashlib.sha256(CODE.encode()).hexdigest()}))
    with TestClient(create_app(s)) as c:
        assert c.post("/v1/speak", json={"text": "hello", "lang": "en"}, headers=token(c)).status_code == 503
