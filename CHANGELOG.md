# Changelog

All notable changes to Sign Setu are recorded here, one section per released version, newest first. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow the release policy in [RELEASE.md](RELEASE.md). The newest section is the version in `app/build.gradle.kts`; a version bump and its section are one change.

## [0.8.52] - 2026-11-26

### Changed

- An export is one PDF with no password; the seal and the hash chain carry the evidence, and `tools/security/verify_seal.py` reads it without a passphrase.
- The draft s.63 certificate names the producing build beside the device, the key fingerprint and the signing algorithm.
- The seal's documentation states what it proves (device possession) and what it does not (authorship), checked against the seal line.
- The content identity gate now covers symbols, the manifest and the GGUF hashes.
- The portal is gated in CI: typecheck and tests, and no write path to session data.
- NDK and CMake are pinned to one version, and the toolchain check enforces it.
- Content language coverage is checked for every caption language in the bundle.

### Fixed

- Retention is one rule with property tests over every start time and window, and the encrypted-database sweep is checked against the same rule over 24 sessions.
- Reading the seal and the station's record reader no longer crashes on malformed input; CI throws 200 malformed inputs at both on every push.
- An export lands whole or not at all: a part file is renamed only when every byte is written, and the failure names a full phone.
- The app's "Check a report" and `verify_seal.py` agree field by field over one exported PDF, and a changed signature makes them disagree.
- A refused microphone records nothing and says so in one line; the locked database refuses another key instead of crashing.
- The caption's lit word carries the text it was measured in, so a caption no longer lights the wrong prompt or hint.
- A single tap on the stage's chat handle reaches the officer; the 333 ms hold is removed.
- Lint findings and unapproved permissions fail the build instead of accumulating.

### Internal

- The session suite gained device tests for retention against an injected clock, export verification in CI, the migration list and the officer's paths out of a session.
- The emulator job runs ten instrumented classes on any app-touching pull request, and the device baseline is generated from the build file and the memory floors.
- The permission table is generated from both manifests, and the documentation is checked against the tree with the version coming from the build file.

## [0.8.5] - 2026-11-04

- Release 0.8.5: hand and body tracking, retention, exports and the officer's session controls.

## [0.8.4] - 2026-11-02

- Version 0.8.4.

## [0.8.3] - 2026-10-30

- Release 0.8.3: the ASL co-trained sign reader and a pink-top avatar.

## [0.8.0] - 2026-10-27

- Release 0.8.0.

## [0.7.0] - 2026-10-25

- Assist on Groq at low reasoning effort; version 0.7.0.

## [0.6.0] - 2026-10-24

- Release 0.6.0: Type-to-Sign practice, elbow IK and gateway hardening fixes.

## [0.5.0] - 2026-10-21

- The counsellor runs on a paired second screen, with officer monitoring.

## [0.4.2] - 2026-10-19

- Release 0.4.2: the optional online assistant and sign stabilisation.

## [0.4.0] - 2026-10-18

- Sandes handoff, the downgrade guard, the ASL gate and tools; version 0.4.0.

## [0.3.0] - 2026-10-18

- Release 0.3.0 with the screen catalogue.