"""The controls table is checked against the tree: an implemented row needs a real path and symbol."""

import pathlib
import shutil
import tempfile
import unittest

import check_attestation

DOC = """# 14. Attestation

## 1. Current state

### 1.1 Which controls exist in the tree

Checked against the tree.

| Control | Status | Evidence |
| --- | --- | --- |
| HTTPS only | Implemented | `app/Http.kt#httpsOnly` |
| Certificate pinning | Aspirational | section 3 |

## 2. Threat model
"""


class CheckAttestationTest(unittest.TestCase):
    """A miniature repository: one document and the one file it names."""

    def setUp(self):
        self.root = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root, ignore_errors=True)
        (self.root / "docs").mkdir()
        (self.root / "app").mkdir()
        (self.root / "app" / "Http.kt").write_text("fun httpsOnly() = true\n", encoding="utf-8")
        self.doc().write_text(DOC, encoding="utf-8")

    def doc(self) -> pathlib.Path:
        return self.root / "docs" / "14-attestation-and-pinning.md"

    def test_the_fixture_is_clean(self):
        self.assertEqual([], check_attestation.check(self.root))

    def test_a_missing_evidence_file_fails(self):
        self.doc().write_text(DOC.replace("app/Http.kt#httpsOnly", "app/Gone.kt#httpsOnly"), encoding="utf-8")
        errors = check_attestation.check(self.root)
        self.assertTrue(any("Gone.kt" in e for e in errors), errors)

    def test_a_missing_evidence_symbol_fails(self):
        self.doc().write_text(DOC.replace("app/Http.kt#httpsOnly", "app/Http.kt#pinSet"), encoding="utf-8")
        errors = check_attestation.check(self.root)
        self.assertTrue(any("pinSet" in e for e in errors), errors)

    def test_an_evidence_path_that_is_a_directory_fails(self):
        self.doc().write_text(DOC.replace("app/Http.kt#httpsOnly", "app#httpsOnly"), encoding="utf-8")
        errors = check_attestation.check(self.root)
        self.assertTrue(any("directory" in e and "app" in e for e in errors), errors)

    def test_evidence_that_is_not_utf8_fails(self):
        (self.root / "app" / "Bin.kt").write_bytes(b"\xff\xfe\x00not utf-8")
        self.doc().write_text(DOC.replace("app/Http.kt#httpsOnly", "app/Bin.kt#httpsOnly"), encoding="utf-8")
        errors = check_attestation.check(self.root)
        self.assertTrue(any("Bin.kt" in e for e in errors), errors)

    def test_a_table_with_no_row_fails(self):
        header_only = DOC.split("| HTTPS only")[0]
        self.doc().write_text(header_only, encoding="utf-8")
        errors = check_attestation.check(self.root)
        self.assertTrue(any("implemented control" in e for e in errors), errors)

    def test_a_table_with_no_implemented_row_fails(self):
        self.doc().write_text(DOC.replace("| HTTPS only | Implemented |", "| HTTPS only | Aspirational |"),
                              encoding="utf-8")
        errors = check_attestation.check(self.root)
        self.assertTrue(any("implemented control" in e for e in errors), errors)


if __name__ == "__main__":
    unittest.main()
