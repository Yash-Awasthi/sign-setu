# 09. Hand Model Integration

The built vision engine and sign reader, and the contract by which the partner team's model can replace either of them. Landmarks stay on the phone and in memory only; any meaning a model suggests is a proposal until the subject picks it.

## 1. What is built

| Layer | Interface | Implementation | Replaceable by the partner team |
| --- | --- | --- | --- |
| Landmark tracking | `camera/LandmarkTracker` | `SplitTracker`: MediaPipe hand, pose and face tasks, `tasks-vision` 1.1.1, video mode; each task on the GPU only if it finds the person in a bundled probe image, otherwise CPU; `HolisticTracker` if the hand or body task cannot open | Yes, a tracker with the same output |
| Segmentation | `sign/SignCapture`, `segmentSigns` | Pause segmenter, thresholds per `SignWindow` | No; tune the constants |
| Sign reading | `sign/SignReader` | `LandmarkSignReader` (314 ISL words, 250 ASL words, ISL letters and digits), `LiteRtSignReader` (the ISL word reader in format 3, the default) and `CtcSpellReader` (ISL spelled names on both hands, ASL fingerspelling), combined per language by `SignReaders` | Yes, the main replacement path |
| Home signs | `sign/HomeSigns` | Signs taught during the session, matched by cosine on the word reader's pooled features (`SignReader.embed`) | Follows the reader |
| Context and choices | `sign/ReadPolicy`, `sign/SignChoices` | `sign_words.json` map, context ranking (`decideRead`, `inContext`), alternatives (`signChoices`), slower cue (`needsSlower`) | No; the map is content |

## 2. Sign windows

| Window | Opened by | Limit | Pause that ends a sign | Face points |
| --- | --- | --- | --- | --- |
| `ANSWER` | Raised hands on the call stage, the camera button (stage), Read signs (officer) | 20 s, or 2 s of quiet hands, or 3 s with no movement | 0.4 s | Kept in memory |
| `NARRATIVE` | Tell me (More page) | 3 minutes, or 5 s of quiet hands | 0.7 s | Dropped |

Outside a window frames still reach the tracker while the stage camera shows, for the skeleton overlay and the nod, shake, point, palm, shrug and courtesy proposals, but no landmark is kept. A chunk shorter than 0.2 s, or with fewer than 3 moving frames, is dropped. Hands count as still when the mean speed of all their points is below 0.3 frame widths per second, so fingerspelling with steady wrists is not cut.

## 3. Landmark tracker

```kotlin
interface LandmarkTracker : AutoCloseable {
    fun track(frame: Bitmap, timestampMs: Long): LandmarkFrame?
    fun warm() {}
    override fun close() {}
}

data class LandmarkFrame(
    val timestampMs: Long,
    val leftHand: List<Offset>? = null,  // 21 points, the subject's own left hand
    val rightHand: List<Offset>? = null, // 21 points
    val face: List<Offset>? = null,      // 468 points
    val pose: List<Offset>? = null,      // 33 points
    val aspect: Float = 3f / 4f,         // upright frame width / height; CameraMirror sets the real value
    // Marked by FramePipeline after tracking: browsUp, mouthOpen, head (nod or shake), pointsAt,
    // stopPalm, shrug, everyday (namaste, wave, thanks, bow), blurry.
)
```

| Property | Value |
| --- | --- |
| Frames | One at a time, newest only (CameraX `STRATEGY_KEEP_ONLY_LATEST`); at most one per 30 ms inside a sign window, one per 33 ms with a person in view, one per 250 ms after 10 s with nobody |
| Thread | One background analysis thread |
| Pixels | RGBA, 16:9 at about 848 by 480 (480 by 848 upright), rotated upright by CameraX into its reused buffer, not mirrored; the preview uses the same 16:9 shape so the skeleton lines up |
| Lifetime | The buffer is reused after `track` returns; an implementation never keeps or copies it |
| Coordinates | Normalised to the upright frame, origin top-left, not mirrored; the stage mirrors for display |
| Sides | Hands are the subject's own left and right, assigned from the pose wrists |
| Errors | `track` returns null for a frame it cannot read; it never throws for a bad frame |
| Start-up | `warm` loads the models off the main thread when the session screen opens, before the first frame; never during app start |
| Delegate | Per task, GPU first; if the GPU fails to open, or an inference throws, that task reopens on the CPU for the next frame. `close` frees the tasks on lock and on memory pressure, and the next use opens them again |
| Schedule | Hands every frame; body and face on alternate frames, each reused for the frame between. The face task adds head angles (nods and shakes) and 19 mouth blendshapes |

The models (`models/hand_landmarker.task` 7.8 MB, `pose_landmarker_lite.task` 5.8 MB, `face_landmarker.task` 3.8 MB, and the fallback `holistic_landmarker.task` 13.7 MB) are mapped from uncompressed assets, checked against the content manifest and opened only if they match. The topology matches Holistic, so the readers see the same features. Release APKs are 64-bit only (arm64-v8a, and x86_64 in the universal build), so every installed device can run the tracker. The stage draws hands, arms, brows and lips, never the face oval or eyes.

## 4. Sign reader

```kotlin
interface SignReader : AutoCloseable {
    val labels: List<String>                      // the vocabulary, lower-case English
    val words: Map<String, String>                // labels to the app's vocabulary (sign_words.json)
    val cards: Map<String, List<String>>          // labels to further picture cards they answer
    fun read(frames: List<LandmarkFrame>): List<SignGuess> // every label, highest first
    fun embed(frames: List<LandmarkFrame>): FloatArray?    // pooled features for home signs; null if none
}

data class SignGuess(val word: String, val score: Float, val spelled: Boolean = false)
```

