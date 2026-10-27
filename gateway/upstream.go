package main

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strings"
	"time"
)

// Some hosts refuse a default library User-Agent with 401.
const userAgent = "SignSetu-Gateway/1.0 (department gateway)"

// upstreamError carries the status the phone sees; the provider's own message never reaches it.
type upstreamError struct {
	status int
	detail string
}

func (e *upstreamError) Error() string { return e.detail }

type Assist func(ctx context.Context, system, user string, maxTokens int) (string, error)

// Speech is an open MP3 stream from the provider; the caller closes it.
type Speak func(ctx context.Context, text, lang string) (io.ReadCloser, error)

func post(ctx context.Context, client *http.Client, url string, body any, headers map[string]string) (*http.Response, error) {
	raw, err := json.Marshal(body)
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(raw))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("User-Agent", userAgent)
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	return client.Do(req)
}

func transportError(err error, what string) error {
	var ne interface{ Timeout() bool }
	if errors.Is(err, context.DeadlineExceeded) || (errors.As(err, &ne) && ne.Timeout()) {
		return &upstreamError{http.StatusGatewayTimeout, what + " timed out"}
	}
	return &upstreamError{http.StatusBadGateway, what + " unreachable"}
}

// upstreamAssist speaks Chat Completions, or the Responses API when ASSIST_URL ends in /responses.
func upstreamAssist(c Config, client *http.Client) Assist {
	responses := strings.HasSuffix(c.AssistURL, "/responses")
	return func(ctx context.Context, system, user string, maxTokens int) (string, error) {
		if c.AssistURL == "" || c.AssistKey == "" {
			return "", &upstreamError{http.StatusServiceUnavailable, "assistant not configured"}
		}
		ctx, cancel := context.WithTimeout(ctx, 30*time.Second)
		defer cancel()
		var body map[string]any
		if responses {
			// temperature is left out: a reasoning model rejects it.
			body = map[string]any{"model": c.AssistModel, "instructions": system, "input": user, "max_output_tokens": maxTokens}
		} else {
			body = map[string]any{"model": c.AssistModel, "temperature": 0.2, "max_tokens": maxTokens,
				"messages": []map[string]string{{"role": "system", "content": system}, {"role": "user", "content": user}}}
			if c.AssistReasoningEffort != "" {
				body["reasoning_effort"] = c.AssistReasoningEffort
			}
		}
		resp, err := post(ctx, client, c.AssistURL, body, map[string]string{"Authorization": "Bearer " + c.AssistKey})
		if err != nil {
			return "", transportError(err, "assistant")
		}
		defer resp.Body.Close()
		if resp.StatusCode >= 300 {
			return "", &upstreamError{http.StatusBadGateway, "assistant failed"}
		}
		raw, err := io.ReadAll(io.LimitReader(resp.Body, 4<<20))
		if err != nil {
			return "", transportError(err, "assistant")
		}
		answer, ok := answerFrom(raw, responses)
		if !ok {
			return "", &upstreamError{http.StatusBadGateway, "assistant returned an invalid response"}
		}
		if answer == "" {
			return "", &upstreamError{http.StatusBadGateway, "assistant returned nothing"}
		}
		return answer, nil
	}
}

func answerFrom(raw []byte, responses bool) (string, bool) {
	if !responses {
		var p struct {
			Choices []struct {
				Message struct {
					Content *string `json:"content"`
				} `json:"message"`
			} `json:"choices"`
		}
		if json.Unmarshal(raw, &p) != nil || len(p.Choices) == 0 {
			return "", false
		}
		if p.Choices[0].Message.Content == nil {
			return "", true
		}
		return strings.TrimSpace(*p.Choices[0].Message.Content), true
	}
	var p struct {
		OutputText string `json:"output_text"`
		Output     []struct {
			Type    string `json:"type"`
			Content []struct {
				Text string `json:"text"`
			} `json:"content"`
		} `json:"output"`
	}
	if json.Unmarshal(raw, &p) != nil {
		return "", false
	}
	if s := strings.TrimSpace(p.OutputText); s != "" {
		return s, true
	}
	// Reasoning items are skipped; only message items hold the answer.
	for _, item := range p.Output {
		if item.Type != "message" {
			continue
		}
		var b strings.Builder
		for _, part := range item.Content {
			b.WriteString(part.Text)
		}
		if s := strings.TrimSpace(b.String()); s != "" {
			return s, true
		}
	}
	return "", true
}

