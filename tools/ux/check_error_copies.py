"""Checks that the five broken states tell the officer what to do, not what failed.

No model, no camera, no microphone, no second screen and a full phone each have one string here.
Each must name an action (Allow, Import, Pair, Free, Open, Tap, ...) and none may show an error code.

Run: python tools/ux/check_error_copies.py
"""
from __future__ import annotations

import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STRINGS = ROOT / "app/src/main/res/values/strings.xml"

STATES = {
    "no model": "settings_assistant_missing",
    "no camera": "sign_to_type_no_camera",
    "no microphone": "voice_no_permission",
    "no second screen": "second_screen_not_listed",
    "storage full": "export_failed",
}

FIX = re.compile(r"\b(Allow|Import|Pair|Free|Open|Tap|Turn on|Connect|Update|Choose|Type)\b")
CODE = re.compile(r"\b(?:error|errno|code)\s*[:#]?\s*[A-Z0-9][A-Z0-9-]{1,}\b", re.IGNORECASE)


def messages(text: str) -> dict[str, str]:
    out = {}
    for name, body in re.findall(r'<string name="([^"]+)">(.*?)</string>', text, re.S):
        out[name] = body
    return out


def problems(text: str) -> list[str]:
    found = messages(text)
    bad = []
    for state, name in STATES.items():
        body = found.get(name)
        if body is None:
            bad.append(f"{state}: {name} is missing")
            continue
        if not FIX.search(body):
            bad.append(f"{state}: {name} names no fix")
        if CODE.search(body):
            bad.append(f"{state}: {name} shows an error code")
    return bad


def main() -> int:
    ap = argparse.ArgumentParser(description="Broken states carry their fix.")
    ap.add_argument("--strings", type=Path, default=STRINGS)
    args = ap.parse_args()
    bad = problems(args.strings.read_text(encoding="utf-8"))
    print("\n".join(bad) if bad else f"{len(STATES)} broken states, each with a fix and no error code.")
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
