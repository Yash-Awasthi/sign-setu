"""Two builds of one commit must produce the same APK, byte for byte.

`check_reproducible.py apk1 apk2` compares two already-built APKs: whole-file SHA-256 and
size, every zip entry in central-directory order (name, CRC32, uncompressed size, compressed
size, timestamp), and the APK Signing Block. When both APKs are signed, the signature block
is expected to differ and is excluded: the pair is identical iff everything outside the block
is byte-identical and every zip entry matches. When both are unsigned, as this repo's release
APK is, the rule is a whole-file SHA-256 match.

`check_reproducible.py --build` proves it end to end. It extracts `git archive HEAD` into a
fresh directory under C:/tmp, so the compared tree is the committed HEAD and not the dirty
worktree, builds `:app:assembleStandardRelease` twice with the Gradle output deleted between
runs, and compares the two APKs with the rule above. `local.properties` is untracked, so it
is copied into the extracted tree; its `sdk.dir`, `gatewayUrl` and `assistUserAgent` values
feed the Android SDK location and the BuildConfig fields of the same names. The script
prints every input it can see and one JSON line, so a run can be pasted into evidence.

Exit codes: 0 identical, 1 different, 2 bad input or a build/tooling failure.
"""
import argparse
import hashlib
import json
import locale
import os
import pathlib
import shutil
import struct
import subprocess
import sys
import tarfile
import tempfile
import time
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
BUILD_SCRIPT = ROOT / "app" / "build.gradle.kts"
WRAPPER_PROPERTIES = ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"
LOCAL_PROPERTIES = ROOT / "local.properties"
LOCAL_PROPERTIES_KEYS = ("sdk.dir", "gatewayUrl", "assistUserAgent")
APK_DIR = pathlib.Path("app") / "build" / "outputs" / "apk" / "standard" / "release"
GRADLE_TASK = ":app:assembleStandardRelease"
BUILD_ARGS = ["--console=plain", "--no-build-cache", GRADLE_TASK]
TEMP_PARENT = pathlib.Path("C:/tmp")
SIGNING_MAGIC = b"APK Sig Block 42"
EOCD_MAGIC = b"PK\x05\x06"
CHUNK = 1 << 20


class CompareError(Exception):
    """An input that cannot be read as an APK."""


def ascii_safe(text) -> str:
    """One printable line: non-ASCII, if any, is escaped rather than written raw."""
    return str(text).encode("ascii", "backslashreplace").decode("ascii")


def say(text: str = "") -> None:
    print(ascii_safe(text), flush=True)


def warn(text: str) -> None:
    print(ascii_safe(text), file=sys.stderr, flush=True)


def stamp(seconds: float) -> str:
    return time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(seconds))


def days_from_civil(year: int, month: int, day: int) -> int:
    """Days since 1970-01-01 for a proleptic Gregorian date (Howard Hinnant's algorithm)."""
    year -= month <= 2
    era = (year if year >= 0 else year - 399) // 400
    year_of_era = year - era * 400
    day_of_year = (153 * (month + (-3 if month > 2 else 9)) + 2) // 5 + day - 1
    day_of_era = year_of_era * 365 + year_of_era // 4 - year_of_era // 100 + day_of_year
    return era * 146097 + day_of_era - 719468


def today_epoch_day() -> int:
    now = time.localtime()
    return days_from_civil(now.tm_year, now.tm_mon, now.tm_mday)


def epoch_day_date(epoch_day: int) -> str:
    return time.strftime("%Y-%m-%d", time.gmtime(epoch_day * 86400))


def sha256_file(path: pathlib.Path) -> tuple[str, int]:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as handle:
        while True:
            block = handle.read(CHUNK)
            if not block:
                break
            digest.update(block)
            size += len(block)
    return digest.hexdigest(), size


