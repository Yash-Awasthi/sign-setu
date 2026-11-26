# Theme contrast

Every colour pair the app actually draws, measured in all ten themes in the light
scheme, the dark scheme and the OLED dark scheme. This is the table behind the
palette choices in `ui/theme/Palettes.kt` and `ui/theme/MihonPalettes.kt`; a change
that drops a pair under its floor fails `ContrastTest`.

## Method

Ratio is WCAG 2.1 relative luminance, `(L1 + 0.05) / (L2 + 0.05)`. Floors: 4.5:1
for every pair that carries words, 3:1 for `outline`, 1:1 for `outlineVariant`
(decoration only). Translucent ink is composited over its background first, so the
muted band line is measured as it looks, not as white.

Regenerate with:

```
./gradlew --console=plain :app:testStandardDebugUnitTest --tests app.signsetu.ContrastTest
```

The test prints the full table and prints `CONTRAST TABLE` before it; the run fails
listing every pair under its floor.

## Text pairs

Each row is a foreground/background pair from the Material scheme. `light`, `dark`
and `oled` are the ratio in that mode.

### Emerald

| pair | floor | light | dark | oled |
|---|---|---|---|---|
| `onBackground/background` | 4.5 | 15.23 | 16.04 | 17.46 |
| `onSurface/surface` | 4.5 | 15.89 | 16.04 | 17.46 |
| `onSurfaceVariant/surfaceVariant` | 4.5 | 5.57 | 7.51 | 9.23 |
| `onSurfaceVariant/surface` | 4.5 | 6.67 | 9.10 | 9.91 |
| `onSurface/surfaceContainer` | 4.5 | 14.05 | 13.54 | 16.26 |
| `onPrimary/primary` | 4.5 | 5.91 | 6.40 | 6.40 |
| `onPrimaryContainer/primaryContainer` | 4.5 | 12.12 | 7.35 | 7.35 |
| `onSecondary/secondary` | 4.5 | 7.36 | 8.84 | 8.84 |
| `onSecondaryContainer/secondaryContainer` | 4.5 | 11.11 | 9.57 | 9.57 |
| `onTertiary/tertiary` | 4.5 | 6.06 | 8.54 | 8.54 |
| `onTertiaryContainer/tertiaryContainer` | 4.5 | 10.81 | 8.16 | 8.16 |
| `onError/error` | 4.5 | 6.54 | 7.66 | 7.66 |
| `onErrorContainer/errorContainer` | 4.5 | 12.77 | 7.17 | 7.17 |
| `inverseOnSurface/inverseSurface` | 4.5 | 12.33 | 13.23 | 13.23 |
| `outline/surface` | 3.0 | 3.79 | 4.27 | 4.65 |
| `outlineVariant/surface` | 1.0 | 1.44 | 1.63 | 1.77 |

### Mocha

| pair | floor | light | dark | oled |
|---|---|---|---|---|
| `onBackground/background` | 4.5 | 14.83 | 14.90 | 16.56 |
| `onSurface/surface` | 4.5 | 15.21 | 14.90 | 16.56 |
| `onSurfaceVariant/surfaceVariant` | 4.5 | 5.34 | 6.12 | 7.51 |
| `onSurfaceVariant/surface` | 4.5 | 6.26 | 7.25 | 8.06 |
| `onSurface/surfaceContainer` | 4.5 | 13.72 | 12.58 | 15.43 |
| `onPrimary/primary` | 4.5 | 8.87 | 14.40 | 14.40 |
| `onPrimaryContainer/primaryContainer` | 4.5 | 10.05 | 7.46 | 7.46 |
| `onSecondary/secondary` | 4.5 | 6.67 | 11.49 | 11.49 |
| `onSecondaryContainer/secondaryContainer` | 4.5 | 10.41 | 9.22 | 9.22 |
| `onTertiary/tertiary` | 4.5 | 5.46 | 9.02 | 9.02 |
| `onTertiaryContainer/tertiaryContainer` | 4.5 | 9.89 | 7.08 | 7.08 |
| `onError/error` | 4.5 | 6.54 | 7.66 | 7.66 |
| `onErrorContainer/errorContainer` | 4.5 | 12.77 | 7.17 | 7.17 |
| `inverseOnSurface/inverseSurface` | 4.5 | 11.83 | 12.58 | 12.58 |
| `outline/surface` | 3.0 | 3.62 | 3.05 | 3.39 |
| `outlineVariant/surface` | 1.0 | 1.40 | 1.49 | 1.66 |

