"""Checks docs/22-screen-inventory.md against the app's navigation.

Every screen the app can open must have a purpose, three primary actions and an exit. The
navigation itself is the source of truth: each `entry<...Key>` in AppRoot.kt needs a row, and a
row may not name a route that no longer exists, so a screen added or deleted without its
inventory entry fails here.

Run: python tools/ux/check_inventory.py
"""
from __future__ import annotations

import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NAV = ROOT / "app/src/main/java/app/signsetu/ui/AppRoot.kt"
DOC = ROOT / "docs/22-screen-inventory.md"

ENTRY = re.compile(r"\bentry<([A-Za-z0-9_]+)>")
ROW = re.compile(r"^\|\s*`([A-Za-z0-9_]+)`\s*\|(.*)\|\s*$")


def routes() -> set[str]:
    """The NavKeys the app actually shows, from the one entryProvider."""
    return set(ENTRY.findall(NAV.read_text(encoding="utf-8", errors="replace")))


def rows(text: str) -> dict[str, list[str]]:
    """Route -> the three cells after it (purpose, actions, exit)."""
    out: dict[str, list[str]] = {}
    for line in text.splitlines():
        m = ROW.match(line)
        if not m:
            continue
        cells = [c.strip() for c in m.group(2).split("|")]
        if len(cells) >= 3:
            out[m.group(1)] = cells[:3]
    return out


def problems(text: str, shown: set[str]) -> list[str]:
    found = rows(text)
    bad = []
    for route in sorted(shown - set(found)):
        bad.append(f"{route}: opens in the app but has no row")
    for route in sorted(set(found) - shown):
        bad.append(f"{route}: has a row but no entry in AppRoot.kt")
    for route, (purpose, actions, exit_) in sorted(found.items()):
        if not purpose:
            bad.append(f"{route}: no purpose")
        if len([a for a in actions.split(";") if a.strip()]) != 3:
            bad.append(f"{route}: needs three primary actions separated by ';'")
        if not exit_:
            bad.append(f"{route}: no exit")
    return bad


def main() -> int:
    ap = argparse.ArgumentParser(description="Screen inventory against the navigation.")
    ap.add_argument("--doc", type=Path, default=DOC)
    args = ap.parse_args()
    bad = problems(args.doc.read_text(encoding="utf-8", errors="replace"), routes())
    print("\n".join(bad) if bad else f"{len(rows(args.doc.read_text(encoding='utf-8')))} screens: purpose, three actions and exit present.")
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
