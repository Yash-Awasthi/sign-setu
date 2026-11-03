# Sign Setu gateway (Go)

The department gateway the app uses while online. It replaced the earlier Python gateway with the same API, the same PostgreSQL tables and the same environment variables, so the app and an existing database carry over unchanged. A build without `gatewayUrl` never connects, and the app works the same without it.

| Route | Caller | Purpose |
| --- | --- | --- |
| `GET /healthz` | Host, app warm-up | Liveness; never touches the database |
| `GET /readyz` | Operator | Readiness; 503 when the database does not answer |
| `POST /v1/enroll` | Phone | Trades a one-time enrolment code for a device token |
| `POST /v1/assist` | Phone | Proxy to the AI service; the key stays here and no text is stored. The system prompt must match a template in `app_prompts.json` (language names fill the marked slots: one line, up to 80 characters), so a caller cannot add instructions |
| `POST /v1/speak` | Phone | Typed text (up to 500 characters) and a language code to MP3, streamed as it arrives: Cartesia, in the same voice per language as the recordings; 422 for a language with no voice, 503 when its key is unset. The text is never stored |
| `POST /v1/contrib` | Phone | Opted-in training records (1 to 50 per call, up to 2 MB): landmarks, glosses and corrections only, kept as JSON lines in one file per signer under `CONTRIB_DIR`. The signer id (16 to 64 of `a-z`, `0-9`, `-`) and a consent version are required; any other field is refused with 422. Allows `CONTRIB_PER_MINUTE` per device |
| `DELETE /v1/contrib/{signer}` | Phone | Deletes that signer's file; deleting what is not there succeeds too. The signer id is the only key to the records, so logs never contain it |
| `GET /v1/admin/devices`, `POST /v1/admin/devices/{id}/revoke` | Administrator | Keycloak access token with the `gateway-admin` realm role (`ADMIN_ROLE` renames it). Lists device ids, revoked flag and last seen (to the hour), never tokens; `limit` 1–1000, default 200. A revoked device gets 401 |
| `GET /v1/admin/audit` | Administrator | Same role. Newest first; `limit` 1–1000, `before` pages by id (1 to 2147483647; larger is 422), `action` and `device` filter. Ids, times, device ids and actions only. The read is itself audited |

Phone routes need the device token and allow 30 requests a minute per device. Enrolment allows `ENROLL_PER_MINUTE` requests per client address; `ENROLL_FAILURES_PER_MINUTE` wrong codes lock that address, and `ENROLL_FAILURES_GLOBAL_PER_MINUTE` wrong codes from all addresses lock everyone. Per-address limits treat a whole IPv6 /64 as one address. A lockout answers 429 with `Retry-After` and ends a minute after the last wrong code; refused requests are not counted. The first lockout is audited as `enroll.locked`. Bodies over 256 KB (2 MB for `/v1/contrib`) are refused with 413. If the AI service answers 429, the phone gets 429 `assistant busy` and waits before its one retry.

Every request has a 70 second deadline and a speech stream 60 seconds; the server's write timeout is 75 seconds and headers may be up to 64 KiB. Every JSON reply carries `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`. Bearer tokens use the `Bearer` scheme (case-insensitive); anything else gets 401 `token required`. Admin tokens are accepted with 30 seconds of clock drift. Request logs record the route pattern (for example `DELETE /v1/contrib/{signer}`), never the raw path.

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
| `CONTRIB_DIR`, `CONTRIB_PER_MINUTE` | `/data/contrib`, `6` | Where contribution files go (a persistent volume), and the per-device limit |
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

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| Start fails with `DATABASE_URL is not set` or `not a valid number: NAME` | A required variable is missing or a numeric variable is not a whole number at or above its minimum |
| Start fails after about 30 seconds | The database did not answer during schema setup; check `DATABASE_URL` and the network |
| `/readyz` returns 503 while `/healthz` returns 200 | The process is up but PostgreSQL does not answer |
| Every phone is refused at enrolment | `ENROLL_HASHES` is empty or holds a hash of a different code; make codes with `gateway codes N` |
| Enrolment returns 429 | A lockout from wrong codes; it ends a minute after the last wrong code. Behind a proxy, set `TRUSTED_PROXY_HOPS`, or every caller shares the proxy's address |
| Admin routes return 404 | `OIDC_ISSUER` is empty, which turns them off |
| Admin routes return 401 | The token is not RS256 or ES256, has no expiry, the audience differs from `OIDC_AUDIENCE`, or the `ADMIN_ROLE` realm role is missing |
| `/v1/speak` returns 422 or 503 | 422: no voice for that language. 503: `CARTESIA_API_KEY` is unset |
| `/v1/assist` returns 422 | The system prompt does not match a template in `app_prompts.json`; redeploy after a prompt change |
| Contributions are lost on redeploy | `CONTRIB_DIR` is not on a persistent volume |

## Deploy

`Dockerfile` builds a static binary on a distroless, non-root image (about 15 MB). `render.yaml` builds it on Render from `gateway/`. The image copies every `*.go` file: `gateway.go` (core, enrolment, assist, speak), `admin.go`, `httpio.go` (reply, limits and logging helpers), `auth.go`, `contrib.go`, `upstream.go`, `store.go` and `config.go`.
