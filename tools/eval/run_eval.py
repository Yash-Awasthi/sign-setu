"""One command for the gloss-to-text metrics (docs/10-gloss-to-text.md, steps 0.5 and 6.5).

1. Simulated lattices: runs SentenceEvalTest (real held-out readings, every phrasebook sentence) and keeps its lines.
2. Recorded windows: reads what the debug recorder wrote on the phone, pulled with
       adb pull /sdcard/Android/data/in.signsetu/files/recordings tools/eval/recordings
   A window counts once a person has filled its "label" (gloss, English, Hindi). For those it reports gloss word error
   rate (substitutions, deletions, insertions per signed word), wrong-but-confident (a rung-1 sentence that differs
   from the label) and abstention (rung 3 or 4), overall and per question.
Writes tools/eval/report.html and appends a line to tools/eval/history.jsonl.
    python tools/eval/run_eval.py [--recordings DIR] [--skip-sim]
"""
import argparse
import datetime
import html
import json
import os
import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = pathlib.Path(__file__).resolve().parent


def simulated() -> list:
    env = dict(os.environ, JAVA_HOME=os.environ.get("JAVA_HOME", "C:/work/jdk21"))
    gradle = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    out = subprocess.run([gradle, ":app:testStandardDebugUnitTest", "--tests", "app.signsetu.SentenceEvalTest", "-i", "--console=plain"],
                         cwd=ROOT, env=env, capture_output=True, text=True, encoding="utf-8", errors="replace").stdout
    return [l.strip() for l in out.splitlines() if "question=" in l or "chosen" in l]


def wer(ref: list, hyp: list) -> int:
    d = list(range(len(hyp) + 1))
    for i, r in enumerate(ref, 1):
        prev, d[0] = d[0], i
        for j, h in enumerate(hyp, 1):
            prev, d[j] = d[j], min(d[j] + 1, d[j - 1] + 1, prev + (r != h))
    return d[len(hyp)]


def recorded(folder: pathlib.Path) -> dict:
    decisions = {}
    log = folder / "decisions.jsonl"
    if log.exists():
        for line in log.read_text(encoding="utf-8").splitlines():
            d = json.loads(line)
            decisions.setdefault(d["eventId"], []).append(d)
    rows, edits, words = [], 0, 0
    for f in sorted(folder.glob("window_*.json")):
        w = json.loads(f.read_text(encoding="utf-8"))
        label = w.get("label") or {}
        if not label.get("gloss"):
            continue
        ref = label["gloss"].lower().split()
        hyp = [(t.get("candidates") or [{}])[0].get("label", "?") for t in w["lattice"]["tokens"]]
        e = wer(ref, hyp)
        edits += e
        words += len(ref)
        rung = next((d["detail"] for d in reversed(decisions.get(w["eventId"], [])) if d["what"] == "rung"), "")
        kind, _, said = rung.partition(": ")
        rows.append({"question": w.get("question") or "-", "rung": kind or "-", "wrong1": kind == "Sentence" and said.strip() != (label.get("en") or "").strip(),
                     "abstain": kind in ("WordStrip", "SignAgain"), "wer": e / max(1, len(ref))})
    n = len(rows)
    rung1 = [r for r in rows if r["rung"] == "Sentence"]
    return {
        "labelled windows": n,
        "gloss WER": edits / words if words else None,
        "wrong-but-confident (of rung 1)": sum(r["wrong1"] for r in rung1) / len(rung1) if rung1 else None,
        "abstention": sum(r["abstain"] for r in rows) / n if n else None,
        "by question": {q: len([r for r in rows if r["question"] == q]) for q in sorted({r["question"] for r in rows})},
    }


def main() -> None:
    a = argparse.ArgumentParser()
    a.add_argument("--recordings", default=str(HERE / "recordings"))
    a.add_argument("--skip-sim", action="store_true")
    args = a.parse_args()
    sim = [] if args.skip_sim else simulated()
    rec_dir = pathlib.Path(args.recordings)
    rec = recorded(rec_dir) if rec_dir.exists() else {"labelled windows": 0}
    commit = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=ROOT, capture_output=True, text=True).stdout.strip()
    stamp = datetime.datetime.now().isoformat(timespec="minutes")
    with open(HERE / "history.jsonl", "a", encoding="utf-8") as h:
        h.write(json.dumps({"at": stamp, "commit": commit, "simulated": sim, "recorded": rec}) + "\n")
    fmt = lambda v: "-" if v is None else (f"{v:.3f}" if isinstance(v, float) else html.escape(str(v)))  # noqa: E731
    page = [f"<!doctype html><meta charset='utf-8'><title>Gloss-to-text report</title>",
            "<style>body{font:14px system-ui;margin:24px;max-width:1000px}td,th{border:1px solid #ccc;padding:4px 8px}table{border-collapse:collapse}pre{background:#f6f6f6;padding:8px}</style>",
            f"<h1>Gloss-to-text report</h1><p>{stamp}, commit {commit}. Simulated numbers are development numbers; release gates use recorded, labelled windows only.</p>",
            "<h2>Simulated lattices (real held-out readings)</h2><pre>" + html.escape("\n".join(re.sub(r"\s+", " ", l) for l in sim) or "skipped") + "</pre>",
            "<h2>Recorded windows</h2><table>"] + [f"<tr><th>{html.escape(k)}</th><td>{fmt(v)}</td></tr>" for k, v in rec.items()] + ["</table>"]
    (HERE / "report.html").write_text("\n".join(page), encoding="utf-8")
    print("\n".join(sim))
    print(json.dumps(rec, indent=1))
    print(f"report: {HERE / 'report.html'}")


if __name__ == "__main__":
    main()
