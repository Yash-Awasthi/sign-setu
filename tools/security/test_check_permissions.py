"""The permission table in the docs is generated from the manifests, and the prose names them all."""

import pathlib
import shutil
import tempfile
import unittest

import check_permissions

MAIN = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.WAKE_LOCK" tools:node="remove" />
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
</manifest>
"""

NEARBY = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" tools:remove="android:usesPermissionFlags" />
</manifest>
"""

DOCS = """# Docs

| Permissions (standard) | `CAMERA`, `BLUETOOTH` up to API 30, `BLUETOOTH_SCAN` never for location |

| Permission | `standard` | `nearby` |
| --- | --- | --- |
"""


class CheckPermissionsTest(unittest.TestCase):
    def setUp(self):
        root = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, root, ignore_errors=True)
        (root / "main.xml").write_text(MAIN, encoding="utf-8")
        (root / "nearby.xml").write_text(NEARBY, encoding="utf-8")
        self.main = root / "main.xml"
        self.flavors = {"standard": root / "standard.xml", "nearby": root / "nearby.xml"}
        self.docs = root / "README.md"
        self.docs.write_text(DOCS, encoding="utf-8")

    def check(self):
        return check_permissions.check(self.main, self.flavors, self.docs)

    def test_the_table_is_generated_from_both_manifests(self):
        lines = check_permissions.table(self.main, self.flavors)
        self.assertEqual(
            [
                "| Permission | `standard` | `nearby` |",
                "| --- | --- | --- |",
                "| `CAMERA` | yes | yes |",
                "| `BLUETOOTH` | yes, API ≤ 30 | yes, API ≤ 30 |",
                "| `BLUETOOTH_SCAN` | yes, neverForLocation | yes, no flag |",
                "| `ACCESS_FINE_LOCATION` | — | yes, API ≤ 32 |",
            ],
            lines,
        )

    def test_a_refused_permission_is_not_listed(self):
        self.assertNotIn("WAKE_LOCK", "\n".join(check_permissions.table(self.main, self.flavors)))

    def test_an_empty_table_fails(self):
        self.assertTrue(any("the permission table is stale" in e for e in self.check()), self.check())

    def test_a_generated_table_passes(self):
        check_permissions.write(self.main, self.flavors, self.docs)
        self.assertEqual([], self.check())

    def test_a_permission_missing_from_the_prose_fails(self):
        check_permissions.write(self.main, self.flavors, self.docs)
        self.docs.write_text(self.docs.read_text(encoding="utf-8").replace("`CAMERA`, ", ""), encoding="utf-8")
        errors = self.check()
        self.assertTrue(any("CAMERA" in e for e in errors), errors)

    def test_a_new_permission_breaks_the_docs_until_it_is_written(self):
        check_permissions.write(self.main, self.flavors, self.docs)
        self.main.write_text(MAIN.replace("<uses-permission", '<uses-permission android:name="android.permission.VIBRATE" />\n    <uses-permission', 1), encoding="utf-8")
        self.assertTrue(any("the permission table is stale" in e for e in self.check()), self.check())
        check_permissions.write(self.main, self.flavors, self.docs)
        self.assertIn("| `VIBRATE` | yes | yes |", self.docs.read_text(encoding="utf-8"))

    def test_a_permission_nobody_approved_is_refused(self):
        errors = check_permissions.check_approved(set(check_permissions.APPROVED) | {"SEND_SMS"})
        self.assertTrue(any("SEND_SMS" in e and "APPROVED" in e for e in errors), errors)
        self.assertFalse(any("strike them out" in e for e in errors), errors)

    def test_an_approved_permission_the_manifests_dropped_is_reported(self):
        errors = check_permissions.check_approved({"CAMERA"})
        self.assertTrue(any("CAMERA" not in e and "no longer declare" in e for e in errors), errors)

    def test_the_repository_manifests_are_all_approved(self):
        self.assertEqual([], check_permissions.check_approved(check_permissions.declared_everywhere()))

    def test_every_approved_permission_says_why(self):
        for name, reason in check_permissions.APPROVED.items():
            self.assertTrue(reason.strip(), name)
            self.assertGreater(len(reason), 20, f"{name}: say what it is for, not just that it is used")

    def test_the_repository_documents_match_the_repository_manifests(self):
        self.assertEqual([], check_permissions.check())


if __name__ == "__main__":
    unittest.main()
