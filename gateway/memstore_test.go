package main

import (
	"context"
	"errors"
	"slices"
	"sync"
	"time"
)

// memStore keeps the same rules as pgStore (unique codes and tokens, rising audit ids) for the handler tests;
// store_test.go runs the real one against PostgreSQL.
type memStore struct {
	mu      sync.Mutex
	devices []Device
	codes   map[string]bool
	audit   []AuditRow
	lastID  int64
	broken  bool
}

func newMemStore() *memStore { return &memStore{codes: map[string]bool{}} }

var errBroken = errors.New("database is down")

func (m *memStore) Ping(context.Context) error {
	if m.broken {
		return errBroken
	}
	return nil
}

func (m *memStore) addAudit(action, deviceID string) {
	m.lastID++
	m.audit = append(m.audit, AuditRow{ID: m.lastID, At: time.Now().UTC(), DeviceID: deviceID, Action: action})
}

func (m *memStore) Enroll(_ context.Context, d Device, codeHash string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.codes[codeHash] || slices.ContainsFunc(m.devices, func(x Device) bool { return x.ID == d.ID || x.TokenHash == d.TokenHash }) {
		return errCodeUsed
	}
	m.codes[codeHash] = true
	m.devices = append(m.devices, d)
	m.addAudit("enroll", d.ID)
	return nil
}

func (m *memStore) DeviceByToken(_ context.Context, tokenHash string) (Device, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	for _, d := range m.devices {
		if d.TokenHash == tokenHash {
			return d, nil
		}
	}
	return Device{}, errNotFound
}

func (m *memStore) TouchLastSeen(_ context.Context, id string, at time.Time) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	for i := range m.devices {
		if m.devices[i].ID == id {
			m.devices[i].LastSeen = &at
		}
	}
	return nil
}

func (m *memStore) Audit(_ context.Context, action, deviceID string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.addAudit(action, deviceID)
	return nil
}

func (m *memStore) Devices(_ context.Context, limit int) ([]Device, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	out := slices.Clone(m.devices)
	slices.Reverse(out)
	return out[:min(limit, len(out))], nil
}

func (m *memStore) Revoke(_ context.Context, id string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	for i := range m.devices {
		if m.devices[i].ID == id {
			m.devices[i].Revoked = true
			m.addAudit("admin.revoke", id)
			return nil
		}
	}
	return errNotFound
}

func (m *memStore) AuditRows(_ context.Context, f AuditFilter) ([]AuditRow, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	var out []AuditRow
	for i := len(m.audit) - 1; i >= 0 && len(out) < f.Limit; i-- {
		a := m.audit[i]
		if (f.Before == 0 || a.ID < f.Before) && (f.Action == "" || a.Action == f.Action) && (f.DeviceID == "" || a.DeviceID == f.DeviceID) {
			out = append(out, a)
		}
	}
	return out, nil
}

func (m *memStore) PruneAudit(_ context.Context, before time.Time, apply bool) (int64, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	old := func(a AuditRow) bool { return a.At.Before(before) }
	var n int64
	for _, a := range m.audit {
		if old(a) {
			n++
		}
	}
	if apply && n > 0 {
		m.audit = slices.DeleteFunc(m.audit, old)
		m.addAudit("audit.pruned", "")
	}
	return n, nil
}

func (m *memStore) actions() []string {
	m.mu.Lock()
	defer m.mu.Unlock()
	out := make([]string, len(m.audit))
	for i, a := range m.audit {
		out[i] = a.Action
	}
	return out
}

func (m *memStore) ageDevices(by time.Duration) {
	m.mu.Lock()
	defer m.mu.Unlock()
	for i := range m.devices {
		m.devices[i].CreatedAt = m.devices[i].CreatedAt.Add(-by)
	}
}
