package main

import (
	"context"
	"errors"
	"fmt"
	"net/url"
	"os"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"
)

// pgTest opens the real store in a fresh schema of TEST_DATABASE_URL; without that variable the test is skipped.
func pgTest(t *testing.T, before string) (*pgStore, *pgx.Conn) {
	t.Helper()
	base := os.Getenv("TEST_DATABASE_URL")
	if base == "" {
		t.Skip("TEST_DATABASE_URL is not set")
	}
	ctx := context.Background()
	admin, err := pgx.Connect(ctx, pgURL(base))
	if err != nil {
		t.Fatal(err)
	}
	schema := fmt.Sprintf("gw_test_%d", time.Now().UnixNano())
	if _, err := admin.Exec(ctx, "CREATE SCHEMA "+schema); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		admin.Exec(context.Background(), "DROP SCHEMA "+schema+" CASCADE")
		admin.Close(context.Background())
	})
	u, _ := url.Parse(pgURL(base))
	q := u.Query()
	q.Set("search_path", schema)
	u.RawQuery = q.Encode()
	conn, err := pgx.Connect(ctx, u.String())
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { conn.Close(context.Background()) })
	if before != "" {
		if _, err := conn.Exec(ctx, before); err != nil {
			t.Fatal(err)
		}
	}
	s, err := openStore(ctx, u.String())
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(s.Close)
	return s, conn
}

func TestPgEnrolRevokeAndAudit(t *testing.T) {
	s, _ := pgTest(t, "")
	ctx := context.Background()
	at := time.Date(2026, 10, 27, 10, 0, 0, 0, time.UTC)
	if err := s.Enroll(ctx, Device{ID: "d1", TokenHash: "t1", CreatedAt: at}, "c1"); err != nil {
		t.Fatal(err)
	}
	if err := s.Enroll(ctx, Device{ID: "d2", TokenHash: "t2", CreatedAt: at}, "c1"); !errors.Is(err, errCodeUsed) {
		t.Fatal("a used code was accepted:", err)
	}
	d, err := s.DeviceByToken(ctx, "t1")
	if err != nil || d.ID != "d1" || d.Revoked || d.LastSeen != nil || !d.CreatedAt.Equal(at) {
		t.Fatal(d, err)
	}
	if _, err := s.DeviceByToken(ctx, "nope"); !errors.Is(err, errNotFound) {
		t.Fatal(err)
	}
	if err := s.TouchLastSeen(ctx, "d1", at.Add(time.Hour)); err != nil {
		t.Fatal(err)
	}
	if err := s.Revoke(ctx, "nope"); !errors.Is(err, errNotFound) {
		t.Fatal(err)
	}
	if err := s.Revoke(ctx, "d1"); err != nil {
		t.Fatal(err)
	}
	devs, err := s.Devices(ctx, 10)
	if err != nil || len(devs) != 1 || !devs[0].Revoked || !devs[0].LastSeen.Equal(at.Add(time.Hour)) {
		t.Fatal(devs, err)
	}
	if err := s.Audit(ctx, "assist", "d1"); err != nil {
		t.Fatal(err)
	}
	all, _ := s.AuditRows(ctx, AuditFilter{Limit: 10})
	if len(all) != 3 || all[0].Action != "assist" || all[1].Action != "admin.revoke" || all[2].Action != "enroll" {
		t.Fatal(all)
	}
	page, _ := s.AuditRows(ctx, AuditFilter{Limit: 1, Before: all[0].ID})
	if len(page) != 1 || page[0].ID != all[1].ID {
		t.Fatal(page)
	}
	only, _ := s.AuditRows(ctx, AuditFilter{Limit: 10, Action: "enroll", DeviceID: "d1"})
	if len(only) != 1 || only[0].Action != "enroll" {
		t.Fatal(only)
	}
}

// The Python gateway's tables, before last_seen existed, plus its feedback table that this gateway never touches.
const pythonTables = `
CREATE TABLE devices (id VARCHAR(32) PRIMARY KEY, token_hash VARCHAR(64) NOT NULL UNIQUE, code_hash VARCHAR(64) UNIQUE,
	created_at TIMESTAMPTZ NOT NULL, revoked BOOLEAN NOT NULL);
INSERT INTO devices VALUES ('d1', 't1', 'c1', '2026-01-01T00:00:00Z', false);
CREATE TABLE feedback (id SERIAL PRIMARY KEY, device_id VARCHAR(32) REFERENCES devices(id), predicted VARCHAR(64));
INSERT INTO feedback (device_id, predicted) VALUES ('d1', 'water');
CREATE TABLE audit (id SERIAL PRIMARY KEY, ts TIMESTAMPTZ NOT NULL, device_id VARCHAR(32) NOT NULL, action VARCHAR(32) NOT NULL);
INSERT INTO audit (ts, device_id, action) VALUES ('2026-01-01T00:00:00Z', 'd1', 'old'), ('2026-10-01T00:00:00Z', 'd1', 'new');`

func TestPgAnExistingDatabaseCarriesOverAndPruningSparesFeedback(t *testing.T) {
	s, conn := pgTest(t, pythonTables)
	ctx := context.Background()
	d, err := s.DeviceByToken(ctx, "t1")
	if err != nil || d.ID != "d1" || d.LastSeen != nil {
		t.Fatal("the old device did not carry over:", d, err)
	}
	cutoff := time.Date(2026, 6, 1, 0, 0, 0, 0, time.UTC)
	if n, err := s.PruneAudit(ctx, cutoff, false); n != 1 || err != nil {
		t.Fatal(n, err)
	}
	if n, err := s.PruneAudit(ctx, cutoff, true); n != 1 || err != nil {
		t.Fatal(n, err)
	}
	rows, _ := s.AuditRows(ctx, AuditFilter{Limit: 10})
	if len(rows) != 2 || rows[0].Action != "audit.pruned" || rows[1].Action != "new" {
		t.Fatal(rows)
	}
	var feedback int
	if err := conn.QueryRow(ctx, "SELECT count(*) FROM feedback").Scan(&feedback); err != nil || feedback != 1 {
		t.Fatal(feedback, err)
	}
	if n, _ := s.PruneAudit(ctx, cutoff, true); n != 0 {
		t.Fatal(n)
	}
	// Opening again over a current schema changes nothing.
	again, err := openStore(ctx, conn.Config().ConnString())
	if err != nil {
		t.Fatal(err)
	}
	again.Close()
}
