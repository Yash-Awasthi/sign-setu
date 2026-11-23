package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestContribRetryAfterAuditFailureDoesNotAppendDuplicate(t *testing.T) {
	dir := t.TempDir()
	e := newEnv(t, func(c *Config) { c.ContribDir = dir }, nil, nil)
	token := e.enrolled()
	body := map[string]any{
		"signer":     "signer-1234567890",
		"consent":    "v1",
		"record_ids": []string{strings.Repeat("a", 64)},
		"records": []any{map[string]any{
			"kind": "prompt", "session": map[string]any{},
			"window": map[string]any{"signs": []any{map[string]any{"frames": []any{}}}, "lattice": []any{}},
			"gloss":  []string{"hello"},
		}},
	}

	e.store.failAudit = true
	first := e.do("POST", "/v1/contrib", body, "Authorization", token)
	if first.status != 500 {
		t.Fatalf("first upload status = %d, want 500: %s", first.status, first.body)
	}
	e.store.failAudit = false
	retry := e.do("POST", "/v1/contrib", body, "Authorization", token)
	if retry.status != 200 {
		t.Fatalf("retry status = %d, want 200: %s", retry.status, retry.body)
	}
	var result struct{ Stored int }
	retry.json(t, &result)
	if result.Stored != 0 {
		t.Fatalf("retry stored %d records, want 0", result.Stored)
	}

	raw, err := os.ReadFile(filepath.Join(dir, "signer-1234567890.jsonl"))
	if err != nil {
		t.Fatal(err)
	}
	lines := strings.Split(strings.TrimSpace(string(raw)), "\n")
	if len(lines) != 1 {
		t.Fatalf("stored %d lines after retry, want 1", len(lines))
	}
	var row struct {
		RecordID string `json:"record_id"`
	}
	if err := json.Unmarshal([]byte(lines[0]), &row); err != nil {
		t.Fatal(err)
	}
	if row.RecordID != strings.Repeat("a", 64) {
		t.Fatalf("stored record id %q", row.RecordID)
	}
}

func TestContribRejectsMissingOrRepeatedRecordIDs(t *testing.T) {
	record := map[string]json.RawMessage{
		"kind":   json.RawMessage(`"prompt"`),
		"window": json.RawMessage(`{"signs":[{"frames":[]}],"lattice":[]}`),
		"gloss":  json.RawMessage(`["hello"]`),
	}
	base := contribBody{
		Signer: "signer-1234567890", Consent: "v1",
		Records: []map[string]json.RawMessage{record},
	}
	if got := contribReason(base); got == "" {
		t.Fatal("accepted a record without an id")
	}
	base.RecordIDs = []string{strings.Repeat("a", 64)}
	base.Records = append(base.Records, record)
	base.RecordIDs = append(base.RecordIDs, base.RecordIDs[0])
	if got := contribReason(base); got == "" {
		t.Fatal("accepted repeated record ids")
	}
}

func TestContribDeleteCanBeRetriedAfterAuditFailure(t *testing.T) {
	dir := t.TempDir()
	e := newEnv(t, func(c *Config) { c.ContribDir = dir }, nil, nil)
	token := e.enrolled()
	body := map[string]any{
		"signer":     "signer-1234567890",
		"consent":    "v1",
		"record_ids": []string{strings.Repeat("b", 64)},
		"records": []any{map[string]any{
			"kind": "prompt", "session": map[string]any{},
			"window": map[string]any{"signs": []any{map[string]any{"frames": []any{}}}, "lattice": []any{}},
			"gloss":  []string{"hello"},
		}},
	}
	if r := e.do("POST", "/v1/contrib", body, "Authorization", token); r.status != 200 {
		t.Fatalf("upload status = %d: %s", r.status, r.body)
	}
	path := filepath.Join(dir, "signer-1234567890.jsonl")
	e.store.failAudit = true
	if r := e.do("DELETE", "/v1/contrib/signer-1234567890", nil, "Authorization", token); r.status != 500 {
		t.Fatalf("first deletion status = %d, want 500", r.status)
	}
	if _, err := os.Stat(path); !os.IsNotExist(err) {
		t.Fatalf("contribution file still exists after deletion: %v", err)
	}
	e.store.failAudit = false
	if r := e.do("DELETE", "/v1/contrib/signer-1234567890", nil, "Authorization", token); r.status != 200 {
		t.Fatalf("retry deletion status = %d: %s", r.status, r.body)
	}
}
