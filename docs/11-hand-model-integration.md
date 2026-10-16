# 11. Hand Model Integration

The built vision engine and sign reader (D-25), and the contract by which the partner team's model can replace either of them. Landmarks stay on the phone and in memory only (SEC-71); any meaning a model suggests is a proposal until the subject picks it (FR-86).

## 1. What is built

| Layer | Interface | Built implementation | Replaceable by the partner team |
| --- | --- | --- | --- |
| Landmark tracking | `camera/LandmarkTracker` | `HolisticTracker`: MediaPipe Holistic, `tasks-vision` 1.0.0, CPU, video mode | Yes, a tracker with the same output |
| Segmentation | `sign/SignCapture`, `segmentSigns` | Pause segmenter, thresholds per `SignWindow` | No; tune the constants |
| Sign reading | `sign/SignReader` | `LandmarkSignReader`: 262-word INCLUDE classifier in plain Kotlin | Yes, the main replacement path |
| Context and choices | `sign/SignChoices` | `sign_words.json` map, context limit, alternatives, slower cue | No; the map is content |

## 2. Sign windows

| Window | Opened by | Limit | Pause that ends a sign | Face points |
| --- | --- | --- | --- | --- |
| `ANSWER` | Read signs (officer), hand button (stage) | 20 s | 0.4 s | Kept in memory |
| `NARRATIVE` | Tell me (More page) | 3 minutes | 0.7 s | Dropped |

Outside a window the camera is a preview and no frame reaches the tracker. A chunk shorter than 0.2 s, or with fewer than 3 moving frames, is dropped. Hands count as still when the mean speed of all their points is below 0.3 frame widths per second, so fingerspelling with steady wrists is not cut.

## 3. Landmark tracker

```kotlin
interface LandmarkTracker : AutoCloseable {
    fun track(frame: Bitmap, timestampMs: Long): LandmarkFrame?
    fun warm() {}
    override fun close() {}
}

data class LandmarkFrame(
    val timestampMs: Long,
    val leftHand: List<Offset>? = null,   // 21 points, the subject's own left hand
    val rightHand: List<Offset>? = null,  // 21 points
    val face: List<Offset>? = null,       // 468 points
    val pose: List<Offset>? = null,       // 33 points
    val aspect: Float = 3f / 4f,          // upright frame width / height
)
```

| Property | Value |
| --- | --- |
| Frames | One at a time, newest only (CameraX `STRATEGY_KEEP_ONLY_LATEST`), up to 30 per second, only inside a sign window |
| Thread | One background analysis thread |
| Pixels | RGBA, about 640 by 480, rotated upright by CameraX, not mirrored |
| Lifetime | The buffer is reused after `track` returns; an implementation never keeps or copies it |
| Coordinates | Normalised to the upright frame, origin top-left, not mirrored; the stage mirrors for display |
| Sides | Hands are the subject's own left and right (Holistic assigns them from the pose wrists) |
| Errors | `track` returns null for a frame it cannot read; it never throws for a bad frame |
| Start-up | `warm()` loads the model off the main thread before the first frame; never during app start |

The holistic model (`models/holistic_landmarker.task`, 13.7 MB, float16, SHA-256 `e2dab61191e2dcd0a15f943d8e3ed1dce13c82dfa597b9dd39f562975a50c3f8`) is mapped from the uncompressed asset, checked against the content manifest and opened only if it matches (SEC-74). MediaPipe's native library ships for arm64-v8a only; 32-bit phones keep a plain mirror. The stage draws hands and arms only, never face points.

## 4. Sign reader

```kotlin
interface SignReader : AutoCloseable {
    val labels: List<String>               // the vocabulary, lower-case English
    val words: Map<String, String>         // labels to the app's vocabulary (sign_words.json)
    fun read(frames: List<LandmarkFrame>): List<SignGuess>   // every label, highest first
}

data class SignGuess(val word: String, val score: Float)
```

