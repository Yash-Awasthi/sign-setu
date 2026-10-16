# 11. Secure Development and Release

Implements the CERT-In Guidelines for Secure Application Design, Development, Implementation and Operations (2024): secure SDLC, version control and change management, security test-driven development, SAST/DAST, SBOM, secure build environment.

## 1. Repository

- One Git repository for the app; one for the gateway.
- `main` is protected: pull request required, at least one approving review from someone other than the author, all checks green, no force push.
- Signed commits recommended for maintainers.
- Multi-factor authentication required for every account with write access (CERT-In app guidelines 4.10).
- No secrets in the repository. Secret scanning enabled; a leaked secret is rotated, not just deleted.

## 2. Branches and versions

- Short-lived feature branches from `main`; merge by squash.
- Version `MAJOR.MINOR.PATCH`; `versionCode` increases with every Play upload.
- Release branches `release/x.y` cut at code freeze; only fixes go in.
- Tags `vX.Y.Z` on the exact commit built for release.

## 3. Continuous integration

Every pull request runs:

1. Build debug and release variants.
2. Unit tests and Compose UI tests.
3. Android Lint with security checks as errors.
4. Static analysis (Semgrep with Kotlin/Android rules or MobSF static scan).
5. Dependency vulnerability scan: `osv-scanner scan source --lockfile app/gradle.lockfile`; fail on critical or high in anything that ships (the release runtime classpath).
6. Gradle dependency verification (`gradle/verification-metadata.xml`) and locking (`app/gradle.lockfile`) must pass; after a deliberate dependency change, regenerate both with `--write-verification-metadata sha256` and `--write-locks` and review the diff.
7. Secret scan.

Release builds additionally:

8. Generate the CycloneDX SBOM of shipped components: `python tools/security/sbom.py <path to syft>`.
9. Record SHA-256 of the AAB and APKs.
10. Archive the SBOM, hashes, test report and scan results as the release record.

CI runners use least-privilege tokens; signing credentials are not available to pull-request builds.

## 4. Dependencies

- Add a dependency only when the platform or an existing dependency cannot do the job. Record the reason in the pull request.
- Check licence compatibility (see [copyright and licensing policy](policies/copyright-licensing-policy.md)) and maintenance status before adding.
- Pin versions; update monthly, and immediately for security advisories.

## 5. Signing and keys

- The release signing key is generated offline and held encrypted by the release manager with a second custodian; an offline backup stays in department custody. Losing it means installed apps cannot be updated, so the backup is mandatory.
- Gateway TLS certificate pins: ship a backup pin in every release; rotate before expiry (see [security architecture](../archive/06-security-architecture.md) §3).

## 6. Release process

| Step | Who | Evidence |
| --- | --- | --- |
| 1. Code freeze, release branch | Tech lead | Branch |
| 2. Full test plan on device matrix | QA lead | Test report |
| 3. Security checks ST-01 to ST-13 | Security lead | Security report |
| 4. Content approval for changed content | Content owner | Approval record |
| 5. Audit decision: does the change need re-audit? | Security lead | Decision in release record |
| 6. Build, sign, generate SBOM and hashes | Release manager | Release record |
| 7. Pilot group through MDM or direct signed APK | Release manager | Distribution record |
| 8. Department approval | Department | Approval |
| 9. Staged rollout by district; raise minimum version at gateway when needed | Release manager | Distribution record |

After the CERT-In audit, the production build must be the audited build. Any later change follows change control (section 7) and the audit decision in step 5.

## 7. Change management

| Change type | Examples | Approval | Re-audit |
| --- | --- | --- | --- |
| Content only | New translation, new card | Content owner | No |
| Minor code | Bug fix, UI text | Tech lead + reviewer | Security lead decides |
| Security-relevant code | Storage, network, crypto, permissions, new data flow | Tech lead + security lead | Yes, scoped |
| Architecture or new data | New service, new data type | Project lead + department, DPIA update | Yes |
| Emergency security fix | Actively exploited issue | Security lead, then retrospective review | Follow-up audit |

## 8. Distribution

- Primary channel: Mobile Seva AppStore (MeitY, C-DAC), free for government departments; only signed, verified APKs accepted.
- Department MDM or direct signed APK download from a gov.in site as secondary channels; publish the SHA-256 of every APK. Data disclosures in any listing match the [DPIA](08-privacy-dpia.md) and [privacy notice](policies/privacy-notice.md) exactly.
- Target SDK kept current with Android releases even without Play enforcement (SEC-55).
- Store listing states that the app is for authorised police personnel and is an aid, not an interpreter.
