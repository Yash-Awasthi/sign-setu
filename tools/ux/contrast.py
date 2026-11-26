#!/usr/bin/env python3
"""Recompute the WCAG contrast table from the Kotlin colour tokens.

Reads the hex values out of app/src/main/java/app/signsetu/ui/theme/Palettes.kt and
MihonPalettes.kt (plus the Material 3 baseline for tokens a scheme leaves unset),
composites translucent ink the way it is drawn, and prints the ratio for every pair.
No ratio is copied from a document: run this script to get them.

    python tools/ux/contrast.py            # full table
    python tools/ux/contrast.py --failing  # only pairs under their floor

Exits non-zero when a pair is under its floor, so it can gate a release.
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
THEME = ROOT / "app/src/main/java/app/signsetu/ui/theme"

TEXT = 4.5
LARGE = 3.0
DECORATION = 1.0

# Material 3 baseline schemes: the values lightColorScheme()/darkColorScheme() use for
# every token a palette does not name. Compose 1.13 / Material 3 keeps the M3 baseline.
LIGHT_DEFAULTS = {
    "primary": "FF6650A4", "onPrimary": "FFFFFFFF",
    "primaryContainer": "FFEADDFF", "onPrimaryContainer": "FF21005D",
    "inversePrimary": "FFD0BCFF",
    "secondary": "FF625B71", "onSecondary": "FFFFFFFF",
    "secondaryContainer": "FFE8DEF8", "onSecondaryContainer": "FF1D192B",
    "tertiary": "FF7D5260", "onTertiary": "FFFFFFFF",
    "tertiaryContainer": "FFFFD8E4", "onTertiaryContainer": "FF31111D",
    "error": "FFB3261E", "onError": "FFFFFFFF",
    "errorContainer": "FFF9DEDC", "onErrorContainer": "FF410E0B",
    "background": "FFFFFFFF", "onBackground": "FF1D1B20",
    "surface": "FFFFFFFF", "onSurface": "FF1D1B20",
    "surfaceVariant": "FFE7E0EC", "onSurfaceVariant": "FF49454F",
    "outline": "FF79747E", "outlineVariant": "FFCAC4D0",
    "inverseSurface": "FF322F35", "inverseOnSurface": "FFF5EFF7",
    "surfaceContainerLowest": "FFFFFFFF", "surfaceContainerLow": "FFF7F2FA",
    "surfaceContainer": "FFF3EDF7", "surfaceContainerHigh": "FFECE6F0",
    "surfaceContainerHighest": "FFE6E0E9",
}
DARK_DEFAULTS = {
    "primary": "FFD0BCFF", "onPrimary": "FF381E72",
    "primaryContainer": "FF4F378B", "onPrimaryContainer": "FFEADDFF",
    "inversePrimary": "FF6750A4",
    "secondary": "FFCCC2DC", "onSecondary": "FF332D41",
    "secondaryContainer": "FF4A4458", "onSecondaryContainer": "FFE8DEF8",
    "tertiary": "FFEFB8C8", "onTertiary": "FF492532",
    "tertiaryContainer": "FF633B48", "onTertiaryContainer": "FFFFD8E4",
    "error": "FFF2B8B5", "onError": "FF601410",
    "errorContainer": "FF8C1D18", "onErrorContainer": "FFF9DEDC",
    "background": "FF141218", "onBackground": "FFE6E0E9",
    "surface": "FF141218", "onSurface": "FFE6E0E9",
    "surfaceVariant": "FF49454F", "onSurfaceVariant": "FFCAC4D0",
    "outline": "FF938F99", "outlineVariant": "FF49454F",
    "inverseSurface": "FFE6E0E9", "inverseOnSurface": "FF322F35",
    "surfaceContainerLowest": "FF0F0D13", "surfaceContainerLow": "FF1D1B20",
    "surfaceContainer": "FF211F26", "surfaceContainerHigh": "FF2B2930",
    "surfaceContainerHighest": "FF36343B",
}

PAIRS = [
    ("onBackground", "background", TEXT),
    ("onSurface", "surface", TEXT),
    ("onSurfaceVariant", "surfaceVariant", TEXT),
    ("onSurfaceVariant", "surface", TEXT),
    ("onSurface", "surfaceContainer", TEXT),
    ("onPrimary", "primary", TEXT),
    ("onPrimaryContainer", "primaryContainer", TEXT),
    ("onSecondary", "secondary", TEXT),
    ("onSecondaryContainer", "secondaryContainer", TEXT),
    ("onTertiary", "tertiary", TEXT),
    ("onTertiaryContainer", "tertiaryContainer", TEXT),
    ("onError", "error", TEXT),
    ("onErrorContainer", "errorContainer", TEXT),
    ("inverseOnSurface", "inverseSurface", TEXT),
    ("outline", "surface", LARGE),
    ("outlineVariant", "surface", DECORATION),
]


def parse_schemes() -> dict[str, dict[str, dict[str, str]]]:
    """{theme: {"light"|"dark": {token: ARGB hex}}} from the two palette files."""
    themes: dict[str, dict[str, dict[str, str]]] = {}

    def named_blocks(text: str, pattern: str):
        for m in re.finditer(pattern, text, re.S):
            yield m.group(1), m.group(2)

    def colors(block: str) -> dict[str, str]:
        found = dict(re.findall(r"(\w+)\s*=\s*Color\(0x([0-9A-Fa-f]{8})\)", block))
        found.update((m.group(1), "FF000000") for m in re.finditer(r"(\w+)\s*=\s*Color\.Black", block))
        found.update((m.group(1), "FFFFFFFF") for m in re.finditer(r"(\w+)\s*=\s*Color\.White", block))
        return found

    text = (THEME / "Palettes.kt").read_text(encoding="utf-8")
    # private val EmeraldPalette = Palette(light = lightColorScheme(...), dark = darkColorScheme(...), ...)
    for name, body in named_blocks(text, r"private val (\w+)Palette = Palette\((.*?)\n\)"):
        light = re.search(r"light = lightColorScheme\((.*?)\n    \),", body, re.S)
        dark = re.search(r"dark = darkColorScheme\((.*?)\n    \),", body, re.S)
        if light and dark:
            themes[name] = {"light": colors(light.group(1)), "dark": colors(dark.group(1))}

    # The Contrast palette names one scheme twice and is built in one line.
    contrast = re.search(r"private val ContrastScheme = darkColorScheme\((.*?)\n\)", text, re.S)
    if contrast:
        scheme = colors(contrast.group(1))
        themes["Contrast"] = {"light": dict(scheme), "dark": dict(scheme)}

    mihon = (THEME / "MihonPalettes.kt").read_text(encoding="utf-8")
    names = {
        "MihonTealTurqoise": "Teal Turquoise",
        "MihonTidalWave": "Tidal Wave",
        "MihonTokyoNight": "Tokyo Night",
        "MihonYinYang": "Yin Yang",
        "MihonYotsuba": "Yotsuba",
    }
    for name, title in names.items():
        block = re.search(rf"internal val {name} = mihon\((.*?)\n\)\n", mihon, re.S)
        if not block:
            raise SystemExit(f"cannot find {name} in MihonPalettes.kt")
        body = block.group(1)
        light = re.search(r"light = lightColorScheme\((.*?)\n    \),", body, re.S)
        dark = re.search(r"dark = darkColorScheme\((.*?)\n    \),", body, re.S)
        themes[title] = {"light": colors(light.group(1)), "dark": colors(dark.group(1))}
    return themes


def scheme(theme: dict[str, dict[str, str]], mode: str) -> dict[str, str]:
    base = LIGHT_DEFAULTS if mode == "light" else DARK_DEFAULTS
    out = dict(base)
    out.update(theme["light" if mode == "light" else "dark"])
    if mode == "oled":
        out.update({
            "background": "FF000000", "surface": "FF000000",
            "surfaceVariant": "FF0C0C0C",
            "surfaceContainerLowest": "FF000000", "surfaceContainerLow": "FF070707",
            "surfaceContainer": "FF0C0C0C", "surfaceContainerHigh": "FF131313",
            "surfaceContainerHighest": "FF1B1B1B",
        })
    return out


def argb(hex8: str) -> tuple[float, float, float, float]:
    return tuple(int(hex8[i:i + 2], 16) / 255.0 for i in (2, 4, 6, 0))  # r, g, b, a


def luminance(rgb: tuple[float, float, float]) -> float:
    def channel(v: float) -> float:
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = (channel(v) for v in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def over(fg: tuple[float, float, float, float], bg: tuple[float, float, float, float]):
    a = fg[3]
    return tuple(fg[i] * a + bg[i] * (1 - a) for i in range(3))


def ratio(fg_hex: str, bg_hex: str) -> float:
    fg, bg = argb(fg_hex), argb(bg_hex)
    a = luminance(over(fg, bg) if fg[3] < 1 else fg[:3])
    b = luminance(bg[:3])
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)


DOC = ROOT / "docs/26-theme-contrast.md"
TOLERANCE = 0.06
WHITE = "FFFFFFFF"

MIHON = {
    "MihonTealTurqoise": "Teal Turquoise",
    "MihonTidalWave": "Tidal Wave",
    "MihonTokyoNight": "Tokyo Night",
    "MihonYinYang": "Yin Yang",
    "MihonYotsuba": "Yotsuba",
}


def linear(channel: float) -> float:
    return channel / 12.92 if channel <= 0.04045 else ((channel + 0.055) / 1.055) ** 2.4


def encoded(channel: float) -> float:
    channel = max(0.0, min(1.0, channel))
    return 12.92 * channel if channel <= 0.0031308 else 1.055 * channel ** (1 / 2.4) - 0.055


def to_oklab(rgb: tuple[float, float, float]) -> tuple[float, float, float]:
    r, g, b = (linear(v) for v in rgb)
    l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
    l_, m_, s_ = (v ** (1 / 3) if v >= 0 else -(-v) ** (1 / 3) for v in (l, m, s))
    return (
        0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
        1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
        0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_,
    )


def from_oklab(lab: tuple[float, float, float]) -> tuple[float, float, float]:
    L, a, b = lab
    l_ = L + 0.3963377774 * a + 0.2158037573 * b
    m_ = L - 0.1055613458 * a - 0.0638541728 * b
    s_ = L - 0.0894841775 * a - 1.2914855480 * b
    l, m, s = l_ ** 3, m_ ** 3, s_ ** 3
    return (
        encoded(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
        encoded(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
        encoded(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s),
    )


def toward_black(hex8: str, fraction: float) -> str:
    """`lerp(colour, Color.Black, fraction)` as the palette builds the band ink, in Oklab."""
    lab = to_oklab(argb(hex8)[:3])
    mixed = from_oklab(tuple(v * (1 - fraction) for v in lab))
    return "FF" + "".join(f"{round(v * 255):02X}" for v in mixed)


def parse_bands() -> dict[str, dict[str, object]]:
    """{theme: {"light": band hex, "dark": band hex, "muted": alpha of onBandMuted}}."""
    text = (THEME / "Palettes.kt").read_text(encoding="utf-8")
    out: dict[str, dict[str, object]] = {}
    for name in ("Emerald", "Mocha", "Cerulean", "Amethyst"):
        block = re.search(rf"private val {name}Palette = Palette\((.*?)\n\)", text, re.S).group(1)
        light = re.search(r"inkLight = ink\(Color\(0x([0-9A-Fa-f]{8})\)\)", block).group(1)
        dark = re.search(r"inkDark = ink\(Color\(0x([0-9A-Fa-f]{8})\)\)", block).group(1)
        out[name] = {"light": light, "dark": dark, "muted": 0xE6 / 255}
    # The contrast scheme names white ink on a black band twice, and the hint is not dimmed.
    out["Contrast"] = {"light": "FF000000", "dark": "FF000000", "muted": 1.0}

    mihon = (THEME / "MihonPalettes.kt").read_text(encoding="utf-8")
    for name, title in MIHON.items():
        block = re.search(rf"internal val {name} = mihon\((.*?)\n\)\n", mihon, re.S).group(1)
        light = re.search(r"light = lightColorScheme\((.*?)\n    \),", block, re.S).group(1)
        dark = re.search(r"dark = darkColorScheme\((.*?)\n    \),", block, re.S).group(1)
        lp = re.search(r"primary = Color\(0x([0-9A-Fa-f]{8})\)", light).group(1)
        dp = re.search(r"primary = Color\(0x([0-9A-Fa-f]{8})\)", dark).group(1)
        out[title] = {"light": toward_black(lp, 0.35), "dark": toward_black(dp, 0.6), "muted": 0xE6 / 255}
    return out


def band_table(themes: dict[str, dict[str, dict[str, str]]]):
    inks = parse_bands()
    rows = {}
    for title in themes:
        ink = inks[title]
        muted = WHITE if ink["muted"] == 1.0 else "E6FFFFFF"
        rows[title] = (
            ratio(WHITE, ink["light"]), ratio(muted, ink["light"]),
            ratio(WHITE, ink["dark"]), ratio(muted, ink["dark"]),
        )
    return rows


def call_table(themes: dict[str, dict[str, dict[str, str]]]):
    """The call stage uses the dark scheme whatever the officer's setting."""
    rows = {}
    for title, theme in themes.items():
        dark = scheme(theme, "dark")
        rows[title] = (
            ratio(dark["onBackground"], dark["background"]),
            ratio(dark["onSurfaceVariant"], dark["background"]),
            ratio(dark["primary"], dark["background"]),
            # Palette.call draws accentSoft as the dark onBackground.
            ratio(dark["onBackground"], dark["background"]),
        )
    return rows


