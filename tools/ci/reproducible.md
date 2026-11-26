# Reproducible release APK

Two builds of the same commit should produce the same APK. This document states the check,
the inputs it holds, and the differences it cannot remove, so a green run is evidence and a
red run names its cause.

## One command

    py -3 tools/ci/check_reproducible.py --build

The script:

1. extracts `git archive HEAD` into a fresh `C:/tmp/repro-XXXX` directory, so the built tree
   is the committed HEAD and never the dirty worktree;
2. copies the untracked `local.properties` into that tree, because its `sdk.dir`,
   `gatewayUrl` and `assistUserAgent` values supply the SDK path and the BuildConfig fields
   of the same names;
3. runs `gradlew.bat --console=plain --no-build-cache :app:assembleStandardRelease`, streams
   the output, records the wall seconds, and copies the APK in as `apk1.apk`;
4. deletes `app/build` and `app/.cxx` in the extracted tree so no compiled class, dex, shrunk
   resource or native object is reused, runs the same command again, and copies the result in
   as `apk2.apk`; the tree's `.gradle` directory is kept, so build B reuses the configuration
   cache entry build A stored (a held-constant input, row below);
5. compares the two APKs with the rule below, prints the input manifest and a one-line JSON
   payload, runs `gradlew.bat --stop`, and deletes the temp directory.

`--keep` leaves the temp tree in place and prints its path. The exit code is 0 when the APKs
match, 1 when they differ, and 2 on bad input or a build/tooling failure; the failing command
is printed. The worktree, its `app/build` and its `app/.cxx` are never touched.

The manifest lists the source commit, `git status --porcelain` (a dirty worktree is printed
but does not enter the build), `java -version`, the Gradle wrapper version, `ANDROID_HOME`,
`compileSdk`/`targetSdk`/`minSdk`, `ndkVersion`, the CMake version, every `local.properties`
key and value, the timezone and locale, the build start and end timestamps, the wall seconds
of each run, and the `BUILD_EPOCH_DAY` each build actually wrote into `BuildConfig.java`.

## Comparing two APKs you already have

    py -3 tools/ci/check_reproducible.py apk1.apk apk2.apk

The comparison reports whole-file SHA-256 and size; the zip entry count and central-directory
order; each entry's name, CRC32, uncompressed size, compressed size and timestamp; the
differing entries and any entry present in only one file; and whether an APK Signing Block is
present, with its start offset and total length.

The rule:

- Both APKs unsigned (what `assembleStandardRelease` produces): identical only when the
  whole-file SHA-256 matches. The output says `unsigned, comparing all bytes`.
- Both APKs signed: identical when everything outside the APK Signing Block is byte-identical
  and every zip entry matches. The signature block is the one region allowed to differ, and
  the verdict says `identical outside the signing block`.
- One signed and one unsigned, or any byte or entry difference: exit 1, with a `reason:` line
  for each difference and a final verdict.

## Exact inputs

These are the values the runs hold. Any change to them is a different experiment.

- Source commit: `git rev-parse HEAD` of the checkout. The build tree is `git archive HEAD`.
- JDK: Temurin 21.0.12.1+1 (build 21.0.12.1+1-LTS), Java 21. The build requires Java 21.
- Gradle wrapper: 9.8.2, with `distributionSha256Sum` set in
  `gradle/wrapper/gradle-wrapper.properties`.
- AGP 9.4.1, Kotlin 2.4.21, KSP 2.3.12, read from `gradle/libs.versions.toml`.
- SDK: `compileSdk` 37, `targetSdk` 37, `minSdk` 29.
- NDK 30.0.16248370 and CMake 4.1.2, as pinned in `app/build.gradle.kts`.
- `local.properties` (untracked, copied into the build tree): `sdk.dir=C:\work\android-sdk`,
  `gatewayUrl=https://signsetu-gateway.onrender.com`, and the `assistUserAgent` client name
  the assistant service expects. The two runs use one copy of this file.
