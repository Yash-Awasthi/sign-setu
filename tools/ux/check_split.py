"""Checks the person/officer split promised in docs/03-ux.md section 6.

Person-facing surfaces use the subject type scale, a 56 dp touch target and 96 dp picture cards;
officer-facing surfaces keep the denser Material scale. This reads Theme.kt for those numbers and
the section's table for the surfaces, so a smaller caption or a deleted screen fails here.

Run: python tools/ux/check_split.py
"""
from __future__ import annotations

import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
THEME = ROOT / "app/src/main/java/app/signsetu/ui/theme/Theme.kt"
DOC = ROOT / "docs/03-ux.md"

# Floors the split promises, in sp and dp.
TYPE_FLOORS = {"headlineSmall": 26.0, "titleLarge": 24.0, "labelLarge": 18.0}
TOUCH_FLOOR = 56
CARD_FLOOR = 96

STYLE = re.compile(r"(\w+) = TextStyle\([^)]*fontSize = ([\d.]+)\.sp")
PATH = re.compile(r"`(app/src/main/java/[^`]+\.kt)`")


def sizes(theme: str) -> dict[str, float]:
    """fontSize per name inside the SubjectType copy block."""
    start = theme.find("SubjectType = OfficerType.copy(")
    if start < 0:
        return {}
    depth, end = 0, start
    while end < len(theme):
        if theme[end] == "(":
            depth += 1
        elif theme[end] == ")":
            depth -= 1
            if depth == 0:
                break
        end += 1
    return {name: float(size) for name, size in STYLE.findall(theme[start:end])}


def dp(theme: str, name: str) -> int | None:
    m = re.search(rf"val {name}[^=]*= (\d+)\.dp", theme)
    return int(m.group(1)) if m else None


def doc_paths(text: str) -> list[str]:
    return sorted(set(PATH.findall(text)))


def problems(theme: str, doc: str, root: Path = ROOT) -> list[str]:
    found = sizes(theme)
    bad = []
    for name, floor in TYPE_FLOORS.items():
        size = found.get(name)
        if size is None:
            bad.append(f"SubjectType.{name}: missing")
        elif size < floor:
            bad.append(f"SubjectType.{name}: {size:g} sp, below the {floor:g} sp floor")
    touch = dp(theme, "SubjectTouchTarget")
    if touch is None or touch < TOUCH_FLOOR:
        bad.append(f"SubjectTouchTarget: {touch} dp, below the {TOUCH_FLOOR} dp floor")
    card = dp(theme, "SubjectCardMin")
    if card is None or card < CARD_FLOOR:
        bad.append(f"SubjectCardMin: {card} dp, below the {CARD_FLOOR} dp floor")
    paths = doc_paths(doc)
    if not paths:
        bad.append("docs/03-ux.md: the split table names no surface")
    for p in paths:
        if not (root / p).is_file():
            bad.append(f"{p}: named in the split table but missing")
    return bad


def main() -> int:
    ap = argparse.ArgumentParser(description="The person/officer split.")
    ap.add_argument("--theme", type=Path, default=THEME)
    ap.add_argument("--doc", type=Path, default=DOC)
    args = ap.parse_args()
    bad = problems(args.theme.read_text(encoding="utf-8"), args.doc.read_text(encoding="utf-8"))
    print("\n".join(bad) if bad else f"{len(doc_paths(args.doc.read_text(encoding='utf-8')))} surfaces split; type, touch and card floors hold.")
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
