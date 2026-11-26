"""The release evidence beside the APKs: sums that catch one changed byte, and a signing status read from the file."""
import json
import pathlib
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


def fake_sbom(path: pathlib.Path, components=2) -> pathlib.Path:
    doc = {
        "bomFormat": "CycloneDX",
        "specVersion": "1.5",
        "serialNumber": "urn:uuid:00000000-0000-0000-0000-000000000000",
        "version": 1,
        "metadata": {"tools": [{"vendor": "test", "name": "sbom_lockfile.py", "version": "1.0"}]},
        "components": [
            {"type": "library", "bom-ref": f"pkg:maven/g/a{i}@1.0", "name": f"a{i}", "version": "1.0"}
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

    def test_a_record_from_another_commit_is_not_held_to_the_live_file(self):
        if not release_manifest.VERIFICATION.is_file():
            self.skipTest("no gradle/verification-metadata.xml on this machine")
        self.cut(commit="0" * 40)
        self.assertEqual(release_manifest.check(self.dir), [])

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
