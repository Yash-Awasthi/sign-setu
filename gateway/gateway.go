package main

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"regexp"
	"strconv"
	"time"
	"unicode/utf8"
)

// The largest legitimate body is an assist request: 4,000 system and 20,000 user characters, each up to six bytes once
// JSON-escaped. Anything larger is refused before it is read.
const maxBodyBytes = 256 << 10

const lastSeenResolution = time.Hour

var langCode = regexp.MustCompile(`^[a-z]{2,3}$`)

type Gateway struct {
	cfg    Config
	store  Store
	assist Assist
	speak  Speak
	keys   KeySource
	now    func() time.Time
	log    *slog.Logger

	limiter, enrollLimiter, enrollFailures, enrollFailuresAll *RateLimiter
}

func newGateway(cfg Config, store Store, assist Assist, speak Speak, keys KeySource, now func() time.Time, log *slog.Logger) *Gateway {
	return &Gateway{
		cfg: cfg, store: store, assist: assist, speak: speak, keys: keys, now: now, log: log,
		limiter:           newRateLimiter(cfg.RatePerMinute, now),
		enrollLimiter:     newRateLimiter(cfg.EnrollPerMinute, now),
		enrollFailures:    newRateLimiter(cfg.EnrollFailuresPerMinute, now),
		enrollFailuresAll: newRateLimiter(cfg.EnrollFailuresGlobalPerMinute, now),
	}
}

func (g *Gateway) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", g.healthz)
	mux.HandleFunc("GET /readyz", g.readyz)
	mux.HandleFunc("POST /v1/enroll", g.enroll)
	mux.HandleFunc("POST /v1/assist", g.device(g.assistRoute))
	mux.HandleFunc("POST /v1/speak", g.device(g.speakRoute))
	mux.HandleFunc("GET /v1/admin/devices", g.admin(g.adminDevices))
	mux.HandleFunc("POST /v1/admin/devices/{id}/revoke", g.admin(g.adminRevoke))
	mux.HandleFunc("GET /v1/admin/audit", g.admin(g.adminAudit))
	return g.recoverAndLog(limitBody(mux))
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}

func fail(w http.ResponseWriter, status int, detail string) {
	writeJSON(w, status, map[string]string{"detail": detail})
}

func (g *Gateway) internal(w http.ResponseWriter, r *http.Request, err error) {
	g.log.Error("request failed", "path", r.URL.Path, "err", err)
	fail(w, http.StatusInternalServerError, "internal error")
}

func refuse(w http.ResponseWriter, l *RateLimiter, key string) {
	w.Header().Set("Retry-After", strconv.Itoa(l.RetryAfter(key)))
	fail(w, http.StatusTooManyRequests, "rate limit")
}

func limitBody(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.ContentLength > maxBodyBytes {
			fail(w, http.StatusRequestEntityTooLarge, "too large")
			return
		}
		// A chunked body with no length is cut off at the cap while it is decoded.
		r.Body = http.MaxBytesReader(w, r.Body, maxBodyBytes)
		next.ServeHTTP(w, r)
	})
}

type statusWriter struct {
	http.ResponseWriter
	status int
}

func (s *statusWriter) WriteHeader(code int) {
	if s.status == 0 {
		s.status = code
	}
	s.ResponseWriter.WriteHeader(code)
}

func (s *statusWriter) Write(b []byte) (int, error) {
	if s.status == 0 {
		s.status = http.StatusOK
	}
	return s.ResponseWriter.Write(b)
}

func (s *statusWriter) Unwrap() http.ResponseWriter { return s.ResponseWriter }

// recoverAndLog logs method, path, status and time only: never a header, a token or a body.
func (g *Gateway) recoverAndLog(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		sw := &statusWriter{ResponseWriter: w}
		defer func() {
			if p := recover(); p != nil {
				if p == http.ErrAbortHandler {
					panic(p)
				}
				g.log.Error("panic", "path", r.URL.Path, "panic", p)
				if sw.status == 0 {
					fail(sw, http.StatusInternalServerError, "internal error")
				}
			}
			g.log.Info("request", "method", r.Method, "path", r.URL.Path, "status", sw.status, "ms", time.Since(start).Milliseconds())
		}()
		next.ServeHTTP(sw, r)
	})
}

