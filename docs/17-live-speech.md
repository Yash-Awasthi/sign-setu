# Live speech to text

When the person answers by voice, the officer sees their words appear as they talk, the way a phone assistant's
microphone works, but nothing leaves the department: the words come from the station brain (`brain/`), reached on
the station network or over iroh through the department's own relay ([16](16-relay.md)). The recording is still the
answer; the text is the app's reading of it, never the person's words.

The first rule: the live text is a preview. The reading that goes in the record comes from transcribing the whole
take again once it ends, exactly as before this feature.

## 1. What the officer and the person see

1. The officer opens the voice sheet. Recording stays blocked until the person's consent is logged for the session.
2. The officer taps the button, and the person speaks. The button turns red and reads "Listening. Tap to stop".
3. Their words appear under the button: settled words in full colour, words that may still change greyed after them.
   The caption above says they are the station computer's hearing, live and not confirmed.
4. Two to three seconds after the person stops speaking, the take ends by itself. The officer can also tap again to end
   it, and it ends at 60 seconds in any case.
5. The recording is saved to the record, and the brain transcribes it whole; that text, and its translation into the
   officer's language, is logged as the app's reading of the answer.

Without the station link on and the station's heartbeat offering live speech ([18](18-station.md)), the sheet records the same way and shows no words; the
officer taps to stop. Nothing else changes.

## 2. How it works

The speech model is the brain's own, faster-whisper (`--asr` sets the size). The method needs only a model that
gives each word with its time, so a larger Whisper or an Indian-language model can take its place; the Whisper
behaviour described below is what the method was tuned against.

```
phone (VoiceSheet)                                   station brain
  tap ──► POST /v1/live {lang}            ──────►  new take, its id
  every 0.4 s:
    POST /v1/live/{id}  (new PCM)          ──────►  appended to the take's audio
                                           ◄──────  words so far, tentative words, end?
                         worker thread, about every 0.5 s of new audio:
                           VAD on the audio ─► end of speech?
                           Whisper pass on the audio after the settled words
                           local agreement ─► newly settled words
  end, or tap ──► POST /v1/live/{id}/stop  ──────►  take dropped from memory
  WAV ──► POST /v1/asr  (whole take)       ──────►  the reading kept in the record
```

Each piece is an ordinary request, over HTTPS on the station network or as one iroh stream from anywhere
(`brain/link.py`). A dropped connection or a network change loses at most one piece's reply; the next piece carries
on. Transcription runs on its own thread, so a reply never waits for Whisper: it returns whatever the last pass found.

### 2.1 Settling words: local agreement

Every pass transcribes the audio after the last settled word again, from the start of that stretch, so each pass sees
more of the sentence than the one before. A word is settled once two passes in a row give the same word in the same
place (local agreement, as in Machacek, Dabre and Bojar, "Turning Whisper into Real-Time Transcription System",
2023). Comparison ignores case and punctuation only; it keeps vowel signs and other marks, because removing them
would merge different Indian-language words.

A word Whisper keeps changing would hold back everything after it. A word that ended more than 4 seconds before the
end of the audio is settled as last heard (`STALE`).

### 2.2 The seam between settled and new words

Whisper's word timings move by a few tenths of a second from pass to pass, and stretch at the edges of the audio.
Cutting each pass at the settled words' end time alone dropped or repeated words at the seam. The last settled words
(up to four) are looked for in the new pass by their text, and the words after the best match are the new ones. A
match counts only near where the settled words ended in time (within 2 seconds, or 1 second for a one-word match),
so a similar phrase later ("Is your mother nearby? Is your father nearby?") cannot swallow the words between. Only
when the text is not found does the time cut decide.

### 2.3 Keeping each pass short

Settled audio is cut from the buffer once it is longer than 12 seconds, at the end of a settled sentence (`.`, `?`,
`!`, the Devanagari danda, the Urdu full stop), where Whisper starts cleanly. Without a sentence end it is cut at the
last settled word once the buffer reaches 24 seconds. A cut never reaches into words not yet settled, and a word's
end is capped at its start plus 1.2 seconds, since a stretched end can run into the next word.

Whisper is given the settled words already cut from the audio as its prompt, for context. Words still in the audio
are not given: Whisper then repeats the prompt instead of listening, or, kept from repeating, drops those words.

### 2.4 End of speech

The VAD that faster-whisper ships (Silero) runs on the buffer at every check. Once there has been speech and the last
1.5 seconds hold none, the take ends and its tentative words are settled. Only the VAD runs when there is no new
speech since the last pass, so the end is not delayed by a Whisper pass.

### 2.5 Decoding

Live passes use one greedy decode, with one retry at temperature 0.4 when it fails Whisper's own quality checks, and
no run of 6 tokens may repeat. The full retry ladder of the whole-take pass can take ten times as long, too slow for
words shown live; without any retry, greedy decoding loops on one word in Hindi ("वुग वुग वुग ..."). Six tokens still
let a word double ("धीरे धीरे"). Word timestamps are on, and the model's own VAD filter drops silence.

A language Whisper does not have (Chhattisgarhi, Gondi, Halbi, Odia) is detected on the first pass and then held,
so the passes agree with each other.

## 3. The protocol

All three routes need the pairing token, like every other route but `/healthz`.

