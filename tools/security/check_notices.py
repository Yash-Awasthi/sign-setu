"""Fail when THIRD-PARTY-NOTICES.md is not what notices.py renders from the current inputs.

Exit 0 when the file matches, 1 when it has drifted (with the first differing lines), 2 when an
input is missing. `--write` regenerates the file instead of checking it.
"""
import argparse
import difflib
import pathlib
import sys

import licences
import notices
from sbom import flavoured_lines


def differences(expected: str, actual: str, name="THIRD-PARTY-NOTICES.md") -> list[str]:
    """The first differing lines, so the failure names the change rather than dumping the file."""
    return list(difflib.unified_diff(actual.splitlines(), expected.splitlines(),
                                     fromfile=name, tofile=f"{name} (regenerated)", lineterm="",
                                     n=1))[:40]


def check(path=notices.OUT, lock=notices.LOCK, licence_map=None, version=None) -> list[str]:
    """Every disagreement between the committed notices and a fresh render."""
    import sbom_lockfile

    if not path.is_file():
        return [f"{path} does not exist; run python tools/security/notices.py"]
    lines = lock.read_text(encoding="utf-8").splitlines()
    licence_map = licences.load() if licence_map is None else licence_map
    expected = notices.render(lines, licence_map, version or sbom_lockfile.project_version())
    actual = path.read_text(encoding="utf-8")
    if expected == actual:
        return []
    return differences(expected, actual, path.name)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--notices", type=pathlib.Path, default=notices.OUT)
    parser.add_argument("--lock", type=pathlib.Path, default=notices.LOCK)
    parser.add_argument("--write", action="store_true", help="regenerate the file instead of checking")
    args = parser.parse_args(argv)
    if args.write:
        return notices.main(args.lock.read_text(encoding="utf-8").splitlines(), args.notices)
    try:
        errors = check(args.notices, args.lock)
    except OSError as error:
        print(error, file=sys.stderr)
        return 2
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        print(f"{args.notices.name}: stale; run python tools/security/notices.py", file=sys.stderr)
        return 1
    lines = args.lock.read_text(encoding="utf-8").splitlines()
    print(f"{args.notices.name}: {len(flavoured_lines(lines))} components, no drift")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
