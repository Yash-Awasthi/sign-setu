"""Keeps the README's version line generated from app/build.gradle.kts.

The build file is the only place a version is written. `--write` regenerates the
README line from it; the default mode exits 1 when the two disagree, so CI fails
on drift instead of a department reading the wrong number.
"""
import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
GRADLE = ROOT / "app" / "build.gradle.kts"
README = ROOT / "README.md"

LINE_START = "- Version "
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


def render(values: dict) -> str:
    """The one README line that carries a version."""
    release = RELEASES.get(values["minSdk"], values["minSdk"])
    return (
        f"{LINE_START}{values['versionName']} (version code {values['versionCode']}). "
        f"Android {release} (API {values['minSdk']}) and later, 64-bit; compiled against API "
        f"{values['compileSdk']}, target API {values['targetSdk']}; Kotlin and Jetpack Compose; "
        "one `app` module."
    )


def generated(text: str, values: dict) -> str:
    """README text with its version line replaced."""
    lines = text.splitlines(keepends=True)
    for i, line in enumerate(lines):
        if line.startswith(LINE_START):
            lines[i] = render(values) + "\n"
            return "".join(lines)
    raise ValueError("README.md has no '- Version ' line to generate")


def check(readme: pathlib.Path | None = None, gradle: pathlib.Path | None = None) -> list[str]:
    """Empty when the README version line matches the build; else the errors."""
    readme = README if readme is None else readme
    values = build_values(GRADLE if gradle is None else gradle)
    text = readme.read_text(encoding="utf-8")
    want = render(values)
    have = [line for line in text.splitlines() if line.startswith(LINE_START)]
    if not have:
        return ["README.md: no '- Version ' line"]
    return [] if have[0] == want else [f"README.md: {have[0]}\n  expected: {want}"]


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="rewrite the README line from the build")
    args = parser.parse_args(argv)
    try:
        if args.write:
            values = build_values()
            README.write_text(generated(README.read_text(encoding="utf-8"), values), encoding="utf-8")
            print(f"README.md: {render(values)}")
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
