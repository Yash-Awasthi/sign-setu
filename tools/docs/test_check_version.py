"""The version a document states is generated from the build file, never typed."""

import pathlib
import tempfile
import unittest
from unittest import mock

import check_version

GRADLE = (
    "android {\n"
    "    compileSdk = 37\n"
    "    defaultConfig {\n"
    "        minSdk = 29\n"
    "        targetSdk = 37\n"
    "        versionCode = 23\n"
    '        versionName = "0.8.51"\n'
    "    }\n"
    "}\n"
)


class CheckVersionTest(unittest.TestCase):
    def setUp(self):
        root = pathlib.Path(tempfile.mkdtemp())
        self.gradle = root / "build.gradle.kts"
        self.readme = root / "README.md"
        self.docs = root / "docs-README.md"
        self.gradle.write_text(GRADLE, encoding="utf-8")
        self.readme.write_text("# Sign Setu\n\n- Version 0.8.5 (version code 22). Old.\n\nMore.\n", encoding="utf-8")
        self.docs.write_text(
            "# Docs\n\n| Field | Value |\n| --- | --- |\n| Version | 0.8.5 (version code 22), applicationId `in.signsetu` |\n",
            encoding="utf-8",
        )
        self.current_docs()

    def current_docs(self):
        """The docs index shown with the build's version, so a test can break one document at a time."""
        text = self.docs.read_text(encoding="utf-8")
        values = check_version.build_values(self.gradle)
        self.docs.write_text(check_version.generated(text, values, check_version.ROW_START, check_version.render_row), encoding="utf-8")

    def check(self):
        return check_version.check(self.readme, self.gradle, self.docs)

    def test_both_stale_documents_fail(self):
        self.docs.write_text(self.docs.read_text(encoding="utf-8").replace("0.8.51", "0.8.5"), encoding="utf-8")
        self.assertEqual(2, len(self.check()))

    def test_write_generates_both_documents_from_the_build(self):
        with mock.patch.object(check_version, "README", self.readme), mock.patch.object(
            check_version, "DOCS_README", self.docs
        ), mock.patch.object(check_version, "GRADLE", self.gradle):
            self.assertEqual(0, check_version.main(["--write"]))
            self.assertEqual([], self.check())
        text = self.readme.read_text(encoding="utf-8")
        self.assertIn("0.8.51", text)
        self.assertIn("version code 23", text)
        self.assertIn("API 29", text)
        self.assertIn("\nMore.\n", text)
        self.assertIn("| Version | 0.8.51 (version code 23), applicationId `in.signsetu` |", self.docs.read_text(encoding="utf-8"))

    def test_the_exit_code_reports_drift(self):
        with mock.patch.object(check_version, "README", self.readme), mock.patch.object(
            check_version, "DOCS_README", self.docs
        ), mock.patch.object(check_version, "GRADLE", self.gradle):
            self.assertEqual(1, check_version.main([]))
            check_version.main(["--write"])
            self.assertEqual(0, check_version.main([]))

    def test_a_duplicated_version_field_is_an_error(self):
        self.gradle.write_text(self.gradle.read_text(encoding="utf-8") + 'android {\n    versionName = "9.9.9"\n}\n', encoding="utf-8")
        with self.assertRaises(ValueError):
            check_version.build_values(self.gradle)

    def test_a_document_without_a_version_line_is_an_error(self):
        self.readme.write_text("# Sign Setu\n", encoding="utf-8")
        errors = self.check()
        self.assertEqual(1, len(errors))
        self.assertIn("no '- Version ' line", errors[0])

    def test_the_repository_documents_match_the_repository_build(self):
        self.assertEqual([], check_version.check())


if __name__ == "__main__":
    unittest.main()
