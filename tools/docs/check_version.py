"""Keeps the version a document states generated from app/build.gradle.kts.

The build file is the only place a version is written. `--write` regenerates both
places that repeat it (the README line and the docs version row) from the build;
the default mode exits 1 when either disagrees, so CI fails on drift instead of a
department reading the wrong number.
"""
import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
GRADLE = ROOT / "app" / "build.gradle.kts"
README = ROOT / "README.md"
DOCS_README = ROOT / "docs" / "README.md"

LINE_START = "- Version "
ROW_START = "| Version |"

FIELDS = {
    "versionCode": re.compile(r'^\s*versionCode\s*=\s*(\d+)\s*$', re.MULTILINE),
    "versionName": re.compile(r'^\s*versionName\s*=\s*"([^"]+)"\s*$', re.MULTILINE),
    "minSdk": re.compile(r'^\s*minSdk\s*=\s*(\d+)\s*$', re.MULTILINE),
    "targetSdk": re.compile(r'^\s*targetSdk\s*=\s*(\d+)\s*$', re.MULTILINE),
    "compileSdk": re.compile(r'^\s*compileSdk\s*=\s*(\d+)\s*$', re.MULTILINE),
}


def build_values(gradle: pathlib.Path | None = None) -> dict:
    """The numbers the build actually uses; a missing or duplicated one is an error."""
    gradle = GRADLE if gradle is None else gradle
    text = gradle.read_text(encoding="utf-8")
    values = {}
    for name, pattern in FIELDS.items():
        found = pattern.findall(text)
        if len(found) != 1:
            raise ValueError(f"{gradle}: expected one {name}, found {len(found)}")
        values[name] = found[0]
    return values


# The API level a department reads as a release name; a new minSdk needs its name added here.
RELEASES = {"29": "10", "30": "11", "31": "12", "32": "12L", "33": "13", "34": "14", "35": "15", "36": "16"}


def render_line(values: dict) -> str:
    """The one README line that carries a version."""
    release = RELEASES.get(values["minSdk"], values["minSdk"])
    return (
        f"{LINE_START}{values['versionName']} (version code {values['versionCode']}). "
        f"Android {release} (API {values['minSdk']}) and later, 64-bit; compiled against API "
        f"{values['compileSdk']}, target API {values['targetSdk']}; Kotlin and Jetpack Compose; "
        "one `app` module."
    )


def render_row(values: dict) -> str:
    """The version row of the documentation index."""
    return f"{ROW_START} {values['versionName']} (version code {values['versionCode']}), applicationId `in.signsetu` |"


# Each document that repeats the version: its line prefix and how to render that line.
def parts() -> list[tuple[pathlib.Path, str, object]]:
    return [(README, LINE_START, render_line), (DOCS_README, ROW_START, render_row)]


def generated(text: str, values: dict, start: str, render) -> str:
    """Text with its version line replaced."""
    lines = text.splitlines(keepends=True)
    for i, line in enumerate(lines):
        if line.startswith(start):
            lines[i] = render(values) + "\n"
            return "".join(lines)
    raise ValueError(f"no '{start}' line to generate")


def check(readme: pathlib.Path | None = None, gradle: pathlib.Path | None = None, docs: pathlib.Path | None = None) -> list[str]:
    """Empty when both documents state the build's version; else the errors."""
    values = build_values(GRADLE if gradle is None else gradle)
    overrides = (README if readme is None else readme, DOCS_README if docs is None else docs)
    errors = []
    for (_, start, render), path in zip(parts(), overrides):
        have = [line for line in path.read_text(encoding="utf-8").splitlines() if line.startswith(start)]
        if not have:
            errors.append(f"{path}: no '{start}' line")
        elif have[0] != render(values):
            errors.append(f"{path}: {have[0]}\n  expected: {render(values)}")
    return errors


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="rewrite both documents from the build")
    args = parser.parse_args(argv)
    try:
        values = build_values()
        if args.write:
            for path, start, render in parts():
                path.write_text(generated(path.read_text(encoding="utf-8"), values, start, render), encoding="utf-8")
                print(f"{path}: {render(values)}")
            return 0
        errors = check()
    except ValueError as error:
        print(error, file=sys.stderr)
        return 1
    for error in errors:
        print(error, file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
