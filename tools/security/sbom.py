"""Writes a CycloneDX SBOM of what ships in the release APK (SEC-51).

Only libraries on the release runtime classpath ship; build tools such as Android Lint do not, so they are left out.
Needs Syft (https://github.com/anchore/syft, Apache-2.0):
    python tools/security/sbom.py path/to/syft.exe
Output: app/build/sbom/sign-setu-release.cdx.json
"""
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
LOCK = ROOT / "app" / "gradle.lockfile"
OUT = ROOT / "app" / "build" / "sbom"


def main(syft: str) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    shipped = [line for line in LOCK.read_text(encoding="utf-8").splitlines()
               if "=" in line and "releaseRuntimeClasspath" in line.split("=", 1)[1]]
    staging = OUT / "release-runtime"
    staging.mkdir(exist_ok=True)
    (staging / "gradle.lockfile").write_text("\n".join(shipped) + "\n", encoding="utf-8")
    target = OUT / "sign-setu-release.cdx.json"
    subprocess.run([syft, "scan", f"dir:{staging}", "-o", f"cyclonedx-json={target}", "-q"], check=True)
    print(f"{len(shipped)} shipped components -> {target}")


if __name__ == "__main__":
    main(sys.argv[1])
