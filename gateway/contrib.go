package main

import (
	"encoding/json"
	"errors"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"sync"
)

// An upload holds landmarks, glosses and corrections only; a session of 10 prompts is well under this.
const maxContribBytes = 2 << 20

// The signer id is random on the phone and is the only key to a signer's records, so it must be long enough not to guess.
var signerID = regexp.MustCompile(`^[a-z0-9-]{16,64}$`)

var contribKinds = map[string]bool{"prompt": true, "correction": true}

// The only keys a record may carry: anything else (an image, audio) is refused rather than stored.
var contribRecordKeys = map[string]bool{"kind": true, "session": true, "window": true, "gloss": true, "correction": true}

var contribMu sync.Mutex

type contribBody struct {
	Signer  string                       `json:"signer"`
	Consent string                       `json:"consent"`
	Records []map[string]json.RawMessage `json:"records"`
}

// contribReason is empty for a valid upload, else why it is refused; the phone drops a refused record from its queue.
func contribReason(b contribBody) string {
	switch {
	case !signerID.MatchString(b.Signer):
		return "signer must be 16 to 64 of a-z, 0-9 and -"
	case b.Consent == "" || chars(b.Consent) > 16:
		return "consent version required"
	case len(b.Records) == 0 || len(b.Records) > 50:
		return "1 to 50 records"
	}
	for i, r := range b.Records {
		for k := range r {
			if !contribRecordKeys[k] {
				return "record " + strconv.Itoa(i) + ": unexpected field " + k
			}
		}
		var kind string
		if json.Unmarshal(r["kind"], &kind) != nil || !contribKinds[kind] {
			return "record " + strconv.Itoa(i) + ": kind must be prompt or correction"
		}
		var window struct {
			Signs []struct {
				Frames []json.RawMessage `json:"frames"`
			} `json:"signs"`
			Lattice json.RawMessage `json:"lattice"`
		}
		if json.Unmarshal(r["window"], &window) != nil || len(window.Signs) == 0 || len(window.Lattice) == 0 {
			return "record " + strconv.Itoa(i) + ": window needs signs and a lattice"
		}
		var gloss []string
		if json.Unmarshal(r["gloss"], &gloss) != nil || len(gloss) == 0 {
			return "record " + strconv.Itoa(i) + ": gloss must be a list of words"
		}
	}
	return ""
}

func (g *Gateway) contribFile(signer string) string {
	return filepath.Join(g.cfg.ContribDir, signer+".jsonl")
}

func (g *Gateway) contribRoute(w http.ResponseWriter, r *http.Request, dev Device) {
	if !g.contribLimiter.Allow(dev.ID) {
		refuse(w, g.contribLimiter, dev.ID)
		return
	}
	var body contribBody
	if !decode(w, r, &body) {
		return
	}
	if reason := contribReason(body); reason != "" {
		fail(w, http.StatusUnprocessableEntity, reason)
		return
	}
	at := isoTime(g.now())
	err := func() error {
		contribMu.Lock()
		defer contribMu.Unlock()
		if err := os.MkdirAll(g.cfg.ContribDir, 0o700); err != nil {
			return err
		}
		f, err := os.OpenFile(g.contribFile(body.Signer), os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o600)
		if err != nil {
			return err
		}
		enc := json.NewEncoder(f)
		for _, rec := range body.Records {
			if err := enc.Encode(map[string]any{"at": at, "device": dev.ID, "signer": body.Signer, "consent": body.Consent, "record": rec}); err != nil {
				f.Close()
				return err
			}
		}
		if err := f.Sync(); err != nil {
			f.Close()
			return err
		}
		return f.Close()
	}()
	if err == nil {
		err = g.store.Audit(r.Context(), "contrib", dev.ID)
	}
	if err != nil {
		g.internal(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]int{"stored": len(body.Records)})
}

func (g *Gateway) contribDelete(w http.ResponseWriter, r *http.Request, dev Device) {
	signer := r.PathValue("signer")
	if !signerID.MatchString(signer) {
		fail(w, http.StatusUnprocessableEntity, "bad signer id")
		return
	}
	contribMu.Lock()
	err := os.Remove(g.contribFile(signer))
	contribMu.Unlock()
	// Deleting what was never stored, or was already deleted, is the same success to the phone.
	if err != nil && !errors.Is(err, os.ErrNotExist) {
		g.internal(w, r, err)
		return
	}
	if err := g.store.Audit(r.Context(), "contrib.delete", dev.ID); err != nil {
		g.internal(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{})
}
