# 24. Deployment package

One page for a department taking a release from its tag to a checked install. Everything below is produced by the
release job or lives in this repository; none of it replaces the department's own change control, and none of it is
legal advice. The policy behind the tag and the version is in [RELEASE.md](../RELEASE.md).

## 1. The seven artefacts

| Artefact | Where it is produced or lives | How to check it | What a checker can prove |
| --- | --- | --- | --- |
| Signed APK | The `release` job in `.github/workflows/ci.yml`, attached to the GitHub release for tag `v<versionName>`; two files, `sign-setu-<version>-arm64.apk` and `sign-setu-<version>-universal.apk` | `apksigner verify --print-certs sign-setu-<version>-arm64.apk` | The file is intact and signed by the release certificate. Its SHA-256 certificate digest is the one an installed build already carries, so the update installs over the old app instead of being a different app. It does not prove who built it |
| SBOM | `tools/security/sbom.py` with Syft in the release job; `tools/security/sbom_lockfile.py` writes the same document from the lockfile where Syft is absent; `sign-setu-release.cdx.json`, published beside the APKs | `python -c "import json; d=json.load(open('sign-setu-release.cdx.json')); print(d['bomFormat'], d['specVersion'], len(d['components']))"` | It is a CycloneDX document, and it lists every dependency on the release runtime classpath with a version, so an advisory can be matched to the release. It does not cover the Android platform, the model file, or build-only tools |
| SHA256SUMS | The release job, after both APKs are signed and before publishing | `sha256sum -c SHA256SUMS` | Each APK is byte for byte the file that was published, and the hashes also printed in the release notes are the same ones |
| DPIA | `docs/13-dpia.md`, with its owner and review date in the header | Read the header table; `python tools/security/check_permissions.py` | The assessment names an owner and a review date, and its permission rows match the manifests. It is an assessment, not a signature, and it is not legal advice |
| Walkthrough | `docs/19-walkthrough.md` with the images in `docs/screens/`, mirrored in the app's Help | `python tools/docs/check_docs.py` | Every screen image is described and every link resolves, so the printed guide and the in-app Help describe screens that still exist |
| Model allow-list procedure | `app/src/main/java/app/signsetu/assistant/LocalModel.kt`, the `ModelStore.KNOWN_MODELS` set | `grep -n 'KNOWN_MODELS' app/src/main/java/app/signsetu/assistant/LocalModel.kt` | The shipped app accepts only the SHA-256 values in that set, and any other imported file is deleted before it is parsed. Adding a model is a reviewed change to that constant (section 6) |
| Verification instructions | This page, with `tools/security/verify_seal.py` and `tools/security/seal_agreement.py` | `python tools/security/verify_seal.py --pdf report.pdf --key-fingerprint <sha256>` | The report's seal signature holds under the key the seal carries, and that key is the one whose fingerprint is printed on the certificate (section 7) |

### The unsigned build measured on this machine

There is no release keystore here, so the artefact measured below is the unsigned `standard` release APK; nothing in
this table is a signing or installer claim. The numbers describe commit `6115f5d` only, built from a clean
`git archive` export of that commit.

| Fact | Measured value |
| --- | --- |
| Build | `:app:assembleStandardRelease` in the export, `BUILD SUCCESSFUL` |
| APK | app-standard-release-unsigned.apk, unsigned, 189402815 bytes |
| SHA256SUMS | `d857211e08434ef8dfebac3f638a743a2677822893b3caca5339afd5e5141a24  app-standard-release-unsigned.apk`; `sha256sum -c SHA256SUMS` prints `OK` |
| SBOM | CycloneDX 1.5 JSON with 234 components, equal to the 234 release-runtime lines of the lockfile; written by `tools/security/sbom_lockfile.py` because Syft is not installed here; sha256 24c8779ed8a2af48754c178771f66aad519716b6bab4b046972d1d8079ed1d7f, 68411 bytes, kept as sign-setu-release.cdx.json beside the APK under `app/build/sbom/` and regenerated from the lockfile, so it is not committed |
| Dependency verification | `gradle/verification-metadata.xml` sha256 fe3c0eca068688b27d0f15ad1551c5d09ae13c86df04964d57566ab7654bcf78 |