### Cerulean

| pair | floor | light | dark | oled |
|---|---|---|---|---|
| `onBackground/background` | 4.5 | 15.39 | 16.63 | 17.64 |
| `onSurface/surface` | 4.5 | 15.82 | 16.63 | 17.64 |
| `onSurfaceVariant/surfaceVariant` | 4.5 | 5.55 | 6.78 | 7.78 |
| `onSurfaceVariant/surface` | 4.5 | 6.53 | 7.88 | 8.36 |
| `onSurface/surfaceContainer` | 4.5 | 14.22 | 14.32 | 16.43 |
| `onPrimary/primary` | 4.5 | 7.05 | 10.32 | 10.32 |
| `onPrimaryContainer/primaryContainer` | 4.5 | 12.85 | 7.53 | 7.53 |
| `onSecondary/secondary` | 4.5 | 7.05 | 11.05 | 11.05 |
| `onSecondaryContainer/secondaryContainer` | 4.5 | 11.64 | 10.30 | 10.30 |
| `onTertiary/tertiary` | 4.5 | 5.70 | 8.92 | 8.92 |
| `onTertiaryContainer/tertiaryContainer` | 4.5 | 12.25 | 8.58 | 8.58 |
| `onError/error` | 4.5 | 6.54 | 7.66 | 7.66 |
| `onErrorContainer/errorContainer` | 4.5 | 12.77 | 7.17 | 7.17 |
| `inverseOnSurface/inverseSurface` | 4.5 | 12.35 | 14.32 | 14.32 |
| `outline/surface` | 3.0 | 3.68 | 3.13 | 3.32 |
| `outlineVariant/surface` | 1.0 | 1.41 | 1.46 | 1.54 |

### Amethyst

| pair | floor | light | dark | oled |
|---|---|---|---|---|
| `onBackground/background` | 4.5 | 16.28 | 16.64 | 17.19 |
| `onSurface/surface` | 4.5 | 16.66 | 16.64 | 17.19 |
| `onSurfaceVariant/surfaceVariant` | 4.5 | 5.87 | 6.79 | 7.45 |
| `onSurfaceVariant/surface` | 4.5 | 6.92 | 7.74 | 8.00 |
| `onSurface/surfaceContainer` | 4.5 | 15.03 | 14.59 | 16.01 |
| `onPrimary/primary` | 4.5 | 11.48 | 9.88 | 9.88 |
| `onPrimaryContainer/primaryContainer` | 4.5 | 13.15 | 11.03 | 11.03 |
| `onSecondary/secondary` | 4.5 | 7.81 | 10.65 | 10.65 |
| `onSecondaryContainer/secondaryContainer` | 4.5 | 12.16 | 11.08 | 11.08 |
| `onTertiary/tertiary` | 4.5 | 6.36 | 13.35 | 13.35 |
| `onTertiaryContainer/tertiaryContainer` | 4.5 | 12.64 | 8.50 | 8.50 |
| `onError/error` | 4.5 | 6.54 | 7.66 | 7.66 |
| `onErrorContainer/errorContainer` | 4.5 | 12.77 | 7.17 | 7.17 |
| `inverseOnSurface/inverseSurface` | 4.5 | 13.15 | 14.59 | 14.59 |
| `outline/surface` | 3.0 | 3.80 | 3.00 | 3.10 |
| `outlineVariant/surface` | 1.0 | 1.43 | 1.41 | 1.46 |

### Contrast

| pair | floor | light | dark | oled |
|---|---|---|---|---|
| `onBackground/background` | 4.5 | 21.00 | 21.00 | 21.00 |
| `onSurface/surface` | 4.5 | 21.00 | 21.00 | 21.00 |
| `onSurfaceVariant/surfaceVariant` | 4.5 | 21.00 | 21.00 | 19.56 |
| `onSurfaceVariant/surface` | 4.5 | 21.00 | 21.00 | 21.00 |
| `onSurface/surfaceContainer` | 4.5 | 21.00 | 21.00 | 19.56 |
| `onPrimary/primary` | 4.5 | 16.13 | 16.13 | 16.13 |
| `onPrimaryContainer/primaryContainer` | 4.5 | 21.00 | 21.00 | 21.00 |
| `onSecondary/secondary` | 4.5 | 14.30 | 14.30 | 14.30 |
| `onSecondaryContainer/secondaryContainer` | 4.5 | 21.00 | 21.00 | 21.00 |
| `onTertiary/tertiary` | 4.5 | 11.91 | 11.91 | 11.91 |
| `onTertiaryContainer/tertiaryContainer` | 4.5 | 21.00 | 21.00 | 21.00 |
| `onError/error` | 4.5 | 7.57 | 7.57 | 7.57 |
| `onErrorContainer/errorContainer` | 4.5 | 21.00 | 21.00 | 21.00 |
| `inverseOnSurface/inverseSurface` | 4.5 | 21.00 | 21.00 | 21.00 |
| `outline/surface` | 3.0 | 21.00 | 21.00 | 21.00 |
| `outlineVariant/surface` | 1.0 | 21.00 | 21.00 | 21.00 |

