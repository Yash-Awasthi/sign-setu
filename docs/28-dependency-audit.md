# 28. Dependency audit

The lockfile is the list of coordinates that ship. This page records what was asked of OSV about those coordinates on 2026-11-27, the two hits the first run of the gate returned, and how both were cleared. The checker in `tools/security/check_dependencies.py` repeats the query and fails the gate when a pinned version sits below a fixed release; `tools/security/data/osv-snapshot-2026-11-27.json` is the committed answer so the same decision can be reproduced without the network.

## 1. What was queried

| Input | Value |
| --- | --- |
| Lockfile | `app/gradle.lockfile`, SHA-256 `dd2f9793a514d846ce648df4e991d5cb95766aad03552b72e88cec7aecdc6520` |
| Coordinate lines in the file | 471 |
| Lines on a release runtime classpath, queried | 274 |
| Unique group:artifact:version triples queried | 274 |
| `settings-gradle.lockfile` | one line, `empty=incomingCatalogForLibs0`; no coordinates, nothing to query |
| Ecosystem and version | Maven, the version pinned in the lockfile |
| Batches | 3 (100, 100 and 74 queries), no pagination token returned |
| Ecosystem records returned | none; no triple is below a fixed release |

The scope is the release runtime classpath, the same filter the osv job in `.github/workflows/ci.yml` and `tools/security/sbom_lockfile.py` use. Lint, test, KSP and annotation-tool lines in the lockfile do not ship and were not audited.

## 2. Hits the gate reported before the fix

The gate's first run against the lockfile as committed at `6ceacf5` exited 1 with two HIGH findings. Both were on one triple each.

| OSV id | Alias | Severity | Pinned here | Fixed in | Reachable from | Summary |
| --- | --- | --- | --- | --- | --- | --- |
| GHSA-xxph-c9ww-hj94 | CVE-2026-102554 | HIGH | com.google.guava:guava:33.3.1-android | 33.7.2 | `releaseCompileClasspath`, `releaseLintChecksClasspath`, `releaseRuntimeClasspath` | CompactHashMap, CompactHashSet and MapMakerInternalMap deserialization triggers uncontrolled memory allocation |
| GHSA-735f-pc8j-v9w8 | CVE-2024-7254 | HIGH | com.google.protobuf:protobuf-javalite:4.26.1 | 4.27.5 | `releaseCompileClasspath`, `releaseLintChecksClasspath`, `releaseRuntimeClasspath` | protobuf-java potential denial of service when parsing untrusted data |

The OSV ranges were read at the pin, so the fixed version above is the lowest fixed release above the pinned version, not the lowest fixed release in the record. GHSA-735f-pc8j-v9w8 also names 3.25.5 for the 3.x line and 4.28.2 for the 4.28 line; neither is the first fix above 4.26.1.

## 3. Resolution

| Package | Before | After | Where the pin lives after the fix |
| --- | --- | --- | --- |
| com.google.guava:guava | 33.3.1-android | 33.7.2-android | `gradle/libs.versions.toml` `guava`, constrained in `app/build.gradle.kts` |
| com.google.protobuf:protobuf-javalite | 4.26.1 | 4.36.2 | `gradle/libs.versions.toml` `protobuf`, declared in `app/build.gradle.kts` |

Both libraries had already been moved in the version catalog, and both resolved at the fixed versions on `standardReleaseRuntimeClasspath` and `nearbyReleaseRuntimeClasspath`, the two classpaths an APK is built from. Only the build-type-level `debug*` and `release*` configurations still carried the older triples: they are locked, so resolution keeps the pin recorded when the catalog was older, and no flavor variant reads them. Removing those two lines from `app/gradle.lockfile` and re-locking let the resolution the flavored variants already perform rewrite the file; the two triples are gone from every configuration and nothing new entered a release runtime classpath.

The same re-lock refreshed seven coordinates that only lint, unit-test and instrumented-test classpaths carry: the Android test engine `1.0.1` to `1.0.8` on four artefacts, `gradle-work-action` `32.4.1` to `32.5.3`, and `androidx.concurrent:concurrent-futures` `1.2.0` with its `-ktx` module. It also moved four lint classpaths from `androidx.collection:collection-ktx` `1.5.0` to the `1.6.0` the rest of the graph already used. None reaches an APK, and every one is already checksummed in `gradle/verification-metadata.xml`.

The command that proved the gate green, with no advisory and no pin below its fix:

```
python tools/security/check_dependencies.py --offline   # 274 components, 0 vulnerabilities, 0 below a fixed release; exits 0
```

## 4. Reproducing

```
python tools/security/check_dependencies.py             # live query, exits 1 on a pin below its fix
python tools/security/check_dependencies.py --offline   # same decision from the committed snapshot
python tools/security/check_dependencies.py --write-snapshot  # refresh the snapshot from a live query (needs network)
./gradlew :app:dependencies --write-locks               # re-lock after a pin changes
python -m unittest discover -s tools/security -v        # the gate's tests, no network
```

`--write-snapshot` refreshes the committed snapshot from a live query. Offline mode also fails when the lockfile holds a triple the snapshot never queried, so a stale snapshot cannot silently certify a new dependency, and a snapshot taken before a pin moved has to be refreshed in the same change as the lockfile.

## 5. What this record does not cover

- `brain/requirements.txt`: the Python station brain is not in the Gradle lockfile.
- The vendored llama.cpp commit at `app/src/main/cpp/third_party/PROVENANCE.md` and the relay release binary pinned in `relay/setup.sh`: neither is a Maven coordinate.
- The assistant model file: accepted by allow-list and hash, not by coordinate.
- Build-time tooling in the same lockfile: lint checks, unit-test and KSP classpaths, which never reach an APK.
- A component whose OSV record names no fixed release: it would be printed by the checker and would not fail the gate, because no pin can clear it. There were none on this date.