The built reader takes the frames of one chunk, resampled to 32, with per frame both hands (21 points each), shoulders, elbows and wrists, normalised to the mean shoulder midpoint and width, and presence flags (98 features). It runs a per-frame linear layer, two 1-D convolutions and pooling to 262 logits. Weights: `models/sign_reader.bin` (1.4 MB, float32) with its spec in `sign_reader.json`; both are checked against the manifest by `SignReaders` before use. On the INCLUDE test split it scores top-1 97.8% and top-3 99.4%; with children on phone cameras it is unmeasured. One read takes about 18 ms on a desktop JVM. Reads run one at a time on a background dispatcher.

Retraining: `tools/ml` (prepare, train, export), then `python tools/content/build_content.py --manifest-only`.

## 5. How readings are used

1. While a question is on screen, guesses are limited to labels whose mapped concept is one of that question's answer cards; with nothing on screen all labels are allowed. The top three are kept.
2. A best guess below 0.4 with lost tracking (over 30% of the chunk untracked, or gaps over 150 ms) or fast hands (wrist over 2.5 frame widths per second) shows the subject "A little slower, please".
3. On Done the words are logged as the app's reading (source MACHINE, "read by the app, not checked"). The officer may log their own reading beside it.
4. The subject sees alternatives only when the officer taps Ask them to choose: two or three picture cards per sign and "None of these". Only their tap records a meaning.
5. Guesses are never shown to the subject automatically, never shown on the officer glance strip and never sent over the second-screen link.

Core answer words that INCLUDE does not cover (yes, no, water, pain, help, stop, don't know) cannot be read; mother, father, police, home (house) and toilet (bathroom) are covered.

## 6. Replacing the reader or tracker

1. Implement `SignReader` (or `LandmarkTracker`) in plain Kotlin or with LiteRT `CompiledModel` (`com.google.ai.edge.litert`), GPU first with CPU fallback; the legacy `org.tensorflow.lite` artifacts are not used.
2. Ship the model under `app/src/main/assets/content/models/`, list it with `python tools/content/build_content.py --manifest-only`, and load it only when its hash matches.
3. Return it from `SignReaders.create` (or `LandmarkTrackers.create` in the release source set). The app creates it once, off the main thread.
4. Provide the label list and an updated `sign_words.json` so context limiting and picture choices keep working.
5. Native libraries are aligned to 16 KB pages; no new permission (the build gate refuses INTERNET, RECORD_AUDIO and location); every new artifact is added to `gradle/verification-metadata.xml` and `app/gradle.lockfile`, scans clean with OSV for anything that ships, and is listed in [open source](09-open-source.md) with its licence and attribution.

## 7. Budgets

| Resource | Budget | Built |
| --- | --- | --- |
| Time per tracked frame | 33 ms on the oldest supported 64-bit phone | To be measured on a phone |
| Time per read | Under 100 ms | About 18 ms on a desktop JVM |
| Memory | Session under 300 MB (NFR-06) | Narrative landmarks about 20 MB for 3 minutes; model mapped, not copied |
| APK | Release under 50 MB (NFR-05) | 47.0 MB (`standard`) |

## 8. Privacy and safety

- Frames and landmarks are never stored, logged or sent anywhere; landmarks stay in view-model memory for the session and are wiped on end, lock and clear (SEC-47, SEC-71).
- MediaPipe's usage statistics are dropped on the device (D-33).
- The partner team holds the training data, its consent records and the model card for any replacement, and supplies them for the DPIA and the audit.

## 9. Acceptance

| Check | Pass mark |
| --- | --- |
| TC-25 to TC-29 with the delivered model | All pass; overlay and dots on the oldest supported 64-bit phone |
| Reference outputs supplied by the partner team | Every landmark within 0.02, or every logit within 1e-3, of the reference |
| PT-05 | Under 300 MB during a 3-minute narrative |
| PT-02 and PT-03 | Show latency and stage smoothness unchanged |
| ST-05, ST-08, ST-13, ST-19 | Manifest gate passes; no debug code in release; OSV clean; tampered model refused |
