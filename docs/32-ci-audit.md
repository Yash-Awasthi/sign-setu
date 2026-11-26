# 32. CI audit

Scope: the one workflow, `.github/workflows/ci.yml`, checked against the tree at commit 402339f.
The question is narrow: does every command it runs exist here, does it fail when the thing it
guards is wrong, and can it run on a hosted runner with no device and no secret.

Method. The file is parsed with `yaml.safe_load`; every job, step and `run:` block is walked;
every path-shaped token a run block names is required to exist; every checker under `tools/` is
matched to a step, and every step command is matched back to a file. The shell loop and the
emulator script are parsed with `bash -n`, and the Python gates were run on the audit machine.

## 1. Jobs, steps and verdicts

`on:` push to master, every pull request, manual. Global `permissions: contents: read`;
`concurrency: ci-${{ github.ref }}`, cancelling a superseded run everywhere except the main
repository, where a release must not be cancelled.

| Job | Steps (command) | Needs / permissions | Verdict |
| --- | --- | --- | --- |
| version | checkout v5; read `versionName` with GNU grep; `git ls-remote --tags origin refs/tags/v$name` to set `new` | — | Sound. Fails on a build file whose `versionName` line is gone; the network call only decides whether a tag already exists. 5-minute timeout. |
| android | checkout; JDK 21 temurin; setup-gradle; `:app:testStandardDebugUnitTest :app:testNearbyDebugUnitTest :app:compileStandardDebugAndroidTestKotlin :app:lintStandardRelease :app:lintStandardDebug :app:verifyCoverage`; upload coverage (always); `python3 tools/ci/check_lint.py --sarif ...standardDebug.sarif`; upload reports (on failure) | — | Sound. `verifyCoverage` is a real task in `app/build.gradle.kts` with floors in `tools/ci/coverage-floors.txt`; the SARIF it ratchets is the debug run, which also covers androidTest. 60-minute timeout. |
| avatar | checkout; node 24 with npm cache on `avatar/package-lock.json`; `npm ci`; `npx vp check`; `npx vp test`; `npx vp build` then `git diff --exit-code -- ../app/src/main/assets/avatar/avatar.js` | — | Sound. The diff step is the gate that the committed bundle matches `avatar/src`. |
| portal | checkout; node 24 with cache on both lockfiles; `npm ci --prefix ../avatar`; `npm ci`; `npx vp check`; `npx vp test`; `npx vp build` | — | Sound. The review screen really does import the avatar sources, hence the first `npm ci`. |
| tools | checkout; Python 3.12; pip install; unittest discovery over seven tool directories; fuzz parsers; nine tool checkers; content, eval and SBOM-relevant checks | — | Sound after this audit; five gates were added (section 2). |
| gateway | checkout; Go from `gateway/go.mod`; `gofmt -l`; `go vet`; `go test -race`; three 30-second fuzz targets; staticcheck; govulncheck | postgres 17 service with a health check | Sound. The three fuzz targets exist in `gateway/gateway_test.go`. |
| station | checkout; Python 3.12; `compileall`; `bash -n` over `brain/*.sh` and `relay/*.sh`; executable-bit check from `git ls-files -s`; shellcheck severity error; PowerShell parse via the language parser; `tomllib` load of `relay/relay.toml` | — | Sound. Both script families and the config exist; shellcheck and pwsh are on the runner image. |
| semgrep | checkout; `semgrep scan --config auto --severity ERROR --error --exclude app/src/main/assets/avatar/avatar.js` | container | Sound; the image is now pinned to a version tag instead of a floating one. |
| osv | checkout; download osv-scanner 2.0.2; keep only release-runtime lockfile lines; scan to `osv.json`; `jq` gate that the worst CVSS is below 7; upload `osv.json` | — | Was a gate that could pass on nothing; two guards added (section 2). |
| app-changed | checkout with full history; diff the pull request's base against HEAD over app, gradle, the Gradle files and this workflow | output `any` | Sound. On anything that is not a pull request the answer is `true`. |
| instrumented | needs version and app-changed; four shards, each: checkout; JDK 21; setup-gradle; enable KVM; `:app:assembleStandardDebug :app:assembleStandardDebugAndroidTest`; the pinned NDK/CMake check; `reactivecircus/android-emulator-runner` running `bash tools/ci/run_instrumented.sh <shard>` with `DEVICE_CLASSES`; upload `out/verify` and the test report (always) | `!cancelled()` and (app changed or new version or manual) | Sound. `tools/ci/check_workflows.py` keeps the class list equal to every `@Test` class on disk minus three measured device-only ones. Shard 0 also compares the app's seal verdict with the off-phone check (section 2). |
| gitleaks | checkout with full history; `gitleaks/gitleaks-action@v3` with the workflow token | `contents: read`, `pull-requests: read` | Sound on a public or user-owned repository; see section 4 for the organisation-licence caveat. |
| release | needs every job; only a push to master in the main repository; checkout with full history and tags; JDK 21; setup-gradle; decode the keystore secret; build, zipalign and apksigner two APKs; download syft; `tools/security/sbom.py`; `tools/security/release_manifest.py out --version` then `--check --sbom-current`; tag, push the tag, create or replace the GitHub release with the sums and notes | `contents: write`, concurrency `release`, secrets for the keystore | Sound. Full history is required and present: the notes are `git log prev..HEAD` and `git tag --list 'v*'`. |