const elevenLabsVoice = "EXAVITQu4vr4xnSDxMaL"

// The same voices as tools/content/build_natural_audio.py, so typed text sounds like the shipped recordings.
var cartesiaVoices = map[string]string{
	"hi": "6b02ffe5-e3cb-48c0-a023-c72f85953375", "hne": "6b02ffe5-e3cb-48c0-a023-c72f85953375",
	"ta": "d4470f50-295e-4e11-82a2-158d45bf6abc", "bn": "48b9e1de-e2fa-4914-8b32-31c437813548",
	"gu": "4590a461-bc68-4a50-8d14-ac04f5923d22", "kn": "4c00ff98-199c-4967-99ae-7b69125edcff",
	"ml": "b426013c-002b-4e89-8874-8cd20b68373a", "mr": "5c32dce6-936a-4892-b131-bafe474afe5f",
	"pa": "9bf2ddcd-bb35-4e90-81b3-21c8183b24f4", "te": "4418bb06-8329-49a1-bb11-53bb64ca0547",
}

func speechProvider(lang string) string {
	switch {
	case lang == "en" || lang == "hi":
		return "elevenlabs"
	case cartesiaVoices[lang] != "":
		return "cartesia"
	}
	return ""
}

func upstreamSpeak(c Config, client *http.Client) Speak {
	return func(ctx context.Context, text, lang string) (io.ReadCloser, error) {
		var url string
		var headers map[string]string
		var body map[string]any
		switch speechProvider(lang) {
		case "elevenlabs":
			if c.ElevenLabsKey == "" {
				return nil, &upstreamError{http.StatusServiceUnavailable, "speech not configured"}
			}
			url = "https://api.elevenlabs.io/v1/text-to-speech/" + elevenLabsVoice + "?output_format=mp3_22050_32"
			headers = map[string]string{"xi-api-key": c.ElevenLabsKey}
			body = map[string]any{"text": text, "model_id": "eleven_v3", "language_code": lang}
		case "cartesia":
			if c.CartesiaKey == "" {
				return nil, &upstreamError{http.StatusServiceUnavailable, "speech not configured"}
			}
			language := lang
			if lang == "hne" {
				language = "hi"
			}
			url = "https://api.cartesia.ai/tts/bytes"
			headers = map[string]string{"X-API-Key": c.CartesiaKey, "Cartesia-Version": "2025-04-16"}
			body = map[string]any{"model_id": "sonic-3", "transcript": text, "language": language,
				"voice":         map[string]string{"mode": "id", "id": cartesiaVoices[lang]},
				"output_format": map[string]any{"container": "mp3", "sample_rate": 22050, "bit_rate": 32000}}
		default:
			return nil, &upstreamError{http.StatusUnprocessableEntity, "no voice for this language"}
		}
		resp, err := post(ctx, client, url, body, headers)
		if err != nil {
			return nil, transportError(err, "speech")
		}
		if resp.StatusCode >= 300 {
			resp.Body.Close()
			return nil, &upstreamError{http.StatusBadGateway, "speech failed"}
		}
		return resp.Body, nil
	}
}

// newUpstreamClient is shared by every call, so connections and TLS sessions to the providers are reused.
func newUpstreamClient() *http.Client {
	t := http.DefaultTransport.(*http.Transport).Clone()
	t.MaxIdleConnsPerHost = 16
	t.ResponseHeaderTimeout = 30 * time.Second
	return &http.Client{Transport: t}
}
