# 28. Dependency audit

The lockfile is the list of coordinates that ship. This page records what was asked of OSV about those coordinates on 2026-11-27, what came back, and whether each hit can be cleared by moving a pin. The checker in `tools/security/check_dependencies.py` repeats the query and fails the gate when a pinned version sits below a fixed release; `tools/security/data/osv-snapshot-2026-11-27.json` is the committed answer so the same decision can be reproduced without the network.

## 1. What was queried

| Input | Value |
| --- | --- |
| Lockfile | `app/gradle.lockfile`, SHA-256 `0d7afe9ab93a4474b50621378a6382641fb45fdc4f405f2c28dcf2da5d0264d4` |
| Coordinate lines in the file | 473 |
| Lines on a release runtime classpath, queried | 276 |
| Unique group:artifact:version triples queried | 276 |
| `settings-gradle.lockfile` | one line, `empty=incomingCatalogForLibs0`; no coordinates, nothing to query |
| Ecosystem and version | Maven, the version pinned in the lockfile |
| Batches | 3 (100, 100 and 76 queries), no pagination token returned |
| Ecosystem records returned | 2 vulnerabilities on 2 triples, both on exactly one triple each |

The scope is the release runtime classpath, the same filter the osv job in `.github/workflows/ci.yml` and `tools/security/sbom_lockfile.py` use. Lint, test, KSP and annotation-tool lines in the lockfile do not ship and were not audited.

## 2. Hits

Two triples are below a fixed release. Both are HIGH.

| OSV id | Alias | Severity | Pinned here | Fixed in | Reachable from | Summary |
| --- | --- | --- | --- | --- | --- | --- |
| GHSA-xxph-c9ww-hj94 | CVE-2026-102554 | HIGH | com.google.guava:guava:33.3.1-android | 33.7.2 | `releaseCompileClasspath`, `releaseLintChecksClasspath`, `releaseRuntimeClasspath` | CompactHashMap, CompactHashSet and MapMakerInternalMap deserialization triggers uncontrolled memory allocation |
| GHSA-735f-pc8j-v9w8 | CVE-2024-7254 | HIGH | com.google.protobuf:protobuf-javalite:4.26.1 | 4.27.5 | `releaseCompileClasspath`, `releaseLintChecksClasspath`, `releaseRuntimeClasspath` | protobuf-java potential denial of service when parsing untrusted data |

The OSV ranges were read at the pin, so the fixed version above is the lowest fixed release above the pinned version, not the lowest fixed release in the record. GHSA-735f-pc8j-v9w8 also names 3.25.5 for the 3.x line and 4.28.2 for the 4.28 line; neither is the first fix above 4.26.1.

## 3. Whether each is fixable here

Both are fixable by moving a pin, and neither was moved in this change, which is an audit only.

| Hit | Fixed release published | Already fixed elsewhere in this lockfile | Fix in this change |
| --- | --- | --- | --- |
| GHSA-xxph-c9ww-hj94 | 33.7.2 | Yes: `com.google.guava:guava:33.7.2-android` on `standardReleaseRuntimeClasspath` and `nearbyReleaseRuntimeClasspath` | No: a catalog bump is required, and this change is not allowed to upgrade a dependency |
| GHSA-735f-pc8j-v9w8 | 4.27.5 | Yes: `com.google.protobuf:protobuf-javalite:4.36.2` on `standardReleaseRuntimeClasspath` and `nearbyReleaseRuntimeClasspath` | No: same reason |

Both vulnerable pins are reached only by the build-type-level `release*` configurations. The two variants that produce an APK, `standard` and `nearby`, resolve the fixed versions of both libraries. The lockfile keeps the older triples because those build-type-level configurations are locked too, even though no flavor variant uses them.

The gate therefore reports two hits and exits 1. That is the honest result for the lockfile as committed: the 276-triple set this audit is defined on contains two versions below their fix. Clearing it needs either a reviewed version-catalog bump, or a decision to stop locking the build-type-level release configurations that hold the older pins; the second is a build change and is not made here.

## 4. Reproducing

```
python tools/security/check_dependencies.py            # live query, exits 1 on a pin below its fix
python tools/security/check_dependencies.py --offline  # same decision from the committed snapshot
python -m unittest discover -s tools/security -v       # the gate's tests, no network
```

`--write-snapshot` refreshes the committed snapshot from a live query. Offline mode also fails when the lockfile holds a triple the snapshot never queried, so a stale snapshot cannot silently certify a new dependency.

## 5. What this record does not cover

- `brain/requirements.txt`: the Python station brain is not in the Gradle lockfile.
- The vendored llama.cpp commit at `app/src/main/cpp/third_party/PROVENANCE.md` and the relay release binary pinned in `relay/setup.sh`: neither is a Maven coordinate.
- The assistant model file: accepted by allow-list and hash, not by coordinate.
- Build-time tooling in the same lockfile: lint checks, unit-test and KSP classpaths, which never reach an APK.
- A component whose OSV record names no fixed release: it would be printed by the checker and would not fail the gate, because no pin can clear it. There were none on this date.