def signing_block(path: pathlib.Path) -> tuple[int, int, int] | None:
    """(start, total length, central-directory offset) of the APK Signing Block, or None.

    The block sits between the last entry's data and the central directory: a leading uint64
    size, the id-value pairs, a trailing uint64 size and the 16-byte magic. The EOCD record
    gives the central-directory offset.
    """
    size = path.stat().st_size
    tail_length = min(size, 22 + 65535)
    with path.open("rb") as handle:
        handle.seek(size - tail_length)
        tail = handle.read(tail_length)
        found = tail.rfind(EOCD_MAGIC)
        if found < 0 or found + 22 > len(tail):
            return None
        cd_offset = struct.unpack_from("<I", tail, found + 16)[0]
        if cd_offset < 32 or cd_offset > size:
            return None
        handle.seek(cd_offset - 24)
        trailer = handle.read(24)
        if len(trailer) != 24 or trailer[8:24] != SIGNING_MAGIC:
            return None
        block_size = struct.unpack_from("<Q", trailer, 0)[0]
        start = cd_offset - 8 - block_size
        if start < 0:
            return None
        handle.seek(start)
        leading = handle.read(8)
        if len(leading) != 8 or struct.unpack("<Q", leading)[0] != block_size:
            return None
    return start, block_size + 8, cd_offset


def zip_entries(path: pathlib.Path) -> list[dict]:
    try:
        with zipfile.ZipFile(path) as archive:
            return [
                {
                    "name": info.filename,
                    "crc32": info.CRC,
                    "uncompressed": info.file_size,
                    "compressed": info.compress_size,
                    "date_time": list(info.date_time),
                }
                for info in archive.infolist()
            ]
    except (zipfile.BadZipFile, OSError) as exc:
        raise CompareError(f"{path.as_posix()}: not a readable zip/APK: {exc}") from exc


def first_difference(path_a: pathlib.Path, offset_a: int, path_b: pathlib.Path, offset_b: int, length: int) -> int | None:
    """Offset of the first differing byte in two equal-length regions, or None."""
    with path_a.open("rb") as handle_a, path_b.open("rb") as handle_b:
        handle_a.seek(offset_a)
        handle_b.seek(offset_b)
        done = 0
        while done < length:
            want = min(CHUNK, length - done)
            block_a = handle_a.read(want)
            block_b = handle_b.read(want)
            if block_a != block_b:
                for index in range(min(len(block_a), len(block_b))):
                    if block_a[index] != block_b[index]:
                        return done + index
                return done + min(len(block_a), len(block_b))
            done += len(block_a)
    return None


def whole_file_difference(path_a: pathlib.Path, size_a: int, path_b: pathlib.Path, size_b: int) -> int | None:
    common = min(size_a, size_b)
    found = first_difference(path_a, 0, path_b, 0, common)
    if found is not None:
        return found
    return common if size_a != size_b else None


