# 35 — Lifecycle fixes: resources that outlived their screen

Six resources were found by a static audit of `app/src/main` to survive the screen, sheet or session that
owned them. Each fix below is the smallest change that removes the leak. Where a plain JVM test cannot see
the resource (an Android service, a native document, a Compose effect) the entry says so and gives the check
that needs a phone instead. Nothing here was measured on a device.

## What changed

| Fix | File | Leak removed | Proof |
|---|---|---|---|
| P1 — the mic stops when the app leaves the foreground | `ui/session/VoiceSheet.kt`, `ui/components/StopOnBackground.kt` | `AudioRecord` and its reader thread ran to the 60 s cap after Home, because a bottom sheet stays composed while the activity is stopped | `StopOnBackgroundTest` (attaches to ON_STOP only); the call site is a review item |
| P2 — the recorded answer frees its track when it ends, and leaving the pane silences it | `voice/WavPlayer.kt`, `ui/session/OfficerPane.kt` | a `MODE_STATIC` `AudioTrack` holding the whole take was released only by the next `play()` | no JVM test: `AudioTrack` is a platform service; manual check below |
| P3 — speech stops when the app leaves the foreground | `ui/components/SpeechPlayer.kt` | `ExoPlayer` kept decoding and playing a caption line behind another app | no JVM test: an effect's lifetime is Compose's; manual check below |
| P8 — release no longer builds the phone's voice | `ui/components/SpeechPlayer.kt`, `ui/components/LazyResource.kt` | `release()` touched the lazy TTS delegate, binding the engine on every stage exit only to shut it down | `LazyResourceTest` (taking without use builds nothing); `SpeechPlayer` itself needs Android |
| P5 — one bitmap ring per screen | `camera/CameraMirror.kt` | three fresh frame bitmaps (about 5 MB) on every rebind or tracker change; `close()` only dropped the references | no JVM test: `FrameConverter` holds `Bitmap`s and the ring is owned by a Compose effect |
| P10 — the PDF document is closed on every exit | `export/SessionReport.kt` | `PdfDocument` stayed open when laying out or numbering a page threw | no JVM test: `PdfDocument` is native and the renderer is not opened in the JVM suite |

## P1 — microphone

`VoiceSheet` remembered a `VoiceCapture` and released it only from `onDispose`. A `ModalBottomSheet` stays
composed when the activity is stopped, and the app has no foreground service for capture, so a take started
and then backgrounded kept the microphone open (silent or refused by the platform) with the indicator on.
The sheet now stops and discards the take on `ON_STOP`; the live-transcription loop cancels with it and its
`liveStop` runs under `NonCancellable`, as before.

Manual check: consent, tap record, press Home, return. The microphone indicator must go out as the sheet
loses the foreground, and no `ANSWER_SPOKEN` row may be written for that take.

## P2 — recorded answers

`play()` installed a position marker at the last frame and a listener that calls the same `stop()` the next
`play()` used to call, so the track is freed when the take ends. `stop()` now tolerates a track that has
already been released. `OfficerPane` also stops the player on dispose: leaving the pane mid-play used to leave
the rest of the take sounding until it finished.

Manual check: play a recorded answer to its end, then play a second one; the first must be silent and the
second must start at once. Leave the pane mid-play; the audio must stop with the pane.

## P3 — ExoPlayer and TTS

`rememberSpeechPlayer` gained a background stop next to its dispose release, so a caption line stops when the
inspector leaves the app. `stop()` is used rather than `pause()`: the line is not resumed on return, and the
officer taps to hear it again.

Manual check: play a question's recording, press Home. Nothing must be audible while the app is in the
background or on return.

## P8 — TTS on release

`release()` used `voice.shutdown()` on a `by lazy` delegate, which initialised the engine in order to shut it
down. The voice is now a `LazyResource`: `get()` builds it for `speakOffline`, `peek()` lets `stop()` reach an
engine that already exists, and `take()` hands it to `release()` only when it was built.

Manual check: open and leave a stage without asking for typed speech; the phone must not bind its
text-to-speech engine (`adb shell dumpsys activity services | findstr tts` before and after).

## P5 — frame ring

The ring moved out of the analysis effect into `remember`, so a rebind, a tracker change or a permission
round-trip reuses the three bitmaps instead of allocating a new ring. Its `close()` moved to a `DisposableEffect`
keyed on the converter, which runs when the mirror leaves composition, not on every effect restart.

Manual check: move between the stage and the officer pane ten times, then read memory:
`adb shell dumpsys meminfo app.signsetu | findstr Native` should be flat, where before it climbed by roughly
5 MB per round-trip.

## P10 — PDF document

The `PdfDocument` is now created and closed in `export()` around `render()`, so a throw while laying out a page,
encoding the seal or numbering pages still closes it. `render()` takes the document as a parameter and no
longer closes it.

Manual check: export a record with attachments twice in a row on a phone with little free memory; both PDFs
must be written and open, and `adb shell dumpsys meminfo` must not show native growth between them.

## What the suite proves

`StopOnBackgroundTest` drives a `Lifecycle` by hand: only `ON_STOP` runs the callback, and a detached observer
is not called. `LazyResourceTest` proves a resource is never built by `take()` or `peek()` alone and that
`get()` builds it once. Neither test can see the composable that uses them; that link is the code review item,
and the resource behaviour itself is the manual check above.

Commits on this branch: P1 763174e, P2 e3a3710, P3 and P8 6ff9a94, P5 654602c, P10 de2abb4.