- Locale: `English_United States` (code page 1252), preferred encoding UTF-8. Timezone:
  India Standard Time (UTC+05:30).
- `BUILD_EPOCH_DAY`: the epoch day of `LocalDate.now()` in the `EpochDay` ValueSource in
  `app/build.gradle.kts`, read at configuration time. The manifest prints the number and date
  each build used.
- Gradle configuration cache: one extracted tree, so build B reuses the entry build A stored.
  Every task that produces APK content runs in both builds; only the configuration phase is
  shared.

## What can differ, and why

| What can differ | Held by the two runs? | Reason |
| --- | --- | --- |
| Build date, through `BUILD_EPOCH_DAY` | Held only within one calendar day | The ValueSource returns `LocalDate.now().toEpochDay()` at configuration time. Run A and run B on opposite sides of local midnight write different `long` values into `BuildConfig`, so `classes.dex` differs. Two runs in one day hold it. |
| `local.properties` values (`sdk.dir`, `gatewayUrl`, `assistUserAgent`) | Held: one file is copied into the one build tree | The file is untracked and its values become the SDK path and the BuildConfig strings `GATEWAY_URL` and `ASSIST_UA`. A different file, or `-PgatewayUrl=...`, changes the APK. |
| Absolute build path if it reaches a native library | Held within one invocation (both builds run in the same `C:/tmp/repro-XXXX`); not held between invocations | Native debug information or `__FILE__` can embed the build directory in a `lib*.so`. Because the two builds share one path, such a leak stays identical and this check cannot see it; two separate `--build` runs use different paths and would expose it. |
| JDK, Gradle wrapper, AGP, NDK, CMake versions | Held: same machine, same pinned versions | A different compiler, dexer, shrinker or native toolchain emits different bytecode, resources or native code. |
| R8 / ProGuard configuration | Held: same commit's rules | Shrinking and obfuscation are build inputs; changed rules change names, dex layout and the classes kept. |
| Zip entry timestamps | Held: the current AGP normalises them | If AGP stopped normalising, each entry's DOS timestamp would be the build clock and every entry would differ. |
| Locale and timezone | Held: same environment for both runs | Locale-sensitive formatting can reach resources or generated code; the timezone shifts `LocalDate.now()` and therefore `BUILD_EPOCH_DAY`. |
| Dependency cache and offline resolution | Held: same Gradle cache, plus lockfiles and verification metadata | A refreshed or partial cache, a changed repository, or an unlocked dynamic version can swap an artifact under the build. |
| Gradle configuration cache | Held: both runs use one extracted tree | Build B reuses build A's configuration cache entry, so the configuration phase runs once; every task that writes APK content still runs in both builds. A run without that cache re-evaluates `BUILD_EPOCH_DAY` and every Gradle-time value. |
| Wall-clock build time | Not held: run B starts after run A | It is not an APK input unless a build step stamps it; the manifest records it so such a stamp is visible as a cause rather than a surprise. |
| APK signature | Held: `assembleStandardRelease` writes an unsigned APK | If the APK is signed, the APK Signing Block differs by signature and certificate. The comparison then excludes only that block. |

Everything else in the table is a real difference. When the verdict is `different`, the
`reason:` lines name the entry or the byte offset, so the cause can be matched to a row above.

## Limits

- `BUILD_EPOCH_DAY` is stable only within one calendar day. A run that crosses local midnight
  is expected to fail, and that failure is correct rather than a bug in the tool.
- Both builds run in the same extracted tree, so the absolute build path is held constant. A
  leak of that path into a native library would not surface here; two separate `--build`
  invocations, each with its own `C:/tmp/repro-XXXX`, would show it.
- The comparison ignores the APK Signing Block, because a signature is expected to differ
  between signings. It does not verify the certificate or that the signed content is valid;
  that is `apksigner verify`'s job.
- Reproof is only as strong as the manifest: two runs on different machines, JDKs or SDK
  builds can agree on the table above and still differ. Record the manifest with the result.
