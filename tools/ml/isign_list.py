"""iSign clips to landmark, those whose sentence holds an app word first, then the rest.

    python isign_list.py ISIGN_CSV VIDEO_DIR OUT.txt
"""
import csv
import json
import pathlib
import re
import sys

import exp


def main(table, videos, out):
    words = {w.lower() for w in json.loads((exp.APP_MODELS / "sign_reader.json").read_text(encoding="utf-8"))["labels"]}
    root = pathlib.Path(videos)
    members = {p.stem: p.relative_to(root).as_posix() for p in root.rglob("*.mp4")}
    first, rest = [], []
    for r in csv.DictReader(open(table, encoding="utf-8")):
        m = members.get(r["uid"])
        if m:
            text = " ".join(re.findall(r"[a-z']+", r["text"].lower()))
            # Multi-word labels count as a phrase inside the sentence.
            (first if any(f" {w} " in f" {text} " for w in words) else rest).append(m)
    pathlib.Path(out).write_text("\n".join(first + rest) + "\n", encoding="utf-8")
    print(f"iSign: {len(first)} clips with an app word, {len(rest)} without, {len(members)} videos")


if __name__ == "__main__":
    main(*sys.argv[1:4])
