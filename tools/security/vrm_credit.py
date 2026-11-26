"""Generate the avatar's credit from the shipped VRM's own metadata.

`assets/avatar/model.vrm` is a GLB whose first chunk is the glTF JSON. Its `VRMC_vrm.meta` names the
title, the authors, the licence URL and whether credit is required. `AvatarCredit.kt` is rendered
from those fields so the app cannot credit the wrong person or drift from the model; nothing about
the credit is typed by hand. A field the credit needs and the metadata lacks is an error, never a
guess.

`python tools/security/vrm_credit.py --write` regenerates the Kotlin. `test_vrm_credit.py` proves
the file matches the model and that the model's bytes are the ones the credit was written for.
"""
import argparse
import hashlib
import json
import pathlib
import struct

ROOT = pathlib.Path(__file__).resolve().parents[2]
MODEL = ROOT / "app" / "src" / "main" / "assets" / "avatar" / "model.vrm"
OUT = ROOT / "app" / "src" / "main" / "java" / "app" / "signsetu" / "ui" / "settings" / "AvatarCredit.kt"
GLB_MAGIC = b"glTF"
JSON_CHUNK = 0x4E4F534A

# The licence names VRM authors point at; a URL outside this map is an error, not a label to invent.
LICENCE_NAMES = {
    "https://vrm.dev/licenses/1.0/": "VRM Public License 1.0",
}


def meta_of(path=MODEL) -> dict:
    """The `VRMC_vrm.meta` object from the GLB's JSON chunk."""
    data = path.read_bytes()
    magic, version, length = struct.unpack_from("<4sII", data, 0)
    if magic != GLB_MAGIC or version != 2 or length != len(data):
        raise ValueError(f"{path}: not a glTF 2.0 binary of the stated length")
    chunk_length, chunk_type = struct.unpack_from("<II", data, 12)
    if chunk_type != JSON_CHUNK:
        raise ValueError(f"{path}: first chunk is not JSON")
    document = json.loads(data[20:20 + chunk_length].decode("utf-8"))
    try:
        return document["extensions"]["VRMC_vrm"]["meta"]
    except KeyError as error:
        raise ValueError(f"{path}: no VRMC_vrm.meta ({error})") from error


def credit_fields(meta) -> dict:
    """The fields the credit is built from, or an error naming the one that is missing."""
    missing = [field for field in ("name", "authors", "licenseUrl", "creditNotation")
               if not meta.get(field)]
    if missing:
        raise ValueError(f"VRM meta has no {', '.join(missing)}; credit cannot be generated")
    if not isinstance(meta["authors"], list) or not all(isinstance(a, str) and a.strip()
                                                        for a in meta["authors"]):
        raise ValueError(f"VRM meta authors is not a list of names: {meta['authors']!r}")
    url = str(meta["licenseUrl"])
    if url not in LICENCE_NAMES:
        raise ValueError(f"VRM meta licence {url} has no known name; add it to LICENCE_NAMES")
    authors = [author.strip() for author in meta["authors"]]
    return {
        "title": str(meta["name"]),
        "authors": authors,
        "author": " and ".join(authors),
        "licence_name": LICENCE_NAMES[url],
        "licence_url": url,
        "credit_required": str(meta["creditNotation"]) == "required",
    }


def kotlin_string(value: str) -> str:
    """A Kotlin string literal with the characters a credit could contain escaped."""
    escaped = value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
    return '"' + escaped.replace("\n", "\\n") + '"'


def entry_name(fields) -> str:
    return f"Avatar: {fields['title']}, by {fields['author']}"


def text(fields) -> str:
    return (f"{fields['licence_name']}, {fields['licence_url']}. The model ships unmodified; "
            f"its metadata requires this credit.")


def render(meta, digest=None) -> str:
    """The `AvatarCredit.kt` source for this metadata."""
    fields = credit_fields(meta)
    lines = [
        "package app.signsetu.ui.settings",
        "",
        "/**",
        " * The shipped avatar's credit, generated from assets/avatar/model.vrm by",
        " * tools/security/vrm_credit.py. Do not edit: the model's metadata is the source.",
        " */",
        "internal object AvatarCredit {",
        f"    const val TITLE = {kotlin_string(fields['title'])}",
        f"    const val AUTHOR = {kotlin_string(fields['author'])}",
        f"    const val LICENCE_NAME = {kotlin_string(fields['licence_name'])}",
        f"    const val LICENCE_URL = {kotlin_string(fields['licence_url'])}",
        "",
        "    /** Name column of the licences dialog. */",
        f"    const val ENTRY_NAME = {kotlin_string(entry_name(fields))}",
        "",
        "    /** Licence column: the credit the model's own metadata requires. */",
        f"    const val TEXT = {kotlin_string(text(fields))}",
        "}",
        "",
    ]
    return "\n".join(lines)


def digest_of(path=MODEL) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--model", type=pathlib.Path, default=MODEL)
    parser.add_argument("--out", type=pathlib.Path, default=OUT)
    parser.add_argument("--write", action="store_true", help="rewrite AvatarCredit.kt")
    args = parser.parse_args(argv)
    source = render(meta_of(args.model))
    if not args.write:
        current = args.out.read_text(encoding="utf-8") if args.out.is_file() else ""
        return 0 if current == source else 1
    args.out.write_text(source, encoding="utf-8", newline="\n")
    print(f"{args.model.name} ({digest_of(args.model)[:12]}) -> {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
