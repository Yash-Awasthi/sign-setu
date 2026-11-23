"""Checks the documentation against the tree, so a described feature that no longer
exists fails CI and a documented number comes from the code.

Five checks, all measured here:
  1. every relative link resolves, so a deleted screen or tool leaves no dangling claim;
  2. every screenshot in docs/screens/ is used by docs/12-screens.md, and every
     reference in it exists;
  3. every docs/*.md is in the document map of docs/README.md;
  4. a file a document names by path exists, so a removed file cannot stay described;
  5. the counts the README states (languages, ISL and ASL words, retention hours)
     equal what the bundle and the settings code hold.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"
README = ROOT / "README.md"
SCREENS_DOC = DOCS / "12-screens.md"
DOCS_MAP = DOCS / "README.md"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "content"
SETTINGS = ROOT / "app" / "src" / "main" / "java" / "app" / "signsetu" / "data" / "settings" / "SettingsStore.kt"

LINK = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")


def markdown_files() -> list[pathlib.Path]:
    """The documents a reader lands on: the root README and the numbered docs."""
    return [README, DOCS_MAP] + sorted(p for p in DOCS.glob("*.md") if p != DOCS_MAP)


def relative_links(text: str) -> list[str]:
    """Link targets that live in the repository: no http, no anchors, no mail."""
    out = []
    for target in LINK.findall(text):
        if target.startswith(("http://", "https://", "mailto:", "#")):
            continue
        out.append(target.split("#", 1)[0])
    return out


def check_links() -> list[str]:
    errors = []
    for path in markdown_files():
        for target in relative_links(path.read_text(encoding="utf-8")):
            if not (path.parent / target).exists():
                errors.append(f"{path.relative_to(ROOT)}: link to a missing file: {target}")
    return errors


def check_screens() -> list[str]:
    text = SCREENS_DOC.read_text(encoding="utf-8")
    used = {t for t in relative_links(text) if t.startswith("screens/")}
    present = {f"screens/{p.name}" for p in (DOCS / "screens").glob("*.png")}
    return [f"docs/screens/{u.split('/', 1)[1]}: referenced by 12-screens.md but missing" for u in sorted(used - present)] + [
        f"docs/{p}: captured but described nowhere; add it to 12-screens.md" for p in sorted(present - used)
    ]


def check_docs_map() -> list[str]:
    listed = set(relative_links(DOCS_MAP.read_text(encoding="utf-8")))
    return [
        f"docs/{p.name}: not in the document map of docs/README.md"
        for p in sorted(DOCS.glob("*.md"))
        if p.name not in listed and p != DOCS_MAP
    ]


# A path a document names in backticks; an elided, wildcarded or absolute path is not checkable.
CODE_PATH = re.compile(r"`([^`\n]+)`")
FILE_EXTENSIONS = (".py", ".kt", ".md", ".json", ".kts", ".go", ".toml", ".yml", ".sh", ".gguf", ".task", ".js", ".ts")


def check_paths() -> list[str]:
    """A file a document names by path must exist, so a removed file cannot stay described."""
    top = {p.name for p in ROOT.iterdir()}
    errors = []
    for path in markdown_files():
        for span in CODE_PATH.findall(path.read_text(encoding="utf-8")):
            name = span.strip()
            if not name.endswith(FILE_EXTENSIONS) or any(c in name for c in " *<:") or "..." in name:
                continue
            if name.split("/", 1)[0] not in top:
                continue
            if not (ROOT / name).exists():
                errors.append(f"{path.relative_to(ROOT)}: names a file that does not exist: {name}")
    return errors


def counts() -> dict:
    """The numbers the documents quote, read from the bundle and the settings code."""
    languages = json.loads((ASSETS / "languages.json").read_text(encoding="utf-8"))["languages"]
    isl = json.loads((ASSETS / "models" / "sign_reader.json").read_text(encoding="utf-8"))["labels"]
    asl = json.loads((ASSETS / "models" / "asl_reader.json").read_text(encoding="utf-8"))["labels"]
    hours = re.search(r"RETENTION_CHOICES = listOf\(([\d,\s]+)\)", SETTINGS.read_text(encoding="utf-8"))
    choices = [int(h) for h in hours.group(1).split(",") if h.strip()]
    default = re.search(r"DEFAULT_RETENTION_HOURS = (\d+)", SETTINGS.read_text(encoding="utf-8"))
    return {
        "languages": len(languages),
        "isl": len(isl),
        "asl": len(asl),
        "retentionFrom": min(h for h in choices if h > 0),
        "retentionTo": max(choices),
        "retentionDefault": int(default.group(1)),
    }


def claims() -> list[tuple[pathlib.Path, str]]:
    """Each claim is the sentence a document must carry, written from a measured number."""
    return [
        (README, "{languages} languages"),
        (README, "ISL: {isl} words"),
        (README, "ASL (experimental): {asl} words"),
        (README, "{retentionFrom} to {retentionTo} hours"),
        (README, "deleted {retentionDefault} hours after its last activity by default"),
        (DOCS_MAP, "Languages ({languages})"),
    ]


def check_claims() -> list[str]:
    values = counts()
    errors = []
    for path, template in claims():
        want = template.format(**values)
        if want not in path.read_text(encoding="utf-8"):
            errors.append(f"{path.relative_to(ROOT)}: does not state '{want}' (measured from the bundle)")
    return errors


def check() -> list[str]:
    return check_links() + check_screens() + check_docs_map() + check_paths() + check_claims()


def main() -> int:
    errors = check()
    for error in errors:
        print(error, file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
