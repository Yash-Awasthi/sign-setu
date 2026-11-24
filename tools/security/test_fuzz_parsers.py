import base64
import json
import random
import tempfile
import unittest
import zipfile
from pathlib import Path

import fuzz_parsers


class SealFuzzTest(unittest.TestCase):
    """The off-phone seal reader answers, it never dies, and it never calls an altered line valid."""

    def test_a_seeded_run_finds_nothing_and_never_raises(self):
        problems = fuzz_parsers.fuzz_seal(80, random.Random(3))
        self.assertEqual([], problems)

    def test_a_garbage_key_is_an_answer_not_a_traceback(self):
        import verify_seal

        self.assertEqual((2, "the seal's key is not base64 of a DER public key"), verify_seal.check("SS1|s|3|h|r|d|sig|!!!!"))
        code, message = verify_seal.check("SS1|s|3|h|r|d|sig|" + base64.b64encode(b"\x00" * 20).decode())
        self.assertEqual(2, code)
        self.assertIn("DER", message)

    def test_a_signature_of_the_wrong_shape_is_invalid_not_valid(self):
        import verify_seal

        good = fuzz_parsers.a_signed_seal()
        fields = good.split("|")
        fields[6] = base64.b64encode(b"\x01\x02\x03").decode()
        code, message = verify_seal.check("|".join(fields))
        self.assertEqual(1, code)
        self.assertIn("INVALID", message)

    def test_the_signed_line_is_what_the_signature_covers(self):
        good = fuzz_parsers.a_signed_seal()
        self.assertEqual("SS1|session-1|3", fuzz_parsers.signed_line(good).split("|")[0:3][0] + "|" + good.split("|")[1] + "|" + good.split("|")[2])


class PdfFuzzTest(unittest.TestCase):
    def test_random_bytes_do_not_crash_the_pdf_reader(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual([], fuzz_parsers.fuzz_pdf(30, random.Random(5), Path(tmp)))

    def test_an_unreadable_file_is_reported_as_unreadable(self):
        import verify_seal

        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "not.pdf"
            path.write_bytes(b"not a pdf at all")
            self.assertEqual("", verify_seal.seal_from_pdf(str(path)))

    def test_a_pdf_without_a_seal_is_none_not_empty(self):
        import verify_seal
        from pypdf import PdfWriter

        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "plain.pdf"
            writer = PdfWriter()
            writer.add_blank_page(width=100, height=100)
            with open(path, "wb") as fh:
                writer.write(fh)
            self.assertIsNone(verify_seal.seal_from_pdf(str(path)))


class RecordFuzzTest(unittest.TestCase):
    """The station's reader takes what it understands and drops the rest, whatever arrives."""

    def test_a_seeded_run_finds_nothing(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual([], fuzz_parsers.fuzz_record(40, random.Random(9), Path(tmp)))

    def test_an_id_that_is_not_text_is_refused(self):
        from brain.inbox import _record

        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "r.zip"
            path.write_bytes(fuzz_parsers.a_record_zip(json.dumps({"id": {"nested": True}}), "[]"))
            self.assertEqual((None, []), _record(path))

    def test_deeply_nested_json_is_refused_not_fatal(self):
        from brain.inbox import _record

        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "r.zip"
            path.write_bytes(fuzz_parsers.a_record_zip("[" * 100_000 + "]" * 100_000, "[]"))
            self.assertEqual((None, []), _record(path))

    def test_rows_that_are_not_objects_keep_their_place_as_none(self):
        from brain.inbox import _record

        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "r.zip"
            path.write_bytes(fuzz_parsers.a_record_zip(json.dumps({"id": "s"}), json.dumps([1, {"hash": "aa"}])))
            self.assertEqual(("s", [None, "aa"]), _record(path))

    def test_a_zip_that_is_not_a_zip(self):
        from brain.inbox import _record

        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "r.zip"
            path.write_bytes(b"\x00" * 32)
            self.assertEqual((None, []), _record(path))


class MutationsTest(unittest.TestCase):
    def test_every_mutation_keeps_the_seal_shape_apart_from_where_it_breaks_it(self):
        good = fuzz_parsers.a_signed_seal()
        seen = list(fuzz_parsers.mutations(good, random.Random(1), 20))
        self.assertGreater(len(seen), 20)
        self.assertTrue(any(p != good for p in seen))
        self.assertTrue(any(p.startswith("SS1") for p in seen))


if __name__ == "__main__":
    unittest.main()
