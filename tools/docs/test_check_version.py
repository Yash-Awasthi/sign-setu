"""The README's version line is generated from the build file, never typed."""

import pathlib
import tempfile
import unittest
from unittest import mock

import check_version


class CheckVersionTest(unittest.TestCase):
    def setUp(self):
        self.dir = pathlib.Path(tempfile.mkdtemp())
        self.gradle = self.dir / "build.gradle.kts"
        self.readme = self.dir / "README.md"
        self.gradle.write_text(
            "android {\n"
            "    compileSdk = 37\n"
            "    defaultConfig {\n"
            "        minSdk = 29\n"
            "        targetSdk = 37\n"
            "        versionCode = 23\n"
            '        versionName = "0.8.51"\n'
            "    }\n"
            "}\n",
            encoding="utf-8",
        )
        self.readme.write_text("# Sign Setu\n\n- Version 0.8.5 (version code 22). Old.\n\nMore.\n", encoding="utf-8")

    def test_a_stale_version_line_fails(self):
        self.assertEqual(1, len(check_version.check(self.readme, self.gradle)))

    def test_write_generates_the_line_from_the_build(self):
        values = check_version.build_values(self.gradle)
        self.readme.write_text(
            check_version.generated(self.readme.read_text(encoding="utf-8"), values), encoding="utf-8"
        )
        self.assertEqual([], check_version.check(self.readme, self.gradle))
        text = self.readme.read_text(encoding="utf-8")
        self.assertIn("0.8.51", text)
        self.assertIn("version code 23", text)
        self.assertIn("API 29", text)
        self.assertIn("\nMore.\n", text)

    def test_main_reports_a_mismatch_and_write_fixes_it(self):
        with mock.patch.object(check_version, "README", self.readme), mock.patch.object(
            check_version, "GRADLE", self.gradle
        ):
            self.assertEqual(1, check_version.main([]))
            self.assertEqual(0, check_version.main(["--write"]))
            self.assertEqual(0, check_version.main([]))

    def test_a_duplicated_version_field_is_an_error(self):
        self.gradle.write_text(
            self.gradle.read_text(encoding="utf-8") + 'android {\n    versionName = "9.9.9"\n}\n',
            encoding="utf-8",
        )
        with self.assertRaises(ValueError):
            check_version.build_values(self.gradle)

    def test_a_readme_without_a_version_line_is_an_error(self):
        self.readme.write_text("# Sign Setu\n", encoding="utf-8")
        self.assertEqual(["README.md: no '- Version ' line"], check_version.check(self.readme, self.gradle))

    def test_the_repository_readme_matches_the_repository_build(self):
        self.assertEqual([], check_version.check())


if __name__ == "__main__":
    unittest.main()
