# 20. Vendor and dependency review

Four components carry a licence, an update path and an advisory source of their own: SQLCipher, MediaPipe with LiteRT and protobuf, the vendored llama.cpp, and iroh with its relay. Everything else in the build is a version-catalog entry that follows the same release process. This page records the pin, the licence, the way a new version arrives and the place to watch for each of the four.

## 1. The four

| Component | Pinned here | Licence | How it updates | Advisory source |
| --- | --- | --- | --- | --- |
| SQLCipher for Android 4.19.1 | `gradle/libs.versions.toml` line 12 (`sqlcipher = "4.19.1"`); the coordinate `net.zetetic:sqlcipher-android` at line 52; used at `app/build.gradle.kts` line 197 | BSD-style (`docs/08-open-source.md` line 72) | A version-catalog bump, then the lockfile and checksum change that go with it | github.com/sqlcipher/sqlcipher-android (named at `docs/08-open-source.md` line 112) and osv.dev |
| MediaPipe Tasks Vision 1.1.1, with LiteRT 1.4.2 and protobuf-javalite 4.36.2 | `gradle/libs.versions.toml` lines 28, 29 and 30; the coordinates at lines 73, 74 and 75 | MediaPipe and LiteRT Apache-2.0 (`docs/08-open-source.md` lines 46 and 47); protobuf-javalite BSD-3-Clause (line 48) | A catalog bump; `SignPipelineDeviceTest` must pass after any MediaPipe or protobuf update (`README.md` line 56) | github.com/google-ai-edge/mediapipe (`docs/08-open-source.md` line 112) and osv.dev |
| llama.cpp v0.5.0, the GGUF runtime | Vendored at commit 7fe450e: `app/src/main/cpp/CMakeLists.txt` lines 7-11, and `app/src/main/cpp/third_party/PROVENANCE.md` lines 7-12 | MIT, copyright The ggml authors (`app/src/main/cpp/third_party/PROVENANCE.md` line 12) | Replace the included paths, then record the new tag, commit and archive SHA-256 (`app/src/main/cpp/third_party/PROVENANCE.md` lines 35-38) | github.com/ggml-org/llama.cpp (`app/src/main/cpp/third_party/PROVENANCE.md` line 7) and osv.dev |
| iroh 1.2.0 | `gradle/libs.versions.toml` lines 21 and 62 (`computer.iroh:iroh-android`); `brain/requirements.txt` line 17 (`iroh==1.2.0`); relay release v1.2.0 pinned by SHA-256 at `relay/setup.sh` lines 13-14 | MIT or Apache-2.0 (`docs/08-open-source.md` lines 39-40) | A catalog and requirements bump, plus a new relay release and its hash | github.com/n0-computer/iroh releases (`relay/setup.sh` line 43) and osv.dev |

Every version above is the pin the build uses today, and the code wins over this page.

JNA 5.19.1 rides with iroh: `gradle/libs.versions.toml` lines 22 and 63, Apache-2.0 or LGPL-2.1 per `docs/08-open-source.md` line 42. It moves when iroh moves.

The assistant model is a separate file rather than a dependency: Gemma 3 1B instruction-tuned in GGUF, 806 MB (`docs/README.md` line 18), under the Gemma Terms of Use, which are not OSI open source (`docs/08-open-source.md` line 91, and the limitations row at `docs/README.md` line 290). It is accepted only from an allow-list after a hash check (`docs/04-security.md` section 2.6, line 78), so its version is whatever the allow-list names and the hash decides whether a file is that version.

## 2. Update cadence

The repository pins every one of them: the version catalog, `app/gradle.lockfile`, `gradle/verification-metadata.xml`, `brain/requirements.txt`, the vendored commit and the relay hash. A bump is one change that moves the pin and its lockfile, checksum or hash together; a version without its hash is not a bump. The llama.cpp procedure is `app/src/main/cpp/third_party/PROVENANCE.md` lines 35-38, and the relay hash is the sha256 at `relay/setup.sh` line 14.

Those are the pinning controls in `docs/04-security.md` section 2.6 line 74: dependencies pinned, locked in `gradle.lockfile` and checksum-verified in `gradle/verification-metadata.xml`, BouncyCastle forced to 1.86, llama.cpp vendored at a fixed commit. A pin can also be held deliberately: BouncyCastle stays at 1.86 because 1.72 has known critical vulnerabilities (`docs/08-open-source.md` lines 73-74).

Review rule: each vendor is reviewed at every minor release of the app, and within 7 days of an advisory that names the pinned version. Nothing moves between reviews except by such a reviewed change.

## 3. Advisory subscription

The one automated control is the osv job in `.github/workflows/ci.yml`: the job at line 201, osv-scanner v2.0.2 at line 207. It derives `shipped/gradle.lockfile` from the release runtime lines at line 211 and runs the scan at line 212. It covers the app's shipped Maven and Gradle runtime dependencies only.

| Vendor | Covered by the osv job |
| --- | --- |
| SQLCipher, MediaPipe, LiteRT, protobuf-javalite, iroh, JNA | Yes: each is on the shipped runtime classpath |
| `brain/requirements.txt` | No |
| The vendored llama.cpp commit | No |
| The relay release binary | No |

The manual control is watching each upstream repository's security advisories, the four repositories named in section 1, plus osv.dev. It is held by a named role in the department's IT security cell [confirm]: the role, not a person's name.

On a hit, the answer is a reviewed pin bump, or a documented decision recorded in the findings table of `docs/04-security.md` section 6, "Findings and accepted risks", whose columns are ID | Finding | Severity | Decision, if the pin is held.

The gap is stated plainly: no automated advisory feed covers the Python brain requirements, the vendored commit or the relay binary today.

## 4. What this review does not cover

The Gemma model licence and the ASL and ISL data terms are in [08-open-source.md](08-open-source.md), not here. The department's own subscription holder is unconfirmed [confirm].