### Teal Turquoise

| pair | floor | light | dark | oled |
|---|---|---|---|---|
| `onBackground/background` | 4.5 | 19.53 | 11.95 | 15.60 |
| `onSurface/surface` | 4.5 | 19.53 | 11.95 | 15.60 |
| `onSurfaceVariant/surfaceVariant` | 4.5 | 18.07 | 10.01 | 14.53 |
| `onSurfaceVariant/surface` | 4.5 | 19.53 | 11.95 | 15.60 |
| `onSurface/surfaceContainer` | 4.5 | 18.07 | 10.01 | 14.53 |
| `onPrimary/primary` | 4.5 | 4.77 | 12.79 | 12.79 |
| `onPrimaryContainer/primaryContainer` | 4.5 | 4.77 | 12.79 | 12.79 |
| `onSecondary/secondary` | 4.5 | 4.77 | 12.79 | 12.79 |
| `onSecondaryContainer/secondaryContainer` | 4.5 | 5.32 | 5.29 | 5.29 |
| `onTertiary/tertiary` | 4.5 | 8.59 | 6.08 | 6.08 |
| `onTertiaryContainer/tertiaryContainer` | 4.5 | 7.02 | 4.65 | 4.65 |
| `onError/error` | 4.5 | 6.54 | 7.66 | 7.66 |
| `onErrorContainer/errorContainer` | 4.5 | 12.77 | 7.17 | 7.17 |
| `inverseOnSurface/inverseSurface` | 4.5 | 19.53 | 11.95 | 11.95 |
| `outline/surface` | 3.0 | 4.30 | 5.09 | 6.65 |
| `outlineVariant/surface` | 1.0 | 1.63 | 1.72 | 2.25 |

### Tidal Wave

| pair | floor | light | dark | oled |
|---|---|---|---|---|
| `onBackground/background` | 4.5 | 16.61 | 13.23 | 16.26 |
| `onSurface/surface` | 4.5 | 16.61 | 13.23 | 16.26 |
| `onSurfaceVariant/surfaceVariant` | 4.5 | 8.05 | 8.47 | 11.50 |
| `onSurfaceVariant/surface` | 4.5 | 9.08 | 10.05 | 12.35 |
| `onSurface/surfaceContainer` | 4.5 | 14.72 | 11.16 | 15.15 |
| `onPrimary/primary` | 4.5 | 6.46 | 7.72 | 7.72 |
| `onPrimaryContainer/primaryContainer` | 4.5 | 10.94 | 7.27 | 7.27 |
| `onSecondary/secondary` | 4.5 | 6.46 | 7.72 | 7.72 |
| `onSecondaryContainer/secondaryContainer` | 4.5 | 11.89 | 7.27 | 7.27 |
| `onTertiary/tertiary` | 4.5 | 13.22 | 13.22 | 13.22 |
| `onTertiaryContainer/tertiaryContainer` | 4.5 | 11.26 | 10.15 | 10.15 |
| `onError/error` | 4.5 | 6.54 | 7.66 | 7.66 |
| `onErrorContainer/errorContainer` | 4.5 | 12.77 | 7.17 | 7.17 |
| `inverseOnSurface/inverseSurface` | 4.5 | 16.70 | 13.86 | 13.86 |
| `outline/surface` | 3.0 | 4.38 | 5.40 | 6.63 |
| `outlineVariant/surface` | 1.0 | 1.66 | 1.83 | 2.25 |

### Tokyo Night

