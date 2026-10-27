package main

import (
	"fmt"
	"strconv"
	"strings"
)

type Config struct {
	DatabaseURL string
	// SHA-256 hex of each valid one-time enrolment code, issued by the department with the device.
	EnrollHashes          map[string]bool
	AssistURL             string
	AssistKey             string
	AssistModel           string
	AssistReasoningEffort string
	ElevenLabsKey         string
	CartesiaKey           string
	OIDCIssuer            string
	OIDCAudience          string
	AdminRole             string
	// Days a device token stays valid after enrolment; 0 means it never expires.
	TokenTTLDays   int
	AssistMaxChars int
	RatePerMinute  int
	// Reverse proxies in front of the gateway that append to X-Forwarded-For; 0 trusts none and uses the socket address.
	TrustedProxyHops int
	EnrollPerMinute  int
	// Wrong codes from one address; while reached, that address waits (at most a minute after its last wrong code).
	EnrollFailuresPerMinute int
	// Wrong codes across all callers, which a spoofed X-Forwarded-For cannot dodge; high, so one caller cannot lock all.
	EnrollFailuresGlobalPerMinute int
	Port                          string
}

func defaults() Config {
	return Config{
		EnrollHashes:                  map[string]bool{},
		OIDCAudience:                  "signsetu-portal",
		AdminRole:                     "gateway-admin",
		AssistMaxChars:                20_000,
		RatePerMinute:                 30,
		EnrollPerMinute:               10,
		EnrollFailuresPerMinute:       20,
		EnrollFailuresGlobalPerMinute: 200,
		Port:                          "8000",
	}
}

func configFromEnv(getenv func(string) string) (Config, error) {
	c := defaults()
	str := func(name string, dst *string) {
		if v := strings.TrimSpace(getenv(name)); v != "" {
			*dst = v
		}
	}
	var bad []string
	num := func(name string, dst *int, min int) {
		v := strings.TrimSpace(getenv(name))
		if v == "" {
			return
		}
		n, err := strconv.Atoi(v)
		if err != nil || n < min {
			bad = append(bad, name)
			return
		}
		*dst = n
	}
	str("DATABASE_URL", &c.DatabaseURL)
	str("ASSIST_URL", &c.AssistURL)
	str("ASSIST_KEY", &c.AssistKey)
	str("ASSIST_MODEL", &c.AssistModel)
	str("ASSIST_REASONING_EFFORT", &c.AssistReasoningEffort)
	str("ELEVENLABS_API_KEY", &c.ElevenLabsKey)
	str("CARTESIA_API_KEY", &c.CartesiaKey)
	str("OIDC_ISSUER", &c.OIDCIssuer)
	str("OIDC_AUDIENCE", &c.OIDCAudience)
	str("ADMIN_ROLE", &c.AdminRole)
	str("PORT", &c.Port)
	num("TOKEN_TTL_DAYS", &c.TokenTTLDays, 0)
	num("TRUSTED_PROXY_HOPS", &c.TrustedProxyHops, 0)
	num("ENROLL_PER_MINUTE", &c.EnrollPerMinute, 1)
	num("ENROLL_FAILURES_PER_MINUTE", &c.EnrollFailuresPerMinute, 1)
	num("ENROLL_FAILURES_GLOBAL_PER_MINUTE", &c.EnrollFailuresGlobalPerMinute, 1)
	for _, h := range strings.Split(getenv("ENROLL_HASHES"), ",") {
		if h = strings.ToLower(strings.TrimSpace(h)); h != "" {
			c.EnrollHashes[h] = true
		}
	}
	if len(bad) > 0 {
		return c, fmt.Errorf("not a valid number: %s", strings.Join(bad, ", "))
	}
	if c.DatabaseURL == "" {
		return c, fmt.Errorf("DATABASE_URL is not set; the gateway keeps enrolments in PostgreSQL")
	}
	return c, nil
}