No step carries `continue-on-error`, so no failure is silently absorbed. The one `|| true` left is
on the osv-scanner invocation itself, because that tool exits nonzero when it finds anything; the
`jq` gate after it is what decides, and it now fails closed on an empty staging file or a document
whose `results` is not an array.

## 2. Checkers and where they run

Every script under `tools/` that decides pass or fail, and the step that runs it.

| Checker | Runs in | Before this audit |
| --- | --- | --- |
| `tools/ci/check_lint.py` | android, after lint | wired |
| `tools/ci/check_workflows.py` | tools | wired |
| `tools/ci/check_portal.py` | tools | wired |
| `tools/ci/check_toolchain.py` | instrumented, after the APK build installs the NDK | **not wired** |
| `tools/docs/check_version.py` | tools | wired |
| `tools/docs/check_docs.py` | tools | wired |
| `tools/docs/baseline.py` | tools | wired |
| `tools/docs/check_release.py` | tools | **not wired** |
| `tools/docs/check_attestation.py` | tools | **not wired** |
| `tools/security/check_permissions.py` | tools | wired |
| `tools/security/fuzz_parsers.py` | tools | wired |
| `tools/security/release_manifest.py --check` | tools and release | wired |
| `tools/security/seal_agreement.py` | instrumented shard 0, after the seal test writes its files | **not wired** |
| `tools/content/build_content.py --check` | tools | wired |
| `tools/content/check_content.py` | tools | wired |
| `tools/ml/freeze_eval.py --check` | tools | wired |
| `tools/ml/eval.py --check` | tools | wired |
| `tools/ux/check_tokens.py` | tools | **not wired** |
| `tools/ux/check_inventory.py` | tools | **not wired** |
| `tools/ux/check_split.py` | tools | **not wired** |
| `tools/ux/check_baselines.py` | tools | **not wired** |
| `tools/ux/check_error_copies.py` | tools | **not wired** |
| `tools/security/sbom_lockfile.py` | producer, imported by `release_manifest.py`; not a gate | not a gate |
| `tools/ci/check_reproducible.py` | nothing; see section 4 | cannot run on ubuntu |
| `tools/security/verify_seal.py` | instrumented shard 0 | wired |
| `tools/ml/eval_*.py`, `tools/ml/int8_check.py`, `tools/isl/read_back.py`, `tools/content/coverage.py`, `tools/ux/count_paths.py` | nothing; they read training caches, weights or a device pull that CI does not have | out of scope |

The reverse direction: every path a `run:` block names exists in the tree. 41 path-shaped tokens
were extracted and all 41 resolve. The one path that had gone stale was the release-evidence
directory, hard-coded to `release/0.8.52`; it now resolves the newest directory under release,
so a version bump cannot leave the gate checking last release's evidence forever.

## 3. Gaps closed in this change

