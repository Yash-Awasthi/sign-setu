# 30. Release verification

What the committed evidence for 0.8.52 lets a verifier check offline, what it cannot, and the commands that produce
the verdict. The evidence lives in `release/0.8.52/` and is written by `tools/security/release_manifest.py`; the
release job that signs the published files is described in [24-deployment-package.md](24-deployment-package.md).

## 1. What is committed

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `release/0.8.52/PROVENANCE.json` | 2215 | `5145e7002dbf61956465ee9f8ae17c7d45f4a6f9381372fa288162f8d2c9e7a9` |
| `release/0.8.52/SHA256SUMS` | 283 | `ba9c37e565b3e83df19bbadcdd81b086e528b771d96c1c95aca0facfa8e78679` |
| `release/0.8.52/sign-setu-release.cdx.json` | 78582 | `cba8ed65dc0e71e850d8d1dd6d239efa1fcdba5a8ca3ff9ecd6494ecb15656e5` |
| `release/0.8.52/README.md` | 2369 | `9ab320ac55ed4697ddb1e2b81605934a3d6307a757af0e779c425fc74bbb6d28` |

`SHA256SUMS` lists three names: the two APKs and the SBOM. It does not list `PROVENANCE.json` or itself, and nothing
signs the directory.

## 2. The commands, measured 2026-11-27

Run from the repository root, in the order below. The check was run against the tree at `d569d07`; the evidence was
cut at `9dbacf15`, which is an ancestor of it.

| Command | Exit | What it printed |
| --- | --- | --- |
| `python tools/security/release_manifest.py release/0.8.52 --check` | 0 | `1 of 3 listed files present`, two APKs absent, `SBOM 276 components` |
| `python tools/security/release_manifest.py release/0.8.52 --check --sbom-current` | 0 | the same, with the SBOM's 276 components also equal to the lockfile's shipped lines |
| `python -m unittest discover -s tools/security` | 0 | `Ran 90 tests in 18.029s`, `OK` |
| `python tools/docs/check_docs.py` | 0 | no output |
| `sha256sum -c SHA256SUMS` | not run | the two APKs are not in the clone and the network was not reachable; run it beside the downloaded assets |

## 3. Hashes verified against the bytes

| Recorded digest | What was hashed | Result |
| --- | --- | --- |
| `cba8ed65...6e5` | `sign-setu-release.cdx.json`, 78582 bytes | matches, and equals the bytes `sbom_lockfile.py` writes for `app/gradle.lockfile` at `9dbacf15`; 276 components, CycloneDX 1.5, each with a name, version and purl |
| `ae2e167d...fc8` | `gradle/verification-metadata.xml` at `9dbacf15`, 314052 bytes | matches; the file is unchanged at the current HEAD |
| `0d7afe9a...4d4` | `app/gradle.lockfile` at `9dbacf15`, 267964 bytes | matches; 477 non-blank lines, 276 shipped |
| `e58bb2ed...926` | `settings-gradle.lockfile` at `9dbacf15`, 198 bytes | matches; 4 lines |
| `21bd5da2...607` | the pin in `gradle/wrapper/gradle-wrapper.properties` | matches the wrapper's `distributionSha256Sum`; the `gradle-9.8.2-bin.zip` archive is not on this machine, so the archive bytes were not hashed |
| `8c9ba7ab...be81` | `sign-setu-0.8.52-arm64.apk`, 136662709 bytes | recorded, not verified: no APK bytes in the clone |
| `320dd6cc...2e6` | `sign-setu-0.8.52-universal.apk`, 189431933 bytes | recorded, not verified: no APK bytes in the clone |

The check reads the dependency digests through git at the commit `PROVENANCE.json` names, so a later HEAD cannot
excuse a wrong digest; when git does not have that commit it falls back to the working tree and says so.

Every field of `PROVENANCE.json` that is a function of the committed files was rebuilt and compared: `format`,
`version`, `versionCode`, `commit`, the SBOM block, both dependency digests with their line counts, the Gradle pin and
the `verify` line, with no difference. The two artefact rows carry a byte count and a SHA-256 that cannot be rebuilt
without the APK files, so those are the only fields this page reports as recorded rather than verified.

## 4. Red proofs

Each was run on a copy of the committed directory under the temp directory; the committed directory was not touched.

