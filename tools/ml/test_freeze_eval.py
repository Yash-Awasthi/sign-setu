"""Tests for freeze_eval.py: the committed freeze self-checks, overwrite is refused, a tampered digest is caught.

No corpus access; runs on the committed tools/ml/data when present.
    py -3 tools/ml/test_freeze_eval.py
"""
import contextlib
import io
import pathlib
import shutil
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import freeze_eval


class FreezeEvalTest(unittest.TestCase):
    def setUp(self):
        self.data = freeze_eval.OUT

    def run_main(self, argv):
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            code = freeze_eval.main(argv)
        return code, buf.getvalue()

    def committed(self):
        if not (self.data / "manifest.tsv").exists():
            raise unittest.SkipTest(f"no committed freeze at {self.data}")

    def test_check_committed(self):
        self.committed()
        self.assertEqual(self.run_main(["--check"])[0], 0)

    def test_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as tmp:
            manifest = pathlib.Path(tmp) / "manifest.tsv"
            manifest.write_text("", encoding="ascii")
            self.assertEqual(self.run_main(["--out", tmp])[0], 2)
            self.assertEqual(manifest.read_text(encoding="ascii"), "")

    def test_tampered_digest_fails(self):
        self.committed()
        with tempfile.TemporaryDirectory() as tmp:
            dst = pathlib.Path(tmp) / "data"
            shutil.copytree(self.data, dst)
            manifest = dst / "manifest.tsv"
            lines = manifest.read_text(encoding="ascii").splitlines()
            fields = lines[1].split("\t")
            fields[-1] = fields[-1][:-1] + ("0" if fields[-1][-1] != "0" else "1")
            lines[1] = "\t".join(fields)
            manifest.write_text("\n".join(lines) + "\n", encoding="ascii", newline="\n")
            self.assertEqual(self.run_main(["--check", "--out", str(dst)])[0], 1)


if __name__ == "__main__":
    unittest.main()
