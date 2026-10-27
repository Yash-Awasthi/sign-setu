package main

import (
	_ "embed"
	"encoding/json"
	"regexp"
	"strings"
)

// Written by the app's GatewayPromptContractTest (UPDATE_PROMPT_FIXTURE=1); that test fails when a prompt changes without it.
//
//go:embed app_prompts.json
var promptFixture []byte

var slot = regexp.MustCompile("§[A-Z]+§")

// A language name or the like, never a line break, so a slot cannot carry extra instructions.
const slotFill = "[^\n§]{1,80}"

func compileTemplate(text string) *regexp.Regexp {
	parts := slot.Split(text, -1)
	for i, p := range parts {
		parts[i] = regexp.QuoteMeta(p)
	}
	return regexp.MustCompile(`^(?:` + strings.Join(parts, slotFill) + `)$`)
}

func loadTemplates(raw []byte) ([]*regexp.Regexp, error) {
	var f struct {
		Templates []string `json:"templates"`
	}
	if err := json.Unmarshal(raw, &f); err != nil {
		return nil, err
	}
	out := make([]*regexp.Regexp, len(f.Templates))
	for i, t := range f.Templates {
		out[i] = compileTemplate(t)
	}
	return out, nil
}

var templates = func() []*regexp.Regexp {
	t, err := loadTemplates(promptFixture)
	if err != nil || len(t) == 0 {
		panic("app_prompts.json is unreadable or empty")
	}
	return t
}()

// promptAllowed reports whether system is one of the app's own prompts, so a caller cannot add instructions.
func promptAllowed(system string) bool {
	system = strings.TrimSpace(system)
	for _, t := range templates {
		if t.MatchString(system) {
			return true
		}
	}
	return false
}
