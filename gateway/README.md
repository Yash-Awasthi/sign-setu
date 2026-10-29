# Sign Setu gateway (Go)

The department gateway the app uses while online. It replaced the earlier Python gateway with the same API, the same PostgreSQL tables and the same environment variables, so the app and an existing database carry over unchanged. A build without `gatewayUrl` never connects, and the app works the same without it.

| Route | Caller | Purpose |
| --- | --- | --- |
| `GET /healthz` | Host, app warm-up | Liveness; never touches the database |
| `GET /readyz` | Operator | Readiness; 503 when the database does not answer |
| `POST /v1/enroll` | Phone | Trades a one-time enrolment code for a device token |
| `POST /v1/assist` | Phone | Proxy to the AI service; the key stays here and no text is stored. The system prompt must match a template in `app_prompts.json` (language names fill the marked slots: one line, up to 80 characters), so a caller cannot add instructions |
| `POST /v1/speak` | Phone | Typed text (up to 500 characters) and a language code to MP3, streamed as it arrives: Cartesia, in the same voice per language as the recordings; 422 for a language with no voice, 503 when its key is unset. The text is never stored |
| `GET /v1/admin/devices`, `POST /v1/admin/devices/{id}/revoke` | Administrator | Keycloak access token with the `gateway-admin` realm role (`ADMIN_ROLE` renames it). Lists device ids, revoked flag and last seen (to the hour), never tokens; `limit` 1–1000, default 200. A revoked device gets 401 |
| `GET /v1/admin/audit` | Administrator | Same role. Newest first; `limit` 1–1000, `before` pages by id, `action` and `device` filter. Ids, times, device ids and actions only. The read is itself audited |

Phone routes need the device token and allow 30 requests a minute per device. Enrolment allows `ENROLL_PER_MINUTE` requests per client address; `ENROLL_FAILURES_PER_MINUTE` wrong codes lock that address, and `ENROLL_FAILURES_GLOBAL_PER_MINUTE` wrong codes from all addresses lock everyone. A lockout answers 429 with `Retry-After` and ends a minute after the last wrong code; refused requests are not counted. The first lockout is audited as `enroll.locked`. Bodies over 256 KB are refused with 413.

`app_prompts.json` is written by the app's `GatewayPromptContractTest` (`UPDATE_PROMPT_FIXTURE=1`) and built into the binary, so a prompt change needs a redeploy.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `DATABASE_URL` | required | PostgreSQL URL; `postgres://`, `postgresql://` and the old `postgresql+psycopg://` all work. A secret |
| `PORT` | `8000` | Listen port; the host sets it |
| `ENROLL_HASHES` | empty | Comma-separated SHA-256 hex of each issued code; empty means no phone can enrol |
| `ASSIST_URL`, `ASSIST_KEY`, `ASSIST_MODEL` | empty | AI service. A URL ending in `/responses` uses the Responses API; anything else Chat Completions |
| `ASSIST_REASONING_EFFORT` | empty | `low` for gpt-oss models |
| `CARTESIA_API_KEY` | empty | Key for `/v1/speak`. Secret |
| `OIDC_ISSUER`, `OIDC_AUDIENCE`, `ADMIN_ROLE` | empty, `signsetu-portal`, `gateway-admin` | Keycloak realm URL; empty turns the admin routes off. Tokens must be RS256 or ES256 and carry an expiry |
| `TOKEN_TTL_DAYS` | `0` | Days a device token lasts after enrolment; 0 is no expiry |
| `TRUSTED_PROXY_HOPS` | `0` | Proxies that append to `X-Forwarded-For`. With 0 the header is ignored; a wrong value lets callers choose their own address |
| `ENROLL_PER_MINUTE`, `ENROLL_FAILURES_PER_MINUTE`, `ENROLL_FAILURES_GLOBAL_PER_MINUTE` | `10`, `20`, `200` | Enrolment limits above |

Rate limits are kept in memory, per instance; move them to a shared store before running more than one.

## Run and test

Go 1.27 or later, from `gateway/`:

```
go test ./...                       # handler tests use an in-memory store
TEST_DATABASE_URL=postgres://... go test ./...   # also runs the PostgreSQL tests, each in its own schema
DATABASE_URL=postgres://... go run .
```

CI also runs `go test -race`, the three fuzz tests, `staticcheck` and `govulncheck`.

## Maintenance

```
gateway codes 10                                  # ten enrolment codes and their ENROLL_HASHES line
DATABASE_URL=... gateway prune-audit --days 90         # reports, deletes nothing
DATABASE_URL=... gateway prune-audit --days 90 --apply # deletes audit rows older than 90 days
```

`prune-audit` takes `--before YYYY-MM-DD` (UTC) instead of `--days`, refuses a future cutoff and audits each applied run as `audit.pruned`. It never touches the `feedback` table left by the Python gateway.

## Deploy

`Dockerfile` builds a static binary on a distroless, non-root image (about 15 MB). `render.yaml` builds it on Render from `gateway/`.
