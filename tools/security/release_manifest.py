"""Write and check the release evidence beside the APKs: SHA256SUMS, the SBOM and PROVENANCE.json.

`release_manifest.py release/0.8.52` reads the APKs named `sign-setu-<version>-<abi>.apk` in that
directory (plus the SBOM, taken from `app/build/sbom/` or regenerated from `app/gradle.lockfile`)
and writes three files there:

  SHA256SUMS        one `<sha256>  <name>` line per APK and for the SBOM, so `sha256sum -c` works
  PROVENANCE.json   commit, version, each artefact's path/size/sha256/signing status, the SHA-256
                    of `gradle/verification-metadata.xml`, the lockfile digests, the SBOM producer
  (the SBOM itself, CycloneDX JSON, from the resolved release runtime classpath)

The signing status is read from each APK's bytes, not taken on trust: an APK Signing Block
immediately before the central directory of the zip, or a `META-INF/*.RSA|DSA|EC` entry, means
signed. A release APK built without the release keystore is recorded `unsigned` with
`shippable: false`, because it is not installable as a release.

`release_manifest.py --check <dir>` recomputes every hash, fails on one byte of difference, and
fails when an APK or the SBOM present in the directory is missing from SHA256SUMS. The digests of
`gradle/verification-metadata.xml` and the two lockfiles are compared with their bytes at the
recorded commit (read through git, so a later HEAD cannot excuse a wrong digest), the Gradle
distribution with the pin in `gradle/wrapper/gradle-wrapper.properties`, and the SBOM with what a
CycloneDX document must be: a specVersion, and a name, version and purl on every component. Files
listed but absent (the published APKs are never committed) are reported and skipped.

Exit codes: 0 the evidence matches, 1 a mismatch, 2 bad input.
"""
import argparse
import datetime
import hashlib
import json
import pathlib
import re
import struct
import subprocess
import sys
import zipfile

from sbom import shipped_lines
from sbom_lockfile import document as sbom_document, commit_sha, project_version

ROOT = pathlib.Path(__file__).resolve().parents[2]
LOCK = ROOT / "app" / "gradle.lockfile"
SETTINGS_LOCK = ROOT / "settings-gradle.lockfile"
VERIFICATION = ROOT / "gradle" / "verification-metadata.xml"
BUILD = ROOT / "app" / "build.gradle.kts"
SBOM_BUILD = ROOT / "app" / "build" / "sbom" / "sign-setu-release.cdx.json"
SBOM_NAME = "sign-setu-release.cdx.json"
SUMS_NAME = "SHA256SUMS"
PROVENANCE_NAME = "PROVENANCE.json"
API = "1"
SIGNING_MAGIC = b"APK Sig Block 42"
EOCD = b"PK\x05\x06"
EOCD_MIN = 22
EOCD_WINDOW = 1 << 16
CHUNK = 1 << 20


class EvidenceError(Exception):
    """An input that cannot be evidence."""


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(CHUNK), b""):
            digest.update(block)
    return digest.hexdigest()


def git_bytes(root: pathlib.Path, rel: str, commit) -> bytes | None:
    """The bytes of `rel` at `commit` from git, or None when git does not have that object."""
    if not commit or commit == "unknown":
        return None
    try:
        result = subprocess.run(["git", "-C", str(root), "show", f"{commit}:{rel}"],
                                capture_output=True, check=False)
    except OSError:
        return None
    return result.stdout if result.returncode == 0 else None


def digest_at(path: pathlib.Path, commit, root: pathlib.Path = ROOT) -> tuple[str, str]:
    """(sha256, where) of the file's bytes at `commit`, else of the working tree when git lacks them."""
    try:
        rel = path.relative_to(root).as_posix()
    except ValueError:
        rel = None
    if rel:
        data = git_bytes(root, rel, commit)
        if data is not None:
            return hashlib.sha256(data).hexdigest(), f"commit {str(commit)[:12]}"
    return sha256_file(path), "the working tree"


def digest_problem(path: pathlib.Path, recorded, commit) -> list[str]:
    """One error when a recorded SHA-256 is not the digest of the bytes at the recorded commit."""
    actual, source = digest_at(path, commit)
    if recorded == actual:
        return []
    try:
        name = path.relative_to(ROOT).as_posix()
    except ValueError:
        name = path.name
    return [f"{name}: SHA-256 is {actual} at {source}, {PROVENANCE_NAME} says {recorded}"]


