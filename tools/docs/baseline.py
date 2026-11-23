"""Keeps the device baseline in the documents generated from the code that enforces it.

What a phone must be is decided by `app/build.gradle.kts` (min SDK, ABIs) and by
`LocalModel.kt` (the RAM and free-memory floors it refuses below), so those are the
only places the numbers live. `--write` regenerates the baseline line in `README.md`
and the Devices row in `docs/README.md`; the default mode exits 1 when either drifts.
"""
import argparse
import pathlib
import re
import sys

import check_version

ROOT = pathlib.Path(__file__).resolve().parents[2]
GRADLE = ROOT / "app" / "build.gradle.kts"
ASSETS = ROOT / "app" / "src" / "main" / "assets"
LOCAL_MODEL = ROOT / "app" / "src" / "main" / "java" / "app" / "signsetu" / "assistant" / "LocalModel.kt"

README_START = "- Runs on "
DOCS_ROW_START = "| Devices |"


def _mib(source: pathlib.Path, name: str) -> int:
    """A MiB constant from the Kotlin source, as the code writes it (`2_400L * 1024 * 1024`)."""
    text = source.read_text(encoding="utf-8")
    found = re.findall(rf"{name} = ([\d_]+)L \* 1024 \* 1024", text)
    if len(found) != 1:
        raise ValueError(f"{source}: expected one {name}, found {len(found)}")
    return int(found[0].replace("_", ""))


def facts(gradle: pathlib.Path | None = None, local_model: pathlib.Path | None = None, assets: pathlib.Path | None = None) -> dict:
    """The baseline, measured: SDK levels, ABIs, memory floors and the size of the shipped assets."""
    values = check_version.build_values(GRADLE if gradle is None else gradle)
    text = (GRADLE if gradle is None else gradle).read_text(encoding="utf-8")
    abis = re.search(r"abiFilters \+= listOf\(([^)]*)\)", text)
    if abis is None:
        raise ValueError("app/build.gradle.kts: no abiFilters list")
    shipped = assets if assets is not None else ASSETS
    size = sum(p.stat().st_size for p in shipped.rglob("*") if p.is_file())
    model = local_model if local_model is not None else LOCAL_MODEL
    return {
        **values,
        "abis": [a.strip().strip('"') for a in abis.group(1).split(",") if a.strip()],
        "ramMib": _mib(model, "MIN_MEMORY_BYTES"),
        "freeMib": _mib(model, "MIN_FREE_BYTES"),
        "assetsMb": f"{size / 1e6:.1f}",
    }


def render_line(f: dict) -> str:
    """The README line that states the baseline."""
    return (
        f"{README_START}{' and '.join(f['abis'])}; the on-device assistant runs on arm64 only, so x86_64 serves "
        f"the emulator. The assistant model loads only on a phone reporting at least {f['ramMib']} MiB of RAM and "
        f"{f['freeMib']} MiB free memory; the app's own assets are {f['assetsMb']} MB, and the model file the "
        f"department hands over is separate, so allow room for it as well."
    )


def render_row(f: dict) -> str:
    """The Devices row of the reference configuration."""
    return (
        f"{DOCS_ROW_START} Android {check_version.RELEASES.get(f['minSdk'], f['minSdk'])} (API {f['minSdk']}) or "
        f"later, {', '.join(f['abis'])}; compiled against API "
        f"{f['compileSdk']}, target API {f['targetSdk']}; {f['ramMib']} MiB RAM and {f['freeMib']} MiB free memory "
        f"for the optional model; {f['assetsMb']} MB of shipped assets |"
    )


def parts() -> list[tuple[pathlib.Path, str, object]]:
    return [(ROOT / "README.md", README_START, render_line), (ROOT / "docs" / "README.md", DOCS_ROW_START, render_row)]


def generated(text: str, values: dict, start: str, render) -> str:
    lines = text.splitlines(keepends=True)
    for i, line in enumerate(lines):
        if line.startswith(start):
            lines[i] = render(values) + "\n"
            return "".join(lines)
    raise ValueError(f"no '{start}' line to generate")


def check() -> list[str]:
    values = facts()
    errors = []
    for path, start, render in parts():
        have = [line for line in path.read_text(encoding="utf-8").splitlines() if line.startswith(start)]
        if not have:
            errors.append(f"{path}: no '{start}' line")
        elif have[0] != render(values):
            errors.append(f"{path}: {have[0]}\n  expected: {render(values)}")
    return errors


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="rewrite the baseline in both documents")
    args = parser.parse_args(argv)
    try:
        values = facts()
        if args.write:
            for path, start, render in parts():
                path.write_text(generated(path.read_text(encoding="utf-8"), values, start, render), encoding="utf-8")
                print(f"{path.name}: {render(values)}")
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