| Route | Body | Reply |
| --- | --- | --- |
| `POST /v1/live` | JSON `{"lang": "hi"}` (optional) | `{"id": "..."}`; 503 when four takes are already open |
| `POST /v1/live/{id}` | 16 kHz mono 16-bit little-endian PCM, at most 64 KB, may be empty | `{"text", "tentative", "end", "lang"}`; 404 for an unknown or expired take; 413 for a larger piece |
| `POST /v1/live/{id}/stop` | empty | the same, with every word settled; the take is gone |

`text` is the settled words, `tentative` the words after them that may still change, `end` true once the person has
stopped speaking or the take reached 60 seconds.

The pieces of a take (and of an inbox archive) are not counted against the brain's limit of 240 requests a minute: they
come twice a second, and that limit is kept per phone over iroh and per address on the station network. A take bounds
them instead: four takes at most, 64 KB a piece, 60 seconds of audio each, and a take with no piece for 20 seconds is
dropped. The token is checked on every piece, and wrong tokens are still limited to ten a minute.

The phone (`ui/session/VoiceSheet.kt`) asks every 400 ms. When a reply is slow, the audio waiting is sent in pieces
of one second, under the brain's limit.

## 4. Privacy and the record

- Audio lives only in memory on both sides. The brain drops a take's audio when it stops or goes idle, and writes
  nothing to disk; the phone wipes each piece once sent.
- Recording needs the consent logged for the session, as before. It now runs from the officer's tap until the person
  stops speaking or the officer taps again, never longer than 60 seconds; [13](13-dpia.md) says so.
- The live text is shown, not recorded. The record holds the recording and the reading from the whole take, marked
  as the app's.
- Nothing about this goes to anyone else's service. Off the station network the pieces travel end-to-end encrypted to
  the brain's key, through the department's relay at most.

## 5. Settings

In `brain/brain/live.py`:

| Name | Value | What it does |
| --- | --- | --- |
| `PASS_EVERY` | 0.5 s | New audio needed before another pass |
| `END_SILENCE` | 1.5 s | Silence after speech that ends the take |
| `STALE` | 4 s | Age after which a word is settled without agreement |
| `KEEP` | 12 s | Buffer length after which settled audio is cut |
| `SEAM` | 1 s | Time window for matching the settled words by text (twice that for two words or more) |
| `MAX_TAKES` | 4 | Takes open at once |
| `MAX_CHUNK` | 64 KB | Largest piece |
| `IDLE` | 20 s | A take with no piece for this long is dropped |
| `MAX_SECONDS` | 60 s | Longest take, the phone's own cap |

On the phone, `LIVE_MS` (400 ms) and `PIECE_BYTES` (1 second of audio) are in `VoiceSheet.kt`.

## 6. How it was tested

Real speech (the app's own recorded questions, several joined with 0.6 s pauses, 17 to 24 s a take, then 4 s of
silence) was sent at real-time speed, as the phone sends it, to Whisper `small` (INT8) on a 20-thread desktop
CPU. Tested on English, Hindi and Tamil with Whisper `small`:

| Language | Takes | Result |
| --- | --- | --- |
| English | 3 | Live text the same as the whole-take pass, word for word, every run; the take ended 2 to 3.5 s after the speech |
| Hindi | 3 | No sentence dropped and no lag after the fixes below; close to the whole-take pass |
| Tamil | 1 | Close to the whole-take pass |

A pass took 1.6 to 2.5 seconds. Over iroh through a relay, a whole English take ran as 44 pieces, every reply 200,
with the same text.

What the tests found and fixed: the time-only seam dropped clause-final words ("city?", "home?"); faster-whisper's
temperature retries made one pass take 17 seconds; greedy decoding looped in Hindi; a prompt holding words still in
the audio made Whisper drop them; and a text match on a similar later phrase skipped a sentence.

Not tested: the app on a phone, a station computer slower than the test machine (passes come less often there,
so the words appear in larger steps), and noisy rooms.

## 7. Limits

- The live text is only as good as the speech model, which matters most for Indian languages. The languages not
  listed in section 6 are untested; Bengali needs a stronger model than Whisper `small`: a larger Whisper (`--asr`) or
  an Indian-language speech model.
- Hindi live words change more before they settle than English ones, and the settled words can differ from the
  whole-take reading. The whole-take reading is the one kept.
- One worker transcribes every take in turn, sharing the CPU with the brain's other work; with several phones
  talking at once, each sees its words less often.
- The person's pauses longer than 1.5 seconds end the take. The officer taps again to go on, which starts a second
  recording.
- The brain does not speak replies: there is no voice mode. A conversation by voice would need a text-to-speech voice
  that runs in real time on a CPU, which the present voices (Indic Parler-TTS) do not.

## 8. Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| The voice sheet records but shows no words | The station link is off, or the heartbeat does not offer `live`: the speech model (`--asr`) is still loading, or speech to text is switched off for this phone in Settings, Local AI brain. `status` shows what the station offers |
| Words appear in large, late steps | The station is slower than the test machine; a pass takes longer, so words arrive less often. Use a smaller `--asr` model or a faster computer |
| Hindi or another Indian language reads badly | The model is too small for that language; use a larger `--asr` model or an Indian-language speech model |
| The take ends while the person is still thinking | A pause over 1.5 seconds ends a take. Tap again to record the next part |
| 503 from `POST /v1/live` | Four takes are already open; wait for one to stop or go idle (20 seconds) |
| 404 on a piece | The take expired or was stopped; the phone starts a new one |
| 413 on a piece | The piece is over 64 KB; the phone sends one second at a time, so a custom client must do the same |
| Live words differ from the saved reading | Expected: the live text is a preview, and the reading from the whole take is the one kept |
