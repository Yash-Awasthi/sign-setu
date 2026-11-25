"""Holds the Android lint findings to a list that may only shrink.

The owner's gate (plan.md, M2.1): lint is a ratchet. Every finding the app has today is written down in
`tools/ci/lint-known.json` as a rule id and a file; a run that finds anything else fails, a run that finds an error at
any level fails whatever the list says, and a listed finding that no longer occurs has to be struck from the list, so the
file can only get shorter. Zero findings is the goal; this keeps the number from ever going up.

Reads the SARIF report `:app:lintStandardDebug` writes. Run: python tools/ci/check_lint.py [--sarif PATH] [--write]
Exit 0 within the list, 1 a new or changed finding, an error, or a stale entry.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KNOWN = ROOT / "tools/ci/lint-known.json"
# CI lints the release build; a developer usually has the debug report. Either is read, the release one first.
REPORTS = [
    ROOT / "app/build/reports/lint-results-standardRelease.sarif",
    ROOT / "app/build/reports/lint-results-standardDebug.sarif",
]
SARIF = next((p for p in REPORTS if p.exists()), REPORTS[1])


def findings(sarif: dict) -> list[dict]:
    """One entry per finding: the rule, the file, and the severity the tool assigns the rule."""
    run = sarif["runs"][0]
    levels = {r["id"]: (r.get("defaultConfiguration") or {}).get("level", "warning") for r in run["tool"]["driver"].get("rules", [])}
    out = []
    for r in run.get("results", []):
        where = ""
        for loc in r.get("locations", []):
            uri = loc.get("physicalLocation", {}).get("artifactLocation", {}).get("uri")
            if uri:
                where = uri
                break
        out.append({"rule": r["ruleId"], "file": where, "level": r.get("level") or levels.get(r["ruleId"], "warning")})
    return out


def keyed(found: list[dict]) -> dict[tuple[str, str], int]:
    """Rule and file to how many times it occurs: line numbers move with every edit, the pair does not."""
    counts: dict[tuple[str, str], int] = {}
    for f in found:
        k = (f["rule"], f["file"])
        counts[k] = counts.get(k, 0) + 1
    return counts


def compare(found: list[dict], known: dict[tuple[str, str], int]) -> list[str]:
    """Everything wrong with this run, in words; empty when it is within the ratchet."""
    problems = []
    for f in found:
        if f["level"] == "error":
            problems.append(f"ERROR {f['rule']} in {f['file'] or '(project)'}: errors are never tolerated")
    now = keyed(found)
    for k, n in sorted(now.items()):
        was = known.get(k, 0)
        if was == 0:
            problems.append(f"new finding: {k[0]} in {k[1] or '(project)'}")
        elif n > was:
            problems.append(f"{k[0]} in {k[1] or '(project)'} now occurs {n} times, the list allows {was}")
    for k, was in sorted(known.items()):
        n = now.get(k, 0)
        if n < was:
            problems.append(f"stale entry: {k[0]} in {k[1] or '(project)'} occurs {n} times, the list says {was} — strike it from the list")
    return problems


def load_known(path: Path = KNOWN) -> dict[tuple[str, str], int]:
    if not path.exists():
        return {}
    data = json.loads(path.read_text(encoding="utf-8"))
    return {(e["rule"], e["file"]): int(e.get("count", 1)) for e in data.get("known", [])}


def write_known(found: list[dict], path: Path = KNOWN) -> None:
    now = keyed(found)
    entries = [{"rule": rule, "file": file, "count": n} for (rule, file), n in sorted(now.items())]
    path.write_text(
        json.dumps(
            {
                "about": "Android lint findings this repository has today, by rule and file. May only shrink: "
                         "tools/ci/check_lint.py fails on a new finding, an error, or an entry that no longer occurs.",
                "known": entries,
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )


def main() -> int:
    ap = argparse.ArgumentParser(description="Hold the lint findings to a list that may only shrink.")
    ap.add_argument("--sarif", default=str(SARIF), help=f"the lint SARIF report (default {SARIF.relative_to(ROOT)})")
    ap.add_argument("--write", action="store_true", help="rewrite the list from this run (only to shrink it)")
    a = ap.parse_args()

    path = Path(a.sarif)
    if not path.exists():
        print(f"no lint report at {path}; run ./gradlew :app:lintStandardDebug first", file=sys.stderr)
        return 2
    found = findings(json.loads(path.read_text(encoding="utf-8")))
    if a.write:
        write_known(found)
        print(f"wrote {len(keyed(found))} entries to {KNOWN.relative_to(ROOT)}")
        return 0

    known = load_known()
    problems = compare(found, known)
    print(f"{len(found)} finding(s) in {path.name}; the list allows {sum(known.values())}")
    if problems:
        for p in problems:
            print(f"  {p}", file=sys.stderr)
        return 1
    print("no new finding, no error, no stale entry")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