def compare_apks(apk1: pathlib.Path, apk2: pathlib.Path) -> tuple[bool, list[str], dict]:
    """Compare two APKs and return (identical, printable lines, JSON payload)."""
    lines: list[str] = []
    reasons: list[str] = []
    payload: dict = {"mode": "compare"}

    facts = {}
    for key, path in (("apk1", apk1), ("apk2", apk2)):
        try:
            sha, size = sha256_file(path)
        except OSError as exc:
            raise CompareError(f"{path.as_posix()}: cannot read: {exc}") from exc
        facts[key] = {"path": path.as_posix(), "sha256": sha, "size": size}
        payload[key] = facts[key]
        lines.append(f"{key}: {path.as_posix()}")
        lines.append(f"{key} sha256: {sha}")
        lines.append(f"{key} size: {size}")

    blocks = {}
    for key, path in (("apk1", apk1), ("apk2", apk2)):
        blocks[key] = signing_block(path)
        if blocks[key] is None:
            lines.append(f"{key} signing block: absent")
            payload[f"{key}_signing_block"] = {"present": False, "start": None, "length": None}
        else:
            start, length, cd_offset = blocks[key]
            lines.append(f"{key} signing block: present start {start} length {length}")
            payload[f"{key}_signing_block"] = {
                "present": True,
                "start": start,
                "length": length,
                "central_directory": cd_offset,
            }

    entries = {}
    for key, path in (("apk1", apk1), ("apk2", apk2)):
        entries[key] = zip_entries(path)
        lines.append(f"{key} zip entries: {len(entries[key])}")
        payload[f"{key}_zip_entries"] = len(entries[key])

    names_a = [entry["name"] for entry in entries["apk1"]]
    names_b = [entry["name"] for entry in entries["apk2"]]
    order_same = names_a == names_b
    payload["zip_order_same"] = order_same
    if order_same:
        lines.append("zip order: same")
    else:
        lines.append("zip order: differs")
        index = next(
            (i for i, (a, b) in enumerate(zip(names_a, names_b)) if a != b),
            min(len(names_a), len(names_b)),
        )
        left = names_a[index] if index < len(names_a) else "(end)"
        right = names_b[index] if index < len(names_b) else "(end)"
        lines.append(f"reason: zip entry order differs at index {index}: {left} -> {right}")
        reasons.append(f"zip entry order differs at index {index}")

    only_a = sorted(set(names_a) - set(names_b))
    only_b = sorted(set(names_b) - set(names_a))
    payload["entries_only_in_apk1"] = only_a
    payload["entries_only_in_apk2"] = only_b
    lines.append("entries only in apk1: " + (", ".join(only_a) if only_a else "none"))
    lines.append("entries only in apk2: " + (", ".join(only_b) if only_b else "none"))
    for name in only_a:
        lines.append(f"reason: entry {name} exists only in apk1")
        reasons.append(f"entry {name} exists only in apk1")
    for name in only_b:
        lines.append(f"reason: entry {name} exists only in apk2")
        reasons.append(f"entry {name} exists only in apk2")

    by_name_a = {entry["name"]: entry for entry in entries["apk1"]}
    by_name_b = {entry["name"]: entry for entry in entries["apk2"]}
    differing: list[str] = []
    for name in names_a:
        if name not in by_name_b:
            continue
        left = by_name_a[name]
        right = by_name_b[name]
        if left == right:
            continue
        differing.append(name)
        for field in ("crc32", "uncompressed", "compressed", "date_time"):
            if left[field] != right[field]:
                lines.append(f"entry {name}: {field} {left[field]} -> {right[field]}")
                lines.append(f"reason: zip entry {name} differs in {field} ({left[field]} -> {right[field]})")
                reasons.append(f"zip entry {name} differs in {field}")
    payload["differing_entries"] = differing
    if differing:
        lines.append("differing entries: " + ", ".join(differing))
    else:
        lines.append("differing entries: none")

    duplicate_a = len(names_a) != len(set(names_a))
    duplicate_b = len(names_b) != len(set(names_b))
    payload["duplicate_entry_names"] = {"apk1": duplicate_a, "apk2": duplicate_b}
    if duplicate_a or duplicate_b:
        lines.append(f"reason: duplicate zip entry names (apk1 {duplicate_a}, apk2 {duplicate_b})")
        reasons.append("duplicate zip entry names")

    entries_match = (
        len(names_a) == len(names_b)
        and order_same
        and not only_a
        and not only_b
        and not differing
        and not duplicate_a
        and not duplicate_b
    )
    whole_file_match = facts["apk1"]["sha256"] == facts["apk2"]["sha256"]
    lines.append("whole file sha256: " + ("match" if whole_file_match else "differs"))

    signed_a = blocks["apk1"] is not None
    signed_b = blocks["apk2"] is not None
    result: str

    if signed_a and signed_b:
        start_a, length_a, cd_a = blocks["apk1"]
        start_b, length_b, cd_b = blocks["apk2"]
        lines.append("signed: comparing everything outside the APK Signing Block")
        outside_same = True
        if start_a != start_b:
            outside_same = False
            lines.append(f"reason: signing blocks start at different offsets ({start_a} and {start_b})")
            reasons.append("signing blocks start at different offsets")
        prefix_length = min(start_a, start_b)
        prefix_diff = first_difference(apk1, 0, apk2, 0, prefix_length)
        if prefix_diff is not None:
            outside_same = False
            lines.append(f"reason: bytes before the signing block differ at offset {prefix_diff}")
            reasons.append(f"bytes before the signing block differ at offset {prefix_diff}")
        suffix_length_a = facts["apk1"]["size"] - cd_a
        suffix_length_b = facts["apk2"]["size"] - cd_b
        if suffix_length_a != suffix_length_b:
            outside_same = False
            lines.append(
                f"reason: bytes from the central directory to the end differ in length "
                f"({suffix_length_a} and {suffix_length_b})"
            )
            reasons.append("bytes after the signing block differ in length")
        else:
            suffix_diff = first_difference(apk1, cd_a, apk2, cd_b, suffix_length_a)
            if suffix_diff is not None:
                outside_same = False
                lines.append(f"reason: bytes from the central directory to the end differ at offset {suffix_diff}")
                reasons.append(f"bytes after the signing block differ at offset {suffix_diff}")
        lines.append("bytes outside the signing block: " + ("identical" if outside_same else "differ"))
        payload["bytes_outside_signing_block_same"] = outside_same
        if outside_same and entries_match:
            result = "identical outside the signing block"
        else:
            result = "different"
    elif not signed_a and not signed_b:
        lines.append("unsigned, comparing all bytes")
        if whole_file_match:
            result = "identical"
        else:
            result = "different"
            offset = whole_file_difference(apk1, facts["apk1"]["size"], apk2, facts["apk2"]["size"])
            lines.append(
                f"reason: whole-file bytes differ at offset {offset} "
                f"(sizes {facts['apk1']['size']} and {facts['apk2']['size']})"
            )
            reasons.append(f"whole-file bytes differ at offset {offset}")
    else:
        result = "different"
        lines.append(
            "reason: APK Signing Block present in "
            + ("apk1 but not apk2" if signed_a else "apk2 but not apk1")
        )
        reasons.append("APK Signing Block present in one APK only")

    identical = result != "different"
    payload["result"] = result
    payload["reasons"] = reasons
    lines.append(f"verdict: {result}")
    return identical, lines, payload


