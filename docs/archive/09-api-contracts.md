# 09. Integration Contracts

The app talks only to the project gateway. This document defines (1) the gateway API the app uses and (2) what the gateway needs from each partner. Partner sections marked [confirm] are filled in from the partners' sample payloads in phase 0.

## 1. Common rules

| Rule | Value |
| --- | --- |
| Base URL | `https://<gateway-host>/v1/` per environment |
| Transport | HTTPS, TLS 1.2+, certificate pinned (SEC-31) |
| Auth | `POST /v1/attest` with key attestation chain returns a 1-hour token; then header `Authorization: Bearer <token>` |
| Format | JSON, UTF-8; audio as `audio/ogg; codecs=opus` or `audio/wav` 16 kHz mono [confirm with Bhashini] |
| Language codes | ISO 639-1 where available (`hi`, `en`, `bn`, `ta`, `te`, `mr`, `gu`, `kn`, `ml`, `pa`, `or`, `as`) |
| Request ID | Header `X-Request-Id: <uuid>`, echoed in response and in gateway logs |
| Timeout | App 8 s, one retry on network error or 5xx; no retry on 4xx |
| Size limits | Text 500 characters; audio 30 s; landmark window 90 frames |
| Errors | `{ "error": { "code": "STRING", "message": "safe text", "retryable": bool } }`; never stack traces or internals (CERT-In 6.1.8) |

Error codes: `INVALID_REQUEST`, `TOO_LARGE`, `UNSUPPORTED_LANGUAGE`, `INTEGRITY_FAILED`, `RATE_LIMITED`, `UPSTREAM_TIMEOUT`, `UPSTREAM_ERROR`, `NO_SIGN_DETECTED`.

## 2. Text to sign

`POST /v1/sign`

```json
{ "text": "Do you need water?", "lang": "en" }
```

Response (internal stable shape; the gateway or app adapter converts the ISL team's format into this):

```json
{
  "fps": 25,
  "frames": [
    {
      "pose": [[x, y, z, visibility], ...],   // 33 points or partner's set
      "left":  [[x, y, z], ...] | null,        // 21 points
      "right": [[x, y, z], ...] | null,        // 21 points
      "face":  [[x, y, z], ...] | null         // subset incl. mouth and brows
    }
  ],
  "coords": "normalised_image",                 // 0..1, origin top-left
  "unknown": ["word1"],                         // words with no sign
  "gloss": ["WATER", "YOU", "NEED", "Q"]        // optional, for display/debug
}
```

**Needed from the ISL team [confirm]:** landmark set and order, coordinate system and scale, frame rate, whether face points (mouth, eyebrows) are included, how fingerspelling is represented, how unknown words are reported, input languages accepted, max text length, typical latency, error format, vocabulary list. Written confirmation that request text is not stored.

## 3. Preset signs (bundled, no API)

Triage questions use recorded clips, not the API.

```
assets/isl/<question_id>.mp4     H.264, 480p, silent, <= 6 s
assets/content/triage.json       question_id, group, sensitivity, captions{lang}, cards[]
```

## 4. Sign to text

`POST /v1/recognise`

```json
{
  "fps": 15,
  "frames": [ { "pose": [...], "left": [...] | null, "right": [...] | null } ],
  "vocab": "triage_v1"
}
```

Response:

```json
{ "results": [ { "text": "water", "lang": "en", "confidence": 0.82 } ] }
```

Or `NO_SIGN_DETECTED`. The app maps confidence to High (>= 0.8), Medium (0.5-0.8), Low (< 0.5) [tune with ML team].

**Needed from the ML team [confirm]:** landmark model and version they trained on (must match MediaPipe Tasks output), expected frame rate and window length, vocabulary, whether face landmarks are needed, output format, latency, accuracy on held-out signers, written confirmation that landmarks are not stored.

## 5. Speech to text

`POST /v1/speech?lang=hi` with audio body.

```json
{ "text": "kya aapko paani chahiye", "lang": "hi" }
```

Gateway calls Bhashini ASR. **Needed [confirm]:** Bhashini account and pipeline IDs, supported languages and audio formats, latency, data retention terms in writing.

## 6. Translation

`POST /v1/translate`

```json
{ "text": "Do you need water?", "from": "en", "to": "ta" }
```

```json
{ "text": "...", "from": "en", "to": "ta" }
```

Used only for free-text captions. Preset content is pre-translated and reviewed.

## 7. Versioning

- Breaking changes create `/v2/`; `/v1/` stays available for at least two app release cycles (in line with the Government of India Open API policy on backward compatibility).
- Partner format changes are absorbed in the gateway adapters; the app contract changes only by version.

## 8. Fakes for development

`app/src/test/assets/fakes/` holds one sample response per endpoint, including error cases, slow responses (simulated delay) and malformed payloads. The fake implementations read these so the app runs fully without a network.
