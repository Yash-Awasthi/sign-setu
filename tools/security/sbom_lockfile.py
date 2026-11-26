"""Writes a CycloneDX 1.5 SBOM of the release runtime classpath from app/gradle.lockfile alone.

This replaces the Syft scan when Syft is absent: tools/security/sbom.py shells out to the
binary, and this script needs none, so a machine with no Syft still produces the document.
A Syft run remains preferred wherever the binary exists, because Syft reads the resolved
files themselves rather than the lockfile coordinates.

One component per lockfile line whose configuration names a release runtime classpath, the
same filter sbom.py uses, sorted by purl; build-only tools are left out.
"""
import argparse
import json
import os
import pathlib
import re
import subprocess
import uuid

from sbom import shipped_lines

ROOT = pathlib.Path(__file__).resolve().parents[2]
LOCK = ROOT / "app" / "gradle.lockfile"
BUILD = ROOT / "app" / "build.gradle.kts"
OUT = ROOT / "app" / "build" / "sbom" / "sign-setu-release.cdx.json"
PROJECT = "sign-setu"


def project_version(build=BUILD) -> str:
    """versionName from the app module, or unspecified when the file is not readable."""
    try:
        found = re.search(r'versionName\s*=\s*"([^"]+)"', build.read_text(encoding="utf-8"))
    except OSError:
        found = None
    return found.group(1) if found else "unspecified"


def purls(lines):
    """(group, artifact, version, purl) for every shipped line, sorted by purl."""
    out = []
    for line in shipped_lines(lines):
        group, artifact, version = (line.split("=", 1)[0].split(":") + ["", ""])[:3]
        out.append((group, artifact, version, f"pkg:maven/{group}/{artifact}@{version}"))
    return sorted(out, key=lambda row: row[3])


def document(lines, commit, project=PROJECT, version=None) -> dict:
    """A CycloneDX 1.5 document whose serial number is stable for the same inputs."""
    version = version or project_version()
    rows = purls(lines)
    components = [
        {"type": "library", "bom-ref": purl, "group": group, "name": artifact, "version": ver, "purl": purl}
        for group, artifact, ver, purl in rows
    ]
    serial = uuid.uuid5(uuid.NAMESPACE_URL, f"{project}@{version}@{commit}:" + ",".join(r[3] for r in rows))
    return {
        "bomFormat": "CycloneDX",
        "specVersion": "1.5",
        "serialNumber": f"urn:uuid:{serial}",
        "version": 1,
        "metadata": {
            "tools": [{"vendor": project, "name": "sbom_lockfile.py", "version": "1.0"}],
            "component": {
                "type": "application",
                "bom-ref": f"{project}@{version}",
                "name": project,
                "version": version,
                "properties": [{"name": "sign-setu:commit", "value": commit}],
            },
        },
        "components": components,
    }


def commit_sha(explicit=None, root=ROOT) -> str:
    """Explicit argument, then GIT_COMMIT, then git in the tree, then unknown."""
    if explicit:
        return explicit
    if os.environ.get("GIT_COMMIT"):
        return os.environ["GIT_COMMIT"]
    try:
        return subprocess.run(["git", "-C", str(root), "rev-parse", "HEAD"], capture_output=True,
                              text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def main(lock=LOCK, out=OUT, commit=None) -> int:
    lines = lock.read_text(encoding="utf-8").splitlines()
    doc = document(lines, commit_sha(commit))
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(doc, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    shipped = len(shipped_lines(lines))
    assert len(doc["components"]) == shipped, f"{len(doc['components'])} components for {shipped} shipped lines"
    print(f"{shipped} shipped components -> {out}")
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--lock", type=pathlib.Path, default=LOCK)
    parser.add_argument("--out", type=pathlib.Path, default=OUT)
    parser.add_argument("--commit", help="commit the tree came from; defaults to HEAD")
    args = parser.parse_args()
    raise SystemExit(main(args.lock, args.out, args.commit))
