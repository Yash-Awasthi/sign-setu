"""Checks the two machine-checkable claims of the release policy.

The newest section of CHANGELOG.md must name the version in app/build.gradle.kts, every section
must carry at least one line, and the supported-device matrix in RELEASE.md must cover every API
from minSdk to targetSdk. A release that skips a changelog entry, or a matrix that forgets an
Android release, fails here instead of at the first officer who installs it.
"""
import pathlib
import re
import sys

import check_version

ROOT = pathlib.Path(__file__).resolve().parents[2]
CHANGELOG = ROOT / "CHANGELOG.md"
POLICY = ROOT / "RELEASE.md"

HEADING = re.compile(r"^## \[([^\]]+)\] - (\d{4}-\d{2}-\d{2})\s*$", re.MULTILINE)
MATRIX_ROW = re.compile(r"^\|\s*(\d+)\s*\|", re.MULTILINE)
VERSION = re.compile(r"^(\d+)\.(\d+)\.(\d+)$")


def sections(text: str) -> list[tuple[str, str, str]]:
    """(version, date, body) per '## [x.y.z] - date' heading, in file order."""
    found = list(HEADING.finditer(text))
    out = []
    for i, match in enumerate(found):
        end = found[i + 1].start() if i + 1 < len(found) else len(text)
        out.append((match.group(1), match.group(2), text[match.end():end]))
    return out


def ordered(version: str) -> tuple[int, int, int]:
    match = VERSION.match(version)
    if not match:
        raise ValueError(f"not MAJOR.MINOR.PATCH: {version}")
    return tuple(int(part) for part in match.groups())


def check_changelog(text: str, version_name: str) -> list[str]:
    entries = sections(text)
    if not entries:
        return ["CHANGELOG.md: no '## [version] - YYYY-MM-DD' section"]
    errors = []
    newest = entries[0][0]
    if newest != version_name:
        errors.append(f"CHANGELOG.md: newest section is {newest}, build version is {version_name}")
    previous = None
    for version, _, body in entries:
        try:
            key = ordered(version)
        except ValueError as error:
            errors.append(f"CHANGELOG.md: {error}")
            continue
        if previous is not None and key >= previous:
            errors.append(f"CHANGELOG.md: {version} is not older than the section before it")
        previous = key
        if not any(line.lstrip().startswith("- ") for line in body.splitlines()):
            errors.append(f"CHANGELOG.md: section {version} has no entry")
    return errors


def check_matrix(text: str, min_sdk: str, target_sdk: str) -> list[str]:
    have = {int(api) for api in MATRIX_ROW.findall(text)}
    wanted = set(range(int(min_sdk), int(target_sdk) + 1))
    return [f"RELEASE.md: supported-device matrix has no row for API {api}" for api in sorted(wanted - have)]


def check(changelog: pathlib.Path | None = None, policy: pathlib.Path | None = None, gradle: pathlib.Path | None = None) -> list[str]:
    """Empty when the changelog and matrix agree with the build; else the errors."""
    values = check_version.build_values(gradle)
    changelog = CHANGELOG if changelog is None else changelog
    policy = POLICY if policy is None else policy
    return check_changelog(changelog.read_text(encoding="utf-8"), values["versionName"]) + check_matrix(
        policy.read_text(encoding="utf-8"), values["minSdk"], values["targetSdk"]
    )


def main() -> int:
    try:
        errors = check()
    except (OSError, ValueError) as error:
        print(error, file=sys.stderr)
        return 1
    for error in errors:
        print(error, file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())