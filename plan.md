# plan.md — Sign Setu

Consolidated plan. Absorbs `plan.txt` (the owner's 14-line wishlist, reproduced
verbatim in Appendix D — every
line is re-stated below as a concrete subtask in Phase 4/5, nothing dropped) and
the plan-like parts of `README.md` and `docs/11-product-overview.md`,
`docs/12-screens.md`, `docs/19-walkthrough.md`. The numbered docs stay as
reference; the audit and compliance docs (`docs/04-security.md`,
`docs/13-dpia.md`, `docs/14-attestation-and-pinning.md`) remain normative.

Generated 2026-11-24. Evidence base: `README.md`, the absorbed wishlist, 21 docs under
`docs/`, `.github/workflows/ci.yml` (12 jobs), `app/build.gradle.kts`,
`gradle/libs.versions.toml`, `gradle/verification-metadata.xml`,
`settings-gradle.lockfile`, 174 Kotlin files (38,300 lines) with 388 `@Test` in
49 files, `avatar/` (JS module + tests), `portal/` (Vite/TS reviewer portal),
`gateway/` (14 Go files), `brain/` (Python station service), `relay/`,
`content/`, `tools/{ml,security,content}`, and `render.yaml`.

**Order:** corrective → preventive → perfective → widening → depth → enterprise.

