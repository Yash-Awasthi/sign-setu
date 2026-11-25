"""The review portal keeps its half of the CI gate: the `portal` job still typechecks, tests and
builds, and `portal/src` never grows a write path to session data.

The portal reads the app's content JSON at build time; a session record is written and read on the
phone under SQLCipher. So no page of the portal may reach one: the source may not call `fetch(`,
`XMLHttpRequest`, `WebSocket`, `EventSource`, `navigator.sendBeacon`, a POST/PUT/PATCH/DELETE
`method:`, or `indexedDB`, and no import may name `app/src/main/java` or an `/assets/session`,
`/assets/record` or `/assets/ledger` file.

The one browser write the portal may make is its own review store: `localStorage.setItem` appears
only in `portal/src/reviews.ts`, and that file keeps the single key `signsetu.reviews.v1`.

Checks:
- `.github/workflows/ci.yml`: the `portal` job runs `npm ci`, `npx vp check`, `npx vp test` and
  `npx vp build`, so a dropped typecheck or test run fails CI.
- `portal/src/**/*.ts` and `*.tsx`: write tokens above are absent. Test files (`*.test.ts`) are
  skipped for those tokens because they mock the modules under test, but their imports are checked
  like any other source.
- `portal/src/reviews.ts`: holds the review store and its key; no other file writes to storage.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "ci.yml"
PORTAL_SRC = ROOT / "portal" / "src"

STEPS = ("npm ci", "npx vp check", "npx vp test", "npx vp build")
REVIEW_STORE = "reviews.ts"
REVIEW_KEY = "signsetu.reviews.v1"

# A browser write to anything but the review store would be a second copy of session data.
WRITE_PATTERNS = {
    "fetch(": r"\bfetch\s*\(",
    "XMLHttpRequest": r"\bXMLHttpRequest\b",
    "WebSocket": r"\bWebSocket\b",
    "EventSource": r"\bEventSource\b",
    "navigator.sendBeacon": r"\bnavigator\s*\.\s*sendBeacon\b",
    "method: POST/PUT/PATCH/DELETE": r"\bmethod\s*:\s*[\"'`]\s*(POST|PUT|PATCH|DELETE)\b",
    "indexedDB": r"\bindexedDB\b",
}

SESSION_PATHS = ("app/src/main/java", "/assets/session", "/assets/record", "/assets/ledger")


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


def source_files(src: pathlib.Path) -> list[pathlib.Path]:
    """Every TypeScript source of the portal, test files included."""
    return sorted(p for p in src.rglob("*") if p.is_file() and p.suffix in (".ts", ".tsx"))


def check(workflow: pathlib.Path | None = None, portal_src: pathlib.Path | None = None) -> list[str]:
    workflow = WORKFLOW if workflow is None else workflow
    src = PORTAL_SRC if portal_src is None else portal_src
    errors: list[str] = []

    job_text = job(workflow.read_text(encoding="utf-8"), "portal")
    if not job_text:
        errors.append("ci.yml: no portal job")
    else:
        for step in STEPS:
            if step not in job_text:
                errors.append(f"ci.yml: the portal job no longer runs `{step}`")

    if not src.is_dir():
        errors.append(f"portal: no source directory at {src}")
        return errors

    for path in source_files(src):
        try:
            shown = path.relative_to(src.parent.parent).as_posix()
        except ValueError:
            shown = path.as_posix()
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if ".test." not in path.name:
                for name, pattern in WRITE_PATTERNS.items():
                    if re.search(pattern, line):
                        errors.append(
                            f"{shown}:{number}: write path `{name}`; the portal only reads content JSON"
                        )
            for token in SESSION_PATHS:
                if token in line:
                    errors.append(
                        f"{shown}:{number}: import reaches app session data ({token}); a session record stays on the phone"
                    )
            if "localStorage.setItem" in line and path.name != REVIEW_STORE:
                errors.append(f"{shown}:{number}: localStorage.setItem outside reviews.ts, the only browser write")

    reviews = src / REVIEW_STORE
    if not reviews.is_file():
        errors.append(f"portal/src/{REVIEW_STORE}: the review store is gone")
    elif REVIEW_KEY not in reviews.read_text(encoding="utf-8"):
        errors.append(f"portal/src/{REVIEW_STORE}: the review store no longer uses {REVIEW_KEY}")
    return errors


def main() -> int:
    errors = check()
    for error in errors:
        print(error, file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
