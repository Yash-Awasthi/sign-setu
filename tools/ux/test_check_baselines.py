import shutil
import tempfile
import unittest
from pathlib import Path

import check_baselines

DOC = "| Home | [01-home.png](screens/01-home.png) |\n| Language | [05-home-language.png](screens/05-home-language.png) |\n"


class BaselineTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.dir)
        self.screens = self.dir / "screens"
        self.before = self.dir / "before"
        self.screens.mkdir()
        self.before.mkdir()
        for name in ("01-home.png", "05-home-language.png"):
            (self.screens / name).write_bytes(b"png-" + name.encode())
            (self.before / name).write_bytes(b"png-" + name.encode())

    def test_the_catalogue_names_each_image_once(self):
        self.assertEqual(["01-home.png", "05-home-language.png"], check_baselines.names(DOC + DOC))

    def test_copied_files_pass(self):
        self.assertEqual([], check_baselines.problems(DOC, self.before, self.screens))

    def test_a_missing_baseline_is_reported(self):
        (self.before / "01-home.png").unlink()
        self.assertEqual(
            ["01-home.png: missing from docs/screens/before/"],
            check_baselines.problems(DOC, self.before, self.screens),
        )

    def test_a_changed_baseline_is_reported(self):
        (self.before / "05-home-language.png").write_bytes(b"re-shot")
        self.assertEqual(
            ["05-home-language.png: differs from docs/screens/05-home-language.png"],
            check_baselines.problems(DOC, self.before, self.screens),
        )

    def test_the_real_baselines_match_the_catalogue(self):
        text = check_baselines.DOC.read_text(encoding="utf-8")
        self.assertEqual([], check_baselines.problems(text, check_baselines.BEFORE, check_baselines.SCREENS))
        self.assertEqual(50, len(check_baselines.names(text)))


if __name__ == "__main__":
    unittest.main()