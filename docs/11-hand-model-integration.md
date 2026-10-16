# 11. Hand Model Integration

The contract between the app and the hand-detection model delivered by the partner team. Release 1 uses the model only to draw the subject's hands over the camera mirror (D-20); recognition of meaning (FR-22) comes later and always ends in show-back (FR-66). Landmarks stay on the phone (D-02).

## 1. Interface

The app depends only on this interface, in `app/src/main/java/app/signsetu/camera/HandTracker.kt`:

```kotlin
interface HandTracker : AutoCloseable {
    fun detect(frame: Bitmap, timestampMs: Long): List<TrackedHand>
    override fun close() {}
}

data class TrackedHand(val points: List<Offset>, val score: Float)
```

## 2. Input

| Property | Value |
| --- | --- |
| Frames | One at a time, newest only; older frames are dropped while a call runs (CameraX `STRATEGY_KEEP_ONLY_LATEST`) |
| Thread | One dedicated background thread; every call to `detect` comes from it |
| Pixels | `Bitmap` in `ARGB_8888`, rotated upright, not mirrored |
| Size | The camera's default analysis size, typically 640 by 480 or 480 by 640; the model resizes as it needs |
| Timestamp | Camera timestamp in milliseconds, increasing |
| Lifetime | The bitmap is recycled when `detect` returns; an implementation never keeps it or copies it elsewhere |

## 3. Output

| Property | Value |
| --- | --- |
| Hands | Zero, one or two `TrackedHand` values; an empty list when no hand is visible |
| Points | Exactly 21, in the MediaPipe hand topology: 0 wrist, 4 thumb tip, 8 index tip, 12 middle tip, 16 ring tip, 20 little finger tip |
| Coordinates | Normalised to the upright frame: x and y from 0 to 1, origin top-left, not mirrored; the app mirrors for display |
| Score | Confidence from 0 to 1; the app draws every hand it receives, so the model returns only hands it would show |
| Errors | `detect` does not throw for a bad or empty frame; the app treats any exception as no hands |

## 4. Budgets

| Resource | Budget | Reason |
| --- | --- | --- |
| Time per call | 33 ms on the oldest test device (Android 10, 3 GB RAM) | Keeps the overlay in step with the preview at 30 frames per second; slower calls only make the overlay lag |
| Memory | 100 MB above the stage without a model | The session stays under 300 MB (NFR-06); the stage with the camera measured 139 MB |
| APK size | 10 MB for the model in the base APK | The release APK is 12.9 MB against a 30 MB limit (NFR-05); a larger model is delivered as an on-demand pack |
| Start-up | Created on first use, never during app start | Cold start stays under 2 s (NFR-01) |

## 5. Packaging and runtime

- The model ships as `app/src/main/assets/content/models/<name>.tflite`, is listed in `manifest.json` by `python tools/content/build_content.py --manifest-only`, and is loaded only if its hash checks (SEC-54).
- Runtime: LiteRT `CompiledModel` (`com.google.ai.edge.litert`), trying the GPU accelerator first and falling back to the CPU. The legacy `org.tensorflow.lite` artifacts are not used.
- Native libraries are aligned to 16 KB pages, as Android 15 and later require.
- No new permission: the build fails if the merged manifest gains INTERNET, RECORD_AUDIO or a location permission (SEC-30, SEC-42).
- Every new artifact is added to `gradle/verification-metadata.xml` and `app/gradle.lockfile`, scans clean with OSV for anything that ships, and is listed with its licence in [open source](09-open-source.md).

## 6. Wiring

1. Implement `HandTracker` under `app/src/release/java/app/signsetu/camera/`.
2. Return it from `HandTrackers.create` in the same source set. The app creates it once, on first use, and keeps it for the life of the process.
3. Debug builds keep their stand-in tracker, which draws a waving hand when the file `files/demo_hands` exists.

## 7. Privacy and safety

- Frames and landmarks are never stored, logged or sent anywhere (SEC-04, SEC-47).
- In release 1 the output only draws the overlay. Any meaning a later model suggests is shown back to the subject, and only the subject's Yes records it (FR-66).
- The partner team holds the training data, its consent records and the model card, and supplies them for the DPIA and the audit.

## 8. Acceptance

| Check | Pass mark |
| --- | --- |
| TC-19 with the delivered model | Overlay drawn over the live preview on the oldest test device |
| Reference images supplied by the partner team | Every point within 0.02 of the reference output |
| PT-05 | Under 300 MB during a session with the model running |
| PT-02 and PT-03 | Show latency and stage smoothness unchanged from the test report |
| ST-05, ST-08, ST-13 | Manifest gate passes; no debug code in release; OSV clean for shipped components |