def version_code(build=BUILD) -> str:
    """versionCode from the app module, or unknown when the file is not readable."""
    try:
        found = re.search(r'versionCode\s*=\s*(\d+)', build.read_text(encoding="utf-8"))
    except OSError:
        found = None
    return found.group(1) if found else "unknown"


def zip_end(handle, size: int) -> tuple[int, int]:
    """(central directory offset, entry count) from the ZIP end-of-central-directory record."""
    window = min(size, EOCD_WINDOW)
    handle.seek(size - window)
    tail = handle.read(window)
    at = tail.rfind(EOCD)
    if at < 0 or len(tail) - at < EOCD_MIN:
        raise struct.error("no end-of-central-directory record")
    _, _, _, _, entries, _, offset, _ = struct.unpack("<4s4H2LH", tail[at:at + EOCD_MIN])
    if offset == 0xFFFFFFFF:
        raise struct.error("zip64 central directory")
    return offset, entries


def apk_signing(path: pathlib.Path) -> str:
    """signed when an APK Signing Block sits before the central directory, or a v1 certificate exists.

    The block is read where the format puts it: its 16-byte magic ends exactly where the central
    directory starts, and the 8-byte size before the block repeats the size at its end. The same
    magic anywhere else in the file is not a signature.
    """
    try:
        size = path.stat().st_size
        if size < EOCD_MIN + 8:
            return "unsigned"
        with path.open("rb") as handle:
            offset, entries = zip_end(handle, size)
            if entries and offset >= 24:
                handle.seek(offset - 16)
                if handle.read(16) == SIGNING_MAGIC:
                    handle.seek(offset - 24)
                    block = struct.unpack("<Q", handle.read(8))[0]
                    if 24 <= block <= offset - 8:
                        handle.seek(offset - block - 8)
                        if struct.unpack("<Q", handle.read(8))[0] == block:
                            return "signed"
        if zipfile.is_zipfile(path):
            with zipfile.ZipFile(path) as archive:
                for name in archive.namelist():
                    if name.startswith("META-INF/") and name.upper().endswith((".RSA", ".DSA", ".EC")):
                        return "signed"
    except (OSError, zipfile.BadZipFile, struct.error, ValueError):
        pass
    return "unsigned"


def apks(directory: pathlib.Path, version: str) -> list[pathlib.Path]:
    """Every `sign-setu-<version>-<abi>.apk` in the directory, sorted by name."""
    found = sorted(p for p in directory.glob(f"sign-setu-{version}-*.apk") if p.is_file())
    if not found:
        raise EvidenceError(f"no sign-setu-{version}-*.apk in {directory}")
    return found


def sbom_source(explicit=None) -> tuple[pathlib.Path, str]:
    """The SBOM file and its producer: an explicit path, then the Syft output, then the lockfile reader."""
    if explicit is not None:
        explicit = pathlib.Path(explicit)
        if not explicit.is_file():
            raise EvidenceError(f"{explicit} is not a file")
        return explicit, producer_of(explicit)
    if SBOM_BUILD.is_file():
        return SBOM_BUILD, producer_of(SBOM_BUILD)
    return SBOM_BUILD, "sbom_lockfile.py"


def producer_of(path: pathlib.Path) -> str:
    """The tool named in the document's metadata, or the lockfile reader for a bare document."""
    try:
        doc = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return "unknown"
    tools = (doc.get("metadata") or {}).get("tools") or []
    names = [str(tool.get("name", "")) for tool in tools if isinstance(tool, dict)]
    return names[0] if names else "unknown"


def sbom_components(path: pathlib.Path) -> int:
    try:
        doc = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise EvidenceError(f"{path} is not JSON: {error}") from error
    if doc.get("bomFormat") != "CycloneDX":
        raise EvidenceError(f"{path} is not a CycloneDX document")
    return len(doc.get("components") or [])