def parse_doc(text: str):
    pairs, band, call = {}, {}, {}
    section = ""
    for line in text.splitlines():
        if line.startswith("### "):
            section = line[4:].strip()
            continue
        if line.startswith("## "):
            section = line[3:].strip()
            continue
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 5 or set(cells[0]) <= set("-: `"):
            continue
        head = cells[0].strip("`")
        start = 1 if section in ("Stage band", "Dark call stage") else 2
        try:
            nums = tuple(float(c) for c in cells[start:])
        except ValueError:
            continue
        if section == "Stage band":
            band[head] = nums
        elif section == "Dark call stage":
            call[head] = nums
        elif "/" in head and len(nums) == 3:
            fg, bg = head.split("/")
            pairs[(section, fg, bg)] = nums
    return pairs, band, call


def compare_doc(path: pathlib.Path, themes, band, call) -> list[str]:
    doc_pairs, doc_band, doc_call = parse_doc(path.read_text(encoding="utf-8"))
    out: list[str] = []

    def check(where: str, documented: float, computed: float) -> None:
        if abs(documented - computed) > TOLERANCE:
            out.append(f"{where}: document {documented:.2f}, recomputed {computed:.2f}")

    for (title, fg, bg), values in doc_pairs.items():
        theme = themes.get(title)
        if theme is None:
            out.append(f"{title}: named by the document, not found in the palettes")
            continue
        for mode, documented in zip(("light", "dark", "oled"), values):
            check(f"{title} {mode} {fg}/{bg}", documented, ratio(scheme(theme, mode)[fg], scheme(theme, mode)[bg]))

    for title, values in doc_band.items():
        computed = band.get(title)
        if computed is None:
            out.append(f"{title}: band row for a theme the palettes do not have")
            continue
        for i, (mode, part) in enumerate((("light", "onBand"), ("light", "muted"),
                                          ("dark", "onBand"), ("dark", "muted"))):
            check(f"{title} band {mode} {part}", values[i], computed[i])

    for title, values in doc_call.items():
        computed = call.get(title)
        if computed is None:
            out.append(f"{title}: call row for a theme the palettes do not have")
            continue
        for i, part in enumerate(("text", "muted", "accent", "accentSoft")):
            check(f"{title} call {part}", values[i], computed[i])
    return out


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--failing", action="store_true", help="only pairs under their floor")
    parser.add_argument("--doc", nargs="?", const=str(DOC), metavar="PATH",
                        help="compare docs/26-theme-contrast.md with these numbers")
    args = parser.parse_args()

    themes = parse_schemes()
    failures: list[str] = []
    print(f"{'theme':<15} {'mode':<6} {'pair':<38} {'ratio':>6}  floor")
    for title, theme in themes.items():
        for mode in ("light", "dark", "oled"):
            s = scheme(theme, mode)
            for fg, bg, floor in PAIRS:
                r = ratio(s[fg], s[bg])
                if r < floor:
                    failures.append(f"{title} {mode} {fg}/{bg} {r:.2f} < {floor}")
                if not args.failing or r < floor:
                    print(f"{title:<15} {mode:<6} {fg + '/' + bg:<38} {r:6.2f}  {floor}")

    band = band_table(themes)
    call = call_table(themes)

    if args.doc:
        mismatches = compare_doc(pathlib.Path(args.doc), themes, band, call)
        for line in mismatches:
            print(line)
        if mismatches:
            print(f"\n{len(mismatches)} rows disagree with the document", file=sys.stderr)
            return 1
        print(f"\n{args.doc}: every row matches a recomputation from the palette hex values")
        return 0

    print()
    print("stage band: onBand / onBandMuted over inkLight and inkDark")
    print(f"{'theme':<15} {'light':>7} {'muted':>7} {'dark':>7} {'muted':>7}")
    for title, (l_on, l_muted, d_on, d_muted) in band.items():
        print(f"{title:<15} {l_on:7.2f} {l_muted:7.2f} {d_on:7.2f} {d_muted:7.2f}")
    print()
    print("dark call stage: text/bg, muted/bg, accent/bg, accentSoft/bg")
    print(f"{'theme':<15} {'text':>7} {'muted':>7} {'accent':>7} {'soft':>7}")
    for title, row in call.items():
        print(f"{title:<15} " + " ".join(f"{v:7.2f}" for v in row))

    if failures:
        print("\nUNDER FLOOR", file=sys.stderr)
        for line in failures:
            print("  " + line, file=sys.stderr)
        return 1
    print(f"\n{len(themes)} themes x 3 modes x {len(PAIRS)} pairs: all at or above their floor")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
