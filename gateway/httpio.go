package main

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"time"
	"unicode/utf8"
)

func writeJSON(w http.ResponseWriter, status int, v any) {
	// Replies carry device tokens and the fleet list, so no proxy or browser may keep a copy.
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}

func fail(w http.ResponseWriter, status int, detail string) {
	writeJSON(w, status, map[string]string{"detail": detail})
}

func (g *Gateway) internal(w http.ResponseWriter, r *http.Request, err error) {
	g.log.Error("request failed", "path", route(r), "err", err)
	fail(w, http.StatusInternalServerError, "internal error")
}

func refuse(w http.ResponseWriter, l *RateLimiter, key string) {
	w.Header().Set("Retry-After", strconv.Itoa(l.RetryAfter(key)))
	fail(w, http.StatusTooManyRequests, "rate limit")
}

func limitBody(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		limit := int64(maxBodyBytes)
		if r.URL.Path == "/v1/contrib" {
			limit = maxContribBytes
		}
		if r.ContentLength > limit {
			fail(w, http.StatusRequestEntityTooLarge, "too large")
			return
		}
		// A chunked body with no length is cut off at the cap while it is decoded.
		r.Body = http.MaxBytesReader(w, r.Body, limit)
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

// route is the matched pattern, so a signer id (the only key to a signer's records) never reaches the logs.
func route(r *http.Request) string {
	if r.Pattern != "" {
		return r.Pattern
	}
	return "unmatched"
}

// recoverAndLog logs method, route, status and time only: never a header, a token, a body or a path parameter.
func (g *Gateway) recoverAndLog(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		ctx, cancel := context.WithTimeout(r.Context(), requestTimeout)
		defer cancel()
		r = r.WithContext(ctx)
		sw := &statusWriter{ResponseWriter: w}
		defer func() {
			if p := recover(); p != nil {
				if p == http.ErrAbortHandler {
					panic(p)
				}
				g.log.Error("panic", "path", route(r), "panic", p)
				if sw.status == 0 {
					fail(sw, http.StatusInternalServerError, "internal error")
				}
			}
			g.log.Info("request", "method", r.Method, "path", route(r), "status", sw.status, "ms", time.Since(start).Milliseconds())
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
