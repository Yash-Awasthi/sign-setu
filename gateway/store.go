package main

import (
	"context"
	"errors"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

var (
	errNotFound = errors.New("not found")
	errCodeUsed = errors.New("enrolment code already used")
)

type Device struct {
	ID        string
	TokenHash string
	CreatedAt time.Time
	Revoked   bool
	LastSeen  *time.Time
}

type AuditRow struct {
	ID       int64
	At       time.Time
	DeviceID string
	Action   string
}

type AuditFilter struct {
	Limit    int
	Before   int64
	Action   string
	DeviceID string
}

// Store is PostgreSQL in production; the tests use an in-memory copy with the same rules.
type Store interface {
	Ping(ctx context.Context) error
	// Enroll adds the device and its audit row together; errCodeUsed when the code was used before.
	Enroll(ctx context.Context, d Device, codeHash string) error
	DeviceByToken(ctx context.Context, tokenHash string) (Device, error)
	TouchLastSeen(ctx context.Context, id string, at time.Time) error
	Audit(ctx context.Context, action, deviceID string) error
	Devices(ctx context.Context, limit int) ([]Device, error)
	// Revoke marks the device and writes its audit row together; errNotFound for an unknown id.
	Revoke(ctx context.Context, id string) error
	AuditRows(ctx context.Context, f AuditFilter) ([]AuditRow, error)
	// PruneAudit counts rows older than before and deletes them only when apply is set.
	PruneAudit(ctx context.Context, before time.Time, apply bool) (int64, error)
}

// The same tables the earlier Python gateway created, so an existing database carries over unchanged.
const schema = `
CREATE TABLE IF NOT EXISTS devices (
	id VARCHAR(32) PRIMARY KEY,
	token_hash VARCHAR(64) NOT NULL UNIQUE,
	code_hash VARCHAR(64) UNIQUE,
	created_at TIMESTAMPTZ NOT NULL,
	revoked BOOLEAN NOT NULL,
	last_seen TIMESTAMPTZ
);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS last_seen TIMESTAMPTZ;
CREATE TABLE IF NOT EXISTS audit (
	id SERIAL PRIMARY KEY,
	ts TIMESTAMPTZ NOT NULL,
	device_id VARCHAR(32) NOT NULL,
	action VARCHAR(32) NOT NULL
);`

type pgStore struct{ pool *pgxpool.Pool }

// pgURL accepts the URL forms hosts hand out and the SQLAlchemy form the Python gateway used.
func pgURL(url string) string {
	for _, scheme := range []string{"postgresql+psycopg://", "postgres://"} {
		if rest, ok := strings.CutPrefix(url, scheme); ok {
			return "postgresql://" + rest
		}
	}
	return url
}

func openStore(ctx context.Context, url string) (*pgStore, error) {
	cfg, err := pgxpool.ParseConfig(pgURL(url))
	if err != nil {
		return nil, errors.New("DATABASE_URL is not a PostgreSQL URL")
	}
	// Unnamed statements work behind a transaction-mode pooler such as Neon's.
	cfg.ConnConfig.DefaultQueryExecMode = pgx.QueryExecModeExec
	cfg.MaxConns = 8
	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return nil, err
	}
	if _, err := pool.Exec(ctx, schema); err != nil {
		pool.Close()
		return nil, err
	}
	return &pgStore{pool}, nil
}

func (s *pgStore) Close() { s.pool.Close() }

func (s *pgStore) Ping(ctx context.Context) error {
	_, err := s.pool.Exec(ctx, "SELECT 1")
	return err
}

func (s *pgStore) Enroll(ctx context.Context, d Device, codeHash string) error {
	return pgx.BeginFunc(ctx, s.pool, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `INSERT INTO devices (id, token_hash, code_hash, created_at, revoked) VALUES ($1, $2, $3, $4, false)`,
			d.ID, d.TokenHash, codeHash, d.CreatedAt)
		var pgErr *pgconn.PgError
		if errors.As(err, &pgErr) && pgErr.Code == "23505" {
			return errCodeUsed
		}
		if err != nil {
			return err
		}
		return insertAudit(ctx, tx, "enroll", d.ID)
	})
}

func (s *pgStore) DeviceByToken(ctx context.Context, tokenHash string) (Device, error) {
	var d Device
	err := s.pool.QueryRow(ctx, `SELECT id, token_hash, created_at, revoked, last_seen FROM devices WHERE token_hash = $1`, tokenHash).
		Scan(&d.ID, &d.TokenHash, &d.CreatedAt, &d.Revoked, &d.LastSeen)
	if errors.Is(err, pgx.ErrNoRows) {
		return d, errNotFound
	}
	return d, err
}

func (s *pgStore) TouchLastSeen(ctx context.Context, id string, at time.Time) error {
	_, err := s.pool.Exec(ctx, `UPDATE devices SET last_seen = $2 WHERE id = $1`, id, at)
	return err
}

type execer interface {
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
}

func insertAudit(ctx context.Context, db execer, action, deviceID string) error {
	_, err := db.Exec(ctx, `INSERT INTO audit (ts, device_id, action) VALUES ($1, $2, $3)`, time.Now().UTC(), deviceID, action)
	return err
}

func (s *pgStore) Audit(ctx context.Context, action, deviceID string) error {
	return insertAudit(ctx, s.pool, action, deviceID)
}

func (s *pgStore) Devices(ctx context.Context, limit int) ([]Device, error) {
	rows, err := s.pool.Query(ctx, `SELECT id, token_hash, created_at, revoked, last_seen FROM devices ORDER BY created_at DESC LIMIT $1`, limit)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(r pgx.CollectableRow) (Device, error) {
		var d Device
		err := r.Scan(&d.ID, &d.TokenHash, &d.CreatedAt, &d.Revoked, &d.LastSeen)
		return d, err
	})
}

func (s *pgStore) Revoke(ctx context.Context, id string) error {
	return pgx.BeginFunc(ctx, s.pool, func(tx pgx.Tx) error {
		tag, err := tx.Exec(ctx, `UPDATE devices SET revoked = true WHERE id = $1`, id)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return errNotFound
		}
		return insertAudit(ctx, tx, "admin.revoke", id)
	})
}

func (s *pgStore) AuditRows(ctx context.Context, f AuditFilter) ([]AuditRow, error) {
	// Zero values mean "no filter"; ids start at 1 and actions are never empty.
	rows, err := s.pool.Query(ctx, `SELECT id, ts, device_id, action FROM audit
		WHERE ($1 = 0 OR id < $1) AND ($2 = '' OR action = $2) AND ($3 = '' OR device_id = $3)
		ORDER BY id DESC LIMIT $4`, f.Before, f.Action, f.DeviceID, f.Limit)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(r pgx.CollectableRow) (AuditRow, error) {
		var a AuditRow
		err := r.Scan(&a.ID, &a.At, &a.DeviceID, &a.Action)
		return a, err
	})
}

func (s *pgStore) PruneAudit(ctx context.Context, before time.Time, apply bool) (int64, error) {
	var n int64
	err := pgx.BeginFunc(ctx, s.pool, func(tx pgx.Tx) error {
		if err := tx.QueryRow(ctx, `SELECT count(*) FROM audit WHERE ts < $1`, before).Scan(&n); err != nil {
			return err
		}
		if !apply || n == 0 {
			return nil
		}
		tag, err := tx.Exec(ctx, `DELETE FROM audit WHERE ts < $1`, before)
		if err != nil {
			return err
		}
		n = tag.RowsAffected()
		// Maintenance that destroys records is itself an auditable action.
		return insertAudit(ctx, tx, "audit.pruned", "")
	})
	return n, err
}
