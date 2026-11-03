package main

import (
	"errors"
	"math"
	"net/http"
	"strconv"
	"time"
)

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
	// audit.id is a 32-bit SERIAL; a larger cursor would be a database range error, not a 422.
	before, ok := queryInt(w, r, "before", 0, 1, math.MaxInt32)
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
