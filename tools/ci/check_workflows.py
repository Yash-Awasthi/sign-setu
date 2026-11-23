"""The emulator job in CI must stay wide: every instrumented class the emulator can run, on
every pull request that touches the app.

`app/src/androidTest` holds the classes; `.github/workflows/ci.yml` names the ones the
emulator runs. A new class added to the app is therefore either added to the job or
declared device-only here, with the measurement that says so. Three classes are
device-only because they fail on this machine's Android 14 x86_64 emulator: the two that
measure frame time against a phone's GPU, and `StageEndToEndTest`, which drives the
emulator's camera feed.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "ci.yml"
ANDROID_TEST = ROOT / "app" / "src" / "androidTest"

# Measured on an Android 14 x86_64 emulator (Pixel 6 profile) with `connectedStandardDebugAndroidTest`:
# each of these failed, so they run on a phone, not in CI.
DEVICE_ONLY = {
    "TrackingPerformanceTest": "frame budget over a signer's clip: fails on the emulator",
    "StageFrameRateTest": "drawn stage frame rate: fails on the emulator",
    "StageEndToEndTest": "drives the camera feed and the character's frame clock: fails on the emulator",
}


def job(text: str, name: str) -> str:
    """One top-level job of a workflow, from its name to the next top-level key."""
    lines = text.splitlines()
    start = None
    for i, line in enumerate(lines):
        if line.rstrip() == f"  {name}:":
            start = i
            break
    if start is None:
        return ""
    end = start + 1
    while end < len(lines) and not re.match(r"^  [a-zA-Z0-9_-]+:", lines[end]):
        end += 1
    return "\n".join(lines[start:end])


def instrumented_classes(text: str) -> list[str]:
    """The classes the emulator job runs, in order."""
    found = re.search(r"for c in ([^;]+); do", text)
    return found.group(1).split() if found else []


def test_classes(root: pathlib.Path | None = None) -> list[str]:
    """Every instrumented class on disk that holds a test."""
    root = ANDROID_TEST if root is None else root
    return sorted(p.stem for p in root.rglob("*.kt") if "@Test" in p.read_text(encoding="utf-8"))


def check(workflow: pathlib.Path | None = None, root: pathlib.Path | None = None) -> list[str]:
    text = (WORKFLOW if workflow is None else workflow).read_text(encoding="utf-8")
    job_text = job(text, "instrumented")
    errors = []
    if not job_text:
        return ["ci.yml: no instrumented job"]
    classes = instrumented_classes(job_text)
    expected = [c for c in test_classes(root) if c not in DEVICE_ONLY]
    missing = sorted(set(expected) - set(classes))
    extra = sorted(set(classes) - set(expected))
    if missing:
        errors.append(f"ci.yml: the emulator job skips classes the emulator can run: {missing}")
    if extra:
        errors.append(f"ci.yml: the emulator job names classes that are not instrumented tests: {extra}")
    needs = re.search(r"^\s*needs: (.+)$", job_text, re.MULTILINE)
    if needs is None or "app-changed" not in needs.group(1):
        errors.append("ci.yml: the emulator job does not need the app-changed filter")
    if "needs.app-changed.outputs.any == 'true'" not in job_text:
        errors.append("ci.yml: the emulator job does not run on a pull request that changed the app")
    if "github.event_name == 'workflow_dispatch'" not in job_text:
        errors.append("ci.yml: a manual run does not force the emulator job")
    if "timeout-minutes: 45" not in job_text:
        errors.append("ci.yml: the emulator job has no 45-minute timeout")
    changed = job(text, "app-changed").replace("\\", "")
    for want in ("app/", "gradle/", "settings.gradle.kts", "build.gradle.kts"):
        if want not in changed:
            errors.append(f"ci.yml: the app-changed filter does not look at {want}")
    return errors


def main() -> int:
    errors = check()
    for error in errors:
        print(error, file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
