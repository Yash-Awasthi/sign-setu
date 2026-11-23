"""The baseline a phone must meet is generated from the build file and the code that enforces it."""

import pathlib
import shutil
import tempfile
import unittest
from unittest import mock

import baseline

GRADLE = (
    "android {\n"
    "    compileSdk = 37\n"
    "    defaultConfig {\n"
    "        minSdk = 29\n"
    "        targetSdk = 37\n"
    "        versionCode = 23\n"
    '        versionName = "0.8.51"\n'
    "        externalNativeBuild { cmake { abiFilters += listOf(\"arm64-v8a\", \"x86_64\"); arguments += \"-DCMAKE_BUILD_TYPE=Release\" } }\n"
    "    }\n"
    "}\n"
)

LOCAL_MODEL = '''
class LocalModel {
    companion object {
        private const val MIN_MEMORY_BYTES = 2_400L * 1024 * 1024
        private const val MIN_FREE_BYTES = 1_200L * 1024 * 1024
    }
}
'''


class BaselineTest(unittest.TestCase):
    def setUp(self):
        root = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, root, ignore_errors=True)
        (root / "assets").mkdir()
        (root / "assets" / "a.bin").write_bytes(b"x" * 2_000_000)
        self.gradle = root / "build.gradle.kts"
        self.gradle.write_text(GRADLE, encoding="utf-8")
        self.model = root / "LocalModel.kt"
        self.model.write_text(LOCAL_MODEL, encoding="utf-8")
        self.readme = root / "README.md"
        self.readme.write_text("# Sign Setu\n\n- Runs on \n", encoding="utf-8")
        (root / "docs").mkdir()
        self.docs = root / "docs" / "README.md"
        self.docs.write_text("# Docs\n\n| Devices |\n", encoding="utf-8")
        for name, target in (
            ("ROOT", root),
            ("GRADLE", self.gradle),
            ("LOCAL_MODEL", self.model),
            ("ASSETS", root / "assets"),
        ):
            patcher = mock.patch.object(baseline, name, target)
            patcher.start()
            self.addCleanup(patcher.stop)

    def facts(self):
        return baseline.facts(self.gradle, self.model, self.gradle.parent / "assets")

    def test_the_floors_come_from_the_code_that_enforces_them(self):
        f = self.facts()
        self.assertEqual(2400, f["ramMib"])
        self.assertEqual(1200, f["freeMib"])
        self.assertEqual(["arm64-v8a", "x86_64"], f["abis"])
        self.assertEqual("2.0", f["assetsMb"])

    def test_a_missing_abi_list_is_an_error(self):
        self.gradle.write_text(GRADLE.replace('abiFilters += listOf("arm64-v8a", "x86_64")', ""), encoding="utf-8")
        with self.assertRaises(ValueError):
            self.facts()

    def test_the_generated_line_names_every_measured_floor(self):
        line = baseline.render_line(self.facts())
        for want in ("2400 MiB", "1200 MiB", "2.0 MB", "arm64-v8a", "x86_64"):
            self.assertIn(want, line)

    def test_a_stale_baseline_fails_and_write_fixes_it(self):
        f = self.facts()
        self.readme.write_text(baseline.generated(self.readme.read_text(encoding="utf-8"), f, baseline.README_START, baseline.render_line), encoding="utf-8")
        self.docs.write_text(baseline.generated(self.docs.read_text(encoding="utf-8"), f, baseline.DOCS_ROW_START, baseline.render_row), encoding="utf-8")
        self.assertEqual([], baseline.check())
        self.readme.write_text(self.readme.read_text(encoding="utf-8").replace("2400 MiB", "3 GB RAM"), encoding="utf-8")
        self.assertEqual(1, len(baseline.check()))

    def test_a_new_memory_floor_breaks_the_documents_until_they_are_written(self):
        f = self.facts()
        self.readme.write_text(baseline.generated(self.readme.read_text(encoding="utf-8"), f, baseline.README_START, baseline.render_line), encoding="utf-8")
        self.docs.write_text(baseline.generated(self.docs.read_text(encoding="utf-8"), f, baseline.DOCS_ROW_START, baseline.render_row), encoding="utf-8")
        self.model.write_text(LOCAL_MODEL.replace("2_400L", "3_000L"), encoding="utf-8")
        self.assertEqual(2, len(baseline.check()))

class RepositoryBaselineTest(unittest.TestCase):
    def test_the_repository_baseline_matches_the_repository_code(self):
        self.assertEqual([], baseline.check())


if __name__ == "__main__":
    unittest.main()