// decode reads a JSON body into v; false after it has answered 413 or 422.
func decode(w http.ResponseWriter, r *http.Request, v any) bool {
	err := json.NewDecoder(r.Body).Decode(v)
	var tooLarge *http.MaxBytesError
	switch {
	case errors.As(err, &tooLarge):
		fail(w, http.StatusRequestEntityTooLarge, "too large")
		return false
	case err != nil:
		fail(w, http.StatusUnprocessableEntity, "invalid body")
		return false
	}
	return true
}

func chars(s string) int { return utf8.RuneCountInString(s) }

func (g *Gateway) healthz(w http.ResponseWriter, r *http.Request) {
	// Liveness only: a database blip must not make the host kill a process that is still serving the app.
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func (g *Gateway) readyz(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()
	if err := g.store.Ping(ctx); err != nil {
		fail(w, http.StatusServiceUnavailable, "database unavailable")
		return
	}
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func (g *Gateway) enroll(w http.ResponseWriter, r *http.Request) {
	who := clientKey(r, g.cfg.TrustedProxyHops)
	if !g.enrollLimiter.Allow(who) {
		refuse(w, g.enrollLimiter, who)
		return
	}
	// Wrong codes lock out the address that sent them; a much higher cap across all callers bounds guessing from many
	// addresses without letting one caller lock every officer out.
	if g.enrollFailures.Exceeded(who) {
		refuse(w, g.enrollFailures, who)
		return
	}
	if g.enrollFailuresAll.Exceeded("all") {
		refuse(w, g.enrollFailuresAll, "all")
		return
	}
	var body struct {
		Code *string `json:"code"`
	}
	if !decode(w, r, &body) {
		return
	}
	if body.Code == nil || chars(*body.Code) < 8 || chars(*body.Code) > 128 {
		fail(w, http.StatusUnprocessableEntity, "code must be 8 to 128 characters")
		return
	}
	codeHash := sha256Hex(*body.Code)
	if !g.cfg.EnrollHashes[codeHash] {
		// Recording the failure is also the check, so concurrent wrong codes cannot slip past the cap together.
		ok, filled := g.enrollFailures.Take(who)
		if !ok {
			refuse(w, g.enrollFailures, who)
			return
		}
		okAll, filledAll := g.enrollFailuresAll.Take("all")
		if !okAll {
			refuse(w, g.enrollFailuresAll, "all")
			return
		}
		if filled || filledAll {
			// The wrong code that reaches the cap; later ones are refused without touching the database.
			if err := g.store.Audit(r.Context(), "enroll.locked", ""); err != nil {
				g.log.Error("audit failed", "action", "enroll.locked", "err", err)
			}
		}
		fail(w, http.StatusForbidden, "bad enrolment code")
		return
	}
	token := newToken()
	dev := Device{ID: newToken()[:32], TokenHash: sha256Hex(token), CreatedAt: g.now().UTC()}
	switch err := g.store.Enroll(r.Context(), dev, codeHash); {
	case errors.Is(err, errCodeUsed):
		// A used code answers exactly like a wrong one.
		fail(w, http.StatusForbidden, "bad enrolment code")
	case err != nil:
		g.internal(w, r, err)
	default:
		writeJSON(w, http.StatusOK, map[string]string{"device_id": dev.ID, "token": token})
	}
}

type deviceHandler func(w http.ResponseWriter, r *http.Request, dev Device)

func (g *Gateway) device(next deviceHandler) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		token := bearer(r)
		if token == "" {
			fail(w, http.StatusUnauthorized, "token required")
			return
		}
		dev, err := g.store.DeviceByToken(r.Context(), sha256Hex(token))
		if errors.Is(err, errNotFound) || (err == nil && dev.Revoked) {
			fail(w, http.StatusUnauthorized, "unknown device")
			return
		}
		if err != nil {
			g.internal(w, r, err)
			return
		}
		now := g.now().UTC()
		if ttl := g.cfg.TokenTTLDays; ttl > 0 && !dev.CreatedAt.AddDate(0, 0, ttl).After(now) {
			fail(w, http.StatusUnauthorized, "token expired")
			return
		}
		if !g.limiter.Allow(dev.ID) {
			refuse(w, g.limiter, dev.ID)
			return
		}
		// Written at most once an hour, so authenticating a request is not a database write each time.
		if dev.LastSeen == nil || now.Sub(*dev.LastSeen) >= lastSeenResolution {
			if err := g.store.TouchLastSeen(r.Context(), dev.ID, now); err != nil {
				g.internal(w, r, err)
				return
			}
		}
		next(w, r, dev)
	}
}

