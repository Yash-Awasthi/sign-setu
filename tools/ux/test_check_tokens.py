import tempfile
import unittest
from pathlib import Path

import check_tokens

CLEAN = """
Text("Sign", fontSize = TypeSize.s16, modifier = Modifier.padding(Space.md))
Surface(shape = Radius.lg, color = StageTone.ink, border = BorderStroke(Space.hair, Tone.line))
"""


def tree(files: dict[str, str]) -> Path:
    root = Path(tempfile.mkdtemp()) / "ui"
    for name, text in files.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    return root


class TokenTest(unittest.TestCase):
    def test_tokens_pass(self):
        self.assertEqual([], check_tokens.problems(tree({"a/Ok.kt": CLEAN})))

    def test_dp_and_sp_numbers_are_reported(self):
        bad = check_tokens.problems(tree({"a/Bad.kt": 'Modifier.padding(12.dp)\nText("x", fontSize = 26.sp)\n'}))
        self.assertEqual(2, len(bad))
        self.assertTrue(bad[0].endswith(": 12.dp"), bad[0])
        self.assertTrue(bad[1].endswith(": 26.sp"), bad[1])

    def test_inline_corner_radius_is_reported(self):
        bad = check_tokens.problems(tree({"a/Bad.kt": "Surface(shape = RoundedCornerShape(16.dp))\nBox(Modifier.clip(RoundedCornerShape(50)))\n"}))
        self.assertEqual(2, len(bad))

    def test_hex_colour_is_reported(self):
        bad = check_tokens.problems(tree({"a/Bad.kt": "val ink = Color(0xFF4F4539)\n"}))
        self.assertEqual(1, len(bad))
        self.assertTrue(bad[0].endswith(": Color(0xFF4F4539)"), bad[0])

    def test_shape_counts_once_with_its_dp(self):
        bad = check_tokens.problems(tree({"a/Bad.kt": "Surface(shape = RoundedCornerShape(8.dp))\n"}))
        self.assertEqual(1, len(bad))

    def test_theme_directory_is_the_allowed_home(self):
        bad = check_tokens.problems(tree({"theme/Tokens.kt": "val x = 12.dp\nval c = Color(0xFF112233)\n"}))
        self.assertEqual([], bad)

    def test_comments_are_ignored(self):
        bad = check_tokens.problems(tree({"a/Ok.kt": "// 12.dp and Color(0xFF112233) explain the token\nval x = Space.md\n"}))
        self.assertEqual([], bad)

    def test_the_real_ui_has_no_raw_values(self):
        self.assertEqual([], check_tokens.problems())


if __name__ == "__main__":
    unittest.main()