## 2. What the release job builds and signs

The `release` job runs only on a push to `master` in the private repository, after the version, build, test, analysis
and emulator jobs have passed. It reads one version: `versionName` from `app/build.gradle.kts`, which the `version`
job exports as `VERSION`. The tag it publishes is `v$VERSION`, so the tag and the build carry the same number; a tag is
never typed from memory.

- **Two ABIs, one flavour.** The job loops over `universal arm64` and runs `:app:assembleStandardRelease` each time; the
  arm64 pass adds `-Pabi=arm64-v8a`, the universal pass takes the default `arm64-v8a` plus `x86_64`. Both are the
  `standard` flavour; nothing in the bundle is the `nearby` flavour.
- **Signing.** The keystore arrives as the CI secret `RELEASE_KEYSTORE_B64`, is decoded to a temporary file, and
  `apksigner` signs the zipaligned APK with the alias `signsetu` and the password from `RELEASE_KEYSTORE_PASSWORD`.
  v4 signing is off. The temporary keystore is deleted in the same step.
- **Sums and SBOM.** `sha256sum` over both APKs writes `SHA256SUMS`; Syft writes the CycloneDX SBOM through
  `tools/security/sbom.py`. Where Syft is not installed, `tools/security/sbom_lockfile.py` writes the same document
  from the lockfile, so the evidence exists either way; the Syft run stays the preferred one.
- **Publish.** The job moves the tag, writes the release notes from the commit subjects since the previous tag, and
  uploads or replaces every asset. The same version therefore has its assets replaced, not duplicated.

The signing key is not in the repository, the release, or this page; see section 8.

## 3. How to check the download

Work in one directory holding the downloaded assets.

1. `sha256sum -c SHA256SUMS`. Every line must say `OK`. A mismatch means the download is damaged or not the file the
   release published; do not install it.
2. `apksigner verify --print-certs sign-setu-<version>-arm64.apk`. It must print the signer certificate and exit 0.
   Compare the printed SHA-256 certificate digest with the one in the department's release record for the previous
   version. The same digest is what lets a new build install over an installed one and keep its sessions
   ([RELEASE.md](../RELEASE.md) section 4). A different digest is a different signer: stop and ask.
3. Check the SBOM. The one-line Python above must print `CycloneDX` and a spec version, and a component count greater
   than zero. `tools/security/sbom.py` builds it from the lines of `app/gradle.lockfile` whose configuration names the
   release runtime classpath, so it describes what the shipped app links and leaves build-only tools out. The same
   filter is in `tools/security/sbom_lockfile.py`, which needs no Syft binary and is the one to run without it.

None of these three checks needs this repository, the signing key or network access.

## 4. The DPIA: who owns it, and its review date

The header of [13-dpia.md](13-dpia.md) states the facts a department needs, and this page repeats them rather than a
summary:

| Field | Value |
| --- | --- |
| Document owner | Yash Awasthi, Sign Setu maintainer |
| Review date | 2027-05-26, and again at any release that adds or removes a stored field or a permission |
| Controller | The police department that deploys the app `[confirm]` |
| Audience | Department legal officer and Data Protection Officer; not legal advice |

The page names an owner, not a signature: there is no signature block, and this page does not invent one. The
department's controller and its Data Protection Officer approve it on their side, which is why the controller row is
still `[confirm]` until the department fills it in. The permission rows are checked against the manifests by
`python tools/security/check_permissions.py`.

## 5. Where the officer walkthrough is

The walkthrough is [19-walkthrough.md](19-walkthrough.md), and it mirrors the app's own Help. An officer reaches the
same text from Settings, Help, "Help and walkthrough"; from the "Take the tour" card on the home screen; from the last
first-run step, before the first unlock; and in a session from the officer's three-dot menu, Help. It is in English and
Hindi.

