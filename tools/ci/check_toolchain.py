"""The NDK and CMake a checkout builds with are one version, wherever they are named.

`app/build.gradle.kts` pins `ndkVersion` and the CMake version beside the native build's
`path`; `app/src/main/cpp/CMakeLists.txt` sets the minimum CMake it accepts; `README.md` and
`docs/README.md` state both for a person setting up a checkout. This script holds the expected
pair and fails when any of those places, or the SDK this machine has installed, disagrees.
"""
import argparse
import os
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
BUILD = ROOT / "app" / "build.gradle.kts"
CMAKE_LISTS = ROOT / "app" / "src" / "main" / "cpp" / "CMakeLists.txt"
README = ROOT / "README.md"
DOCS = ROOT / "docs" / "README.md"

EXPECTED_NDK = "30.0.16248370"
EXPECTED_CMAKE = "4.1.2"

# Distinguishes "no --sdk given, read the environment" from a caller that passes sdk=None to
# skip the installed-SDK check.
ENV_SDK = object()


def label(path: pathlib.Path) -> str:
    """A path as a reader knows it: repository-relative when inside the repository, else its name."""
    try:
        return path.resolve().relative_to(ROOT).as_posix()
    except ValueError:
        return path.name


def version_tuple(version: str) -> tuple[int, ...]:
    return tuple(int(part) for part in version.split("."))


def sdk_root(sdk: pathlib.Path | None | object = ENV_SDK) -> pathlib.Path | None:
    """The SDK root to check: the one given, else the environment's, else none."""
    if sdk is not ENV_SDK:
        return sdk
    value = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    return pathlib.Path(value) if value else None


def ndk_version(build: pathlib.Path | None = None) -> str:
    """The single `ndkVersion = "..."` of a build script."""
    build = BUILD if build is None else build
    found = re.findall(r'(?m)^\s*ndkVersion\s*=\s*"([^"]+)"', build.read_text(encoding="utf-8"))
    if len(found) != 1:
        raise ValueError(f"{label(build)}: {len(found)} ndkVersion assignments, expected one")
    return found[0]


def cmake_version(build: pathlib.Path | None = None) -> str:
    """The `version = "..."` beside the CMakeLists path in the app's externalNativeBuild block."""
    build = BUILD if build is None else build
    lines = build.read_text(encoding="utf-8").splitlines()
    for i, line in enumerate(lines):
        if 'path = file("src/main/cpp/CMakeLists.txt")' not in line:
            continue
        indent = len(line) - len(line.lstrip())
        block = [line]
        for following in lines[i + 1:]:
            if following.strip().startswith("}") and len(following) - len(following.lstrip()) <= indent:
                break
            block.append(following)
        found = re.findall(r'\bversion\s*=\s*"([^"]+)"', "\n".join(block))
        if len(found) != 1:
            raise ValueError(
                f"{label(build)}: {len(found)} cmake version assignments beside the CMakeLists path, expected one"
            )
        return found[0]
    raise ValueError(
        f'{label(build)}: no externalNativeBuild cmake block with path = file("src/main/cpp/CMakeLists.txt")'
    )


def cmake_minimum(cmake_lists: pathlib.Path | None = None) -> str:
    """The version `cmake_minimum_required(VERSION ...)` asks for."""
    cmake_lists = CMAKE_LISTS if cmake_lists is None else cmake_lists
    text = cmake_lists.read_text(encoding="utf-8")
    found = re.findall(r"cmake_minimum_required\(\s*VERSION\s+([0-9]+(?:\.[0-9]+)*)\s*\)", text)
    if len(found) != 1:
        raise ValueError(f"{label(cmake_lists)}: {len(found)} cmake_minimum_required calls, expected one")
    return found[0]


def check(
    build: pathlib.Path | None = None,
    cmake_lists: pathlib.Path | None = None,
    readme: pathlib.Path | None = None,
    docs: pathlib.Path | None = None,
    sdk: pathlib.Path | None | object = ENV_SDK,
) -> list[str]:
    build = BUILD if build is None else build
    cmake_lists = CMAKE_LISTS if cmake_lists is None else cmake_lists
    readme = README if readme is None else readme
    docs = DOCS if docs is None else docs
    errors: list[str] = []

    try:
        ndk = ndk_version(build)
    except ValueError as exc:
        errors.append(str(exc))
    else:
        if ndk != EXPECTED_NDK:
            errors.append(f'{label(build)}: ndkVersion is "{ndk}" but expected "{EXPECTED_NDK}"')

    try:
        cmake = cmake_version(build)
    except ValueError as exc:
        errors.append(str(exc))
    else:
        if cmake != EXPECTED_CMAKE:
            errors.append(f'{label(build)}: cmake version is "{cmake}" but expected "{EXPECTED_CMAKE}"')

    try:
        minimum = cmake_minimum(cmake_lists)
    except ValueError as exc:
        errors.append(str(exc))
    else:
        if version_tuple(minimum) > version_tuple(EXPECTED_CMAKE):
            errors.append(
                f'{label(cmake_lists)}: cmake_minimum_required is {minimum} '
                f'but expected at most "{EXPECTED_CMAKE}"'
            )

    for path in (readme, docs):
        text = path.read_text(encoding="utf-8")
        for phrase in (f"NDK `{EXPECTED_NDK}`", f"CMake {EXPECTED_CMAKE}"):
            if phrase not in text:
                errors.append(
                    f'{label(path)}: missing "{phrase}" '
                    f'(expected NDK "{EXPECTED_NDK}" and CMake "{EXPECTED_CMAKE}")'
                )

    root = sdk_root(sdk)
    if root is not None:
        for kind, version in (("ndk", EXPECTED_NDK), ("cmake", EXPECTED_CMAKE)):
            if not (root / kind / version).is_dir():
                errors.append(f'{root.as_posix()}: missing {kind} directory for expected version "{version}"')

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--build", type=pathlib.Path, help="build script to read (default: app/build.gradle.kts)")
    parser.add_argument("--cmake-lists", type=pathlib.Path, help="CMakeLists.txt to read")
    parser.add_argument("--readme", type=pathlib.Path, help="top-level README to read")
    parser.add_argument("--docs", type=pathlib.Path, help="docs/README.md to read")
    parser.add_argument(
        "--sdk", type=pathlib.Path, default=ENV_SDK, help="SDK root (default: $ANDROID_HOME or $ANDROID_SDK_ROOT)"
    )
    args = parser.parse_args()
    errors = check(args.build, args.cmake_lists, args.readme, args.docs, args.sdk)
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    print(
        f"NDK {EXPECTED_NDK} and CMake {EXPECTED_CMAKE}: "
        "build script, CMake minimum, installed SDK and docs agree"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
