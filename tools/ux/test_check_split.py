import unittest
from pathlib import Path

import check_split

THEME = """
private val SubjectType = OfficerType.copy(
    headlineMedium = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp),
    labelLarge = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp),
)
val SubjectTouchTarget = 56.dp
val SubjectCardMin: Dp = 96.dp
"""

DOC = "| `app/src/main/java/app/signsetu/ui/session/Stage.kt` | `app/src/main/java/app/signsetu/ui/start/StartScreen.kt` |\n"


class SplitTest(unittest.TestCase):
    def test_the_promised_floors_hold(self):
        root = Path(self.tmp())
        (root / "app/src/main/java/app/signsetu/ui/session").mkdir(parents=True)
        (root / "app/src/main/java/app/signsetu/ui/start").mkdir(parents=True)
        (root / "app/src/main/java/app/signsetu/ui/session/Stage.kt").touch()
        (root / "app/src/main/java/app/signsetu/ui/start/StartScreen.kt").touch()
        self.assertEqual([], check_split.problems(THEME, DOC, root))

    def test_a_smaller_caption_is_reported(self):
        theme = THEME.replace("fontSize = 26.sp", "fontSize = 22.sp")
        bad = check_split.problems(theme, DOC, Path("."))
        self.assertIn("SubjectType.headlineSmall: 22 sp, below the 26 sp floor", bad)

    def test_a_missing_surface_is_reported(self):
        bad = check_split.problems(THEME, DOC, Path(self.tmp()))
        self.assertIn("app/src/main/java/app/signsetu/ui/session/Stage.kt: named in the split table but missing", bad)

    def test_the_real_theme_and_doc_agree(self):
        self.assertEqual([], check_split.problems(check_split.THEME.read_text(encoding="utf-8"), check_split.DOC.read_text(encoding="utf-8")))

    def tmp(self) -> str:
        import tempfile

        d = tempfile.mkdtemp()
        self.addCleanup(lambda: None)
        return d


if __name__ == "__main__":
    unittest.main()
