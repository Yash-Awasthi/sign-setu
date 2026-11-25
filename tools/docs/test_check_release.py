"""The changelog and the device matrix follow the build file."""

import pathlib
import shutil
import tempfile
import unittest

import check_release

GRADLE = (
    "android {\n"
    "    compileSdk = 37\n"
    "    defaultConfig {\n"
    "        minSdk = 29\n"
    "        targetSdk = 37\n"
    "        versionCode = 24\n"
    '        versionName = "0.8.52"\n'
    "    }\n"
    "}\n"
)

CHANGELOG = """# Changelog

## [0.8.52] - 2026-11-26

- A current change.

## [0.8.5] - 2026-11-04

- An older change.
"""

MATRIX = "\n".join(f"| {api} | Android | Supported | None |" for api in range(29, 38))


class CheckReleaseTest(unittest.TestCase):
    def setUp(self):
        self.root = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root, ignore_errors=True)
        self.gradle = self.root / "build.gradle.kts"
        self.changelog = self.root / "CHANGELOG.md"
        self.policy = self.root / "RELEASE.md"
        self.gradle.write_text(GRADLE, encoding="utf-8")
        self.changelog.write_text(CHANGELOG, encoding="utf-8")
        self.policy.write_text("# Release policy\n\n| API | Android | Position | Evidence |\n| --- | --- | --- | --- |\n" + MATRIX + "\n", encoding="utf-8")

    def check(self):
        return check_release.check(self.changelog, self.policy, self.gradle)

    def test_the_fixture_is_clean(self):
        self.assertEqual([], self.check())

    def test_a_stale_newest_section_fails(self):
        self.changelog.write_text(CHANGELOG.replace("0.8.52", "0.8.51", 1), encoding="utf-8")
        errors = self.check()
        self.assertTrue(any("newest section is 0.8.51" in e for e in errors), errors)

    def test_an_empty_section_fails(self):
        self.changelog.write_text(CHANGELOG.replace("- A current change.", ""), encoding="utf-8")
        errors = self.check()
        self.assertTrue(any("section 0.8.52 has no entry" in e for e in errors), errors)

    def test_sections_out_of_order_fail(self):
        self.changelog.write_text(CHANGELOG.replace("[0.8.5]", "[0.9.0]"), encoding="utf-8")
        errors = self.check()
        self.assertTrue(any("is not older" in e for e in errors), errors)

    def test_a_missing_api_row_fails(self):
        self.policy.write_text(self.policy.read_text(encoding="utf-8").replace("| 33 | Android | Supported | None |\n", ""), encoding="utf-8")
        errors = self.check()
        self.assertTrue(any("API 33" in e for e in errors), errors)

    def test_the_repository_changelog_and_matrix_match_the_build(self):
        self.assertEqual([], check_release.check())


if __name__ == "__main__":
    unittest.main()