func (g *Gateway) answerUpstream(w http.ResponseWriter, r *http.Request, err error) {
	var ue *upstreamError
	if errors.As(err, &ue) {
		fail(w, ue.status, ue.detail)
		return
	}
	g.internal(w, r, err)
}

func (g *Gateway) assistRoute(w http.ResponseWriter, r *http.Request, dev Device) {
	var body struct {
		System    *string `json:"system"`
		User      *string `json:"user"`
		MaxTokens *int    `json:"max_tokens"`
	}
	if !decode(w, r, &body) {
		return
	}
	maxTokens := 800
	if body.MaxTokens != nil {
		maxTokens = *body.MaxTokens
	}
	if body.System == nil || body.User == nil || chars(*body.System) > 4000 || maxTokens < 1 || maxTokens > 2000 {
		fail(w, http.StatusUnprocessableEntity, "invalid body")
		return
	}
	if chars(*body.User) > g.cfg.AssistMaxChars {
		fail(w, http.StatusRequestEntityTooLarge, "too long")
		return
	}
	if !promptAllowed(*body.System) {
		fail(w, http.StatusForbidden, "prompt not allowed")
		return
	}
	text, err := g.assist(r.Context(), *body.System, *body.User, maxTokens)
	if err != nil {
		g.answerUpstream(w, r, err)
		return
	}
	// The text itself is never stored.
	if err := g.store.Audit(r.Context(), "assist", dev.ID); err != nil {
		g.internal(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"text": text})
}

func (g *Gateway) speakRoute(w http.ResponseWriter, r *http.Request, dev Device) {
	var body struct {
		Text *string `json:"text"`
		Lang *string `json:"lang"`
	}
	if !decode(w, r, &body) {
		return
	}
	if body.Text == nil || body.Lang == nil || chars(*body.Text) < 1 || chars(*body.Text) > 500 || !langCode.MatchString(*body.Lang) {
		fail(w, http.StatusUnprocessableEntity, "invalid body")
		return
	}
	audio, err := g.speak(r.Context(), *body.Text, *body.Lang)
	if err != nil {
		g.answerUpstream(w, r, err)
		return
	}
	defer audio.Close()
	// The first bytes arrive before the status is sent, so an empty reply is still a 502 and not an empty 200.
	buf := bufio.NewReaderSize(audio, 32<<10)
	if _, err := buf.Peek(1); err != nil {
		fail(w, http.StatusBadGateway, "speech failed")
		return
	}
	// The text itself is never stored.
	if err := g.store.Audit(r.Context(), "speak", dev.ID); err != nil {
		g.internal(w, r, err)
		return
	}
	w.Header().Set("Content-Type", "audio/mpeg")
	w.WriteHeader(http.StatusOK)
	// Streamed as it arrives; a provider that drops mid-stream ends the reply short, which the phone treats as failed.
	if _, err := io.Copy(w, io.LimitReader(buf, 16<<20)); err != nil {
		g.log.Warn("speech stream cut", "err", err)
	}
}

