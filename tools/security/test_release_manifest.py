"""The release evidence beside the APKs: sums that catch one changed byte, and a signing status read from the file."""
import hashlib
import json
import pathlib
import struct
import subprocess
import tempfile
import unittest
import zipfile

import release_manifest


def fake_apk(path: pathlib.Path, signed=False) -> pathlib.Path:
    """A zip that only has to look like an APK to the evidence reader."""
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("AndroidManifest.xml", b"\x03\x00\x08\x00" * 8)
        archive.writestr("classes.dex", b"dex\n035\x00" + b"\x00" * 60)
        if signed:
            archive.writestr("META-INF/CERT.RSA", b"\x30\x82" + b"\x01" * 40)
    return path


def add_signing_block(apk: pathlib.Path) -> pathlib.Path:
    """Insert an empty APK Signing Block where the format puts one: before the central directory."""
    data = apk.read_bytes()
    at = data.rfind(b"PK\x05\x06")
    offset = struct.unpack("<L", data[at + 16:at + 20])[0]
    size = 24  # the block's two size fields and its 16-byte magic, with no id-value pairs
    block = struct.pack("<Q", size) + struct.pack("<Q", size) + release_manifest.SIGNING_MAGIC
    moved = data[:offset] + block + data[offset:]
    at += len(block)
    moved = moved[:at + 16] + struct.pack("<L", offset + len(block)) + moved[at + 20:]
    apk.write_bytes(moved)
    return apk


def git(root: pathlib.Path, *args: str) -> str:
    result = subprocess.run(["git", "-C", str(root), "-c", "commit.gpgsign=false", *args],
                            capture_output=True, text=True, check=True)
    return result.stdout.strip()


def fake_sbom(path: pathlib.Path, components=2) -> pathlib.Path:
    doc = {
        "bomFormat": "CycloneDX",
        "specVersion": "1.5",
        "serialNumber": "urn:uuid:00000000-0000-0000-0000-000000000000",
        "version": 1,
        "metadata": {"tools": [{"vendor": "test", "name": "sbom_lockfile.py", "version": "1.0"}]},
        "components": [
            {"type": "library", "bom-ref": f"pkg:maven/g/a{i}@1.0", "name": f"a{i}", "version": "1.0",
             "purl": f"pkg:maven/g/a{i}@1.0"}
            for i in range(components)
        ],
    }
    path.write_text(json.dumps(doc), encoding="utf-8")
    return path


