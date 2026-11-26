"""The station's record reader and its growth check: unreadable input is answered, and a fabricated tail is not growth.

    python -m unittest brain.test_inbox

The hash recipe below is the app's (StationExport's README.txt, "Checking the chain") written out again here, so the
test checks the reader against the rule rather than against the reader.
"""
import hashlib
import io
import json
import sys
import tempfile
import unittest
import zipfile
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "brain"))

from inbox import Inbox, _record  # noqa: E402

SESSION = "session-1"


def field(value) -> str:
    if value is None:
        return "|-"
    text = value if isinstance(value, str) else str(value)
    return f"|{len(text.encode('utf-16-le')) // 2}:{text}"


def genesis(session: str, started: int) -> str:
    return hashlib.sha256(("signsetu-chain-v1" + field(session) + field(started)).encode()).hexdigest()


def link(previous: str, session: str, row: dict) -> str:
    text = previous + field(session) + field(row["ts"]) + field(row["type"]) + field(row["source"])
    text += field(row["lang"]) + field(row["text"]) + field(row["cardId"]) + field(row["lexiconId"])
    text += field(None) + field(row["itemId"]) + field(row["reason"]) + field(row["shownId"]) + field(row["via"])
    return hashlib.sha256(text.encode()).hexdigest()


def a_row(ts: int, text: str) -> dict:
    return {
        "id": ts, "ts": ts, "type": "NOTE", "source": "OFFICER", "lang": "en", "text": text,
        "cardId": None, "lexiconId": None, "itemId": None, "reason": None, "shownId": None, "via": None,
        "drawing": None, "audio": None, "signs": None,
    }


def a_chain(count: int) -> list[dict]:
    rows = []
    previous = genesis(SESSION, 1000)
    for i in range(count):
        row = a_row(1000 + i, f"row {i} ok \U0001F600")
        row["hash"] = link(previous, SESSION, row)
        previous = row["hash"]
        rows.append(row)
    return rows


def a_zip(rows: list[dict]) -> bytes:
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("session.json", json.dumps({"id": SESSION, "startedAt": 1000}))
        z.writestr("events.json", json.dumps(rows))
    return out.getvalue()


def a_corrupt_zip() -> bytes:
    """The genuine zip with one byte of events.json's deflate stream flipped."""
    blob = bytearray(a_zip(a_chain(2)))
    with zipfile.ZipFile(io.BytesIO(bytes(blob))) as z:
        info = z.getinfo("events.json")
    header = blob[info.header_offset: info.header_offset + 30]
    name_len = int.from_bytes(header[26:28], "little")
    extra_len = int.from_bytes(header[28:30], "little")
    start = info.header_offset + 30 + name_len + extra_len
    blob[start + info.compress_size // 2] ^= 0xFF
    return bytes(blob)


def send(inbox: Inbox, name: str, blob: bytes) -> str | None:
    tid = inbox.start(name, len(blob), hashlib.sha256(blob).hexdigest())
    inbox.add(tid, 0, blob)
    return inbox.done(tid)


class CorruptDeflateTest(unittest.TestCase):
    def test_a_corrupted_deflate_stream_is_invalid_not_an_exception(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "r.zip"
            path.write_bytes(a_corrupt_zip())
            with zipfile.ZipFile(path) as z:
                with self.assertRaises(zlib.error):
                    z.read("events.json")
            self.assertEqual((None, []), _record(path))


class GrowthTest(unittest.TestCase):
    def test_a_fabricated_tail_row_keeps_the_earlier_archive(self):
        genuine = a_zip(a_chain(2))
        rows = a_chain(2)
        fake = a_row(2000, "never written by the app")
        fake["hash"] = "ab" * 32
        rows.append(fake)
        with tempfile.TemporaryDirectory() as tmp:
            inbox = Inbox(Path(tmp))
            self.assertEqual("conversation.zip", send(inbox, "conversation.zip", genuine))
            saved = send(inbox, "conversation.zip", a_zip(rows))
            self.assertNotEqual("conversation.zip", saved)
            self.assertEqual(genuine, (Path(tmp) / "conversation.zip").read_bytes())

    def test_an_earlier_record_with_no_rows_is_kept(self):
        empty = a_zip([])
        grown = a_zip(a_chain(1))
        with tempfile.TemporaryDirectory() as tmp:
            inbox = Inbox(Path(tmp))
            self.assertEqual("conversation.zip", send(inbox, "conversation.zip", empty))
            self.assertNotEqual("conversation.zip", send(inbox, "conversation.zip", grown))
            self.assertEqual(empty, (Path(tmp) / "conversation.zip").read_bytes())

    def test_a_tail_that_continues_the_chain_replaces_the_earlier_archive(self):
        genuine = a_zip(a_chain(2))
        grown = a_zip(a_chain(3))
        with tempfile.TemporaryDirectory() as tmp:
            inbox = Inbox(Path(tmp))
            self.assertEqual("conversation.zip", send(inbox, "conversation.zip", genuine))
            self.assertEqual("conversation.zip", send(inbox, "conversation.zip", grown))
            self.assertEqual(grown, (Path(tmp) / "conversation.zip").read_bytes())


if __name__ == "__main__":
    unittest.main()
