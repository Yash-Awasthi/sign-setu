r"""Fails on raw dimension, shape and colour literals in the app's composables.

Every dp, sp, corner radius and hex colour comes from the token set in
app/src/main/java/app/signsetu/ui/theme/ (Tokens.kt and Tones.kt). A composable
must read those tokens; spelling the number at the call site is what this
checker rejects. The exact patterns are:

  dimension   (?<![\w.])\d+(?:\.\d+)?\.(?:dp|sp)    e.g. 12.dp, 26.sp
  shape       RoundedCornerShape( ... \d ... )      e.g. RoundedCornerShape(16.dp), (50)
  colour      Color\(0x[0-9A-Fa-f]{6,8}\)           e.g. Color(0xFF4F4539)

A value that matches more than one pattern on a line (a corner radius is also a
dp number) is counted once. Files under ui/theme/ are skipped: that directory is
the only allowed home for a literal, because that is where the tokens are
defined. Line comments are ignored.

Run: py -3 tools/ux/check_tokens.py
"""
from __future__ import annotations

import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/app/signsetu/ui"

DIMENSION = re.compile(r"(?<![\w.])\d+(?:\.\d+)?\.(?:dp|sp)")
SHAPE = re.compile(r"RoundedCornerShape\([^)]*?(?<![\w.])\d+(?:\.\d+)?(?:\.dp)?" + r"\s*[,)]")
COLOUR = re.compile(r"Color\(\s*0x[0-9A-Fa-f]{6,8}\s*\)")
COMMENT = re.compile(r"//.*$")


def problems(root: Path = UI) -> list[str]:
    """One 'file:line:col: value' per raw literal, in file order."""
    found: list[str] = []
    for path in sorted(root.rglob("*.kt")):
        inside = path.relative_to(root)
        if inside.parts and inside.parts[0] == "theme":
            continue
        try:
            rel = path.relative_to(ROOT).as_posix()
        except ValueError:
            rel = path.as_posix()
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            code = COMMENT.sub("", line)
            spans: list[tuple[int, int, str]] = []
            for pattern in (DIMENSION, SHAPE, COLOUR):
                spans.extend((m.start(), m.end(), m.group(0)) for m in pattern.finditer(code))
            merged: list[tuple[int, int, str]] = []
            for start, end, value in sorted(spans):
                if merged and start < merged[-1][1]:
                    continue
                merged.append((start, end, value))
            found.extend(f"{rel}:{number}:{start + 1}: {value}" for start, _, value in merged)
    return found


def main() -> int:
    ap = argparse.ArgumentParser(description="Raw dp, sp, shape and colour literals in composables.")
    ap.add_argument("--root", type=Path, default=UI, help="directory to scan (default: ui)")
    args = ap.parse_args()
    bad = problems(args.root)
    print("\n".join(bad))
    print(f"{len(bad)} raw values")
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
