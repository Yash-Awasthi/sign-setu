// Command gateway is the Sign Setu department gateway: device enrolment, the assistant and speech proxies, and the
// administrator's device and audit views. `gateway codes N` and `gateway prune-audit` are its maintenance tools.
package main

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"flag"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"
)

func main() {
	log := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	args := os.Args[1:]
	var err error
	switch {
	case len(args) == 0:
		err = serve(log)
	case args[0] == "codes":
		err = printCodes(os.Stdout, args[1:])
	case args[0] == "prune-audit":
		err = pruneAudit(os.Stdout, args[1:], os.Getenv("DATABASE_URL"), time.Now().UTC())
	default:
		err = fmt.Errorf("unknown command %q; use no argument to serve, or codes, or prune-audit", args[0])
	}
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(2)
	}
}

func serve(log *slog.Logger) error {
	cfg, err := configFromEnv(os.Getenv)
	if err != nil {
		return err
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	store, err := openStore(ctx, cfg.DatabaseURL)
	if err != nil {
		return fmt.Errorf("database: %w", err)
	}
	defer store.Close()
	if len(cfg.EnrollHashes) == 0 {
		log.Warn("ENROLL_HASHES is empty, so no device can enrol")
	}
	if cfg.AssistURL == "" || cfg.AssistKey == "" {
		log.Warn("ASSIST_URL or ASSIST_KEY is empty, so /v1/assist answers 503")
	}
	client := newUpstreamClient()
	var keys KeySource
	if cfg.OIDCIssuer != "" {
		keys = jwksSource(cfg.OIDCIssuer, &http.Client{Timeout: 10 * time.Second})
	}
	g := newGateway(cfg, store, upstreamAssist(cfg, client), upstreamSpeak(cfg, client), keys, time.Now, log)
	srv := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           g.Handler(),
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		// The assistant may take its whole 30 s; the phone itself waits up to 60 s.
		WriteTimeout: 75 * time.Second,
		IdleTimeout:  120 * time.Second,
	}
	errc := make(chan error, 1)
	go func() { errc <- srv.ListenAndServe() }()
	log.Info("listening", "port", cfg.Port)
	select {
	case err := <-errc:
		return err
	case <-ctx.Done():
	}
	// The host sends SIGTERM before a redeploy; requests in flight get to finish.
	shutdown, cancel := context.WithTimeout(context.Background(), 25*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdown); err != nil {
		return err
	}
	if err := <-errc; !errors.Is(err, http.ErrServerClosed) {
		return err
	}
	return nil
}

// printCodes prints N one-time enrolment codes and their ENROLL_HASHES line; only the hashes go on the server.
func printCodes(out io.Writer, args []string) error {
	n := 1
	if len(args) > 0 {
		v, err := strconv.Atoi(args[0])
		if err != nil || v < 1 || v > 1000 {
			return errors.New("usage: gateway codes [N], with N from 1 to 1000")
		}
		n = v
	}
	hashes := ""
	for i := range n {
		b := make([]byte, 9)
		rand.Read(b)
		code := base64.RawURLEncoding.EncodeToString(b)
		fmt.Fprintln(out, code)
		if i > 0 {
			hashes += ","
		}
		hashes += sha256Hex(code)
	}
	fmt.Fprintf(out, "\nENROLL_HASHES=%s\n", hashes)
	return nil
}

// pruneCutoff is the instant before which audit rows expire; a future one would delete every row, so it is refused.
func pruneCutoff(days int, before string, now time.Time) (time.Time, error) {
	if (days == 0) == (before == "") {
		return time.Time{}, errors.New("give exactly one of --days or --before")
	}
	if days < 0 {
		return time.Time{}, errors.New("--days must be at least 1")
	}
	moment := now.AddDate(0, 0, -days)
	if before != "" {
		t, err := time.Parse(time.DateOnly, before)
		if err != nil {
			if t, err = time.Parse(time.RFC3339, before); err != nil {
				return time.Time{}, errors.New("--before takes YYYY-MM-DD or an RFC 3339 time; a date is UTC")
			}
		}
		moment = t.UTC()
	}
	if moment.After(now) {
		return time.Time{}, errors.New("the cutoff is in the future, which would delete every audit row")
	}
	return moment, nil
}

// pruneAudit deletes audit rows older than the agreed retention; nothing goes without --apply.
func pruneAudit(out io.Writer, args []string, databaseURL string, now time.Time) error {
	fs := flag.NewFlagSet("prune-audit", flag.ContinueOnError)
	days := fs.Int("days", 0, "keep this many days of audit rows")
	before := fs.String("before", "", "delete rows older than this UTC date (YYYY-MM-DD)")
	apply := fs.Bool("apply", false, "delete the rows; without this the tool only reports")
	if err := fs.Parse(args); err != nil {
		return err
	}
	moment, err := pruneCutoff(*days, *before, now)
	if err != nil {
		return err
	}
	if databaseURL == "" {
		return errors.New("set DATABASE_URL in the environment")
	}
	ctx := context.Background()
	store, err := openStore(ctx, databaseURL)
	if err != nil {
		return fmt.Errorf("database: %w", err)
	}
	defer store.Close()
	n, err := store.PruneAudit(ctx, moment, *apply)
	if err != nil {
		return err
	}
	verb := "would delete"
	if *apply {
		verb = "deleted"
	}
	fmt.Fprintf(out, "%s %d audit rows older than %s\n", verb, n, moment.Format(time.RFC3339))
	return nil
}