The screen images live in `docs/screens/` and are described one by one in [12-screens.md](12-screens.md). A
department that prints the guide or checks a screenshot uses those files; `python tools/docs/check_docs.py` fails if an
image is described nowhere or a described image is missing.

## 6. The model allow-list procedure as the code has it

The optional on-device assistant loads only a model the department installs itself; the app never downloads one. The
procedure is in [10-on-device-assistant.md](10-on-device-assistant.md) section 3, and the code behind it is
`app/src/main/java/app/signsetu/assistant/LocalModel.kt`:

1. The department obtains the model file and computes its SHA-256 itself, off the phone.
2. The officer opens Settings, Offline, On-device assistant, Import model file, and picks the file.
3. `ModelStore.import` copies it into `models/<name>.gguf` as a `.part` file and computes SHA-256 while copying, through
   a `DigestInputStream`.
4. The file is kept only when that digest is in `ModelStore.KNOWN_MODELS`, the default for the store's `known` set. Any
   other digest, or a read error, deletes the `.part` file; the file is deleted before anything parses it, so a swapped
   or damaged model never reaches the native parser.
5. A new model means adding its SHA-256 to `KNOWN_MODELS` in a reviewed change to that file, after the same hash has
   been confirmed off the phone. The sibling set `KNOWN_SENTENCE_MODELS` does the same for the sentence helper
   ([15-gloss-to-text.md](15-gloss-to-text.md)).

The committed set holds one hash, the Gemma 3 1B file listed with its value in
[10-on-device-assistant.md](10-on-device-assistant.md). The grep in section 1 prints the constant and its line number,
which is the shortest proof that the shipped allow-list is the one a department reviewed.

## 7. How a report is verified

An exported report carries one seal line, signed by a P-256 key in the phone's Android Keystore; [04-security.md](04-security.md)
section 2.10 says exactly what it proves and what it does not.

- **On the phone.** Settings, Records, Check a report picks an exported PDF and checks its seal against this phone's key;
  when the session is still stored it also re-walks the rows. The code is `app/src/main/java/app/signsetu/ui/settings/ReportCheck.kt`,
  and the verdict strings are in `app/src/main/res/values/strings.xml`. A report from another phone is valid but does
  not match this device.
- **Away from the phone.** `python tools/security/verify_seal.py --pdf report.pdf` checks the signature under the key
  the seal carries; `--qr` takes the QR text instead. Add `--key-fingerprint <sha256>` to require that key to be the one
  printed on the certificate. Exit 0 is valid, 1 is an invalid signature or a key mismatch, 2 is no seal or a file the
  tool cannot read. `--passphrase` opens only a report exported by a build before 0.8.52.
- **The two readers agreeing.** `python tools/security/seal_agreement.py --pdf report.pdf --app app.json` compares the
  app's verdict with the off-phone verdict over the same bytes: session, entry count, chain head, Merkle root,
  printed-rows digest, key fingerprint and the verdict itself. `app.json` is written by
  `app/src/androidTest/java/app/signsetu/SealAgreementDeviceTest.kt` on a phone or the emulator. Exit 0 means the two
  readers agree on every field, 1 means they disagree.

What to attach to a support report when a seal is in question is in [21-support.md](21-support.md).

## 8. What is not in the package

- **The signing key.** The keystore and both passwords are CI secrets. A department cannot sign a build of its own
  with them, and nothing in the release or this repository contains them.
- **The source history.** The source tree's git history is the source of truth for what was changed and when. The
  release carries built assets and generated notes from the commit subjects, not the commits or the sources.
- **Proof of the builder.** The bundle verifies what was built, not who built it: `apksigner` proves the certificate,
  the sums prove the bytes and the SBOM proves the dependency list, but no build attestation binds them to a person or
  a machine. The key attestation in [14-attestation-and-pinning.md](14-attestation-and-pinning.md) is about the phone
  and the gateway, not the release.