def sbom_document_problems(path: pathlib.Path) -> tuple[str, list[str]]:
    """(specVersion, what the document is missing: CycloneDX form, a version, a name/version/purl per component)."""
    try:
        doc = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise EvidenceError(f"{path} is not JSON: {error}") from error
    problems = []
    if doc.get("bomFormat") != "CycloneDX":
        problems.append(f"{path.name}: bomFormat is {doc.get('bomFormat')!r}, not CycloneDX")
    spec = str(doc.get("specVersion") or "")
    if not re.fullmatch(r"\d+\.\d+", spec):
        problems.append(f"{path.name}: specVersion is {spec!r}, not <major>.<minor>")
    for number, component in enumerate(doc.get("components") or [], 1):
        if not isinstance(component, dict):
            problems.append(f"{path.name}: component {number} is not an object")
            continue
        for field in ("name", "version", "purl"):
            if not isinstance(component.get(field), str) or not component[field]:
                problems.append(f"{path.name}: component {number} has no {field}")
    return spec, problems


def label(directory: pathlib.Path, name: str) -> str:
    """The file's path from the repository root when it lives there, else directory/name."""
    try:
        return (directory.resolve() / name).relative_to(ROOT).as_posix()
    except ValueError:
        return f"{directory.name}/{name}"


def sbom_expected(commit, version) -> bytes | None:
    """The bytes sbom_lockfile.py writes for the lockfile at `commit`, or None when git lacks it."""
    data = git_bytes(ROOT, LOCK.relative_to(ROOT).as_posix(), commit)
    if data is None:
        return None
    doc = sbom_document(data.decode("utf-8").splitlines(), commit, version=version)
    return (json.dumps(doc, indent=2, sort_keys=True) + "\n").encode("utf-8")


def gradle_wrapper() -> dict:
    """The pinned Gradle distribution and its digest, from the wrapper properties."""
    path = ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"
    try:
        text = path.read_text(encoding="utf-8")
    except OSError:
        return {}
    found = dict(re.findall(r"^(distributionUrl|distributionSha256Sum)=(.+)$", text, re.MULTILINE))
    if not found:
        return {}
    return {"distribution": found.get("distributionUrl", "").replace("\\:", ":"),
            "sha256": found.get("distributionSha256Sum", "")}