The built reader takes the frames of one chunk, resampled to 32, with per frame both hands (21 points each), shoulders, elbows and wrists, normalised to the mean shoulder midpoint and width, and presence flags (98 features). It runs a per-frame linear layer, two 1-D convolutions and average and max pooling (the features `embed` returns) to 314 logits. Weights: `models/sign_reader.bin` (2.5 MB, float16) with its spec in `sign_reader.json`; both are checked against the manifest by `SignReaders` before use. The shipped weights are a soup of five fine-tuned seeds ([model card](models/sign_reader_v3.md)): INCLUDE test top-1 98.2%; new signers on the app's words 38.7% on test and 49.2% on validation; with children on phone cameras it is unmeasured. The same reader also ships in format 3 (`sign_reader_v3.tflite`, 5 MB, float32), with the features computed inside the LiteRT graph; it is the default; Settings, New sign reader engine, off goes back to format 1 after the next start, and format 1 is also used if the format 3 file fails to load. Debug builds run both and log their agreement and time. On 400 held-out validation windows the two agree on every top word. One read takes about 18 ms on a desktop JVM. Reads run one at a time on a background dispatcher.

Retraining: `tools/ml` (prepare, train, export), then `python tools/content/build_content.py --manifest-only`.

## 5. How readings are used

1. While a question is on screen, guesses whose mapped concept is one of that question's answer cards rank first and the others follow, each group by score. The top three are kept.
2. A best guess below 0.4 with lost tracking (over 30% of the chunk untracked, or gaps over 150 ms) or fast hands (wrist over 2.5 frame widths per second) shows the subject "A little slower, please".
3. On Done the words are logged as the app's reading (source MACHINE, "read by the app, not checked"). The officer may pick one guess per sign, logged once as their own reading (source OFFICER) when every sign has a pick, or type a word per sign in the replay.
4. The subject is asked only when the officer taps Ask them to choose: two or three picture cards per sign and "None of these". Only the subject's tap records a meaning; until then the line stays a reading.
5. The reader's words are never shown to the subject (the stage shows one dot per sign). A stage device reads signs itself and sends only the words, one per sign, logged on the officer phone as the app's reading.

Core answer words outside the reader's training data (yes, no, water, pain, help, stop, don't know) cannot be read; mother, father, police, home (house), toilet (bathroom), drink (water), come and give are covered. Yes and no come from the nod and shake proposals and the Yes and No buttons.

The ISL name speller (`isl_spell_ctc`) reads both hands at 15 frames a second and decodes letters and spaces by CTC; a read wins over the word reader only with at least 3 letters, each at least 0.7 sure at its best frame, which kept 0.4% of new signers' word signs from reading as a name while passing 63% of spelled names. Signs the officer reads as a word the reader did not offer are taught to `HomeSigns` and offered first when the pooled features of a later sign are within cosine 0.65 (`tools/ml/oneshot.py`).

## 6. Replacing the reader or tracker

1. Implement `SignReader` (or `LandmarkTracker`) in plain Kotlin or as a LiteRT model like `LiteRtSignReader` (`com.google.ai.edge.litert:litert` 1.4.2; the 2.x line pulls in Play delivery libraries).
2. Ship the model under `app/src/main/assets/content/models/`, list it with `python tools/content/build_content.py --manifest-only`, and load it only when its hash matches.
3. Return it from `SignReaders.create` (or `LandmarkTrackers.create` in the release source set). The app creates it once, off the main thread.
4. Provide the label list and an updated `sign_words.json` so context limiting and picture choices keep working.
5. Native libraries are aligned to 16 KB pages; no new permission (the build gate refuses RECORD_AUDIO, background location, and in `standard` every discovery and location permission); every new artifact is added to `gradle/verification-metadata.xml` and `app/gradle.lockfile`, scans clean with OSV for anything that ships, and is listed in [open source](08-open-source.md) with its licence and attribution.

## 7. Budgets

| Resource | Budget | Current |
| --- | --- | --- |
| Time per tracked frame | 33 ms on the oldest supported 64-bit phone | About 110 ms with Holistic on the CPU of a mid-range phone (9 frames per second); the split tracker and the GPU are not measured |
| Time per read | Under 100 ms | About 18 ms on a desktop JVM |
| Memory | Session under 300 MB | Narrative landmarks about 20 MB for 3 minutes; model mapped, not copied |
| APK | No ceiling; accuracy comes first | arm64-v8a phone APK 118 MB, universal 151 MB (0.8.2) |

## 8. Privacy and safety

- Frames and landmarks are never stored, logged or sent anywhere; landmarks stay in view-model memory for the session and are wiped on end, lock and clear. The one exception is a one-frame skeleton drawing kept with a signed answer when the officer turns evidence mode on for the session.
- MediaPipe's usage statistics are dropped on the device.
- The partner team holds the training data, its consent records and the model card for any replacement, and supplies them for the DPIA and the audit.

## 9. Acceptance

| Check | Pass mark |
| --- | --- |
| Sign window and reader tests with the delivered model | All pass; overlay and dots on the oldest supported 64-bit phone |
| Reference outputs supplied by the partner team | Every landmark within 0.02, or every logit within 1e-3, of the reference |
| Memory | Under 300 MB during a 3-minute narrative |
| Show latency and smoothness | Unchanged |
| Security checks | Manifest gate passes; no debug code in release; OSV clean; tampered model refused |
