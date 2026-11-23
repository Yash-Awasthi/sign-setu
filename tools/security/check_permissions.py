"""The documented permission table is generated from the manifests, and the documented
prose names every permission the app declares.

The merged-manifest gate in `app/build.gradle.kts` refuses a forbidden permission a
library adds; this check does the other half, for both flavours, from the source
manifests: what the app declares, with its API limit and its scan flag, is exactly
what `docs/README.md` says. `--write` regenerates the table.
"""
import argparse
import pathlib
import re
import sys
import xml.etree.ElementTree as ElementTree

ROOT = pathlib.Path(__file__).resolve().parents[2]
MAIN = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
FLAVORS = {"standard": ROOT / "app" / "src" / "standard" / "AndroidManifest.xml", "nearby": ROOT / "app" / "src" / "nearby" / "AndroidManifest.xml"}
DOCS = ROOT / "docs" / "README.md"

ANDROID = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"
TABLE_HEAD = "| Permission | `standard` | `nearby` |"
PROSE_START = "| Permissions (standard) |"


def declared(path: pathlib.Path) -> dict[str, dict]:
    """The permissions one manifest declares, by short name, with their limit and flags; a
    `tools:node="remove"` entry is a permission the app refuses, so it is not declared."""
    out: dict[str, dict] = {}
    if not path.exists():
        return out
    for node in ElementTree.parse(path).getroot().iter("uses-permission"):
        name = (node.get(f"{ANDROID}name") or "").removeprefix("android.permission.")
        if not name or node.get(f"{TOOLS}node") == "remove":
            continue
        out[name] = {
            "max": node.get(f"{ANDROID}maxSdkVersion"),
            "neverForLocation": node.get(f"{ANDROID}usesPermissionFlags") == "neverForLocation",
            "flagRemoved": "usesPermissionFlags" in (node.get(f"{TOOLS}remove") or ""),
        }
    return out


def table(main: pathlib.Path = MAIN, flavors: dict[str, pathlib.Path] | None = None) -> list[str]:
    """The markdown table: one row per permission, in the order the manifests declare them."""
    flavors = FLAVORS if flavors is None else flavors
    base = declared(main)
    merged = {flavor: {**base, **declared(path)} for flavor, path in flavors.items()}
    order = list(base) + [name for name in declared(flavors.get("nearby", pathlib.Path())) if name not in base]
    lines = [TABLE_HEAD, "| --- | --- | --- |"]
    for name in order:
        cells = []
        for flavor in ("standard", "nearby"):
            got = merged[flavor].get(name)
            if got is None:
                cells.append("—")
                continue
            parts = ["yes"]
            if got["max"]:
                parts.append(f"API ≤ {got['max']}")
            if got["neverForLocation"]:
                parts.append("neverForLocation")
            if got["flagRemoved"]:
                parts.append("no flag")
            cells.append(", ".join(parts))
        lines.append(f"| `{name}` | {cells[0]} | {cells[1]} |")
    return lines


def find_table(text: str) -> tuple[int, int] | None:
    """Where the generated table sits in the document, from its head to its last row."""
    lines = text.splitlines()
    for start, line in enumerate(lines):
        if line.strip() == TABLE_HEAD:
            end = start
            while end + 1 < len(lines) and lines[end + 1].startswith("|"):
                end += 1
            return start, end
    return None


def prose_names(text: str) -> set[str]:
    """The permissions the prose row of the reference configuration names."""
    for line in text.splitlines():
        if line.startswith(PROSE_START):
            return set(re.findall(r"`([A-Z][A-Z_]{2,})`", line))
    return set()


def check(main: pathlib.Path = MAIN, flavors: dict[str, pathlib.Path] | None = None, docs: pathlib.Path = DOCS) -> list[str]:
    flavors = FLAVORS if flavors is None else flavors
    text = docs.read_text(encoding="utf-8")
    want = table(main, flavors)
    placed = find_table(text)
    errors = []
    if placed is None:
        errors.append(f"{docs}: no permission table")
    else:
        have = text.splitlines()[placed[0] : placed[1] + 1]
        if have != want:
            errors.append(f"{docs}: the permission table is stale\n  expected:\n    " + "\n    ".join(want))
    named = prose_names(text)
    standard = set(declared(main)) | set(declared(flavors["standard"]))
    missing = sorted(standard - named)
    if missing:
        errors.append(f"{docs}: the prose does not name every standard permission: missing {missing}")
    return errors


def write(main: pathlib.Path = MAIN, flavors: dict[str, pathlib.Path] | None = None, docs: pathlib.Path = DOCS) -> None:
    text = docs.read_text(encoding="utf-8")
    lines = text.splitlines()
    want = table(main, FLAVORS if flavors is None else flavors)
    placed = find_table(text)
    if placed is None:
        raise ValueError(f"{docs}: no permission table to regenerate; add the head line {TABLE_HEAD}")
    lines[placed[0] : placed[1] + 1] = want
    docs.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="regenerate the table in docs/README.md")
    args = parser.parse_args(argv)
    try:
        if args.write:
            write()
            print("\n".join(table()))
            return 0
    except ValueError as error:
        print(error, file=sys.stderr)
        return 1
    errors = check()
    for error in errors:
        print(error, file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
