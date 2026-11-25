"""Checks the controls table of docs/14-attestation-and-pinning.md against the tree.

A row marked Implemented must name, in its Evidence cell, a backticked
relative/path#symbol whose file exists and whose text contains the symbol. A row
marked Aspirational only needs a non-empty Evidence cell, since the control is
described but not built.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
DOC = pathlib.Path("docs") / "14-attestation-and-pinning.md"
HEADING = "### 1.1 Which controls exist in the tree"
BACKTICKED = re.compile(r"`([^`]+)`")
STATUSES = ("Implemented", "Aspirational")


def table(text: str) -> list[str] | None:
    """The table lines between the heading and the next '## ' heading; None without the heading."""
    lines = text.splitlines()
    try:
        start = lines.index(HEADING)
    except ValueError:
        return None
    out = []
    for line in lines[start + 1:]:
        if line.startswith("## "):
            break
        if line.startswith("|"):
            out.append(line)
    return out


def check(root: pathlib.Path) -> list[str]:
    path = root / DOC
    if not path.exists():
        return [f"{DOC}: missing"]
    lines = table(path.read_text(encoding="utf-8"))
    if lines is None:
        return [f"{DOC}: missing the heading '{HEADING}'"]
    errors = []
    for line in lines[2:]:
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) != 3:
            errors.append(f"{DOC}: not a three-column row: {line}")
            continue
        control, status, evidence = cells
        if status not in STATUSES:
            errors.append(f"{DOC}: status is not Implemented or Aspirational: {control}")
            continue
        if not evidence:
            errors.append(f"{DOC}: no evidence: {control}")
            continue
        if status == "Aspirational":
            continue
        for item in BACKTICKED.findall(evidence):
            if "#" not in item:
                errors.append(f"{DOC}: evidence is not path#symbol: {item}")
                continue
            rel, symbol = item.split("#", 1)
            target = root / rel
            if not target.exists():
                errors.append(f"{DOC}: evidence names a missing file: {rel}")
            elif symbol not in target.read_text(encoding="utf-8"):
                errors.append(f"{DOC}: {rel} has no '{symbol}'")
    return errors


def main() -> int:
    root = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT
    errors = check(root)
    for error in errors:
        print(error, file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
