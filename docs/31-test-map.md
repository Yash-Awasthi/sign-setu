# Unit test map: what the JVM suite covers on the critical paths

The suite runs with `:app:testStandardDebugUnitTest`. These tests are plain JVM tests: no
emulator, no Robolectric. Android calls made by the code under test return defaults unless the test
supplies its own in-memory stand-in (the session database is `MemoryDao`, the session report is not
opened).

| Critical path | Test class | What it proves | What it cannot prove without a device |
|---|---|---|---|
| Consent shape for a recorded voice | `VoiceConsentTest` | No `ANSWER_SPOKEN` row is written before consent. Consent is exactly one sealed `SYSTEM` row from the officer with the text `voice_consent`. After consent the row is `ANSWER_SPOKEN` from the subject, names its recording, and the WAV bytes are the ones the hash chain covers. | Microphone capture, the permission dialog, the real WAV encoder, and playback. |
| Export and verify: the QR seal | `SealCheckTest` | The 8-field QR payload carries the seal line, printed-rows digest, signature and key in order. A good signature matches only the same record on this phone; a bad signature is invalid; an unsigned seal is never valid; a malformed payload is rejected before the signature check runs. | Android Keystore key generation and ECDSA signing, camera scanning, and the platform Base64 path. |
| Export and verify: the station zip | `StationExportTest` | Every row travels with its hash and with the drawing and audio bytes it names. A reader can rebuild the chain from the zip alone: each row's seal and the declared head, Merkle root and signed line all recompute from `events.json` and the attached files. | The device public key is absent on a JVM (no Keystore), so the device signature is not checked; the station's own reader is not run. |
| Record, finish, reopen | `SessionFinishTest` | Finishing adds no row and stores the root and head of the seals already written. An edited answer is found at its row after finishing. Reopening and discarding the unsaved tail restores the saved head, root and count, and the chain still verifies. A session with no rows seals the genesis. | Room's real transactions and cascade deletes, wall-clock timing, and the Keystore signature. |
| Settings that must survive process death | `SettingsNormalizerTest` | A stored officer id cannot smuggle a comma or control character into the comma-led provenance row and is capped. A stored signing speed is clamped to 0.25..1.5 and a non-finite value becomes 1.0. | The DataStore file itself: writing, reading after process death, preference key names and the corruption handler need an Android context. |
| Brain timeouts and offline fallback | `BrainContractTest` (existing) | The client and the station agree on every route, body key and timeout; an unpaired client uses no network at all; a paired but dead station returns null instead of throwing. | The real read timeout on a stalled station, TLS pinning, and the iroh transport. |
| Shown, signed and answered flow | `ConversationE2ETest` (existing) | A question is logged before it shows, taps carry a cue, a sign window reads recorded landmarks, and the subject's pick is what makes a meaning theirs. | Camera frames, on-device inference and a second screen. |

Retention, the gloss engine, the pair protocol, speech chunking and the reader have their own
existing tests; this map lists only the paths a wrong answer would break in the record.