| pair | floor | light | dark | oled |
|---|---|---|---|---|
| `onBackground/background` | 4.5 | 4.52 | 10.26 | 14.09 |
| `onSurface/surface` | 4.5 | 6.27 | 10.26 | 14.09 |
| `onSurfaceVariant/surfaceVariant` | 4.5 | 4.78 | 4.54 | 10.46 |
| `onSurfaceVariant/surface` | 4.5 | 6.15 | 8.18 | 11.23 |
| `onSurface/surfaceContainer` | 4.5 | 4.90 | 8.61 | 13.13 |
| `onPrimary/primary` | 4.5 | 5.08 | 7.27 | 7.27 |
| `onPrimaryContainer/primaryContainer` | 4.5 | 4.78 | 4.98 | 4.98 |
| `onSecondary/secondary` | 4.5 | 5.51 | 11.29 | 11.29 |
| `onSecondaryContainer/secondaryContainer` | 4.5 | 4.79 | 6.56 | 6.56 |
| `onTertiary/tertiary` | 4.5 | 5.46 | 7.35 | 7.35 |
| `onTertiaryContainer/tertiaryContainer` | 4.5 | 4.97 | 5.17 | 5.17 |
| `onError/error` | 4.5 | 5.40 | 6.45 | 6.45 |
| `onErrorContainer/errorContainer` | 4.5 | 6.53 | 5.19 | 5.19 |
| `inverseOnSurface/inverseSurface` | 4.5 | 4.52 | 10.26 | 10.26 |
| `outline/surface` | 3.0 | 3.71 | 4.61 | 6.33 |
| `outlineVariant/surface` | 1.0 | 1.69 | 1.80 | 2.47 |

### Yin Yang

| pair | floor | light | dark | oled |
|---|---|---|---|---|
| `onBackground/background` | 4.5 | 15.64 | 13.36 | 16.83 |
| `onSurface/surface` | 4.5 | 15.64 | 13.36 | 16.83 |
| `onSurfaceVariant/surfaceVariant` | 4.5 | 6.48 | 8.52 | 12.81 |
| `onSurfaceVariant/surface` | 4.5 | 7.80 | 10.92 | 13.75 |
| `onSurface/surfaceContainer` | 4.5 | 12.98 | 10.42 | 15.67 |
| `onPrimary/primary` | 4.5 | 21.00 | 6.90 | 6.90 |
| `onPrimaryContainer/primaryContainer` | 4.5 | 21.00 | 21.00 | 21.00 |
| `onSecondary/secondary` | 4.5 | 21.00 | 6.90 | 6.90 |
| `onSecondaryContainer/secondaryContainer` | 4.5 | 14.40 | 5.26 | 5.26 |
| `onTertiary/tertiary` | 4.5 | 21.00 | 21.00 | 21.00 |
| `onTertiaryContainer/tertiaryContainer` | 4.5 | 13.20 | 7.22 | 7.22 |
| `onError/error` | 4.5 | 6.54 | 7.66 | 7.66 |
| `onErrorContainer/errorContainer` | 4.5 | 12.77 | 7.17 | 7.17 |
| `inverseOnSurface/inverseSurface` | 4.5 | 11.49 | 13.36 | 13.36 |
| `outline/surface` | 3.0 | 3.73 | 5.85 | 7.37 |
| `outlineVariant/surface` | 1.0 | 1.68 | 1.78 | 2.25 |

### Yotsuba

| pair | floor | light | dark | oled |
|---|---|---|---|---|
| `onBackground/background` | 4.5 | 16.70 | 13.31 | 16.31 |
| `onSurface/surface` | 4.5 | 16.70 | 13.31 | 16.31 |
| `onSurfaceVariant/surfaceVariant` | 4.5 | 8.01 | 8.50 | 11.52 |
| `onSurfaceVariant/surface` | 4.5 | 9.13 | 10.09 | 12.36 |
| `onSurface/surfaceContainer` | 4.5 | 14.65 | 11.21 | 15.19 |
| `onPrimary/primary` | 4.5 | 6.45 | 7.71 | 7.71 |
| `onPrimaryContainer/primaryContainer` | 4.5 | 13.25 | 7.26 | 7.26 |
| `onSecondary/secondary` | 4.5 | 6.45 | 7.71 | 7.71 |
| `onSecondaryContainer/secondaryContainer` | 4.5 | 11.42 | 7.26 | 7.26 |
| `onTertiary/tertiary` | 4.5 | 6.43 | 7.77 | 7.77 |
| `onTertiaryContainer/tertiaryContainer` | 4.5 | 13.29 | 7.26 | 7.26 |
| `onError/error` | 4.5 | 6.54 | 7.66 | 7.66 |
| `onErrorContainer/errorContainer` | 4.5 | 12.77 | 7.17 | 7.17 |
| `inverseOnSurface/inverseSurface` | 4.5 | 11.57 | 13.31 | 13.31 |
| `outline/surface` | 3.0 | 4.38 | 5.38 | 6.60 |
| `outlineVariant/surface` | 1.0 | 1.66 | 1.83 | 2.25 |