def compare_main(apk1: pathlib.Path, apk2: pathlib.Path) -> int:
    for path in (apk1, apk2):
        if not path.is_file():
            warn(f"error: {path.as_posix()}: no such file")
            return 2
    try:
        identical, lines, payload = compare_apks(apk1, apk2)
    except CompareError as exc:
        warn(f"error: {exc}")
        return 2
    for line in lines:
        say(line)
    say(json.dumps(payload, sort_keys=True))
    return 0 if identical else 1


def read_properties(path: pathlib.Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def build_value(build: pathlib.Path, key: str) -> str:
    """The number or quoted string a `key = ...` line of the build script carries."""
    for line in build.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped.startswith(key) or "=" not in stripped:
            continue
        value = stripped.split("=", 1)[1].strip()
        if value.startswith('"') and value.count('"') >= 2:
            return value.split('"')[1]
        digits = ""
        for char in value:
            if char.isdigit() or (char == "-" and not digits):
                digits += char
            else:
                break
        if digits:
            return digits
    return "unknown"


def cmake_version_from(build: pathlib.Path) -> str:
    lines = build.read_text(encoding="utf-8").splitlines()
    for index, line in enumerate(lines):
        if 'path = file("src/main/cpp/CMakeLists.txt")' not in line:
            continue
        indent = len(line) - len(line.lstrip())
        for following in lines[index + 1:]:
            if following.strip().startswith("}") and len(following) - len(following.lstrip()) <= indent:
                break
            if "version" in following and '"' in following:
                return following.split('"')[1]
    return "unknown"


def wrapper_version(path: pathlib.Path) -> str:
    if not path.is_file():
        return "unknown"
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("distributionUrl="):
            url = line.split("=", 1)[1].strip().replace("\\:", ":")
            name = url.rsplit("/", 1)[-1]
            if name.startswith("gradle-"):
                rest = name[len("gradle-"):]
                for suffix in ("-bin.zip", "-all.zip"):
                    if rest.endswith(suffix):
                        return rest[: -len(suffix)]
                return rest
    return "unknown"


def run_capture(command: list[str], cwd: pathlib.Path | None = None) -> tuple[int, str, str]:
    process = subprocess.run(
        command,
        cwd=None if cwd is None else str(cwd),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    return process.returncode, process.stdout, process.stderr


def git_output(*args: str) -> str:
    code, out, err = run_capture(["git", "-C", str(ROOT), *args])
    if code != 0:
        raise RuntimeError(f"git {' '.join(args)} exited {code}: {err.strip()}")
    return out


def gradle_command(tree: pathlib.Path, extra: list[str]) -> list[str] | None:
    if os.name == "nt":
        script = tree / "gradlew.bat"
        if not script.is_file():
            return None
        return [os.environ.get("COMSPEC", "cmd.exe"), "/c", str(script), *extra]
    script = tree / "gradlew"
    if not script.is_file():
        return None
    return [str(script), *extra]


def extract_head(destination: pathlib.Path) -> None:
    command = ["git", "-C", str(ROOT), "archive", "--format=tar", "HEAD"]
    say("extract: " + " ".join(command))
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    try:
        # filter="data" rejects absolute paths, "..", escaping links and device files.
        with tarfile.open(fileobj=process.stdout, mode="r|") as archive:  # nosemgrep: trailofbits.python.tarfile-extractall-traversal.tarfile-extractall-traversal
            archive.extractall(destination, filter="data")
    except tarfile.TarError as exc:
        process.kill()
        process.wait()
        raise RuntimeError(f"git archive HEAD | tar failed: {exc}") from exc
    code = process.wait()
    if code != 0:
        raise RuntimeError(f"git archive HEAD exited {code}: {process.stderr.read().decode('utf-8', 'replace').strip()}")


def run_build_command(tree: pathlib.Path, label: str) -> float:
    command = gradle_command(tree, BUILD_ARGS)
    if command is None:
        raise RuntimeError("no Gradle wrapper in the extracted tree")
    printable = "gradlew.bat " + " ".join(BUILD_ARGS)
    started = time.time()
    say(f"build {label} start: {stamp(started)}")
    say(f"build {label} command: {printable}")
    process = subprocess.Popen(
        command,
        cwd=str(tree),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )
    assert process.stdout is not None
    process.stdout.reconfigure(encoding="utf-8", errors="replace")
    for line in process.stdout:
        say(line.rstrip("\n"))
    code = process.wait()
    seconds = time.time() - started
    if code != 0:
        raise RuntimeError(f"build {label} failed with exit {code}: {printable}")
    say(f"build {label} end: {stamp(time.time())} wall seconds {seconds:.1f}")
    return seconds


def find_apk(tree: pathlib.Path) -> pathlib.Path:
    release = tree / APK_DIR
    found = sorted(release.glob("*.apk"))
    unsigned = [path for path in found if path.name.endswith("-unsigned.apk")]
    if len(unsigned) == 1:
        return unsigned[0]
    if len(found) == 1:
        return found[0]
    raise RuntimeError(
        f"expected one APK under {release.as_posix()}, found {[path.name for path in found]}"
    )


def used_epoch_day(tree: pathlib.Path) -> int | None:
    """The BUILD_EPOCH_DAY the build actually wrote into BuildConfig.java, if it is still there."""
    generated = tree / "app" / "build" / "generated"
    if not generated.is_dir():
        return None
    for path in generated.rglob("BuildConfig.java"):
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for line in text.splitlines():
            if "BUILD_EPOCH_DAY" not in line or "=" not in line:
                continue
            value = line.split("=", 1)[1].strip()
            digits = ""
            for char in value:
                if char.isdigit() or (char == "-" and not digits):
                    digits += char
                else:
                    break
            if digits:
                try:
                    return int(digits)
                except ValueError:
                    continue
    return None


def print_epoch_day(label: str, epoch_day: int | None) -> None:
    if epoch_day is None:
        say(f"{label}: not found in generated BuildConfig.java")
    else:
        say(f"{label}: {epoch_day} ({epoch_day_date(epoch_day)})")


def stop_daemons(tree: pathlib.Path) -> None:
    command = gradle_command(tree, ["--stop"])
    if command is None:
        return
    code, out, err = run_capture(command, cwd=tree)
    say(f"gradle --stop: exit {code}")
    message = (out + err).strip().splitlines()
    if message:
        say("  " + message[-1])


def print_manifest(tree: pathlib.Path, local: dict[str, str], head: str, dirty: list[str]) -> None:
    say("inputs:")
    say(f"  source repository: {ROOT.as_posix()}")
    say(f"  source commit: {head}")
    if dirty:
        say("  worktree status: dirty")
        for line in dirty:
            say(f"    {line}")
    else:
        say("  worktree status: clean")
    code, out, err = run_capture(["java", "-version"])
    java = (out + err).strip().splitlines()
    if code != 0 and not java:
        say(f"  java: unavailable (exit {code})")
    else:
        for line in java:
            say(f"  java: {line}")
    say(f"  gradle wrapper: {wrapper_version(WRAPPER_PROPERTIES)}")
    say(f"  AGP: {build_value(ROOT / 'gradle' / 'libs.versions.toml', 'agp')}")
    say(f"  ANDROID_HOME: {os.environ.get('ANDROID_HOME', 'unset')}")
    say(f"  compileSdk: {build_value(tree / 'app' / 'build.gradle.kts', 'compileSdk')}")
    say(f"  targetSdk: {build_value(tree / 'app' / 'build.gradle.kts', 'targetSdk')}")
    say(f"  minSdk: {build_value(tree / 'app' / 'build.gradle.kts', 'minSdk')}")
    say(f"  ndkVersion: {build_value(tree / 'app' / 'build.gradle.kts', 'ndkVersion')}")
    say(f"  cmake: {cmake_version_from(tree / 'app' / 'build.gradle.kts')}")
    if local:
        for key in sorted(local):
            say(f"  local.properties {key}={local[key]}")
    else:
        say("  local.properties: absent")
    say(f"  timezone: {time.tzname}")
    say(f"  locale: {locale.getlocale()} preferred encoding {locale.getpreferredencoding(False)}")
    epoch_day = today_epoch_day()
    say(
        f"  BUILD_EPOCH_DAY at configuration time: {epoch_day} ({epoch_day_date(epoch_day)}), "
        "from LocalDate.now().toEpochDay() in a ValueSource in app/build.gradle.kts"
    )


def remove_tree(path: pathlib.Path, keep: bool) -> None:
    if keep:
        say(f"temp dir kept: {path.as_posix()}")
        return
    try:
        shutil.rmtree(path)
        say(f"temp dir removed: {path.as_posix()}")
    except OSError as exc:
        say(f"warning: could not remove temp dir {path.as_posix()}: {exc}")


def build_main(keep: bool) -> int:
    try:
        TEMP_PARENT.mkdir(parents=True, exist_ok=True)
        tree = pathlib.Path(tempfile.mkdtemp(prefix="repro-", dir=str(TEMP_PARENT)))
    except OSError as exc:
        warn(f"error: cannot create a temp dir under {TEMP_PARENT.as_posix()}: {exc}")
        return 2

    payload: dict = {"mode": "build", "temp_dir": tree.as_posix() if keep else None}
    exit_code: int | None = None
    try:
        head = git_output("rev-parse", "HEAD").strip()
        dirty = [line for line in git_output("status", "--porcelain").splitlines() if line]
        say(f"temp dir: {tree.as_posix()}")
        extract_head(tree)

        local: dict[str, str] = {}
        if LOCAL_PROPERTIES.is_file():
            shutil.copyfile(LOCAL_PROPERTIES, tree / "local.properties")
            local = read_properties(LOCAL_PROPERTIES)
            say(
                "local.properties: copied into the build tree; its sdk.dir, gatewayUrl and "
                "assistUserAgent values supply the SDK path and the BuildConfig fields"
            )
        else:
            say("local.properties: absent in the source checkout; the build has default values")

        print_manifest(tree, local, head, dirty)
        payload["commit"] = head
        payload["worktree_dirty"] = dirty

        seconds_a = run_build_command(tree, "A")
        apk_a = find_apk(tree)
        say(f"build A apk: {apk_a.relative_to(tree).as_posix()}")
        sha_a, size_a = sha256_file(apk_a)
        say(f"build A apk sha256: {sha_a} size {size_a}")
        epoch_a = used_epoch_day(tree)
        print_epoch_day("build A BUILD_EPOCH_DAY used", epoch_a)
        first = tree / "apk1.apk"
        shutil.copyfile(apk_a, first)
        payload["build_a"] = {
            "seconds": round(seconds_a, 1),
            "sha256": sha_a,
            "size": size_a,
            "build_epoch_day": epoch_a,
        }

        shutil.rmtree(tree / "app" / "build")
        shutil.rmtree(tree / "app" / ".cxx", ignore_errors=True)
        say("deleted app/build and app/.cxx before the second build")

        seconds_b = run_build_command(tree, "B")
        apk_b = find_apk(tree)
        say(f"build B apk: {apk_b.relative_to(tree).as_posix()}")
        sha_b, size_b = sha256_file(apk_b)
        say(f"build B apk sha256: {sha_b} size {size_b}")
        epoch_b = used_epoch_day(tree)
        print_epoch_day("build B BUILD_EPOCH_DAY used", epoch_b)
        second = tree / "apk2.apk"
        shutil.copyfile(apk_b, second)
        payload["build_b"] = {
            "seconds": round(seconds_b, 1),
            "sha256": sha_b,
            "size": size_b,
            "build_epoch_day": epoch_b,
        }

        identical, lines, compared = compare_apks(first, second)
        for line in lines:
            say(line)
        payload.update(compared)
        payload["mode"] = "build"
        exit_code = 0 if identical else 1
    except (RuntimeError, OSError, CompareError) as exc:
        warn(f"error: {exc}")
        payload["result"] = "error"
        payload["error"] = ascii_safe(str(exc))
        exit_code = 2
    finally:
        stop_daemons(tree)
        remove_tree(tree, keep)

    say(json.dumps(payload, sort_keys=True))
    return exit_code


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--build", action="store_true", help="build HEAD twice under C:/tmp and compare")
    parser.add_argument("--keep", action="store_true", help="keep the temporary build tree")
    parser.add_argument("apk1", nargs="?", type=pathlib.Path, help="first APK to compare")
    parser.add_argument("apk2", nargs="?", type=pathlib.Path, help="second APK to compare")
    args = parser.parse_args()
    if args.build:
        if args.apk1 is not None or args.apk2 is not None:
            parser.error("--build takes no APK arguments")
        return build_main(args.keep)
    if args.keep:
        parser.error("--keep only applies with --build")
    if args.apk1 is None or args.apk2 is None:
        parser.error("two APK paths are required unless --build is given")
    return compare_main(args.apk1, args.apk2)


if __name__ == "__main__":
    sys.exit(main())
