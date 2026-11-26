"""Writes a CycloneDX SBOM of the release runtime classpath with Syft; build-only tools are left out."""
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
LOCK = ROOT / "app" / "gradle.lockfile"
OUT = ROOT / "app" / "build" / "sbom"


def shipped_lines(lines):
    """Lockfile lines on any flavor's release runtime classpath."""
    return [line for line in lines if "=" in line and "releaseruntimeclasspath" in line.split("=", 1)[1].lower()]


def flavoured_lines(lines):
    """Shipped lines a current flavor resolves, without the retired bare releaseRuntimeClasspath.

    The build no longer resolves the unflavored configuration, so a line that only appears there is
    a coordinate no current APK contains.
    """
    bare = "releaseruntimeclasspath"
    out = []
    for line in shipped_lines(lines):
        configs = line.split("=", 1)[1].lower().split(",")
        if any(config != bare and config.endswith(bare) for config in configs):
            out.append(line)
    return out


def main(syft: str) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    shipped = shipped_lines(LOCK.read_text(encoding="utf-8").splitlines())
    staging = OUT / "release-runtime"
    staging.mkdir(exist_ok=True)
    (staging / "gradle.lockfile").write_text("\n".join(shipped) + "\n", encoding="utf-8")
    target = OUT / "sign-setu-release.cdx.json"
    subprocess.run([syft, "scan", f"dir:{staging}", "-o", f"cyclonedx-json={target}", "-q"], check=True)
    print(f"{len(shipped)} shipped components -> {target}")


if __name__ == "__main__":
    main(sys.argv[1])