## Stage band

The band behind the officer's captions. `onBand` is the main line, `onBandMuted`
the smaller hint, both over `inkLight` / `inkDark`.

| theme | light onBand | light muted | dark onBand | dark muted |
|---|---|---|---|---|
| Emerald | 5.91 | 5.18 | 6.41 | 5.55 |
| Mocha | 8.87 | 7.60 | 6.13 | 5.35 |
| Cerulean | 7.05 | 6.04 | 5.55 | 4.83 |
| Amethyst | 11.48 | 9.60 | 6.71 | 5.79 |
| Contrast | 21.00 | 21.00 | 21.00 | 21.00 |
| Teal Turquoise | 10.84 | 9.14 | 11.92 | 9.99 |
| Tidal Wave | 13.01 | 10.83 | 12.17 | 10.23 |
| Tokyo Night | 11.25 | 9.47 | 13.72 | 11.41 |
| Yin Yang | 21.00 | 16.83 | 9.15 | 7.81 |
| Yotsuba | 12.96 | 10.71 | 12.15 | 10.22 |

## Dark call stage

The call stage uses the dark scheme whatever the officer's setting, and the
`accentSoft` line is information text drawn on `bg` (the rights notice).

| theme | text/bg | muted/bg | accent/bg | accentSoft/bg |
|---|---|---|---|---|
| Emerald | 16.04 | 9.10 | 9.43 | 16.04 |
| Mocha | 14.90 | 7.25 | 13.75 | 14.90 |
| Cerulean | 16.63 | 7.88 | 10.32 | 16.63 |
| Amethyst | 16.64 | 7.74 | 10.14 | 16.64 |
| Contrast | 21.00 | 21.00 | 16.13 | 21.00 |
| Teal Turquoise | 11.95 | 11.95 | 9.80 | 11.95 |
| Tidal Wave | 13.23 | 10.05 | 10.01 | 13.23 |
| Tokyo Night | 10.26 | 8.18 | 6.66 | 10.26 |
| Yin Yang | 13.36 | 10.92 | 16.67 | 13.36 |
| Yotsuba | 13.31 | 10.09 | 10.08 | 13.31 |

## What the measurement changed

The first run of the test found 23 failing pairs in the borrowed Mihon schemes and
8 failing stage pairs. The fixes kept every hue recognisable and only moved a value
until the pair cleared its floor:

- TEAL_TURQUOISE: light `onSecondaryContainer` 008080 -> 006463 (3.63 -> 5.32);
  dark `onTertiaryContainer` BF1F2F -> D94A54 (3.18 -> 4.65).
- TIDAL_WAVE: light `onTertiaryContainer` 78FFD6 -> 00382A on its light container
  (1.05 -> 11.26); dark `tertiaryContainer` C3FADA -> 0B3B30 so the light text reads
  (1.05 -> 10.15).
- TOKYO_NIGHT light: `primary` 2E7DE9 -> 1F6BD6, `onSurface` 3760BF -> 2B4C98,
  `onSurfaceVariant` 6172B0 -> 3F4F80, `onSecondaryContainer` 006A83 -> 00536A,
  `tertiary` 9854F1 -> 8642DE, `onTertiaryContainer` 7847BD -> 62339C,
  `error` F52A65 -> CE1B4E.
- TOKYO_NIGHT dark: `onSurfaceVariant` 828BB8 -> B4BCE2, `onPrimaryContainer`
  9AB8FF -> AEC6FF, `onTertiaryContainer` CAABFF -> D7BFFF, `errorContainer`
  C53B53 -> B33248 with `onErrorContainer` FFE8EA.
- YIN_YANG dark: `secondaryContainer` 717171 -> 5C5C5C.
- Stage band: `onBandMuted` white at 75% -> 90%, because the composited hint fell to
  3.83:1 on the Cerulean dark band (now 4.81:1, worst case).
- Call stage: `accentSoft` now takes the dark `onBackground` instead of
  `onPrimaryContainer`; the rights notice is drawn on `bg`, where the container ink
  measured as low as 1.05:1.