def write(directory: pathlib.Path, version=None, commit=None, sbom=None, generated=None) -> int:
    """Write SHA256SUMS, PROVENANCE.json and the SBOM into a directory that holds the APKs."""
    directory.mkdir(parents=True, exist_ok=True)
    version = version or project_version()
    commit = commit_sha(commit)
    built = apks(directory, version)

    source, producer = sbom_source(sbom)
    target = directory / SBOM_NAME
    if source.is_file():
        if source.resolve() != target.resolve():
            target.write_bytes(source.read_bytes())
    else:
        doc = sbom_document(LOCK.read_text(encoding="utf-8").splitlines(), commit, version=version)
        target.write_text(json.dumps(doc, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")

    artefacts = []
    for apk in built:
        status = apk_signing(apk)
        artefacts.append({
            "path": label(directory, apk.name),
            "name": apk.name,
            "sha256": sha256_file(apk),
            "bytes": apk.stat().st_size,
            "signing": status,
            "shippable": status == "signed",
        })
    components = sbom_components(target)
    lines = LOCK.read_text(encoding="utf-8").splitlines()
    provenance = {
        "format": f"sign-setu-release-evidence/{API}",
        "version": version,
        "versionCode": version_code(),
        "commit": commit,
        "generated": generated or datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "artefacts": artefacts,
        "sbom": {
            "path": label(directory, SBOM_NAME),
            "sha256": sha256_file(target),
            "bytes": target.stat().st_size,
            "format": "CycloneDX 1.5",
            "producer": producer,
            "components": components,
            "from": "the resolved release runtime classpath in app/gradle.lockfile",
        },
        "dependencyVerification": {
            "path": "gradle/verification-metadata.xml",
            "status": "present" if VERIFICATION.is_file() else "absent",
            "sha256": sha256_file(VERIFICATION) if VERIFICATION.is_file() else None,
        },
        "dependencyLocks": [
            {"path": "app/gradle.lockfile", "sha256": sha256_file(LOCK),
             "lines": len([line for line in lines if line.strip()]),
             "shipped": len(shipped_lines(lines))},
            {"path": "settings-gradle.lockfile", "sha256": sha256_file(SETTINGS_LOCK),
             "lines": len(SETTINGS_LOCK.read_text(encoding="utf-8").splitlines())},
        ],
        "build": {
            "task": ":app:assembleStandardRelease",
            "keystore": "absent",
            "gradle": gradle_wrapper(),
            "note": "built without the release keystore, so the APKs carry no signature; "
                    "shippable is false until apksigner signs the aligned APK with the release key",
        },
        "verify": f"sha256sum -c {SUMS_NAME} && python tools/security/release_manifest.py --check {label(directory, '').rstrip('/')}",
    }

    sums = [(artefact["sha256"], artefact["name"]) for artefact in artefacts]
    sums.append((sha256_file(target), SBOM_NAME))
    (directory / SUMS_NAME).write_text(
        "".join(f"{digest}  {name}\n" for digest, name in sorted(sums, key=lambda row: row[1])),
        encoding="utf-8", newline="\n")
    (directory / PROVENANCE_NAME).write_text(
        json.dumps(provenance, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")

    for artefact in artefacts:
        state = "unsigned, not shippable" if not artefact["shippable"] else "signed"
        print(f"{artefact['sha256']}  {artefact['bytes']:>12}  {artefact['name']}  {state}")
    print(f"{provenance['sbom']['sha256']}  {target.stat().st_size:>12}  {SBOM_NAME}  {producer}, "
          f"{components} components")
    print(f"wrote {directory / SUMS_NAME} and {directory / PROVENANCE_NAME}")
    return 0


def read_sums(path: pathlib.Path) -> list[tuple[str, str]]:
    """(sha256, name) pairs from a SHA256SUMS file, refusing a malformed line."""
    rows = []
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        parts = line.split(None, 1)
        if len(parts) != 2 or not re.fullmatch(r"[0-9a-f]{64}", parts[0]):
            raise EvidenceError(f"{path}:{number}: not '<sha256>  <name>': {line!r}")
        rows.append((parts[0], parts[1].strip()))
    if not rows:
        raise EvidenceError(f"{path} lists no file")
    return rows


def check(directory: pathlib.Path, sbom_current=False) -> list[str]:
    """Every disagreement with the evidence in the directory, empty when it all matches."""
    errors = []
    sums_path = directory / SUMS_NAME
    provenance_path = directory / PROVENANCE_NAME
    if not sums_path.is_file():
        raise EvidenceError(f"{sums_path} does not exist")
    if not provenance_path.is_file():
        raise EvidenceError(f"{provenance_path} does not exist")
    try:
        provenance = json.loads(provenance_path.read_text(encoding="utf-8"))
    except ValueError as error:
        raise EvidenceError(f"{provenance_path} is not JSON: {error}") from error

    rows = read_sums(sums_path)
    listed = {name for _, name in rows}
    present = []
    for digest, name in rows:
        target = directory / name
        if not target.is_file():
            print(f"{name}: absent, not checked (published asset, not committed)")
            continue
        present.append(name)
        actual = sha256_file(target)
        if actual != digest:
            errors.append(f"{name}: SHA-256 is {actual}, SHA256SUMS says {digest}")

    known = {SBOM_NAME} | {p.name for p in directory.glob("sign-setu-*.apk")}
    for name in sorted(known & {p.name for p in directory.iterdir() if p.is_file()} - listed):
        errors.append(f"{name}: present in {directory} but missing from {SUMS_NAME}")

    artefact_rows = {row["name"]: row for row in provenance.get("artefacts") or []}
    for name, row in artefact_rows.items():
        if name not in listed:
            errors.append(f"{PROVENANCE_NAME}: {name} is not in {SUMS_NAME}")
            continue
        target = directory / name
        if not target.is_file():
            continue
        status = apk_signing(target)
        if status != row.get("signing"):
            errors.append(f"{name}: signing is {status}, {PROVENANCE_NAME} says {row.get('signing')}")
        if row.get("shippable") is not (status == "signed"):
            errors.append(f"{name}: shippable is {row.get('shippable')} for a {status} APK")
        if row.get("sha256") and not any(row["sha256"] == digest and name == entry for digest, entry in rows):
            errors.append(f"{name}: {PROVENANCE_NAME}'s SHA-256 is not in {SUMS_NAME}")
        if row.get("bytes") and row["bytes"] != target.stat().st_size:
            errors.append(f"{name}: {target.stat().st_size} bytes, {PROVENANCE_NAME} says {row['bytes']}")

    sbom_row = provenance.get("sbom") or {}
    sbom_path = directory / SBOM_NAME
    if not sbom_path.is_file():
        errors.append(f"{SBOM_NAME}: missing beside the sums")
    else:
        digest = sha256_file(sbom_path)
        if sbom_row.get("sha256") not in (None, digest):
            errors.append(f"{SBOM_NAME}: SHA-256 is {digest}, {PROVENANCE_NAME} says {sbom_row['sha256']}")
        spec, problems = sbom_document_problems(sbom_path)
        errors.extend(problems)
        if spec and sbom_row.get("format") not in (None, f"CycloneDX {spec}"):
            errors.append(f"{SBOM_NAME}: specVersion is {spec}, {PROVENANCE_NAME} says {sbom_row.get('format')}")
        if sbom_row.get("components") not in (None, sbom_components(sbom_path)):
            errors.append(f"{SBOM_NAME}: {sbom_components(sbom_path)} components, "
                          f"{PROVENANCE_NAME} says {sbom_row['components']}")
        if sbom_row.get("producer") == "sbom_lockfile.py":
            expected = sbom_expected(provenance.get("commit"), provenance.get("version"))
            if expected is None:
                print(f"{SBOM_NAME}: the lockfile at {provenance.get('commit')} is not in git; "
                      f"its bytes are not re-derived")
            elif expected != sbom_path.read_bytes():
                errors.append(f"{SBOM_NAME}: the bytes are not what sbom_lockfile.py writes for the "
                              f"lockfile at {str(provenance.get('commit'))[:12]}")
        if sbom_current:
            wanted = len(shipped_lines(LOCK.read_text(encoding="utf-8").splitlines()))
            if sbom_components(sbom_path) != wanted:
                errors.append(f"{SBOM_NAME}: {sbom_components(sbom_path)} components, "
                              f"the lockfile's release runtime classpath has {wanted}")

    verification = provenance.get("dependencyVerification") or {}
    expected_status = "present" if VERIFICATION.is_file() else "absent"
    if verification.get("status") != expected_status:
        errors.append(f"dependencyVerification says {verification.get('status')}, "
                      f"gradle/verification-metadata.xml is {expected_status}")
    elif expected_status == "present":
        errors.extend(digest_problem(VERIFICATION, verification.get("sha256"), provenance.get("commit")))

    for row in provenance.get("dependencyLocks") or []:
        rel = str(row.get("path") or "")
        target = ROOT / rel if rel else ROOT
        if not rel or not target.is_file():
            errors.append(f"{PROVENANCE_NAME}: dependency lock {rel or '(unnamed)'} is not in the tree")
        else:
            errors.extend(digest_problem(target, row.get("sha256"), provenance.get("commit")))

    gradle = (provenance.get("build") or {}).get("gradle") or {}
    pin = gradle_wrapper()
    if pin:
        for field in ("distribution", "sha256"):
            if gradle.get(field) != pin.get(field):
                errors.append(f"Gradle {field} is {pin.get(field)}, {PROVENANCE_NAME} says {gradle.get(field)}")

    if not provenance.get("artefacts"):
        errors.append(f"{PROVENANCE_NAME} lists no artefact")
    if not re.fullmatch(r"sign-setu-release-evidence/\d+", str(provenance.get("format", ""))):
        errors.append(f"{PROVENANCE_NAME}: format is not sign-setu-release-evidence/<n>")
    print(f"{directory}: {len(present)} of {len(rows)} listed files present, "
          f"{len(artefact_rows)} artefact(s), SBOM {sbom_row.get('components', '?')} components")
    return errors


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("directory", type=pathlib.Path, help="the release directory, e.g. release/0.8.52")
    parser.add_argument("--check", action="store_true", help="recompute the hashes and fail on a mismatch")
    parser.add_argument("--sbom-current", action="store_true",
                        help="with --check, also require the SBOM's component count to match the lockfile")
    parser.add_argument("--version", help="versionName; defaults to the build file")
    parser.add_argument("--commit", help="commit the artefacts came from; defaults to HEAD")
    parser.add_argument("--sbom", type=pathlib.Path, help="SBOM to copy instead of app/build/sbom")
    parser.add_argument("--generated", help="ISO-8601 timestamp to record instead of now")
    args = parser.parse_args(argv)
    try:
        if args.check:
            errors = check(args.directory, sbom_current=args.sbom_current)
            for error in errors:
                print(error, file=sys.stderr)
            return 1 if errors else 0
        return write(args.directory, args.version, args.commit, args.sbom, args.generated)
    except EvidenceError as error:
        print(error, file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
