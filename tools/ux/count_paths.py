"""Counts the ways out of a session that each screen offers.

The owner's rule (plan.md, the appendix): "easy the xclosing of session and don't provide multiple divergent paths that
confuse, at max 3 is fine, no more than that divergent paths on a single screen". A *divergent path* is a control that
ends, leaves or discards the session. Controls that keep you where you are (Keep going, Cancel, Back) are not paths out,
and a destructive control that only changes its words on the second tap is still one path.

This census is static, so it sees every branch of a screen at once: the close dialog's rooted form (which must offer the
export, since that phone keeps no history) shows up beside the everyday form. The cap of three is therefore enforced by
`WrapUpPathsDeviceTest`, which renders the dialog in each state on a device and counts what is really there; this tool's
job is to keep the inventory visible and to catch a label nobody reviewed.

Run: python tools/ux/count_paths.py [--verbose]
"""
from __future__ import annotations

import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/java/app/signsetu"

# Labels of an action that ends, leaves or discards the session.
CLOSING = (
    "wrap_save",
    "wrap_delete",
    "wrap_discard",
    "wrap_thank",
    "end_export",
    "end_session",
    "end_finish",
    "end_delete",
    "officer_end",
    "officer_leave",
    "leave_session",
    "close_session",
    "session_end",
    "session_delete",
    "saved_delete",
    "saved_discard",
)

# The same control, worded for another state.
ALIASES = {
    "wrap_discard": "wrap_delete",
}

# Controls that return to the screen you were on are not ways out.
STAYING = ("wrap_keep_going", "cancel", "back", "dismiss", "sheet_close")

BUTTON = re.compile(
    r"\b(Button|OutlinedButton|TextButton|FilledTonalButton|ElevatedButton|IconButton|FilledIconButton|"
    r"FilledTonalIconButton|OutlinedIconButton|DropdownMenuItem)\s*\("
)
LABEL = re.compile(r"R\.string\.([a-z0-9_]+)")
FUN = re.compile(r"^(?:internal |private |public )?fun\s+([A-Za-z0-9_]+)\s*\(", re.M)


def functions(text: str) -> list[tuple[str, str]]:
    """(name, body) for every function in the file, braces matched."""
    out = []
    for m in FUN.finditer(text):
        i = text.find("{", m.end())
        if i < 0:
            continue
        depth, j = 0, i
        while j < len(text):
            if text[j] == "{":
                depth += 1
            elif text[j] == "}":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        out.append((m.group(1), text[i : j + 1]))
    return out


def _call_at(body: str, start: int) -> str:
    """The call starting at start's `(`: its arguments and the trailing lambda that carries the label."""
    depth, j = 0, start
    while j < len(body):
        if body[j] == "(":
            depth += 1
        elif body[j] == ")":
            depth -= 1
            if depth == 0:
                break
        j += 1
    call = body[start + 1 : j + 1]
    k = j + 1
    while k < len(body) and body[k] in " \t\r\n":
        k += 1
    if k < len(body) and body[k] == "{":
        depth, end = 0, k
        while end < len(body):
            if body[end] == "{":
                depth += 1
            elif body[end] == "}":
                depth -= 1
                if depth == 0:
                    break
            end += 1
        call += body[k : end + 1]
    return call


def ways_out(body: str) -> set[str]:
    """Distinct closing labels attached to a button-ish call site in this body."""
    found: set[str] = set()
    for m in BUTTON.finditer(body):
        for label in LABEL.findall(_call_at(body, m.end() - 1)):
            if label in STAYING or label not in CLOSING:
                continue
            found.add(ALIASES.get(label, label))
    return found


def screen_of(path: Path) -> str:
    return path.relative_to(SRC).with_suffix("").as_posix()


def measure() -> list[tuple[str, str, set[str]]]:
    rows = []
    for path in sorted(SRC.rglob("*.kt")):
        text = path.read_text(encoding="utf-8", errors="replace")
        for name, body in functions(text):
            labels = ways_out(body)
            if labels:
                rows.append((screen_of(path), name, labels))
    return rows


def main() -> int:
    ap = argparse.ArgumentParser(description="Ways out of a session, per screen.")
    ap.add_argument("--verbose", action="store_true", help="list every label")
    args = ap.parse_args()

    rows = measure()
    print(f"{'screen':52} {'composable':28} ways out")
    print("-" * 96)
    for screen, name, labels in sorted(rows, key=lambda r: (-len(r[2]), r[0])):
        print(f"{screen:52} {name:28} {len(labels)}")
        if args.verbose:
            for label in sorted(labels):
                print(f"{'':82}{label}")
    widest = max((len(labels) for _, _, labels in rows), default=0)
    print(f"\n{len(rows)} screen(s) offer a way out; the widest offers {widest}. The cap of three is enforced per state by "
          "WrapUpPathsDeviceTest, since one composable may hold the branches of several states.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
