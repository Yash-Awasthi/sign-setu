"""Checks that a reviewer portal export fills the content review record: python tools/content/test_review_record.py"""
import json
import pathlib
import sys
import tempfile
import unittest
from unittest import mock

import review_record


class ReviewsFillTheRecord(unittest.TestCase):
    def test_export_fills_verdicts_and_notes(self):
        reviews = [
            {"section": "gesture", "id": "wave", "role": "ISL reviewer", "verdict": "A", "notes": ""},
            {"section": "phrase", "id": "want.water", "role": "Language reviewer (Hindi)", "verdict": "C", "notes": "use | pani"},
            {"section": "question", "id": "x2", "role": "Child-welfare reviewer", "verdict": "R", "notes": "presupposes\nharm"},
            {"section": "question", "id": "s1", "role": "Language reviewer (Hindi)", "verdict": "A", "notes": ""},
        ]
        with tempfile.TemporaryDirectory() as tmp:
            export = pathlib.Path(tmp, "export.json")
            export.write_text(json.dumps({"format": "signsetu-content-review", "version": 1, "reviews": reviews}), encoding="utf-8")
            out = pathlib.Path(tmp, "record.md")
            with mock.patch.object(sys, "argv", ["review_record.py", "--reviews", str(export), "--out", str(out)]):
                review_record.main()
            lines = out.read_text(encoding="utf-8").splitlines()

        row = lambda start: next(line for line in lines if line.startswith(start))
        self.assertIn("| A (ISL reviewer) | A (ISL reviewer) |", row("| `wave` |"))
        self.assertIn("|  | C |  | Language reviewer (Hindi): use \\| pani |", row("| Do you want water? |"))
        self.assertIn("| R |  |  | Child-welfare reviewer: presupposes harm |", row("| `x2` |"))
        self.assertIn("Language reviewer (Hindi): 1 of", row("| `hi` |"))
        self.assertEqual(row("| `en` |").split("|")[3].strip(), "")


if __name__ == "__main__":
    unittest.main()
