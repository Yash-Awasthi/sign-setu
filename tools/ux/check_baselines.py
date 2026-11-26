"""Checks docs/screens/before/ against the images docs/12-screens.md links.

The catalogue is the source of truth: every image it links must sit in
docs/screens/before/ too, byte for byte, so a later capture can be compared with the
screen as it was before the change. A baseline that is missing or has drifted fails
here.

Run: py -3 tools/ux/check_baselines.py
"""
from __future__ import annotations

import argparse
import hashlib
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOC = ROOT / "docs/12-screens.md"
SCREENS = ROOT / "docs/screens"
BEFORE = SCREENS / "before"

LINK = re.compile(r"\]\(screens/([^)\s]+)\)")


def names(text: str) -> list[str]:
    """The images the catalogue links, in document order, once each."""
    out: list[str] = []
    for name in LINK.findall(text):
        if name not in out:
            out.append(name)
    return out


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def problems(text: str, before: Path, screens: Path) -> list[str]:
    bad = []
    for name in names(text):
        baseline = before / name
        if not baseline.is_file():
            bad.append(f"{name}: missing from docs/screens/before/")
        elif sha256(baseline) != sha256(screens / name):
            bad.append(f"{name}: differs from docs/screens/{name}")
    return bad


def main() -> int:
    ap = argparse.ArgumentParser(description="Baseline screenshots against the catalogue.")
    ap.add_argument("--doc", type=Path, default=DOC)
    ap.add_argument("--before", type=Path, default=BEFORE)
    ap.add_argument("--screens", type=Path, default=SCREENS)
    args = ap.parse_args()
    text = args.doc.read_text(encoding="utf-8", errors="replace")
    bad = problems(text, args.before, args.screens)
    if bad:
        print("\n".join(bad))
        return 1
    print(f"{len(names(text))} baselines verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())