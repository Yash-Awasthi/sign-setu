package main

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"math"
	"math/big"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

const maxTrackedKeys = 10_000

func sha256Hex(s string) string {
	sum := sha256.Sum256([]byte(s))
	return hex.EncodeToString(sum[:])
}

// newToken is 32 random bytes in URL-safe base64, the same shape as the tokens already issued.
func newToken() string {
	b := make([]byte, 32)
	rand.Read(b)
	return base64.RawURLEncoding.EncodeToString(b)
}

// RateLimiter is an in-process sliding window per key; per instance, so move it to a shared store before running replicas.
type RateLimiter struct {
	perMinute int
	now       func() time.Time
	mu        sync.Mutex
	hits      map[string][]time.Time
}

func newRateLimiter(perMinute int, now func() time.Time) *RateLimiter {
	return &RateLimiter{perMinute: perMinute, now: now, hits: map[string][]time.Time{}}
}

// recent drops hits older than a minute; the caller holds mu.
func (l *RateLimiter) recent(key string, now time.Time) []time.Time {
	q := l.hits[key]
	i := 0
	for i < len(q) && now.Sub(q[i]) > time.Minute {
		i++
	}
	q = q[i:]
	if len(q) == 0 {
		delete(l.hits, key)
	} else {
		l.hits[key] = q
	}
	return q
}

func (l *RateLimiter) Exceeded(key string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	return len(l.recent(key, l.now())) >= l.perMinute
}

// RetryAfter is whole seconds until the oldest hit in the window expires and one more is allowed.
func (l *RateLimiter) RetryAfter(key string) int {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := l.now()
	q := l.recent(key, now)
	if len(q) == 0 {
		return 1
	}
	return max(1, int(math.Ceil((time.Minute - now.Sub(q[0])).Seconds())))
}

// Allow records a hit and reports true, or reports false without recording when the window is full.
func (l *RateLimiter) Allow(key string) bool {
	ok, _ := l.Take(key)
	return ok
}

// Take is Allow that also reports whether this hit filled the window, so exactly one caller sees the cap reached.
func (l *RateLimiter) Take(key string) (ok, filled bool) {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := l.now()
	if len(l.hits) > maxTrackedKeys {
		for k := range l.hits {
			l.recent(k, now)
		}
	}
	q := l.recent(key, now)
	if len(q) >= l.perMinute {
		return false, false
	}
	l.hits[key] = append(q, now)
	return true, len(q)+1 == l.perMinute
}

// clientKey is the caller's address. X-Forwarded-For is believed only for the entries the given number of trusted
// proxies appended on the right; anything further left was written by the caller. With no trusted proxy it is ignored.
func clientKey(r *http.Request, trustedHops int) string {
	peer, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		peer = r.RemoteAddr
	}
	if trustedHops <= 0 {
		return peer
	}
	var parts []string
	for _, h := range r.Header.Values("X-Forwarded-For") {
		for _, p := range strings.Split(h, ",") {
			if p = strings.TrimSpace(p); p != "" {
				parts = append(parts, p)
			}
		}
	}
	if len(parts) < trustedHops {
		return peer
	}
	return parts[len(parts)-trustedHops]
}

func bearer(r *http.Request) string {
	return strings.TrimSpace(strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer "))
}

// KeySource returns the public key that signed a token; production fetches it from the issuer's JWKS.
type KeySource func(ctx context.Context, t *jwt.Token) (any, error)

// jwksSource caches the issuer's keys and refetches at most once a minute when a token names an unknown key.
func jwksSource(issuer string, client *http.Client) KeySource {
	url := strings.TrimRight(issuer, "/") + "/protocol/openid-connect/certs"
	var mu sync.Mutex
	keys := map[string]any{}
	var fetched time.Time
	return func(ctx context.Context, t *jwt.Token) (any, error) {
		kid, _ := t.Header["kid"].(string)
		mu.Lock()
		defer mu.Unlock()
		if k, ok := keys[kid]; ok {
			return k, nil
		}
		if time.Since(fetched) < time.Minute {
			return nil, errors.New("unknown signing key")
		}
		fetched = time.Now()
		fresh, err := fetchJWKS(ctx, client, url)
		if err != nil {
			return nil, err
		}
		keys = fresh
		if k, ok := keys[kid]; ok {
			return k, nil
		}
		return nil, errors.New("unknown signing key")
	}
}

func fetchJWKS(ctx context.Context, client *http.Client, url string) (map[string]any, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("jwks answered %d", resp.StatusCode)
	}
	var set struct {
		Keys []struct {
			Kid, Kty, Crv, N, E, X, Y string
		} `json:"keys"`
	}
	if err := json.NewDecoder(http.MaxBytesReader(nil, resp.Body, 1<<20)).Decode(&set); err != nil {
		return nil, err
	}
	b64 := func(s string) *big.Int {
		b, err := base64.RawURLEncoding.DecodeString(s)
		if err != nil || len(b) == 0 {
			return nil
		}
		return new(big.Int).SetBytes(b)
	}
	out := map[string]any{}
	for _, k := range set.Keys {
		switch {
		case k.Kty == "RSA":
			n, e := b64(k.N), b64(k.E)
			if n != nil && e != nil && e.IsInt64() && e.Int64() <= math.MaxInt32 {
				out[k.Kid] = &rsa.PublicKey{N: n, E: int(e.Int64())}
			}
		case k.Kty == "EC" && k.Crv == "P-256":
			x, y := b64(k.X), b64(k.Y)
			if x == nil || y == nil {
				continue
			}
			point := append([]byte{4}, append(x.FillBytes(make([]byte, 32)), y.FillBytes(make([]byte, 32))...)...)
			if key, err := ecdsa.ParseUncompressedPublicKey(elliptic.P256(), point); err == nil {
				out[k.Kid] = key
			}
		}
	}
	return out, nil
}

// roleClaims checks a Keycloak access token and that its realm roles include role.
func roleClaims(ctx context.Context, keys KeySource, c Config, token, role string) (int, string) {
	if token == "" || c.OIDCIssuer == "" || keys == nil {
		return http.StatusUnauthorized, "token required"
	}
	claims := jwt.MapClaims{}
	_, err := jwt.ParseWithClaims(token, claims, func(t *jwt.Token) (any, error) { return keys(ctx, t) },
		jwt.WithValidMethods([]string{"RS256", "ES256"}), jwt.WithAudience(c.OIDCAudience), jwt.WithIssuer(c.OIDCIssuer),
		jwt.WithExpirationRequired())
	if err != nil {
		return http.StatusUnauthorized, "invalid token"
	}
	// A token may carry the claim with a null body; that is a refusal, not a server error.
	realm, _ := claims["realm_access"].(map[string]any)
	roles, _ := realm["roles"].([]any)
	for _, r := range roles {
		if r == role {
			return 0, ""
		}
	}
	return http.StatusForbidden, role + " role required"
}