**Hierarchy:** Phase → Major (M#.#) → Minor (m#.#.#) → Subtask. One subtask = one

**Done tasks stay.** A finished subtask becomes `- [x]` in place with one evidence line under it (`verified-here: <command> → <result>`). Nothing is ever removed from this list.
PR = one command that proves it.

**Tag (exactly one):** `[C]` corrective · `[P]` preventive · `[F]` perfective ·
`[W]` widening · `[D]` depth · `[E]` enterprise.

**Method anchors:** `mobile-apps` §4/§5 (lifecycle, permissions), `security-operations`
§5 (consent, sealing, threat model), `engineering-craft` (behaviour is the
contract), `design-ux` §1/§5, `data-ml` §8 (accuracy claims with n), `writing-docs`
§1, `devops-cloud` §8.

---

## 0. Verified ground truth

| Fact | Measured | Claimed | Source |
|---|---|---|---|
| Version | `versionName 0.8.51`, `versionCode 23` | "0.8.5 (version code 22)" | `app/build.gradle.kts:36-37` vs `README.md:7` |
| SDK | `minSdk 29`, `targetSdk 37`, `compileSdk 37` | "Android 10 (API 29) and later, 64-bit" | same |
| Toolchain | AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00, Room 2.8.5, SQLCipher 4.19.1 | — | `gradle/libs.versions.toml` |
| Code | **257 Kotlin files, 48,700 lines**, one `app` module (all `.kt` files are under `app/`) | earlier draft said 174 / 38,300 | `find app -name '*.kt' \| wc -l` → 257; `cat` sum → 48,700 |
| Tests | 388 `@Test` in **59** files, two flavours (`standard`, `nearby`) | earlier draft said 49 files | `grep -rho @Test` → 388; `grep -rl @Test` → 59 |
| Instrumented tests | An `instrumented` job **exists**: 4 shards, API 34 `pixel_6` emulator, 10 named classes, gated on `app-changed` (a PR touching `app/`, `gradle/`, `settings.gradle.kts`, `build.gradle.kts` or `ci.yml`), a new version, or a manual run. 3 of the 13 classes on disk are device-only **as measured here**: `TrackingPerformanceTest`, `StageFrameRateTest` and `StageEndToEndTest` each fail on an Android 14 x86_64 emulator. MediaPipe does run on the emulator: `SignPipelineDeviceTest` passed in 333 s. | earlier draft said 8 classes and "MediaPipe needs a phone" — **wrong twice** | `ci.yml` instrumented job; `.github/workflows/ci.yml` + measured runs |
| CI | **12 jobs**: version, android (unit+lint), avatar, portal, tools, gateway, station, semgrep, osv, instrumented, gitleaks, release | earlier draft said 7 | parsed from `jobs:` in `ci.yml` |
| Dependency integrity | `verification-metadata.xml`, `app/gradle.lockfile`, `settings-gradle.lockfile` | same | files present |
| Secret scan | `.gitleaks.toml` present | — | root |
| Modules | `avatar/` JS+Node tests, `portal/` Vite TS, `gateway/` Go 14 files, `brain/` Python, `relay/`, `content/` | — | `ls` |
| Sign readers | ISL 314 words + letters/digits/names; ASL 250 + fingerspelling (experimental) | same | `README.md`, `tools/ml/EVAL.md` |
| Evidence chain | PDF: hash chain, Keystore-signed Merkle root, QR verify page, BSA s.63 draft certificate; `tools/security/verify_seal.py` | same | `README.md` |
| Retention | 24 h default, 4–72 h choices, hourly job | same | `README.md`, `docs/13-dpia.md` |
| Owner wishlist | 14 lines, reproduced in Appendix D (`plan.txt` deleted) | — | Appendix D |

Invariants (regulatory and safety, non-negotiable):

1. **Offline by default.** No network call without an explicit opt-in; no assistant key in an APK; the `standard` flavour has no location or scanning permission and the build fails if one reaches the merged manifest.
2. **Consent before recording.** The person's voice is recorded only between the officer's start and stop taps, with consent logged first.
3. **Sessions are encrypted at rest** under a Keystore key usable only while unlocked; retention is enforced by the hourly job, not by the UI.
4. **Ledger integrity.** Every exported record keeps its hash chain, Merkle root, device signature and the QR verification path; `verify_seal.py` must verify anything the app exports.
5. **The app is an aid, not an interpreter or a statement.** No UI, string or document may imply certified interpretation or a statutory recording.
6. **A reading is never an answer.** Camera-derived text reaches the officer only; the person confirms meaning.
7. **No new permission without a DPIA update** in `docs/13-dpia.md`.

---

## Phase 1 — Corrective: make the documented state the real state

### M1.1 Documentation drift with legal weight

**Why:** the README is what a department reads before deploying. It states
version 0.8.5 / code 22 while the build is 0.8.51 / code 23, and any released
behaviour change missing from it is a compliance problem, not a typo.
**Done when:** the version, features and permissions in the docs are generated or
checked against the build.

- [x] Generate the version line in `README.md` from `app/build.gradle.kts`; a mismatch fails CI. `[C]`
  - verified-here: `python tools/docs/check_version.py` → exit 1 before the write (README said 0.8.5/22, build 0.8.51/23), exit 0 after `--write`; wired into the `tools` job of `.github/workflows/ci.yml`; 6 tests in `tools/docs/test_check_version.py` pass.
- [x] Diff the documented feature list against the code and the screens doc: anything described but removed is deleted, anything present but undocumented is added. `[C]`
  - verified-here: `python tools/docs/check_docs.py` → exit 1 with `docs/05-avatar-and-gestures.md: names a file that does not exist: content/clips.json` and `docs/17-live-speech.md`, `docs/18-station.md`: `brain/link.py`; both paths corrected to `app/src/main/assets/content/clips.json` and `brain/brain/link.py`, now exit 0. Checks: every relative link resolves, every capture in `docs/screens/` is described in `12-screens.md` and every reference exists, every `docs/*.md` is in the map, a `code` path exists, and the stated counts (languages 17, ISL 314, ASL 250, retention 4–72 h, default 24 h) equal the bundle and `SettingsStore.kt`. 12 tests in `tools/docs/` pass; run in the `tools` CI job.
- [x] Re-verify the permission table against the merged manifest of both flavours as a test, and print it into the docs. `[C]`
  - verified-here: `python tools/security/check_permissions.py` → reads `app/src/main/AndroidManifest.xml` and `app/src/{standard,nearby}/AndroidManifest.xml`, generated table now in `docs/README.md` §2.4 (18 permissions, per-flavour API limit and scan flag), exit 0; the prose row gained `ACCESS_NETWORK_STATE` and `ACCESS_LOCAL_NETWORK`, which it was missing. 10 tests in `tools/security/` pass; run in the `tools` CI job. Library-added permissions stay covered by the merged-manifest gate in `app/build.gradle.kts:124-159`.
- [x] State the supported device baseline (API 29, 64-bit, RAM, free storage for the 806 MB model) as measured values, not adjectives. `[C]`
  - verified-here: `python tools/docs/baseline.py` → exit 1 with empty placeholders, exit 0 after `--write`; it reads `minSdk 29`, `abiFilters [arm64-v8a, x86_64]`, `MIN_MEMORY_BYTES = 2400 MiB`, `MIN_FREE_BYTES = 1200 MiB` from `LocalModel.kt:104-105` and 95.1 MB of shipped assets, and writes the README `- Runs on` line and the `| Devices |` row of `docs/README.md`. The 806 MB model file is not on this machine: needs-verify (`needs-verify: assistant model byte size`). 18 tests in `tools/docs/` pass; run in the `tools` CI job.

### M1.2 Instrumented tests run only on a version bump

**Why:** 388 unit tests pass on every PR. The parts that only fail on a device —
SQLCipher under Keystore, the hourly retention job, the second screen, MediaPipe
landmarks, PDF export and verification — run **only** when `version.outputs.new` is
true or a human dispatches the workflow (`ci.yml:202`), and only for 8 named
classes. A regression can therefore sit on `master` until release day.
**Done when:** the instrumented suite runs on every PR that touches `app/` (sharded,
release-blocking) and covers export/verify and retention, not just the 8 classes.

- [x] Widen the shard matrix to the full instrumented suite, keep the 45-minute timeout, and add a `paths: app/**` trigger so unrelated PRs stay fast. `[C]`
  - verified-here: `python tools/ci/check_workflows.py` → exit 1 before (job needed only `version`), exit 0 after. The job now `needs: [version, app-changed]` and runs when `needs.app-changed.outputs.any == 'true' || needs.version.outputs.new == 'true' || workflow_dispatch`, keeping `timeout-minutes: 45` and 4 shards; 10 classes instead of 8, adding `SignPipelineDeviceTest` (MediaPipe, measured pass on this emulator in 333 s) and `SkeletonOverlayDeviceTest` (measured pass in 256 s). 7 tests in `tools/ci/` pass; the check runs in the `tools` CI job so a new instrumented class cannot be left out.
  - deviation: the trigger is the `app-changed` job's `git diff` filter, not `on.pull_request.paths`, because the `release` job needs `instrumented` in the same workflow and a workflow-level `paths` filter would also skip the unit, avatar, portal and gateway jobs on a docs-only PR.
  - deviation: "the full suite" is 10 of 13 classes. Measured on the local Android 14 x86_64 emulator: `TrackingPerformanceTest` exit 1 (frame budget), `StageFrameRateTest` exit 1, `StageEndToEndTest` exit 1. The three are recorded in `DEVICE_ONLY` in `tools/ci/check_workflows.py` with the reason.
- [x] Instrumented test: create a session, export a PDF, verify it with `verify_seal.py`, assert the seal and the hash chain. `[C]`
  - verified-here: `app/src/androidTest/java/app/signsetu/VerifierFixtureDeviceTest.kt` starts a session, logs three events, exports the real PDF and asserts the app's own `checkReport` reports `SealCheck.MATCHES_DEVICE` on the file it wrote. Off the phone: `adb exec-out run-as in.signsetu cat files/verify/report.pdf` then `python tools/security/verify_seal.py --pdf report.pdf --passphrase D6TS-B75N-M92K-S79D` → exit 0, `valid: session 5e40d482-…, 4 sealed entries, chain head 613e02d6…, Merkle root 437e21ab…, device key d507f2ce…`; the same seal with one chain-head field changed → exit 1 `INVALID`. The `instrumented` CI job (shard 0) runs it with `am instrument` and pipes the pulled PDF through `verify_seal.py`.
  - deviation: the CI pull uses `adb exec-out run-as` and a direct `am instrument` run instead of the `connected…AndroidTest` task, because that task uninstalls the app — and the report with it — before a pull could happen.
- [x] Instrumented test: retention job deletes a session past its window and never deletes one inside it, with an injected clock. `[C]`
  - verified-here: `app/src/androidTest/java/app/signsetu/RetentionClockDeviceTest.kt` on an encrypted Room database with `SessionRepository(clock = { … })`: a session 25 h idle is purged while one 23 h idle stays (4 tests, gradle exit 0 on the Android 14 emulator, 2 m 10 s); a session exactly on the 24 h boundary survives the sweep and goes one millisecond later; `purgeExpired(0)` keeps everything; an open session is judged by its last row, not its start. Added to the `instrumented` CI loop.
- [x] Instrumented test: the encrypted database cannot be opened after the device is locked (Keystore behaviour), and the app shows the documented message instead of crashing. `[C]`
  - verified-here: `LockedKeystoreDeviceTest` on the Android 14 emulator, 5 tests, gradle exit 0, 21 s: a database written under one key is refused with `SQLiteNotADatabaseException` by another (14.7 s); 32-byte data key zeroed on close; a phone with no screen lock reports `DatabaseKey.unbound == true` and `container.unlock()` raises `container.keyUnbound` (the `key_unbound_title`/`key_unbound_body` notice) with sessions open and no crash; on this emulator Android refuses to create a key that requires authentication at all — `InvalidAlgorithmParameterException`, which is why `DatabaseKey` has the open-key fallback.
  - needs-verify: the locked-device refusal itself (`UserNotAuthenticatedException` from a key created with `setUserAuthenticationRequired(true)`) is written and skips itself via `Assume` on this emulator, which has no screen lock or biometric. It needs a phone with a PIN or fingerprint: `:app:connectedDebugAndroidTest -P…class=app.signsetu.LockedKeystoreDeviceTest`.
- [x] Instrumented test: permission denial for `RECORD_AUDIO` yields the documented path with no recording and no crash. `[C]`
  - verified-here: `VoicePermissionDeviceTest` on the API 34 emulator after `pm revoke in.signsetu android.permission.RECORD_AUDIO`: `VoiceCapture.permitted()` false, `start()` false without a throw, level stays 0, `stop()` hands over no audio, no file appears in `filesDir`, the sheet's own line is `voice_no_permission` (now a `voiceHint(granted)` function) and becomes `voice_hold` once allowed; `pm grant` restores it and the capture is usable again. 1 test, exit 0, 0.26 s. Added to the `instrumented` CI loop; `:app:testStandardDebugUnitTest :app:testNearbyDebugUnitTest` exit 0.

### M1.3 The owner's list, first pass (high-severity usability)

These come from the owner's list (Appendix D) and are corrective because they break the core journey
for a non-speaking person, not because they are cosmetic.

- [x] Remove the password form from PDF export (owner: "remove password form exports pdfs"); keep the seal and the hash chain, and state the threat-model change in `docs/04-security.md`. `[C]`
  - verified-here: `SessionReport.export` returns the written file and no passphrase; `ExportDialog` shows Share and Done only; `checkReport`, `ReportCheck`, `saveToPhone` and `SavedSessions` no longer take or show one; `tools/security/verify_seal.py --pdf` needs no `--passphrase` (an older encrypted report still takes one). Measured on the API 34 emulator at 0.8.52/24: `ReportExportDeviceTest` 1 test exit 0 (`doc.isEncrypted == false`), `ForensicExportDeviceTest` 1 test exit 0 (unpassworded file, store edited behind the app's back → `RECORD_ALTERED`, inserted row → `DIFFERS_FROM_DEVICE`), `VerifierFixtureDeviceTest` 1 test exit 0; the exported PDF pulled off the phone gives `verify_seal.py --pdf` exit 0 `valid: session 5268f90d…, 4 sealed entries`, and the same seal with one signature character changed gives exit 1 `INVALID`. `:app:testStandardDebugUnitTest :app:testNearbyDebugUnitTest` exit 0, `go test ./...` in `gateway/` ok, all six `tools/*` unittest dirs OK, and `check_version.py`, `check_docs.py`, `baseline.py`, `check_permissions.py`, `check_workflows.py` all exit 0. Threat model: `docs/04-security.md` 2.2, 2.10 (Export passphrase row → removed in 0.8.52) and `docs/01-requirements.md` 4.4 now say the file has no password, so the seal shows alteration and not confidentiality.
- [x] Remove the timed hold when navigating to messages (owner: "no timed hold when going to msgs"). `[C]`
  - verified-here: the stage's chat handle (`OfficerHandleLayer`, `ui/session/StageCharacter.kt`) is now a plain `clickable` button: `HANDLE_HOLD_MS = 333L`, the hold ring, the gesture loop and their haptics are gone. `StageHandleTapDeviceTest` on the API 34 emulator renders `Stage(...)`, finds the node by `show_officer`, asserts the semantics carry `OnClick` and not a long-press, then a single `performClick()` calls the officer callback exactly once (1 test, exit 0, 23.8 s); added to the `instrumented` CI loop and `check_workflows.py` exit 0. `StageEndToEndTest` now taps the handle instead of holding it 1.5 s (that class stays device-only). `docs/02-architecture.md` §7 says one tap. `:app:testStandardDebugUnitTest :app:testNearbyDebugUnitTest` exit 0, `check_docs.py` exit 0.
- [x] Fix caption highlighting during a video call; today the wrong words highlight (owner). `[C]`
  - verified-here: the cause was a bare `IntRange` applied to whatever line the call showed, while the offsets belonged to another one (the language finder's hello against its multi-script prompt, the hint standing in for a caption). `CaptionLight(text, range)` now carries its own text and `inText(shown)` returns null anywhere else; `Stage` builds it from `shownText` (typed text: `typed.text`) and from the waiting line's own text, and `VideoCallStage` lights through `inText(promptText)` and `inText(idleLine)`. `CallCaptionLightTest` on the API 34 emulator: a hello's offsets against the prompt light nothing and `spanStyles` is empty, a matching line lights exactly one span over "Meena", an empty or too-long range lights nothing (3 tests, exit 0, 12.3 s); added to the `instrumented` CI loop, `check_workflows.py` exit 0. `docs/05-avatar-and-gestures.md` records the rule. `:app:testStandardDebugUnitTest :app:testNearbyDebugUnitTest` exit 0, `check_docs.py` exit 0.
- [x] Make the mute control for the person's speech visible on the main surface, never behind a menu (owner: "not deep hidden to the policeman"). `[C]`
  - verified-here: `SubjectSpeech` holds the mute as observed state, `SpeechPlayer.play`, `playBytes` and `speakOffline` return without playing while it is set, and `Stage` stops the line already playing. The button is an `IconButton` in the session header beside Call and Quick notes (`officer_mute` → `ic_volume`, muted → new `ic_volume_off`), strings `speech_mute`/`speech_unmute` in English and Hindi. `OfficerMuteButtonDeviceTest` renders `OfficerPane` on the API 34 emulator with no menu opened: the mute node exists, the More menu's own row does not, one click sets the mute, the label flips to "Unmute their speech", the second click clears it (1 test, exit 0, 24.4 s); added to the `instrumented` CI loop, `check_workflows.py` exit 0. `docs/03-ux.md` §5 records the rule. `:app:testStandardDebugUnitTest :app:testNearbyDebugUnitTest` exit 0, `check_docs.py` exit 0.
- [x] Cap divergent paths at three per screen; measure the current count per screen in `docs/12-screens.md` and record the cuts. `[C]`
  - verified-here: `tools/ux/count_paths.py` (static inventory, 9 unit tests, `tools/ux` discover OK) reports 2 screens that offer a way out and the widest at 4 for the close dialog's branch set. `WrapUpPathsDeviceTest` counts what each state really shows on the API 34 emulator — kept session: 3 (Thank them on the stage, Save and finish, Delete without saving; the export row is absent, Keep going is present and diverges nowhere); rooted phone: 3 (Thank them, Export report, Delete without saving); saved session reopened: 3 (Thank them, Save and finish, Discard unsaved conversation); delete still needs its second tap. 4 tests, exit 0, 32.9 s; added to the `instrumented` CI loop, `check_workflows.py` exit 0. The cut: `SessionWrapUp.kt` no longer offers Export report for a session kept in history — the same sealed PDF is written to `Documents/SignSetu` by the saved session's download (`saveToPhone`, wired at `AppRoot.kt:273`), and only a rooted phone, which keeps no history, still sees the export there. `docs/12-screens.md` §5 holds the measured table, `docs/03-ux.md` §"Finish" the rule, `tools/README.md` the tool. `:app:testStandardDebugUnitTest :app:testNearbyDebugUnitTest` exit 0, `go test ./...` exit 0, all seven `tools/*` unittest dirs OK, `check_version`/`check_docs`/`baseline`/`check_permissions` exit 0.
  - verified-here (2026-11-26 re-run, emulator ci34 API 34): `python tools/ux/count_paths.py` -> exit 0, "2 screen(s) offer a way out; the widest offers 4" and the cap is per state; `./gradlew :app:connectedStandardDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.signsetu.WrapUpPathsDeviceTest` -> BUILD SUCCESSFUL, tests="4" failures="0" errors="0", 31.052 s.

### M1.4 Evidence-chain gaps

- [x] `verify_seal.py` and the in-app "Check a report" agree on every field: test one exported PDF through both and diff the verdicts. `[C]`
  - verified-here: `SealAgreementDeviceTest` (API 34 emulator, signed by the device key, 1 test exit 0, 17.0 s) exports a 3-entry session, reads the seal out of the PDF's metadata and records the app's own verdict `MATCHES_DEVICE` with the six seal fields (session `16591911-6617-4906-8872-bbdaab840e35`, 3 sealed entries, chain head `62c8ef9d…545f`, Merkle root `6b88dd17…3901`, printed-rows digest `9474-ca87-91d8-0b9d`, device key `c59dc519…c64f`) into the app's cache. Pulled off the phone and run through `tools/security/seal_agreement.py --pdf … --app …`: exit 0, "the two readers agree on every seal field", and `verify_seal.py` alone prints `valid: session 16591911…, 3 sealed entries`. The same seal with one signature character flipped in a copy makes the off-phone check say `INVALID` while the app's verdict still says `MATCHES_DEVICE`: `seal_agreement.py` exits 1 with `DISAGREE the off-phone check says invalid`. 9 unit tests for the diff (`tools/security` discover OK, exit 0), including an unsigned seal the off-phone check accepts against a matching app verdict. Added to the `instrumented` CI loop, `check_workflows.py` exit 0; `docs/04-security.md` 2.2 and `tools/README.md` record the pair. `:app:testStandardDebugUnitTest :app:testNearbyDebugUnitTest` exit 0, `check_docs.py` exit 0.
- [x] Export failure paths: storage full, cancelled share sheet, app killed mid-export — no partial PDF, no orphaned temp file, a clear message. `[C]`
  - verified-here: `SessionReport.export` now hands the bytes to `writeReport`, which writes `<name>.part`, refuses a short write, renames to the report's name only when every byte is down, and deletes the part on any failure — a report already under that name is never touched. `purgeStaleExports` deletes any `.part` whatever its age, and the export folder is emptied at start and on every unlock, so a kill mid-write leaves nothing shareable. `WriteReportTest` (4 JVM tests, `:app:testStandardDebugUnitTest` exit 0): the whole file lands with no part beside it, a writer that stops half way and one that throws leave the folder empty, and a failed re-export keeps the earlier whole report. `ExportFailureDeviceTest` on the API 34 emulator (3 tests, exit 0, 18.7 s): with a plain file where `cacheDir/exports` belongs the export fails with `IOException` and no part or PDF is left, an orphaned `sign-setu-report-1.pdf.part` is cleared by the next export which lands as one whole PDF (`%PDF-`, 1 file), and a name taken by a directory makes the rename fail while the part is cleaned up and the occupant untouched. A cancelled share sheet deletes the file on Finish (`OfficerPane.kt:567`); `export_failed` now names a full phone ("Could not create the report: the phone may be full. The session is unchanged.") in English and Hindi. Added to the `instrumented` CI loop, `check_workflows.py` exit 0; `docs/02-architecture.md` §"The export" records the part-file rule. `check_docs.py` exit 0.
- [x] The draft BSA s.63 certificate text carries the app version and the device key fingerprint; assert both appear in a fresh export. `[C]`
  - verified-here: `EvidenceIntegrity.generateSection63Certificate` takes `appVersion` (default `"${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"`) and prints it in section 1 as `• Producing App / Build    : Sign Setu 0.8.52 (24)` beside the hardware line; the device key fingerprint and the signature state were already in section 4 and are now asserted. `EvidenceIntegrityTest` (JVM, `:app:testStandardDebugUnitTest` exit 0) checks the build line carries `BuildConfig`'s real version rather than a literal, the fingerprint, `ECDSA P-256`, and that a certificate without a key says `Not available` / `Not signed` instead of printing nothing. `ReportExportDeviceTest` on the API 34 emulator now reads the certificate page of a fresh export and asserts the shipped build (`Sign Setu 0.8.52 (24)` from `packageManager`), the `DeviceSeal.publicKey()` SHA-256 fingerprint and `ECDSA P-256` are printed (1 test, exit 0, 37.5 s); the repo is constructed with the device signer so the certificate states the signature it really has. `docs/11-product-overview.md` §"What the export carries" lists the new fields. `check_docs.py` exit 0, `:app:testNearbyDebugUnitTest` exit 0.
- [x] Document what the seal proves and what it does not (device possession, not authorship) in `docs/04-security.md`. `[C]`
  - verified-here: `docs/04-security.md` §2.10 "What the seal proves, and what it does not" states the signed line exactly as the code builds it (`EventSeal.sealLine`: `SS1|<session>|<sealed entries>|<chain head>|<Merkle root>`, ECDSA P-256, key carried in the seal) and sets two tables: it proves those four facts, that no character of the line or signature changed after export, that the key lived in a Keystore on the phone, and — with the record still on the phone — that the stored rows, drawings, landmarks and recorded answers rebuild the same head; it does not prove who used the phone, that the record is true, when it was sealed (no trusted timestamp), that the phone was genuine and unrooted (no attestation, F-4), that the printed pages are the sealed entries off-phone (the printed-rows digest is outside the signed line), or that the key belongs to the officer who signs the paper. The paragraph ends on the sentence the plan asks for: a device-possession record, not an authorship record. Reading the code to write it found the gap and it is recorded as finding **F-10** (printed-rows digest in the seal but outside the signature, Low, accepted for now because signing it would change the line and every report already issued). `docs/01-requirements.md` §4.4 points at §2.10. `check_docs.py` exit 0.

---

## Phase 2 — Preventive

### M2.1 The gates this project already values, extended

- [ ] Android coverage per package (session, records, sign readers, stage, settings) with a floor that ratchets; publish the report as a CI artefact. `[P]`
- [x] Fuzz the exported-PDF verifier (`verify_seal.py`) and the record JSON parser with malformed inputs; a crash or a false "valid" is a release blocker. `[P]`
  - verified-here: `tools/security/fuzz_parsers.py` (fixed seed, `--trials`, `--seed`) mutates a genuine P-256 seal line in 20 named ways and then at random, writes random and truncated blobs as PDFs, and builds conversation zips whose `session.json`/`events.json` are empty, not JSON, the wrong JSON type, 100 000-deep nested, 10 MB of id, or a truncated zip. It found three real escapes, all closed in this commit: `verify_seal.check` raised `binascii.Error`/`ValueError` out of `b64decode` and `load_der_public_key` on a garbage key or a short signature (now 2 "not base64 of a DER public key" / "not a readable DER public key", and a wrongly shaped signature is 1 `INVALID`); `seal_from_pdf` raised pypdf's errors on a file that is not a PDF (now `""`, and `verify_seal.py --pdf` prints "this file cannot be read as a PDF" and exits 2, with `seal_agreement.py` matching); `brain/brain/inbox.py::_record` returned a dict as the session id and could hand rows from non-objects (now (None, []) for a non-text id and `None` for a row that is not an object). A mutation outside the signed line — the printed-rows digest — still verifies, which is finding F-10, so the fuzzer's rule is "never valid for an altered signed line" rather than "never valid unless untouched". Re-run clean at 300 inputs seed 1 and 400 inputs seed 7: "no crash and no false valid"; 13 unit tests in `tools/security/test_fuzz_parsers.py` pin each closed escape, `tools/security` discover OK. CI gained the step (200 inputs, seed 1) and `tools/ux` joined the tool-test loop, `check_workflows.py` exit 0; `tools/README.md` §Security records all three. `check_docs.py` exit 0.
- [x] Property tests for the retention clock: any start time, any window 4–72 h, exactly one deletion at the boundary. `[P]`
  - verified-here: the rule now has one home, `data/session/Retention.kt` (`idleSince`, `cutoff`, `expired`, `HOUR_MS`), and `SessionRepository.purgeExpired` builds its cutoff with it. `RetentionPropertyTest` (JVM, `:app:testStandardDebugUnitTest` exit 0, in the standard and nearby flavours' test run): the windows the settings actually offer are asserted to be 4, 8, 12, 24, 48 and 72; 5 000 random start times from −10¹² to +10¹² ms across those windows are kept exactly on the boundary and deleted one millisecond later; 2 000 random points inside a window never expire; 2 000 expired sessions never come back as the clock moves on; 0 and negative hours never expire anything; and over 300 random sweeps of 40 sessions the sessions the rule names are exactly the ones below the cutoff the database is given. `RetentionClockDeviceTest` gained `aRandomSweepTakesExactlyTheSessionsTheRuleNames`, which builds 24 sessions with random starts, ends and last rows (so some are open and some finished), sweeps with a random window, sometimes placing the clock exactly on a boundary, and asserts both the count the sweep returns and the exact set left behind match `Retention` (5 tests, exit 0, 1.07 s). `docs/02-architecture.md` §"Session lifecycle" names the shared rule and both tests. `check_docs.py` exit 0.
- [x] Lint baseline zero, `abortOnError`, and a rule that no new `android.permission.*` can appear without touching the permission test. `[P]`
  - verified-here: `python tools/ci/check_lint.py --sarif app/build/reports/lint-results-standardDebug.sarif` → "64 finding(s) in lint-results-standardDebug.sarif; the list allows 64 / no new finding, no error, no stale entry", exit 0; `python tools/ci/check_workflows.py` → exit 0; `python tools/security/check_permissions.py` → exit 0; `python -m unittest discover -s tools/ci -p "test_*.py"` → "Ran 18 tests ... OK"; `python -m unittest discover -s tools/security -p "test_*.py"` → "Ran 36 tests ... OK". There is no lint-baseline.xml: the tolerated warnings live in `tools/ci/lint-known.json` (22 rule/file entries, sum 64) where strike-a-stale-entry is an error, so the list can only shrink; `abortOnError` is explicit in `app/build.gradle.kts`; CI runs both lint variants and the ratchet on the debug SARIF, which is the one that also covers `androidTest`. `check_permissions.py` now refuses a manifest permission absent from its `APPROVED` table (a reason is mandatory), so a new `android.permission.*` fails until that table and its test are touched.
- [x] Content checks (already in CI) extended to every language: 17 caption languages must each have complete key coverage, and a missing string fails. `[P]`
  - verified-here: `python tools/content/check_content.py` → "content references, ids, card images and 17-language labels are consistent", exit 0; `python -m unittest discover -s tools/content -p "test_*.py"` → "Ran 12 tests ... OK". A `subject_ui` value that is not a full language map, or whose keys name no known language, is now an error rather than passing unchecked; the same `label()` rule (missing, unknown, blank) covers cards, categories, triage groups and questions, phrase labels and slots, so all 17 caption languages must be complete for every checked string.

### M2.2 Sign-reader accuracy as a gate, not a claim

**Why:** `tools/ml/EVAL.md` holds the accuracy story; the readers are the
product's core claim and the place where a confident wrong word does the most
harm.
**Done when:** a regression in accuracy fails before a release.

- [ ] Freeze an evaluation set (landmark sequences only, no video) with per-word labels; store it under `tools/ml/data/` with a manifest. `[P]`
- [ ] `tools/ml/eval.py --check` reports top-1/top-5 per word, the "unclear" rate, and the confusion pairs; CI runs it on the frozen set. `[P]`
- [ ] Fail the build when top-1 drops more than 1 point or the unclear rate rises more than 2 points against the recorded baseline. `[P]`
- [ ] Publish the current numbers in `docs/05-avatar-and-gestures.md` with n and the date. `[P]`

### M2.3 Data and privacy guarantees as tests

- [x] A test that the app performs no network request with the online assistant off and no station paired (instrumented, checking the socket layer). `[P]`
  - verified-here: ./gradlew :app:connectedStandardDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.signsetu.NoNetworkOfflineDeviceTest -> BUILD SUCCESSFUL, OK (1 test), counted ProxySelector selections 0 offline and 1 for the dead-port control.
- [x] A test that the GGUF model is refused unless its SHA-256 is on the allow-list. `[P]`
  - verified-here: ./gradlew :app:testStandardDebugUnitTest --tests app.signsetu.ModelAllowListTest -> BUILD SUCCESSFUL, 5 tests, 0 failures (listed hash accepted, one flipped byte and unlisted hash refused, shipped lists 64-hex).
- [x] A test that a session's database file is unreadable without the Keystore key (extract the file, attempt to open). `[P]`
    - verified-here: ./gradlew --console=plain :app:connectedStandardDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.signsetu.DatabaseAtRestDeviceTest -> 1 test, 0 failures on CI34 API 34; extracted copy has no SQLite header and no plaintext, plain SQLite throws SQLiteException, SQLCipher with zero or random key throws SQLiteNotADatabaseException, and the real key still reads 1 session
- [x] A test that logs contain no transcript text, no names and no paths (grep the log buffer after a scripted session). `[P]`
  - verified-here: ./gradlew :app:connectedStandardDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.signsetu.LogPrivacyDeviceTest -> BUILD SUCCESSFUL, 1 test 0 failures; own-pid logcat after the marker has 0 hits for Zubair/mango tree/wants the and 0 path matches.
- [x] `docs/13-dpia.md` gains a change-log table: every new data field or permission adds a row with its purpose and retention. `[P]`
  - verified-here: python tools/security/check_permissions.py -> exit 0; python -m unittest discover -s tools/security -p "test_*.py" -> Ran 41 tests, OK; python tools/docs/check_docs.py -> exit 0.

### M2.4 Build integrity

- [ ] Release APKs: `SHA256SUMS`, an SBOM, and the dependency-verification metadata hash recorded next to the artefact. `[P]`
- [x] Reproducible build check: same commit, same SDK/NDK, two builds, compare with `apksigcopier`-style diff and document non-deterministic inputs. `[P]`
  - verified-here: /c/GITHUB/.fleet/lock.sh sign-setu 1800 /c/GITHUB/.fleet/lock.sh gradle 1800 /c/GITHUB/.fleet/slot.sh cpu 12 3600 py -3 tools/ci/check_reproducible.py --build -> exit 0 on ffa079a: two builds of git archive HEAD (404.7 s and 278.4 s, app/build and app/.cxx deleted between) each wrote app-standard-release-unsigned.apk sha256 8278f5c8b9b33f12eaa2c1fbdad63f090920ef013fb59ca4d4d0cfd68a91aef0 (189364903 bytes); apksigcopier-style diff: 1922 entries, order same, no differing entries, unsigned so all bytes compared, no signing block, nothing undetermined; the builds shared the Gradle configuration cache; tools/ci/reproducible.md lists the inputs that can drift.
- [x] NDK and CMake versions asserted in the build script; a mismatch fails with the expected value. `[P]`
  - verified-here: python tools/ci/check_toolchain.py -> exit 0, "NDK 30.0.16248370 and CMake 4.1.2: build script, CMake minimum, installed SDK and docs agree"; the same check over a copy with ndkVersion "29.0.1" -> exit 1, "ndkVersion is \"29.0.1\" but expected \"30.0.16248370\""; python -m unittest discover -s tools/ci -p "test_*.py" -> Ran 33 tests, OK; python tools/docs/check_docs.py -> exit 0.
- [x] `avatar/` bundle and `app/src/main/assets/avatar/avatar.js` identity check already exists in CI; extend it to `content/` and the GGUF allow-list. `[P]`
  - verified-here: python tools/content/check_content.py -> exit 0, "content references, ids, card images and 17-language labels are consistent; bundle identity and the GGUF allow-list are consistent"; python tools/content/build_content.py --check -> exit 0, "manifest is current: 1622 files"; python -m unittest discover -s tools/content -p "test_*.py" -> Ran 19 tests, OK; python tools/docs/check_docs.py -> exit 0.

### M2.5 Second screen and station paths

- [ ] Bluetooth second screen: a test for reconnect after a dropped bonded link, and for a rejected frame (wrong MAC, bad signature) being dropped silently with a log line. `[P]`
- [x] Station brain: a contract test between the app and `brain/` covering the request shapes, timeouts and the offline fallback. `[P]`
  - verified-here: ./gradlew --console=plain :app:testStandardDebugUnitTest --tests app.signsetu.BrainContractTest -> BUILD SUCCESSFUL, tests=4 failures=0, exit 0; BrainContractTest pins all 12 /v1 routes, request body keys, per-call timeout values, caps/Feature keys and the offline fallback against both app/src/main/java/app/signsetu/net/BrainClient.kt and brain/brain/app.py; iroh relay half unproven (no live relay: IrohBrain QUIC transport not exercised).
- [ ] Relay: prove the relay sees only ciphertext, with a recorded capture and the fields it can read. `[P]`

---

## Phase 3 — Perfective

### M3.1 Toolchain to stable

**Why:** the toolchain (AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00, Room 2.8.5,
SQLCipher 4.19.1) moves faster than the test evidence around it.
**Done when:** the instrumented suite runs before and after any upgrade.

- **m3.1.1 Upgrade**
  - [ ] AGP, Kotlin, Compose BOM, Room and SQLCipher to the current stable line; re-run the instrumented suite and record any behaviour change. `[F]`
- **m3.1.2 Build speed**
  - [ ] Gradle: configuration cache, build cache, and a measured CI time reduction. `[F]`

### M3.2 Code shape

**Why:** 48,700 lines across 257 files with no module boundary beyond `app`.
**Done when:** the ten biggest files are named and split.

- **m3.2.1 Splitting**
  - [ ] Name the ten largest Kotlin files with their line counts, then split by responsibility. `[F]`
- **m3.2.2 Dead weight**
  - [ ] Remove dead resources and strings; assert `lint --check UnusedResources` is clean. `[F]`

### M3.3 Measured performance

**Why:** "smoother faster flow" is in the owner's list and nothing measures it.
**Done when:** three numbers exist before and after.

- **m3.3.1 Benchmarks**
  - [ ] Baseline Profile + Macrobenchmark: cold start, first stage frame, sign-window open latency; publish before/after. `[F]`
- **m3.3.2 Startup cost**
  - [ ] Cache avatar assets and the stage scene so a restart does not reload them (owner: "cached data for avatar so it doesn't relod on app startups"). `[F]`

### M3.4 Docs and portal hygiene

**Why:** the portal has no CI gate and the docs have no index.
**Done when:** the portal cannot silently gain a write path.

- **m3.4.1 Portal**
  - [x] Portal (`portal/`): typecheck and unit tests in CI, plus a check that it never gains a write path to session data. `[F]`
  - verified-here: `python tools/ci/check_portal.py` -> exit 0 (the portal job runs `npm ci`, `npx vp check`, `npx vp test`, `npx vp build`; portal/src has no fetch, XMLHttpRequest, WebSocket, EventSource, sendBeacon, write-method, IndexedDB or app session import; the only localStorage write is the `signsetu.reviews.v1` store in reviews.ts); `python -m unittest discover -s tools/ci -p 'test_*.py'` -> 26 tests OK; `npx vp check` -> pass, `npx vp test` -> 3 files / 10 tests pass, `npx vp build` -> exit 0.
- **m3.4.2 Docs**
  - [x] `docs/README.md` becomes an index with one line per doc and its audience (officer, developer, reviewer, department). `[F]`
  - verified-here: python tools/docs/check_docs.py -> exit 0; python -m unittest discover -s tools/docs -p "test_*.py" -v -> Ran 20 tests, OK; the same audience check over HEAD docs/README.md -> 19 of 19 documents have no valid audience (the old map has no audience column).
  - [x] `plan.txt` deleted: every line is either implemented above or recorded in Appendix A, and the raw wording is preserved in Appendix D.

---

## Phase 4 — Widening: the owner's list, second pass

Each line of the owner's list (Appendix D) appears here as the feature it asks for. None of these is
optional polish; they are the reason the app is hard to use today.

### M4.1 The consolidated stage (owner: "too crowded and feature heavy")

- [x] One stage layout with a single primary action, a bottom bar of tools (language, pictures, drawing, emoji, typing) and at most three visible paths. `[W]`
ticked-here: one primary action, a five-tool bar and exactly three tagged paths (answers/tools/exit); StageToolsDeviceTest 3 tests, exit 0, 36.5 s; emoji picker row 250, slide-to-open sheet row 258, LANGUAGE wiring row 249.
- [x] Replace scattered per-feature buttons with a bottom bar and side sheets that show the full toolset on one screen. `[W]`
ticked-here: the bar opens one sheet holding all five tools; language returns the picked code, pictures answer their card, drawing and typing run their tools, emoji opens the express board; StageToolSheetDeviceTest 5 tests exit 0, 72.9 s; the slide-to-open gesture on the call surface is row 258.
- [x] Preset emoji/picture chips that appear automatically when a related word is typed or spoken — water, food, family, home, house, toilet, medicine (owner: "EMOJI SHOULD BE PRESET WHEN related word appears"). `[W]`
ticked-here: six preset groups (water, food, family, home, toilet, medicine) match whole words in English, Devanagari and romanised spelling from the prompt and the camera's read words; chips sit under the caption and tap through answer(); RelatedChipsTest 9 tests and RelatedChipsDeviceTest 4 tests exit 0, 27.9 s; a dedicated emoji picker beyond these presets is not added.
- [x] One-tap "I need…" express row (water, food, toilet, pain, help, family) that is always reachable and never buried. `[W]`
ticked-here: NeedRow.kt pins six one-tap tiles (water, food, toilet, pain, help, family) above every state, including an open panel and the faded controls; each calls onNeed(id) and the officer still confirms the need. NeedRowDeviceTest 4 tests exit 0 in 27.9 s; family has no picture asset, so its tile is emoji plus label.
- [ ] A single, obvious way to end a session (owner: "easy the closing of session"), with the other exits demoted to one menu. `[W]`
- [ ] Question-suggestion strip: the next three likely questions for the selected role, one tap each. `[W]`

### M4.2 The video-call surface (owner: "better ui on video call")

- [ ] A call surface that looks like a call: avatar and person tiles, caption overlay, and controls that appear on tap and fade when idle. `[W]`
- [ ] A slide-to-open panel that reveals the full toolset without leaving the call (owner: "slide to open button that make it fully feel like a video call"). `[W]`
- [ ] Hold/wait control and a messages shortcut at the same level, not one hidden behind the other. `[W]`
- [ ] Caption rendering: one line at a time, large type, the current word emphasised correctly (fixes the highlighting bug at the source, not per-screen). `[W]`
- [ ] Responsive layout for phone, tablet and a landscape dock with the second screen. `[W]`

### M4.3 Signing quality (owner: "better hand signs and fingerspellings", "joint restrictions")

- [x] Add joint limits to the avatar rig so wrists and elbows stop bending beyond human range (owner: "wrist and elbow move non humanly"). `[W]`
    - verified-here: cd avatar && npx vp test run src/retarget.test.ts -> 20/20 tests pass; cd avatar && npx vp build -> exit 0
- [ ] Re-record or re-time the fingerspelling transitions; measure the per-letter transition time and the readability check with two signers. `[W]`
- [ ] Improve the sign blend at phrase boundaries (co-articulation) and record the before/after clips for review. `[W]`
- [ ] A signer review sheet: every ISL word with a still frame and a "reads correctly?" column, filled by a Deaf reviewer. `[W]`

### M4.4 Speed (owner: "smoother faster flow")

- [ ] Measure the current time from tap to first signed frame, per screen; publish the numbers and target the three worst. `[W]`
- [x] Pre-warm the stage and the sign readers so the first question does not wait on model load. `[W]`
    - verified-here: ./gradlew --console=plain :app:connectedStandardDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.signsetu.PrewarmDeviceTest -> 1 test, 0 failures on CI34 API 34; the second content load after warm-up stayed under 250 ms
- [ ] Remove blocking work from the main thread in the stage and sign-window paths; verify with a jank count before/after. `[W]`

### M4.5 Richer communication (owner: "more feature richness")

- [x] Drawing board improvements: shapes, undo, and a saved sketch attached to the session record. `[W]`
  verified-here: ./gradlew --console=plain :app:connectedStandardDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.signsetu.DrawingBoardDeviceTest -> 3 tests passed on ci34 (API 34), including a rectangle whose four sides reach the kept PNG with paper inside, and undo removing it; :app:testStandardDebugUnitTest --tests app.signsetu.DrawShapesTest -> 8 passed
- [x] Body-map precision: zoom regions (hand, foot, chest, abdomen) with the touched point recorded exactly. `[W]`
  verified-here: ./gradlew --console=plain :app:testStandardDebugUnitTest --tests app.signsetu.BodyZoomTest -> 7 passed; ./gradlew --console=plain :app:connectedStandardDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.signsetu.BodyMapZoomDeviceTest -> 1 passed on ci34 (API 34): a tap on the hand opens its close-up and the second tap keeps the point the finger chose
- [x] Emotion and pain scale on the stage (faces + 0–10), logged with the session. `[W]`
  verified-here: ./gradlew --console=plain :app:testStandardDebugUnitTest --tests app.signsetu.FeelingsTest -> 4 passed; ./gradlew --console=plain :app:connectedStandardDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.signsetu.FeelingsDeviceTest -> 1 passed on ci34 (API 34): a face sends at once and the numbers then add the pain, kept in the answer's cue
- [x] Family/contact card the person can point at to identify who to call. `[W]`
  verified-here: ./gradlew --console=plain :app:testStandardDebugUnitTest --tests app.signsetu.CallCueTest -> 2 passed; ./gradlew --console=plain :app:connectedStandardDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.signsetu.FamilyCallDeviceTest -> 1 passed on ci34 (API 34): six people each shown as a picture and a word, and a tap sends the person picked

---

## Phase 5 — Depth

### M5.1 Accessibility depth (the primary user cannot speak and may not read)

- [x] TalkBack pass over every screen: labels, order, and a description for the 3D stage and each drawn control. `[D]`
  verified-here: ./gradlew --console=plain :app:connectedStandardDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.signsetu.TalkBackPassTest -> 3 tests, 0 failures on ci34 (API 34): 8 stage states in en, hi and bn each give every pressable control words, the figure announces itself as Signing character, the question is read before its answers, and every picture-board tile carries its word
- [x] Contrast ≥ 4.5:1 in all ten colour themes and both dark modes; measure per theme and publish a table. `[D]`
  verified-here: ./gradlew --console=plain :app:testStandardDebugUnitTest --tests app.signsetu.ContrastTest -> 3 tests, 0 failures; docs/26-theme-contrast.md holds the table for 10 themes x light, dark and OLED, floors 4.5:1 for text and 3:1 for outlines; its row is in the docs index
- [ ] Touch targets ≥ 48 dp, and one-handed reach for every in-session action. `[D]`
- [ ] Switch-access and keyboard-only path through the person's answer flow (tap, picture, nod, finger count). `[D]`
- [ ] Reading level: captions tested with two people who read the target language as a second language; record the words that caused confusion and change them. `[D]`
- [x] Reduced motion honoured: the avatar and stage animations have a low-motion mode that still signs legibly. `[D]`
  verified-here: ./gradlew --console=plain :app:testStandardDebugUnitTest --tests app.signsetu.MotionTest -> 4 tests, 0 failures

### M5.2 Interface craft

Method: `design-ux` §1/§5 — screenshot every screen at 360/412/768 dp, measure
geometry, draft three candidate fixes per problem, score, implement, re-shoot.

- [ ] Baseline screenshots of all screens (the 12 in `docs/12-screens.md`) into `docs/screens/before/`. `[D]`
- [ ] One token set for spacing, type, radius and colour across the ten themes; no raw values in composables. `[D]`
- [x] Screen inventory: every screen with its purpose, its three primary actions and its exit; anything without a purpose is removed. `[D]`
  verified-here: python tools/ux/check_inventory.py -> 8 screens: purpose, three actions and exit present; python -m unittest discover -s tools/ux -> 14 tests, OK
- [x] Person-facing screens use larger type, fewer words and one action; officer-facing screens may be denser — the split is explicit. `[D]`
  verified-here: python tools/ux/check_split.py -> 10 surfaces split; type, touch and card floors hold (and :app:compileStandardDebugKotlin passes)
- [x] Error and empty states: no model, no camera, no microphone, no second screen, storage full — each with the fix, not an error code. `[D]`
  verified-here: python tools/ux/check_error_copies.py -> 5 broken states, each with a fix and no error code
- [ ] Onboarding: the three first-run steps and the tour, measured for completion time with one officer who has not seen the app. `[D]`

### M5.3 Sign-language depth

- [x] Per-word accuracy table for ISL and ASL with n per word; flag every word under a usable threshold and either improve it or mark it "hold to confirm". `[D]`
  - verified-here: `SIGNSETU_DATA=C:/ml-data python tools/ml/per_word.py --write` -> 816 INCLUDE test clips top-1 0.979 with 3 words held; 269 ISLRTC new-signer clips 0.546 with 34 held; 2504 ASL Citizen clips 0.702 with 52 held; wrote docs/models/per_word.md, one row per word with n, top-1, top-3 and the first wrong read; threshold 0.60 over n >= 2
- [ ] The "unclear" path: when two readers disagree, ask the person once with pictures; measure how often that resolves the meaning. `[D]`
- [ ] Fingerspelling: hold-to-commit timing studied with two signers and tuned; publish the chosen value and the reason. `[D]`
- [ ] Regional variation: record which signs differ by region and let a department pick a variant. `[D]`
- [x] Deaf-reviewer loop: a documented review sheet and a stated cadence for adding words. `[D]`
  - verified-here: docs/review/word_review.md holds the sheet (11 fixed columns: id, gloss, language, source, signers, draft_since, reviewer, date, verdict, second, notes), the loop and the cadence (a batch of at most 40 words a fortnight, a review before any release promotes a draft, a second reviewer for a hold or dispute, a reported misread ahead of new words), linked from tools/isl/README.md; python tools/docs/check_version.py -> exit 0; python -m unittest discover -s tools/docs -p 'test_*.py' -> Ran 29 tests, OK; python tools/docs/check_docs.py -> exit 0 on the extracted tree with the sibling's docs/25-screen-inventory.md index row added (at HEAD that check names only that sibling doc). No reviewer is named yet and no verdict is recorded; the page says so.

### M5.4 Evidence and operations depth

- [ ] A full session drill: start → consent → record → sign → answer → finish → export → verify on a second device; timed end to end with a real officer. `[D]`
- [ ] Battery and thermal: a 30-minute session on a mid-range phone, recording and signing, with battery drop and thermal throttling measured. `[D]`
- [x] Storage: measure per-session size (audio, frames, records) and assert the retention job keeps the device inside a documented budget. `[D]`
  - verified-here: ./gradlew --console=plain :app:testStandardDebugUnitTest --tests app.signsetu.StorageBudgetTest -> BUILD SUCCESSFUL; a documented worst-case session (3 min narrative, 60 s recording, one drawing, 400 rows) measures 3118948 B, a typical session 514043 B, and every retention window offered at 2 sessions an hour stays inside the 1 GB budget (72 h keeps 144 worst-case sessions = 449128512 B); python tools/docs/check_docs.py -> exit 0. docs/23-storage-budget.md records the measurements and what the bound does not cover.
- [x] Failure rehearsal: phone dies mid-session; second screen drops; model file corrupt; report what survives and what the officer should do. `[D]`
- verified-here: python tools/security/check_permissions.py -> exit 0; python tools/docs/check_docs.py -> exit 0; ./gradlew --console=plain :app:connectedStandardDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.signsetu.SessionSurvivesRestartDeviceTest -> BUILD SUCCESSFUL on emulator API 34, 1 test OK; the export and content paths are covered by ExportFailureDeviceTest and ContentFallbackTest, not rerun here

---

## Phase 6 — Enterprise readiness

### M6.1 The document a department signs

**Why:** the DPIA is the artefact that unlocks deployment, and it has no owner or
review date.
**Done when:** the DPIA matches the data inventory and is signed.

- **m6.1.1 DPIA**
  - [x] DPIA refresh (`docs/13-dpia.md`) aligned with the current data inventory, with a named owner and a review date. `[E]`
  - verified-here: python tools/security/check_permissions.py -> exit 0; python tools/docs/check_docs.py -> exit 0; the export cache row is in sections 1.2 and 7 with its real 15-minute rule
- **m6.1.2 Attestation truth**
  - [x] Attestation and pinning (`docs/14-attestation-and-pinning.md`): verify the described controls exist in the code, and mark any that are aspirational. `[E]`
  - verified-here: python tools/docs/check_attestation.py -> exit 0; python -m unittest discover -s tools/docs -p 'test_*.py' -> all pass; python tools/docs/check_docs.py -> exit 0

### M6.2 Deployment package and training

**Why:** a department needs a verifiable bundle, not a repository.
**Done when:** the bundle is reproducible and an officer can be trained from it.

- **m6.2.1 Package**
  - [x] Signed APK, SBOM, `SHA256SUMS`, the DPIA, the walkthrough, the model allow-list procedure and the verification instructions in one place. `[E]`
    - verified-here: python tools/docs/check_docs.py -> exit 0 on HEAD plus this unit (repo tree red only from docs/25-screen-inventory.md, a sibling commit with no index row); check_version.py -> exit 0; check_release.py -> exit 0; docs/24-deployment-package.md names all seven artefacts and every path in it exists
- **m6.2.2 Training**
  - [ ] The officer walkthrough with screenshots, an accessibility note, and the three mistakes officers actually make (measured from the drill). `[E]`

### M6.3 Support, release and incident

**Why:** an officer with a bad signing or a failed export has no path today.
**Done when:** each incident class has a first action and a person.

- **m6.3.1 Support and release policy**
  - [x] How an officer reports a bad signing or a failed export, what to attach, and the response window. `[E]`
    - verified-here: python tools/docs/check_docs.py -> exit 0; python tools/docs/check_version.py -> exit 0; grep -n -e '## 1. A bad signing' -e '## 2. A failed export' -e '## 3. What to attach' -e '## 5. Response window' docs/21-support.md -> 5, 13, 23, 44; grep -n -P '[^\x00-\x7F]' docs/21-support.md -> no output
  - [x] Release policy: version discipline, a changelog per release, a supported-device matrix, and an upgrade path that keeps sessions readable. `[E]`
    - verified-here: python tools/docs/check_release.py -> exit 0 (the newest CHANGELOG.md section is 0.8.52, the build version, and the matrix covers API 29 to 37); python tools/docs/check_version.py -> exit 0; python tools/docs/check_docs.py -> exit 0; python -m unittest discover -s tools/docs -p 'test_*.py' -> Ran 26 tests, OK.
- **m6.3.2 Vendors and incidents**
  - [x] Vendor and dependency review: SQLCipher, MediaPipe, llama.cpp/GGUF, iroh — licences, update cadence and the security advisory subscription for each. `[E]`
    - verified-here: python tools/docs/check_docs.py -> exit 0; python tools/docs/check_version.py -> exit 0; grep -n -e 'SQLCipher for Android 4.19.1' -e 'MediaPipe Tasks Vision 1.1.1' -e 'llama.cpp v0.5.0' -e 'iroh 1.2.0' docs/20-vendor-review.md -> rows 9-12 of section 1; grep -n 'Advisory subscription' docs/20-vendor-review.md -> 28:## 3. Advisory subscription
  - [x] Incident plan: a compromised device key, a leaked report password (historical), a bad model file, a signing regression — each with the first action and who is told. `[E]`
    - verified-here: `python tools/docs/check_docs.py` -> exit 0, no output; `python tools/docs/check_version.py` -> exit 0, no output; `grep -n -i Incident docs/04-security.md` -> 243:## 9. Incidents; 247:| Incident | First action, first 15 minutes | Who is told | Why that is the first move |; 260:When a leaked file, ...

---

## 7. Execution model: waves, effort, exit gates

Effort: **S** ≤ 1 day, **M** 2–3 days, **L** 1–2 weeks.

| Wave | Days | Objective | Majors | Effort | Gate |
|---|---|---|---|---|---|
| **W0** | 2–3 | Documentation drift, instrumented tests, high-severity wishes, evidence-chain gaps | M1.1–M1.4 | M+M+M+M | README version matches `build.gradle.kts`; the `instrumented` job runs on PRs touching `app/`, not only on a version bump; every evidence export verifies with `tools/security/verify_seal.py` |
| **W1** | 4–5 | Gates: integrity, sign accuracy, privacy tests, build integrity, second screen | M2.1–M2.5 | M+M+M+M+M | a corrupted export fails CI; reader accuracy has a measured floor; retention is tested by advancing the clock |
| **W2** | 6–7 | Stable toolchain, code shape, measured performance | M3.1–M3.4 | M+M+M+M | cold start and sign-window latency published before/after |
| **W3** | 8–11 | Owner's second pass: stage, video call, signing quality, speed, features | M4.1–M4.5 | L×5 | each wish is either implemented with a test or recorded in Appendix A |
| **W4** | 12–14 | Accessibility, interface craft, signing depth, operations | M5.1–M5.4 | M+M+M+M | a Deaf user completes a full session in a usability pass |
| **W5** | quarter | DPIA, attestation truth, deployment package, support, incident plan | M6.1–M6.3 | M | the package is reproducible and the DPIA is signed |

Wave rules: W0's instrumented-test item is the cheapest way to find out what the
current suite actually does on a device. Nothing in W3 ships without W1's privacy
tests — this app records third parties under consent.

---

## 8. Dependencies and the critical path

```
M1.1 (doc truth)      → every later claim (version, retention, readers)
M1.2 (instrumented)   → M3.1 (toolchain upgrade) → M4.x
M1.4 (evidence gaps)  → M2.1 (integrity gate)    → M6.2 (package)
M2.3 (privacy tests)  → M4.x (any new recording surface)
M3.3 (performance)    → M4.4 (speed wishes)
M5.1 (accessibility)  → M6.2 (training material)
```

Critical path: **M1.2 → M2.1 → M4.x**. Everything the owner asked for in the list
touches recording, staging or export, and those are exactly the paths that have no
executed test today.

Do not start: M3.1 before M1.2 (upgrading an unexecuted suite proves nothing); M4.x
before M2.3 (new recording surfaces without privacy tests); M6.2 before M2.1 (a
deployment package around an unverified seal).

---

## 9. Verification harness (commands that exist today)

| Purpose | Command | Status |
|---|---|---|
| Android unit + lint | `./gradlew :app:testStandardDebugUnitTest :app:testNearbyDebugUnitTest :app:lintStandardRelease` | exists (`ci.yml:42-45`) |
| Instrumented run | `instrumented` job: 4 shards × 8 named classes, API 34 emulator, only on a version bump or manual dispatch | exists (`ci.yml:199-243`); **not on PRs** |
| Avatar (JS) | `npm ci && npx vp check && npx vp test && npx vp build` | exists (`ci.yml:66-70`) |
| Portal | `npm ci && npx vp check && npx vp test && npx vp build` with a git-diff check on generated assets | exists (`ci.yml:87-91`) |
| Content | `python tools/content/build_content.py --check` | exists (`ci.yml:105`) |
| Gateway (Go) | `vex`, `fuzz`, `staticcheck` in the gateway job | exists |
| Evidence verify | `python tools/security/verify_seal.py` | exists |
| Secret scan | `.gitleaks.toml` + gitleaks | exists |
| Dependency integrity | `verification-metadata.xml`, `app/gradle.lockfile`, `settings-gradle.lockfile` | exists |
| **Instrumented execution** | `instrumented` job (4 shards × 8 named classes, API 34 emulator), gated on a version bump or manual dispatch | exists (`ci.yml:199-243`); a normal PR runs none (M1.2) |
| **Reader accuracy gate** | — | **missing** (M2.2) |
| **Retention clock test** | — | **missing** (M2.3) |

---

## 10. Risk register

| # | Risk | Prob | Impact | Detection | Mitigation |
|---|---|---|---|---|---|
| R1 | README says 0.8.5/22, build says 0.8.51/23 | Certain | Wrong support answers | `build.gradle.kts:36-37` vs README | M1.1 |
| R2 | Instrumented tests do not run on PRs (8 classes, version-bump gated) | Certain | Device-only failures land on `master` | `ci.yml:202` gate | M1.2 |
| R3 | An exported record fails verification later | Med | Evidential loss | `verify_seal.py` exists, not gated | M1.4/M2.1 |
| R4 | Sign-reader accuracy regresses silently | High | Wrong transcription | no floor | M2.2 |
| R5 | Retention job misbehaves | Med | Data kept too long (legal) | no clock test | M2.3 |
| R6 | **`standard` flavour gains a location/scanning permission** | Med | Privacy claim false | manifest check exists | keep the build-failure gate |
| R7 | 48,700 lines in one module with no seams | High | Slow, risky change | `wc -l` | M3.2 |
| R8 | Toolchain drift outruns the test evidence | High | Silent behaviour change | version file | M3.1 after M1.2 |
| R9 | Accessibility regressions (the primary users) | Med | Exclusion | no automated check | M5.1 |
| R10 | DPIA out of date with the data inventory | High | Department cannot sign | doc only | M6.1 |

---

## 11. PR batching and rollback

| Batch | Contents | Rollback |
|---|---|---|
| B1 | README + docs version/retention corrections | revert docs |
| B2 | connected-test CI job + one device-labelled test | revert workflow step |
| B3 | verify_seal gate in CI + fixture | revert gate |
| B4 | reader-accuracy floor + corpus | revert threshold |
| B5 | retention clock test | revert test |
| B6 | toolchain upgrade | revert version catalog + wrapper |

Rollback rules: Room/SQLCipher migrations are forward-only and a session must stay
readable after a revert (that is the upgrade-path requirement); a model file is
versioned, never overwritten.

---

## 12. Progress metrics

| Metric | Now (measured) | W1 | W3 | How measured |
|---|---|---|---|---|
| Kotlin files / lines | 257 / 48,700 | same | split | `find`, `wc -l` |
| `@Test` | 388 in 59 files | + gate tests | + feature tests | `grep -rho @Test` |
| Instrumented tests executed | 10 of 13 classes per PR that touches `app/` (3 device-only, measured) | same | same | CI log |
| CI jobs | 12 | 12 | 12 | parsed `jobs:` |
| Reader accuracy floor | none | set | enforced | eval output |
| Version consistency | README wrong | fixed | fixed | script |
| Cold start (ms) | unmeasured | measured | improved | Macrobenchmark |

---

## Appendix A — Owner decisions outstanding

From the owner's list (Appendix D) where the request needs a decision rather than code:

- "don't need export report, we have download button" conflicts with the records/seal flow; decide whether Download and Export are one action.
- Removing the PDF password changes the threat model (anyone with the file can read it). The seal still proves integrity; confirm that is acceptable for department use.
- Number of colour themes (currently ten) versus a smaller, tested set.
- Whether the AI counsellor stays available for witnesses only, or is removed entirely for evidential safety.
- Whether the second screen becomes the default in a station setup.

Blocked on hardware or people:

- Two-phone Bluetooth drill on real devices.
- Deaf signer review for the ISL/ASL sheets.
- A real officer's end-to-end session for timing and wording.
- An Android 12+ pairing run for the second screen.

## Appendix B — Definition of done

1. The failing test exists before the fix: a unit test, an instrumented test or an evaluation-set regression.
2. `./gradlew testStandardDebugUnitTest testNearbyDebugUnitTest lintStandardRelease`, the instrumented job, `avatar`/`portal` tests, `go test ./...` and the content checks all pass; output pasted.
3. No invariant in §0 broken: offline by default, consent before recording, encrypted sessions with enforced retention, ledger integrity, aid-not-interpreter, a reading is never an answer, no permission without a DPIA line.
4. Any change to the evidence path is verified end to end with `verify_seal.py` and in-app "Check a report".
5. Docs updated in the same PR; the README version comes from the build file, never from memory.

## Appendix C — Evidence ledger

| Claim | Command | Result | Status |
|---|---|---|---|
| `versionCode 23`, `versionName 0.8.51` | `grep -nE 'versionCode\|versionName' app/build.gradle.kts` | 23 / "0.8.51" | verified-here |
| `minSdk 29`, `targetSdk 37`, `compileSdk 37` | same | 29 / 37 / 37 | verified-here |
| 257 Kotlin files, 48,700 lines, all under `app/` | `find`, `cat \| wc -l` | 257 / 48,700 | verified-here |
| 388 `@Test` in 59 files | `grep -rho @Test`, `grep -rl @Test` | 388 / 59 | verified-here |
| Instrumented is gated, not absent | `sed -n '199,243p' .github/workflows/ci.yml` | `if: …version.outputs.new == 'true' \|\| workflow_dispatch`; 8 classes; 4 shards; API 34 `pixel_6` | verified-here |
| Java 21 in CI | `ci.yml:38` | temurin 21 | verified-here |
| 12 CI jobs | parse `jobs:` in `.github/workflows/ci.yml` | version, android, avatar, portal, tools, gateway, station, semgrep, osv, instrumented, gitleaks, release | verified-here |
| `plan.txt` 15 lines (14 non-empty) | `wc -l plan.txt` | 15 / 14 | verified-here; file deleted, content in Appendix D |
| "174 files / 38,300 lines", "49 test files" | earlier counts | **wrong** | corrected |
| "Sign readers: ISL 314, ASL 250" | `README.md`, `tools/ml/EVAL.md` | doc claims | needs-verify |
| Evidence chain end-to-end | `tools/security/verify_seal.py` not run here | needs-verify | needs-verify |

## Appendix D — The owner's original list, verbatim

`plan.txt` (14 non-empty lines, deleted) is reproduced here so no wording is lost.
Every line maps to a phase above; the ones that need a decision are in Appendix A.

```
dont-neeed-export-report, we have download button.txt
need joint restirciton of movement as upon animation wrist and elbow move non humanly
need better ui
no timed hold when going to msgs
mssage caption highlighting is bad during video clal look into it
smoother faster flow
cached data for avatar so it doesn't relod on app startups
better hand signs and fnegrsplelings
better overall ui to help people choose what they wanna say . right now it is very croded and feature heavy that can be consolidated into
EMOJI SHOULD BE PRESET WHEN related owrd appears like water food family home house etc
better ui on video call needed with slide to open button that make it fully feel like a video call and button appear when needed , -- capiton and vaatr and user on video call with options to hold(wait) and go to msgs , all other need to have a button and those buttons shall be like modern sidebars , buttom bars tat show full functionality on the screen with language picker meoji picker drwing etc etc what soever available
this is for the people who cant speak and we need more feature richness, more repsonsicve design and better ways
remove password form exports pdfs
also easy the xclosing of session and don't provided multiple divergent paths that confuse , atmax 3 is fine no more than tht divergent paths on a songle screen
and more feature rich and ocncernign to mute guy awith usability porer not deep hidden to the policeman
```

---

## Journal

- 2026-11-24 m1.1.1 [x] README version line generated from `app/build.gradle.kts` | proof: `python tools/docs/check_version.py` → exit 1 before, exit 0 after `--write`; CI `tools` job runs it | next: m1.1.2
- 2026-11-24 m1.1.2 [x] docs diffed against the tree, two dead paths fixed | proof: `python tools/docs/check_docs.py` → exit 1 (3 stale paths), exit 0 after; 12 tool tests pass | next: m1.1.3
- 2026-11-24 m1.1.3 [x] permission table generated from both source manifests, prose row completed | proof: `python tools/security/check_permissions.py` → exit 1 before the table existed, exit 0 after `--write`; 10 tests pass | next: m1.1.4
- 2026-11-24 m1.1.4 [x] device baseline generated from the build and the memory floors; 2.4 GB/1.2 GB replace the unmeasured "3 GB" | proof: `python tools/docs/baseline.py` → exit 1 before, 0 after `--write`; 18 tool tests pass | needs-verify: assistant model byte size | next: m1.2.1
- 2026-11-24 m1.2.1 [x] emulator job widened to 10 classes and gated on an `app-changed` filter | proof: `python tools/ci/check_workflows.py` → exit 1 before, 0 after; 7 CI tests pass | deviation: job-level diff filter instead of `paths:`; 3 classes measured device-only | next: m1.2.2
- 2026-11-24 m1.2.2 [x] the exported PDF is verified off the phone by verify_seal.py | proof: `python tools/security/verify_seal.py --pdf …/report.pdf --passphrase …` → exit 0 valid, tampered seal → exit 1; VerifierFixtureDeviceTest passes on the emulator (97 s) | next: m1.2.3
- 2026-11-24 m1.2.3 [x] retention sweep tested on a device with an injected clock, boundary included | proof: `:app:connectedStandardDebugAndroidTest -P…class=app.signsetu.RetentionClockDeviceTest` → 4 tests, exit 0 | next: m1.2.4
- 2026-11-24 m1.2.4 [x] Keystore and locked-record behaviour tested on a device | proof: `:app:connectedStandardDebugAndroidTest -P…class=app.signsetu.LockedKeystoreDeviceTest` → 5 tests, 4 pass 1 skips, exit 0 | needs-verify: the locked-device refusal needs a phone with a screen lock | next: m1.2.5
- 2026-11-24 m1.2.5 [x] a refused microphone records nothing and says so in one line | proof: `VoicePermissionDeviceTest` with the permission revoked on the emulator → 1 test, exit 0 | next: m1.2.6
- 2026-11-24 m1.3.1 [x] the export is one PDF with no password; the seal still catches an edit | proof: `ReportExportDeviceTest`, `ForensicExportDeviceTest`, `VerifierFixtureDeviceTest` exit 0 on the emulator, `verify_seal.py --pdf` exit 0 valid / exit 1 on a changed signature | next: m1.3.2
- 2026-11-24 m1.3.2 [x] one tap on the stage's chat handle opens the officer side, no timed hold | proof: `StageHandleTapDeviceTest` → 1 test, exit 0, 23.8 s on the emulator | next: m1.3.3
- 2026-11-24 m1.3.3 [x] the lit word travels with its own line, so the call surface cannot light the wrong words | proof: `CallCaptionLightTest` → 3 tests, exit 0, 12.3 s | next: m1.3.4
- 2026-11-24 m1.3.4 [x] the mute for the person's speech is a header button on the officer's surface, and it silences at once | proof: `OfficerMuteButtonDeviceTest` renders the officer pane with no menu open → 1 test, exit 0, 24.4 s | next: m1.3.5
- 2026-11-24 m1.3.5 [x] three ways out and no more: measured per state (3 in each), the close dialog's second route to the same PDF removed | proof: `WrapUpPathsDeviceTest` 4 tests exit 0, 32.9 s; `tools/ux/count_paths.py` widest 4 (branches only) | next: m1.4.1
- 2026-11-24 m1.4.1 [x] the phone and the off-phone checker read one report identically: all six seal fields and the verdict diffed over the same bytes, and a flipped signature makes them disagree | proof: `SealAgreementDeviceTest` exit 0 (17.0 s); `seal_agreement.py --pdf` exit 0, tampered copy exit 1 `DISAGREE` | next: m1.4.2
- 2026-11-24 m1.4.2 [x] an export lands whole or not at all: part file, no short writes, parts purged, one clear message | proof: `WriteReportTest` 4 tests exit 0; `ExportFailureDeviceTest` 3 tests exit 0 (18.7 s) | next: m1.4.3
- 2026-11-24 m1.4.3 [x] the certificate names the software as well as the phone, and a fresh export proves it | proof: `EvidenceIntegrityTest` + `ReportExportDeviceTest` exit 0 (37.5 s) asserting build, key fingerprint and ECDSA P-256 on the certificate page | next: m1.4.4
- 2026-11-24 m1.4.4 [x] stated plainly what the seal proves (those bytes, that key, this phone) and what it does not (who held the phone, that the record is true, when, that the device was clean) | proof: `docs/04-security.md` §2.10, code-checked against `EventSeal.sealLine`; found and recorded F-10 | next: M1.5 or the next open item in §7
- 2026-11-24 m2.1.2 [x] fuzzed the seal reader (payloads and PDFs) and the station's record reader; three real escapes found and closed, the run is in CI | proof: `fuzz_parsers.py` clean at 300/seed 1 and 400/seed 7; 13 unit tests OK | next: m2.1.1 coverage floor
- 2026-11-24 m2.1.3 [x] retention has one rule in `Retention` and property tests over every start time and window; the real SQL sweep is checked against it | proof: `RetentionPropertyTest` exit 0; `RetentionClockDeviceTest` 5 tests exit 0 (1.07 s) | next: m2.1.4 lint baseline