class Evidence(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = pathlib.Path(self.tmp.name)
        self.apk = fake_apk(self.dir / "sign-setu-9.9.9-arm64.apk")
        self.other = fake_apk(self.dir / "sign-setu-9.9.9-universal.apk")
        self.sbom = fake_sbom(pathlib.Path(self.tmp.name).parent / f"{self.tmp.name}.cdx.json")
        self.addCleanup(self.sbom.unlink)

    def tearDown(self):
        self.tmp.cleanup()

    def cut(self, commit="abc123"):
        code = release_manifest.write(self.dir, version="9.9.9", commit=commit,
                                      sbom=self.sbom, generated="2026-11-27T00:00:00Z")
        self.assertEqual(code, 0)
        return release_manifest.check(self.dir)

    def test_round_trip_writes_sums_and_provenance(self):
        self.assertEqual(self.cut(), [])
        lines = (self.dir / "SHA256SUMS").read_text(encoding="utf-8").splitlines()
        self.assertEqual(len(lines), 3)
        self.assertTrue(any(line.endswith("  sign-setu-release.cdx.json") for line in lines))
        doc = json.loads((self.dir / "PROVENANCE.json").read_text(encoding="utf-8"))
        self.assertEqual(doc["version"], "9.9.9")
        self.assertEqual(doc["commit"], "abc123")
        self.assertEqual(doc["generated"], "2026-11-27T00:00:00Z")
        self.assertEqual(len(doc["artefacts"]), 2)
        for artefact in doc["artefacts"]:
            self.assertEqual(artefact["signing"], "unsigned")
            self.assertFalse(artefact["shippable"])
            self.assertTrue(artefact["path"].endswith(artefact["name"]))
        self.assertEqual(doc["sbom"]["components"], 2)
        self.assertEqual(doc["sbom"]["producer"], "sbom_lockfile.py")
        self.assertEqual(doc["dependencyVerification"]["status"],
                         "present" if release_manifest.VERIFICATION.is_file() else "absent")
        if (release_manifest.ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties").is_file():
            self.assertRegex(doc["build"]["gradle"]["sha256"], r"^[0-9a-f]{64}$")
            self.assertTrue(doc["build"]["gradle"]["distribution"].startswith("https://"))
        self.assertIn("sha256sum -c", doc["verify"])

    def test_each_sum_matches_its_file(self):
        self.cut()
        raw = (self.dir / "SHA256SUMS").read_bytes()
        self.assertNotIn(b"\r", raw, "sha256sum -c reads the name and a CR makes the file unreadable")
        for line in raw.decode("utf-8").splitlines():
            digest, name = line.split("  ", 1)
            self.assertEqual(release_manifest.sha256_file(self.dir / name), digest)

    def test_one_changed_byte_fails(self):
        self.cut()
        self.apk.write_bytes(self.apk.read_bytes() + b"\x00")
        errors = release_manifest.check(self.dir)
        self.assertTrue(errors, "a changed APK must not check")
        self.assertIn("SHA-256", errors[0])
        self.assertIn(self.apk.name, errors[0])

    def test_a_changed_sbom_fails(self):
        self.cut()
        self.sbom_bytes = (self.dir / "sign-setu-release.cdx.json").read_bytes()
        (self.dir / "sign-setu-release.cdx.json").write_bytes(self.sbom_bytes.replace(b"a0", b"a9"))
        errors = release_manifest.check(self.dir)
        self.assertTrue(any("sign-setu-release.cdx.json" in error for error in errors))

    def test_an_apk_missing_from_the_sums_fails(self):
        self.cut()
        fake_apk(self.dir / "sign-setu-9.9.9-extra.apk")
        errors = release_manifest.check(self.dir)
        self.assertTrue(any("missing from SHA256SUMS" in error for error in errors))

    def test_signing_status_is_read_from_the_bytes(self):
        signed = fake_apk(self.dir / "sign-setu-9.9.9-signed.apk", signed=True)
        self.assertEqual(release_manifest.apk_signing(signed), "signed")
        self.assertEqual(release_manifest.apk_signing(self.apk), "unsigned")
        self.cut()
        doc = json.loads((self.dir / "PROVENANCE.json").read_text(encoding="utf-8"))
        status = {row["name"]: row for row in doc["artefacts"]}
        self.assertTrue(status[signed.name]["shippable"])

    def test_replacing_a_signed_apk_with_an_unsigned_one_fails(self):
        signed = fake_apk(self.dir / "sign-setu-9.9.9-signed.apk", signed=True)
        self.cut()
        fake_apk(signed)  # same name, no certificate now
        errors = release_manifest.check(self.dir)
        self.assertTrue(any("signing is unsigned" in error for error in errors))
        self.assertTrue(any("shippable" in error for error in errors))

    def test_a_changed_verification_metadata_fails(self):
        if not release_manifest.VERIFICATION.is_file():
            self.skipTest("no gradle/verification-metadata.xml on this machine")
        self.cut(commit=release_manifest.commit_sha())
        path = self.dir / "PROVENANCE.json"
        doc = json.loads(path.read_text(encoding="utf-8"))
        doc["dependencyVerification"]["sha256"] = "0" * 64
        path.write_text(json.dumps(doc), encoding="utf-8")
        errors = release_manifest.check(self.dir)
        self.assertTrue(any("verification-metadata.xml" in error for error in errors))

    def test_a_record_from_another_commit_is_read_at_that_commit(self):
        if not release_manifest.VERIFICATION.is_file():
            self.skipTest("no gradle/verification-metadata.xml on this machine")
        self.cut(commit="0" * 40)  # git does not have this commit, so the working tree is the source
        self.assertEqual(release_manifest.check(self.dir), [])

    def test_a_recorded_lockfile_digest_is_read_from_the_bytes(self):
        self.cut()
        path = self.dir / "PROVENANCE.json"
        doc = json.loads(path.read_text(encoding="utf-8"))
        doc["dependencyLocks"][0]["sha256"] = "0" * 64
        path.write_text(json.dumps(doc), encoding="utf-8")
        errors = release_manifest.check(self.dir)
        self.assertTrue(any("app/gradle.lockfile" in error and "SHA-256" in error for error in errors), errors)

    def test_a_recorded_gradle_pin_is_checked_against_the_wrapper(self):
        if not release_manifest.gradle_wrapper():
            self.skipTest("no gradle wrapper properties on this machine")
        self.cut()
        path = self.dir / "PROVENANCE.json"
        doc = json.loads(path.read_text(encoding="utf-8"))
        doc["build"]["gradle"]["sha256"] = "0" * 64
        path.write_text(json.dumps(doc), encoding="utf-8")
        errors = release_manifest.check(self.dir)
        self.assertTrue(any("Gradle sha256" in error for error in errors), errors)

    def test_a_component_without_a_purl_fails(self):
        self.cut()
        path = self.dir / "sign-setu-release.cdx.json"
        doc = json.loads(path.read_text(encoding="utf-8"))
        del doc["components"][0]["purl"]
        path.write_text(json.dumps(doc), encoding="utf-8")
        errors = release_manifest.check(self.dir)
        self.assertTrue(any("has no purl" in error for error in errors), errors)

    def test_a_spec_version_the_record_does_not_declare_fails(self):
        self.cut()
        path = self.dir / "sign-setu-release.cdx.json"
        doc = json.loads(path.read_text(encoding="utf-8"))
        doc["specVersion"] = "1.6"
        path.write_text(json.dumps(doc), encoding="utf-8")
        errors = release_manifest.check(self.dir)
        self.assertTrue(any("specVersion is 1.6" in error for error in errors), errors)

    def test_a_rewritten_sbom_is_caught_even_when_the_hashes_agree(self):
        """The SBOM is re-derived from the lockfile at the recorded commit, so re-hashing one does not pass."""
        with tempfile.TemporaryDirectory() as repo, tempfile.TemporaryDirectory() as out:
            root = pathlib.Path(repo)
            lock = root / "app" / "gradle.lockfile"
            lock.parent.mkdir()
            lock.write_text("g:a:1.0=releaseRuntimeClasspath\n", encoding="utf-8")
            (root / "settings-gradle.lockfile").write_text("", encoding="utf-8")
            git(root, "init", "-q")
            git(root, "add", "app/gradle.lockfile", "settings-gradle.lockfile")
            git(root, "-c", "user.name=t", "-c", "user.email=t@example.com", "commit", "-qm", "one")
            commit = git(root, "rev-parse", "HEAD")
            saved = (release_manifest.ROOT, release_manifest.LOCK, release_manifest.SETTINGS_LOCK,
                     release_manifest.VERIFICATION, release_manifest.SBOM_BUILD)
            release_manifest.ROOT, release_manifest.LOCK = root, lock
            release_manifest.SETTINGS_LOCK = root / "settings-gradle.lockfile"
            release_manifest.VERIFICATION = root / "gradle" / "verification-metadata.xml"
            release_manifest.SBOM_BUILD = root / "missing.cdx.json"
            try:
                directory = pathlib.Path(out)
                fake_apk(directory / "sign-setu-0.8.52-arm64.apk")
                self.assertEqual(release_manifest.write(directory, version="0.8.52", commit=commit,
                                                        generated="2026-11-26T19:14:56Z"), 0)
                self.assertEqual(release_manifest.check(directory), [])
                sbom = directory / "sign-setu-release.cdx.json"
                doc = json.loads(sbom.read_text(encoding="utf-8"))
                doc["components"][0]["version"] = "9.9.9"
                sbom.write_text(json.dumps(doc, indent=2, sort_keys=True) + "\n", encoding="utf-8")
                digest = release_manifest.sha256_file(sbom)
                sums = directory / "SHA256SUMS"
                rewritten = []
                for line in sums.read_text(encoding="utf-8").splitlines():
                    _, name = line.split("  ", 1)
                    rewritten.append(f"{digest}  {name}" if name == release_manifest.SBOM_NAME else line)
                sums.write_text("\n".join(rewritten) + "\n", encoding="utf-8")
                path = directory / "PROVENANCE.json"
                provenance = json.loads(path.read_text(encoding="utf-8"))
                provenance["sbom"]["sha256"] = digest
                provenance["sbom"]["bytes"] = sbom.stat().st_size
                path.write_text(json.dumps(provenance), encoding="utf-8")
                errors = release_manifest.check(directory)
                self.assertTrue(any("not what sbom_lockfile.py writes" in error for error in errors), errors)
            finally:
                (release_manifest.ROOT, release_manifest.LOCK, release_manifest.SETTINGS_LOCK,
                 release_manifest.VERIFICATION, release_manifest.SBOM_BUILD) = saved

    def test_a_digest_is_read_at_the_recorded_commit_not_at_head(self):
        with tempfile.TemporaryDirectory() as repo:
            root = pathlib.Path(repo)
            path = root / "app" / "gradle.lockfile"
            path.parent.mkdir()
            path.write_bytes(b"one=1\n")
            git(root, "init", "-q")
            git(root, "add", "app/gradle.lockfile")
            git(root, "-c", "user.name=t", "-c", "user.email=t@example.com", "commit", "-qm", "one")
            first = git(root, "rev-parse", "HEAD")
            path.write_bytes(b"two=2\n")
            git(root, "-c", "user.name=t", "-c", "user.email=t@example.com", "commit", "-qam", "two")
            self.assertEqual(release_manifest.digest_at(path, first, root=root),
                             (hashlib.sha256(b"one=1\n").hexdigest(), f"commit {first[:12]}"))
            self.assertEqual(release_manifest.digest_at(path, "0" * 40, root=root)[0],
                             hashlib.sha256(b"two=2\n").hexdigest())

    def test_a_signing_block_before_the_central_directory_is_signed(self):
        self.assertEqual(release_manifest.apk_signing(self.apk), "unsigned")
        add_signing_block(self.apk)
        self.assertEqual(release_manifest.apk_signing(self.apk), "signed")
        with zipfile.ZipFile(self.apk) as archive:
            self.assertIn("classes.dex", archive.namelist())

    def test_the_signing_magic_outside_the_block_is_not_a_signature(self):
        self.apk.write_bytes(self.apk.read_bytes() + release_manifest.SIGNING_MAGIC)
        self.assertEqual(release_manifest.apk_signing(self.apk), "unsigned")

    def test_a_missing_sums_file_is_bad_input_not_a_mismatch(self):
        self.cut()
        (self.dir / "SHA256SUMS").unlink()
        with self.assertRaises(release_manifest.EvidenceError):
            release_manifest.check(self.dir)
        self.assertEqual(release_manifest.main([str(self.dir), "--check"]), 2)

    def test_a_malformed_sum_line_is_bad_input(self):
        self.cut()
        (self.dir / "SHA256SUMS").write_text("not a sum\n", encoding="utf-8")
        with self.assertRaises(release_manifest.EvidenceError):
            release_manifest.check(self.dir)

    def test_absent_apks_are_reported_not_failed(self):
        self.cut()
        self.apk.unlink()
        self.assertEqual(release_manifest.check(self.dir), [])

    def test_main_exit_codes(self):
        self.assertEqual(release_manifest.main([str(self.dir), "--version", "9.9.9",
                                                "--commit", "abc123", "--sbom", str(self.sbom),
                                                "--generated", "2026-11-27T00:00:00Z"]), 0)
        self.assertEqual(release_manifest.main([str(self.dir), "--check"]), 0)
        self.other.write_bytes(self.other.read_bytes() + b"\x00")
        self.assertEqual(release_manifest.main([str(self.dir), "--check"]), 1)

    def test_an_empty_directory_is_bad_input(self):
        with tempfile.TemporaryDirectory() as empty:
            self.assertEqual(release_manifest.main([empty, "--version", "9.9.9"]), 2)

    def test_the_committed_evidence_in_the_repository_matches(self):
        root = release_manifest.ROOT / "release"
        directories = sorted(p for p in root.glob("*") if p.is_dir())
        if not directories:
            self.skipTest("no committed release evidence yet")
        for directory in directories:
            with self.subTest(directory=directory.name):
                self.assertEqual(release_manifest.check(directory), [])


if __name__ == "__main__":
    unittest.main()
