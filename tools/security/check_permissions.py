"""The documented permission table is generated from the manifests, the documented
prose names every permission, and the DPIA change log carries a row for every data
category and every permission, with its purpose and retention.

The merged-manifest gate in `app/build.gradle.kts` refuses a forbidden permission a
library adds; this check does the other half, for both flavours, from the source
manifests: what the app declares, with its API limit and its scan flag, is exactly
what `docs/README.md` says, and `docs/13-dpia.md` records what each one is for and how
long it is kept. `--write` regenerates the table.
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
DPIA = ROOT / "docs" / "13-dpia.md"

ANDROID = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"
TABLE_HEAD = "| Permission | `standard` | `nearby` |"
CHANGE_HEAD = "| Kind | Name | Purpose | Retention |"

# Every permission this app may ever declare, and why. A new one is a decision about privacy, a Play listing and a
# department's threat model, so it cannot arrive with a merge: the check fails until it is named here with its reason
# (plan.md, M2.1). Removing one is just as deliberate: the reason goes with it.
APPROVED = {
    "CAMERA": "signing to the camera, and the officer's video call tile; also the shape reader and the skeleton",
    "VIBRATE": "the tap of an answer felt by the officer holding the phone",
    "USE_BIOMETRIC": "the officer's screen lock, fingerprint or face before records are opened, left, exported or deleted",
    "RECEIVE_BOOT_COMPLETED": "keeps the hourly retention sweep scheduled, so a phone nobody opens still forgets",
    "INTERNET": "the department gateway and the station computer, both off by default",
    "ACCESS_NETWORK_STATE": "whether the network is up before calling either of them",
    "ACCESS_LOCAL_NETWORK": "Android 17 otherwise drops the app's sockets to station-network addresses",
    "RECORD_AUDIO": "push-to-talk to the station's brain and the person's own recorded answer, always after consent",
    "BLUETOOTH": "finding an unpaired second screen on Android 11 and older",
    "BLUETOOTH_CONNECT": "talking to the bonded second screen on Android 12 and later",
    "BLUETOOTH_SCAN": "scanning for an unpaired phone to bond with, never for location",
    "BLUETOOTH_ADMIN": "the nearby flavour's scan, on Android 11 and older",
    "BLUETOOTH_ADVERTISE": "the nearby flavour being found by the other phone",
    "ACCESS_WIFI_STATE": "the nearby flavour's connection to the second screen, on Android 12 and older",
    "CHANGE_WIFI_STATE": "the nearby flavour's connection to the second screen, on Android 12 and older",
    "ACCESS_FINE_LOCATION": "required by Nearby Connections on Android 12 and older, never read by the app",
    "ACCESS_COARSE_LOCATION": "required by Nearby Connections on Android 12 and older, never read by the app",
    "NEARBY_WIFI_DEVICES": "the nearby flavour's connection to the second screen on Android 13 and later",
}
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


def declared_everywhere(main: pathlib.Path = MAIN, flavors: dict[str, pathlib.Path] | None = None) -> set[str]:
    """Every permission any flavour of the app declares."""
    flavors = FLAVORS if flavors is None else flavors
    return set(declared(main)) | {name for path in flavors.values() for name in declared(path)}


def check_approved(names: set[str]) -> list[str]:
    """A permission is a decision about privacy and a Play listing, so it cannot arrive in a merge request: it has to be
    named in APPROVED with its reason, and a reason whose permission has gone has to be struck out."""
    errors = []
    unapproved = sorted(names - set(APPROVED))
    if unapproved:
        errors.append(
            "a permission this app may not declare arrived: " + ", ".join(unapproved)
            + " — name it in APPROVED with the reason it is needed, and write it into docs/README.md"
        )
    gone = sorted(set(APPROVED) - names)
    if gone:
        errors.append("APPROVED names permissions the manifests no longer declare: " + ", ".join(gone) + " — strike them out")
    return errors


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


def categories(path: pathlib.Path = DPIA) -> list[str]:
    """The data categories section 1.2 of the DPIA lists, in order."""
    if not path.exists():
        return []
    lines = path.read_text(encoding="utf-8").splitlines()
    for start, line in enumerate(lines):
        if line.startswith("### 1.2"):
            for head, row in enumerate(lines[start + 1 :], start + 1):
                if row.startswith("| Category |"):
                    out = []
                    for body in lines[head + 2 :]:
                        if not body.startswith("|"):
                            break
                        out.append(cells(body)[0])
                    return out
    return []


def cells(row: str) -> list[str]:
    """The stripped cells of one markdown table row."""
    return [cell.strip() for cell in row.strip().strip("|").split("|")]


def change_log(path: pathlib.Path = DPIA) -> list[tuple[str, str, str, str]]:
    """Every row of the DPIA change log: kind, name, purpose, retention."""
    lines = path.read_text(encoding="utf-8").splitlines()
    for start, line in enumerate(lines):
        if line.strip() == CHANGE_HEAD:
            rows = []
            for body in lines[start + 1 :]:
                if not body.startswith("|"):
                    break
                got = cells(body)
                if all(set(cell) <= {"-", ":"} and cell for cell in got):
                    continue
                rows.append(tuple((got + [""] * 4)[:4]))
            return rows
    return []


def check_dpia(names: set[str], dpia: pathlib.Path = DPIA) -> list[str]:
    """A data category or permission the app has cannot arrive without a DPIA change-log row that says what
    it is for and how long it is kept, and a row for something the app no longer has has to be struck out."""
    if not dpia.exists() or CHANGE_HEAD not in dpia.read_text(encoding="utf-8"):
        return [f"{dpia}: no change-log table; add the head line {CHANGE_HEAD}"]
    rows = change_log(dpia)
    have = {(kind, name): (purpose, retention) for kind, name, purpose, retention in rows}
    want = [("Permission", name) for name in sorted(names)] + [("Data", name) for name in categories(dpia)]
    missing = []
    for kind, name in want:
        got = have.get((kind, name))
        if got is None:
            missing.append(f"{dpia}: no change-log row for {kind} {name} - add its purpose and retention")
        elif not got[0] or not got[1]:
            missing.append(f"{dpia}: the change-log row for {kind} {name} needs a purpose and a retention")
    missing.sort()
    known = {("Data", name) for name in categories(dpia)} | {("Permission", name) for name in names}
    stale = [
        f"{dpia}: the change-log row for {kind} {name} names nothing the app has - strike it out"
        for kind, name, purpose, retention in rows
        if (kind, name) not in known
    ]
    return missing + stale


def check(main: pathlib.Path = MAIN, flavors: dict[str, pathlib.Path] | None = None, docs: pathlib.Path = DOCS, dpia: pathlib.Path = DPIA) -> list[str]:
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
    errors.extend(check_approved(declared_everywhere()))
    named = prose_names(text)
    standard = set(declared(main)) | set(declared(flavors["standard"]))
    missing = sorted(standard - named)
    if missing:
        errors.append(f"{docs}: the prose does not name every standard permission: missing {missing}")
    errors.extend(check_dpia(set(APPROVED), dpia))
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
