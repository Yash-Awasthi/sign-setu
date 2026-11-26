"""The posture gate fails on a manifest that asks for too much or ships a secret, and passes the
good fixture; the repository's own manifest is held to the same rules."""

import pathlib
import shutil
import tempfile
import unittest

import check_android_posture as posture

GOOD = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <uses-permission android:name="android.permission.CAMERA" />
    <application
        android:allowBackup="false"
        android:fullBackupContent="@xml/backup_rules"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:networkSecurityConfig="@xml/network_security_config">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <service
            android:name=".data.session.RetentionJob"
            android:exported="false"
            android:permission="android.permission.BIND_JOB_SERVICE" />
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.exports"
            android:exported="false"
            android:grantUriPermissions="true" />
    </application>
</manifest>
"""

BAD = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <application android:usesCleartextTraffic="true">
        <activity android:name=".LeakyActivity" android:exported="true" />
        <receiver android:name=".LooseReceiver" android:exported="true" />
    </application>
</manifest>
"""

CODE = "package app.signsetu\n\nval camera = Manifest.permission.CAMERA\n"
SECRET = 'package app.signsetu\n\nval apiKey = "sk-abcdefghijklmnopqrstuvwxyz"\n'

RULES = """<?xml version="1.0" encoding="utf-8"?>
<full-backup-content><exclude domain="file" path="." /></full-backup-content>
"""
EXTRACTION = """<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules><cloud-backup><exclude domain="file" path="." /></cloud-backup></data-extraction-rules>
"""
NETWORK = """<?xml version="1.0" encoding="utf-8"?>
<network-security-config><base-config cleartextTrafficPermitted="false" /></network-security-config>
"""


class PostureTest(unittest.TestCase):
    def setUp(self):
        self.root = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root, ignore_errors=True)
        self.main = self.root / "app" / "src" / "main"
        (self.main / "java").mkdir(parents=True)
        (self.main / "res" / "xml").mkdir(parents=True)
        self.write_rules()

    def write_rules(self):
        (self.main / "res" / "xml" / "backup_rules.xml").write_text(RULES, encoding="utf-8")
        (self.main / "res" / "xml" / "data_extraction_rules.xml").write_text(EXTRACTION, encoding="utf-8")
        (self.main / "res" / "xml" / "network_security_config.xml").write_text(NETWORK, encoding="utf-8")

    def fixture(self, manifest=GOOD, code=CODE):
        (self.main / "AndroidManifest.xml").write_text(manifest, encoding="utf-8")
        if code:
            (self.main / "java" / "Code.kt").write_text(code, encoding="utf-8")
        return posture.check(self.main / "AndroidManifest.xml", self.main / "java",
                             secret_roots=[self.main / "java"])

    def test_the_good_fixture_passes(self):
        errors, _ = self.fixture()
        self.assertEqual([], errors)

    def test_every_rule_fires_on_the_bad_fixture(self):
        errors, _ = self.fixture(BAD, code=SECRET)
        joined = "\n".join(errors)
        for wanted in ("usesCleartextTraffic", "allowBackup is true", "LeakyActivity",
                       "LooseReceiver", "CAMERA", "RECORD_AUDIO", "hardcoded apiKey"):
            self.assertIn(wanted, joined, errors)

    def test_a_dangerous_permission_named_only_in_a_comment_fails(self):
        errors, _ = self.fixture(code="// Manifest.permission.CAMERA is asked for elsewhere\n")
        self.assertTrue(any("CAMERA" in error and "no code" in error for error in errors), errors)

    def test_a_cleartext_domain_fails(self):
        (self.main / "res" / "xml" / "network_security_config.xml").write_text(
            """<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">example.test</domain>
    </domain-config>
</network-security-config>
""",
            encoding="utf-8",
        )
        errors, _ = self.fixture()
        self.assertTrue(any("example.test" in error and "cleartext" in error for error in errors), errors)

    def test_a_named_but_missing_backup_rule_fails(self):
        errors, _ = self.fixture(
            manifest=GOOD.replace('android:allowBackup="false"', 'android:allowBackup="true"')
            .replace("backup_rules", "absent_rules")
        )
        self.assertTrue(any("absent_rules" in error for error in errors), errors)

    def test_an_exported_component_needs_a_permission_or_a_reason(self):
        (self.main / "AndroidManifest.xml").write_text(BAD, encoding="utf-8")
        reasons = {"LeakyActivity": "a test fixture reason"}
        errors, _ = posture.check(self.main / "AndroidManifest.xml", self.main / "java",
                                  secret_roots=[self.main / "java"], reasons=reasons)
        # BAD also has RECORD_AUDIO and cleartext; only the export lines are in question.
        self.assertFalse(any("LeakyActivity" in error for error in errors), errors)
        self.assertTrue(any("LooseReceiver" in error for error in errors), errors)

    def test_an_intent_filter_without_exported_fails(self):
        errors, _ = self.fixture(manifest=GOOD.replace(' android:exported="true"', ""))
        self.assertTrue(any("has an intent filter but no android:exported" in error for error in errors), errors)

    def test_a_removed_component_is_not_reported(self):
        (self.main / "AndroidManifest.xml").write_text(
            GOOD.replace(
                '<service\n',
                '<receiver android:name="androidx.profileinstaller.ProfileInstallReceiver" tools:node="remove" />\n        <service\n',
                1,
            ),
            encoding="utf-8",
        )
        lines = posture.report(self.main / "AndroidManifest.xml", self.main / "java")
        self.assertFalse(any("ProfileInstallReceiver" in line for line in lines), lines)

    def test_a_high_entropy_literal_is_reported(self):
        errors, _ = self.fixture(code='package app.signsetu\n\nval blob = "Zx9Qm2Lp7Rt4Vw8Yb3Nc6Kd1Hf5Jg0Sa"\n')
        self.assertTrue(any("high-entropy" in error for error in errors), errors)

    def test_the_repository_manifests_pass(self):
        errors, _ = posture.check_all()
        self.assertEqual([], errors)

    def test_the_release_build_forces_secure_screen(self):
        self.assertEqual([], posture.secure_screen_errors())

    def test_a_release_block_without_the_secure_screen_override_fails(self):
        text = pathlib.Path(posture.GRADLE).read_text(encoding="utf-8")
        copy = self.root / "build.gradle.kts"
        copy.write_text(text, encoding="utf-8")
        self.assertEqual([], posture.secure_screen_errors(copy))
        stripped = text.replace('buildConfigField("boolean", "SECURE_SCREEN", "true")', "", 1)
        self.assertNotEqual(text, stripped)
        copy.write_text(stripped, encoding="utf-8")
        errors = posture.secure_screen_errors(copy)
        self.assertTrue(any("SECURE_SCREEN" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