| Change | Exit | Check's line |
| --- | --- | --- |
| one bit flipped in the SBOM, byte 100 | 1 | `sign-setu-release.cdx.json: SHA-256 is 57a3f9b4..., SHA256SUMS says cba8ed65...` |
| the arm64 APK replaced by a 144-byte zip of the same name | 1 | `...: SHA-256 is 4b1322b9..., SHA256SUMS says 8c9ba7ab...` and `...: 144 bytes, PROVENANCE.json says 136662709` |
| one bit flipped in an APK of a directory cut for this proof | 1 | `...: SHA-256 is 58771a0e..., SHA256SUMS says befea9d4...` |
| an extra `sign-setu-0.8.52-x86.apk` beside the evidence | 1 | `sign-setu-0.8.52-x86.apk: present in ... but missing from SHA256SUMS` |

The third row shows that a single changed byte is enough; the second is the same claim on the committed record, whose
APK bytes are not here, so the stand-in file is what the check sees.

## 5. Checks that were labels and now read bytes

`release_manifest.py --check` used to trust three things it could have measured, and one heuristic:

- the APK signing status searched the last 4096 bytes for the `APK Sig Block 42` magic. It now parses the zip
  end-of-central-directory record, requires the magic to end exactly where the central directory starts, and requires
  the size field before the block to repeat at its end. A copy of the magic anywhere else is not a signature
  (`test_the_signing_magic_outside_the_block_is_not_a_signature`).
- `gradle/verification-metadata.xml` was compared only when `PROVENANCE.json` named the current HEAD, so any other
  commit skipped it. It is now compared with the file's bytes at the recorded commit, through `git show`, always when
  the file is present (`test_a_changed_verification_metadata_fails`, `test_a_digest_is_read_at_the_recorded_commit_not_at_head`).
- both dependency lockfiles' digests were recorded and never checked. They are now checked the same way
  (`test_a_recorded_lockfile_digest_is_read_from_the_bytes`).
- the Gradle distribution digest was a label copied from the wrapper. It is now compared with the wrapper's pin
  (`test_a_recorded_gradle_pin_is_checked_against_the_wrapper`).
- the SBOM was checked for its own hash only. It is now also required to be CycloneDX with a `<major>.<minor>`
  specVersion, to declare the format `PROVENANCE.json` records, to carry a name, version and purl on every component,
  and, when `sbom_lockfile.py` produced it, to equal byte for byte what that tool derives from the lockfile at the
  recorded commit (`test_a_rewritten_sbom_is_caught_even_when_the_hashes_agree`, `test_a_component_without_a_purl_fails`).

## 6. What a verifier can and cannot conclude

From the committed bytes alone a verifier can conclude:

- the SBOM is byte for byte the document `sbom_lockfile.py` derives from `app/gradle.lockfile` at `9dbacf15`, and it
  lists 276 release-runtime components, each with a name, version and purl;
- `gradle/verification-metadata.xml` and both lockfiles are the files whose digests `PROVENANCE.json` records at that
  commit;
- the recorded Gradle distribution digest is the wrapper's pin;
- every file present beside the sums is listed there and matches, and a single changed byte or an unlisted APK exits 1;
- the record was cut from a machine with no release keystore, and both APKs are recorded `unsigned`,
  `shippable: false`.

A verifier cannot conclude, from the clone alone:

- that the published APK files are the bytes whose hashes are recorded. The APKs are excluded by `.gitignore` and were
  never committed; `sha256sum -c SHA256SUMS` beside the downloaded assets is the only check of them, and it is not
  reported here because the assets were not reachable;
- that the APKs carry no signature. That status is read from the APK bytes when they are present, and the check fails
  when the record and the bytes disagree, but with the bytes absent the record is a claim, not a measurement;
- that the APK was built from `9dbacf15`, or that the same build run on another machine would produce the same bytes;
- that `gradle-9.8.2-bin.zip` hashes to its pin.

## 7. The signing status, plainly

**Both `sign-setu-0.8.52-arm64.apk` and `sign-setu-0.8.52-universal.apk` are unsigned.** They carry no APK Signing
Block and no `META-INF` certificate, so they are not installable as a release and are not shippable. `apksigner` with
the release key must sign the aligned APK; that changes every byte and the hash, so the hashes on this page are the
hashes of an unsigned build and must not be quoted as the hashes of a shipped file. The `release` job re-cuts this
evidence beside the signed files at release time.

## 8. When the assets are downloaded

```
sha256sum -c SHA256SUMS
python tools/security/release_manifest.py release/0.8.52 --check
```

The first line is the byte check of the APKs and the SBOM. The second recomputes every recorded digest, parses each
APK's signing block from its bytes, and exits 1 when `PROVENANCE.json` and the files disagree.