func (g *Gateway) admin(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if status, detail := roleClaims(r.Context(), g.keys, g.cfg, bearer(r), g.cfg.AdminRole); status != 0 {
			fail(w, status, detail)
			return
		}
		next(w, r)
	}
}

// isoTime matches the times the Python gateway returned: microseconds when there are any, and +00:00 for UTC.
func isoTime(t time.Time) string {
	t = t.UTC().Truncate(time.Microsecond)
	if t.Nanosecond() == 0 {
		return t.Format("2006-01-02T15:04:05-07:00")
	}
	return t.Format("2006-01-02T15:04:05.000000-07:00")
}

// queryInt reads an optional integer in [min, max]; false after it has answered 422.
func queryInt(w http.ResponseWriter, r *http.Request, name string, def, min, max int64) (int64, bool) {
	raw := r.URL.Query().Get(name)
	if raw == "" {
		return def, true
	}
	n, err := strconv.ParseInt(raw, 10, 64)
	if err != nil || n < min || n > max {
		fail(w, http.StatusUnprocessableEntity, name+" is out of range")
		return 0, false
	}
	return n, true
}

func (g *Gateway) adminDevices(w http.ResponseWriter, r *http.Request) {
	limit, ok := queryInt(w, r, "limit", 200, 1, 1000)
	if !ok {
		return
	}
	devs, err := g.store.Devices(r.Context(), int(limit))
	if err == nil {
		// Listing devices reveals the department's fleet, so the read is audited.
		err = g.store.Audit(r.Context(), "admin.devices", "")
	}
	if err != nil {
		g.internal(w, r, err)
		return
	}
	type row struct {
		ID        string  `json:"id"`
		CreatedAt string  `json:"created_at"`
		Revoked   bool    `json:"revoked"`
		LastSeen  *string `json:"last_seen"`
	}
	out := make([]row, len(devs))
	for i, d := range devs {
		out[i] = row{ID: d.ID, CreatedAt: isoTime(d.CreatedAt), Revoked: d.Revoked}
		if d.LastSeen != nil {
			// Hour precision; an exact second is more than an administrator needs.
			s := isoTime(d.LastSeen.Truncate(time.Hour))
			out[i].LastSeen = &s
		}
	}
	writeJSON(w, http.StatusOK, out)
}

func (g *Gateway) adminRevoke(w http.ResponseWriter, r *http.Request) {
	switch err := g.store.Revoke(r.Context(), r.PathValue("id")); {
	case errors.Is(err, errNotFound):
		fail(w, http.StatusNotFound, "no such device")
	case err != nil:
		g.internal(w, r, err)
	default:
		writeJSON(w, http.StatusOK, map[string]any{})
	}
}

func (g *Gateway) adminAudit(w http.ResponseWriter, r *http.Request) {
	limit, ok := queryInt(w, r, "limit", 200, 1, 1000)
	if !ok {
		return
	}
	before, ok := queryInt(w, r, "before", 0, 1, 1<<62)
	if !ok {
		return
	}
	q := r.URL.Query()
	action, device := q.Get("action"), q.Get("device")
	if (q.Has("action") && (chars(action) < 1 || chars(action) > 32)) || chars(device) > 32 {
		fail(w, http.StatusUnprocessableEntity, "filter is out of range")
		return
	}
	rows, err := g.store.AuditRows(r.Context(), AuditFilter{Limit: int(limit), Before: before, Action: action, DeviceID: device})
	if err == nil {
		// The audit read is itself audited; it shows from the next read onwards.
		err = g.store.Audit(r.Context(), "admin.audit", "")
	}
	if err != nil {
		g.internal(w, r, err)
		return
	}
	type row struct {
		ID       int64  `json:"id"`
		At       string `json:"at"`
		DeviceID string `json:"device_id"`
		Action   string `json:"action"`
	}
	out := make([]row, len(rows))
	for i, a := range rows {
		out[i] = row{a.ID, isoTime(a.At), a.DeviceID, a.Action}
	}
	writeJSON(w, http.StatusOK, out)
}
