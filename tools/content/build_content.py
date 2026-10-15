"""Builds the app's content bundle: renders picture cards and writes the hash manifest.

Run with the tools environment (resvg-py, Pillow):
    python tools/content/build_content.py                  # cards and manifest
    python tools/content/build_content.py --manifest-only  # after editing JSON or audio
Audio is generated separately by build_audio.py, which needs the GPU environment.
"""
import hashlib
import io
import json
import pathlib
import sys

import resvg_py
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
SYMBOLS = ROOT / "content" / "symbols"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "content"
CARD_PX = 320


def render_cards() -> None:
    mapping = json.loads((SYMBOLS / "map.json").read_text(encoding="utf-8"))
    cards = json.loads((ASSETS / "cards.json").read_text(encoding="utf-8"))["cards"]
    missing = [c["id"] for c in cards if c["id"] not in mapping]
    if missing:
        sys.exit(f"cards without a symbol in map.json: {missing}")
    out = ASSETS / "cards"
    out.mkdir(parents=True, exist_ok=True)
    for card in cards:
        svg = SYMBOLS / mapping[card["id"]]
        png = bytes(resvg_py.svg_to_bytes(svg_path=str(svg), width=CARD_PX, height=CARD_PX))
        Image.open(io.BytesIO(png)).save(out / f"{card['id']}.webp", "WEBP", quality=88, method=6)
    print(f"rendered {len(cards)} cards")


def write_manifest() -> None:
    files = {}
    for path in sorted(ASSETS.rglob("*")):
        if path.is_file() and path.name != "manifest.json":
            files[path.relative_to(ASSETS).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    manifest = {"version": 1, "files": files}
    (ASSETS / "manifest.json").write_text(json.dumps(manifest, indent=1, sort_keys=True), encoding="utf-8")
    print(f"manifest: {len(files)} files")


if __name__ == "__main__":
    if "--manifest-only" not in sys.argv:
        render_cards()
    write_manifest()