| Gap | Fix | Proof |
| --- | --- | --- |
| No NDK/CMake consistency gate | `python3 tools/ci/check_toolchain.py` in the instrumented job, after the build has installed the pinned NDK and CMake (ci.yml:309) | exit 0 here (NDK 30.0.16248370, CMake 4.1.2 in the local SDK) |
| Changelog and device matrix never checked | `python tools/docs/check_release.py` (ci.yml:139) | exit 0 |
| Attestation table never checked | `python tools/docs/check_attestation.py` (ci.yml:141) | exit 0 |
| Five UX checkers never ran | token, inventory, split, baseline and error-copy checkers (ci.yml:144-148) | all exit 0 |
| Release evidence pinned to 0.8.52 | newest `release/*/` directory (ci.yml:136-137) | exit 0 on `release/0.8.52` |
| The seal agreement checker was never run, so the app's own verdict and the off-phone check could drift | shard 0 runs `SealAgreementDeviceTest` through the instrumentation runner, pulls `cache/seal-agreement/report.pdf` and `app.json`, and diffs them with `tools/security/seal_agreement.py` (run_instrumented.sh:74-80) | `bash -n` exit 0; dry run exit 0 |
| osv gate passed on an empty lockfile staging file and would pass on schema drift | `test -s shipped/gradle.lockfile` and a `jq -e` type check on `results` (ci.yml:244, 247) | shell lines, syntax checked by the YAML parse and by reading |
| Release SBOM could be stale against the lockfile | `--check --sbom-current` (ci.yml:399) | flag exists in `tools/security/release_manifest.py` |
| Semgrep container was `semgrep/semgrep` with no tag, so a new image could change the gate unprompted | `semgrep/semgrep:1.187.0` (ci.yml:225) | tag confirmed on Docker Hub |
| Only one job had a timeout; a hung Gradle, npm or Go step would burn six hours | timeouts: version 5, android 60, avatar 15, portal 15, tools 30, gateway 25, station 10, semgrep 20, osv 15, gitleaks 10, release 60, instrumented 45 (ci.yml:19, 34, 69, 88, 110, 162, 194, 223, 233, 283, 337, 355) | YAML parses |

## 4. Gaps that stay open

| Gap | Why it stays open |
| --- | --- |
| `tools/ci/check_reproducible.py` is not a CI gate | It extracts to `C:/tmp` and builds twice; it is written for the Windows checkout with `local.properties` and the local SDK. On an ubuntu runner that path does not exist and the two builds would add well over half an hour to every pull request. It stays a pre-release step on the machine that cuts the release. |
| Two checkers other work is adding, `tools/security/check_dependencies.py` and `tools/security/check_android_posture.py`, are not wired | They are not in the tree at this commit. Wiring a path that does not exist would turn every job red the moment this lands; their author should add the step with the file. |
| Action versions are major tags, not commit SHAs | A tag can be moved upstream. Pinning to a SHA needs a tag-to-SHA refresh process and an offline-unverifiable edit here; the tags in use (`actions/checkout@v5`, `setup-java@v5`, `setup-node@v5`, `setup-python@v6`, `setup-go@v6`, `upload-artifact@v6`, `gradle/actions/setup-gradle@v5`, `android-emulator-runner@v2`, `gitleaks-action@v3`, `download-syft@v0`) were kept as they are. |
| The pip install in the tools job is partly unpinned (`numpy`, `resvg-py`, `pillow`, `soundfile`) | There is no Python lockfile in the tree for these four; the two that the release path parses (`cryptography`, `pypdf`) are pinned. Adding a hashed requirements file is a separate change with its own regeneration step. |
| `gitleaks-action@v3` needs a licence secret for an organisation-owned private repository | The workflow cannot supply it. If the repository is user-owned the action runs without one; if it is organisation-owned, the job fails closed rather than skipping the scan, which is the right failure. |
| No gate builds the release APK on a pull request | The signed build needs the keystore secret, and the unsigned release variant is exercised only by `assembleStandardRelease` inside the release job. A secret-free unsigned build could be added, but it doubles Gradle time on every pull request for a gap the release manifest check catches first. |
| The `git ls-remote` in the version job treats a network failure as "tag absent" | The job would report a new version, but the release job then checks the tag itself with `gh release view` and replaces rather than duplicates. A retry loop was not worth the noise. |
| Artifact retention is the default 90 days | The uploaded artifacts are test and coverage reports. The evidence a downloader needs (APKs, `SHA256SUMS`, SBOM, provenance) is published as release assets, which do not expire. |
