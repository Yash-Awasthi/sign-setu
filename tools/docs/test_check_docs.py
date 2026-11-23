"""The documents are checked against the tree: links, screenshots, the map, named files, counts."""

import pathlib
import shutil
import tempfile
import unittest
from unittest import mock

import check_docs

LANGUAGES = '{"languages": [' + ", ".join('{"code": "l%d"}' % i for i in range(17)) + "]}"
ISL = '{"labels": [' + ", ".join('"w%d"' % i for i in range(314)) + "]}"
ASL = '{"labels": [' + ", ".join('"w%d"' % i for i in range(250)) + "]}"
SETTINGS = "object Keys {\n    const val DEFAULT_RETENTION_HOURS = 24\n    val RETENTION_CHOICES = listOf(0, 4, 8, 12, 24, 48, 72)\n}\n"


class CheckDocsTest(unittest.TestCase):
    """A miniature repository: two docs, one screenshot, a bundle and the settings code."""

    def setUp(self):
        self.root = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root, ignore_errors=True)
        (self.root / "docs" / "screens").mkdir(parents=True)
        (self.root / "assets" / "models").mkdir(parents=True)
        (self.root / "app").mkdir()
        (self.root / "assets" / "languages.json").write_text(LANGUAGES, encoding="utf-8")
        (self.root / "assets" / "models" / "sign_reader.json").write_text(ISL, encoding="utf-8")
        (self.root / "assets" / "models" / "asl_reader.json").write_text(ASL, encoding="utf-8")
        (self.root / "app" / "SettingsStore.kt").write_text(SETTINGS, encoding="utf-8")
        (self.root / "docs" / "screens" / "01-home.png").write_bytes(b"png")
        self.readme().write_text(
            "# Sign Setu\n\n17 languages, ISL: 314 words, ASL (experimental): 250 words, 4 to 72 hours, "
            "deleted 24 hours after its last activity by default.\n",
            encoding="utf-8",
        )
        (self.root / "docs" / "README.md").write_text("[Screens](12-screens.md)\n\nLanguages (17)\n", encoding="utf-8")
        self.screens_doc().write_text("[Home](screens/01-home.png)\n", encoding="utf-8")
        self.patch()

    def readme(self) -> pathlib.Path:
        return self.root / "README.md"

    def screens_doc(self) -> pathlib.Path:
        return self.root / "docs" / "12-screens.md"

    def patch(self):
        for name, target in (
            ("ROOT", self.root),
            ("DOCS", self.root / "docs"),
            ("README", self.readme()),
            ("SCREENS_DOC", self.screens_doc()),
            ("DOCS_MAP", self.root / "docs" / "README.md"),
            ("ASSETS", self.root / "assets"),
            ("SETTINGS", self.root / "app" / "SettingsStore.kt"),
        ):
            patcher = mock.patch.object(check_docs, name, target)
            patcher.start()
            self.addCleanup(patcher.stop)

    def test_the_fixture_is_clean(self):
        self.assertEqual([], check_docs.check())

    def test_a_dead_link_fails(self):
        self.screens_doc().write_text("[Gone](screens/99-gone.png)\n", encoding="utf-8")
        errors = check_docs.check()
        self.assertTrue(any("99-gone.png" in e for e in errors), errors)

    def test_a_capture_described_nowhere_fails(self):
        (self.root / "docs" / "screens" / "02-new.png").write_bytes(b"png")
        errors = check_docs.check()
        self.assertTrue(any("02-new.png" in e for e in errors), errors)

    def test_a_document_missing_from_the_map_fails(self):
        (self.root / "docs" / "20-new.md").write_text("# New\n", encoding="utf-8")
        errors = check_docs.check()
        self.assertTrue(any("20-new.md" in e for e in errors), errors)

    def test_a_named_file_that_does_not_exist_fails(self):
        self.readme().write_text(
            self.readme().read_text(encoding="utf-8") + "Retention runs in `app/Retention.kt`.\n",
            encoding="utf-8",
        )
        errors = check_docs.check()
        self.assertTrue(any("Retention.kt" in e for e in errors), errors)

    def test_a_stale_count_fails(self):
        self.readme().write_text(self.readme().read_text(encoding="utf-8").replace("314 words", "315 words"), encoding="utf-8")
        errors = check_docs.check()
        self.assertTrue(any("ISL: 314 words" in e for e in errors), errors)


if __name__ == "__main__":
    unittest.main()
