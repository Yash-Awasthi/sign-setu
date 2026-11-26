# Release evidence for 0.8.52

The two APKs of this release are published as GitHub release assets; they are never committed,
because `.gitignore` excludes `*.apk`. This directory is the record that makes their bytes
auditable, and it is what `python tools/security/release_manifest.py` writes beside them:

| File | What it is |
| --- | --- |
| `SHA256SUMS` | one `<sha256>  <name>` line per APK and for the SBOM; `sha256sum -c SHA256SUMS` runs beside the download |
| `sign-setu-release.cdx.json` | a CycloneDX 1.5 SBOM of the release runtime classpath, read from the resolved graph in `app/gradle.lockfile` |
| `PROVENANCE.json` | commit, version and build task; the Gradle distribution and its pinned SHA-256; each APK's published path, size, SHA-256 and signing status; the SHA-256 of `gradle/verification-metadata.xml`; both lockfiles' digests and counts; the SBOM producer and component count |

## Signing status

Both APKs recorded here are **unsigned**. They were built from commit
`9dbacf15dcffbdd0d520d4dab1a2122e79e09190` with `:app:assembleStandardRelease` on a machine that
holds no release keystore, so the files carry no APK Signing Block and no `META-INF` certificate.
`PROVENANCE.json` therefore records `"signing": "unsigned"` and `"shippable": false` for each of
them, and that status is read from the bytes.

An unsigned APK is not installable as a release and is not shippable. The published release
assets are aligned and signed by the `release` job in CI with the repository's release key, which
changes every byte of the file and so changes its hash: the evidence for those signed files is cut
again, beside them, at release time. Until the release key exists in the deployment, this
directory is the provenance of an unsigned build only, and its hashes must not be quoted as the
hashes of a shipped APK.

## How to check

```
python tools/security/release_manifest.py release/0.8.52 --check
```

The check recomputes every hash listed, exits 1 on one changed byte, on an APK or the SBOM present
but missing from `SHA256SUMS`, on a recorded signing status the bytes contradict, and on a
`verification-metadata.xml` digest that no longer matches the file at the commit that cut the
record. The APKs themselves are not in a clone, so they are reported as absent and skipped; with
them beside the sums, `sha256sum -c SHA256SUMS` covers them too.
