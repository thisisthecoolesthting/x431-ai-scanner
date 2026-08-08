# TCW brand assets

Source-of-truth vector files for **Together Car Works** (TCW) visual identity.

## Palette

| Token | Hex | Use |
|---|---|---|
| Primary blue | `#0B5FFF` | Brand action, launcher background, dominant illustration color |
| Accent amber | `#FF7A1A` | Wrench-mark amber, highlights, secondary illustration accent |
| Ink dark | `#0F1620` | Outlines, wordmark text, structural strokes |
| Surface light | `#F4F6F8` | Card/illustration backgrounds in empty states |
| Surface dark | `#0B0F14` | Dark theme app background (not used in these assets) |

Success green `#10A26A` appears only in empty-state checkmarks (per commercial plan).

## Brand law

- Mark = **two overlapping wrenches forming a stylized letter T** (rounded rectangles + optional bolt-head circles).
- One wrench is primary blue; the other is accent amber. Both carry a ~2 px ink-dark outline.
- **No "X" shape** anywhere (launcher, notification, splash, empty states).
- **No gears**, pill shapes, or circular-only icons.
- **No OEM brand references** (Launch, X431, cnlaunch, CaseForge, etc.) in artwork.
- Square or gently rounded corners on the mark — not a circle or pill.

## File structure

```
assets/brand/
  tcw-mark.svg       — 256×256 two-wrench T mark (viewBox 0 0 108 108)
  tcw-wordmark.svg   — 800×200 mark + "TOGETHER CAR WORKS" wordmark
  README.md          — this file

app/src/main/res/
  drawable/ic_launcher_foreground.xml   — adaptive icon foreground (108 dp)
  drawable/ic_launcher_background.xml   — solid #0B5FFF background
  drawable/ic_notification.xml        — white silhouette for status bar
  drawable/splash_logo.xml            — mark only (Compose renders wordmark text)
  drawable/empty_*.xml                — empty-state illustrations (240×160)
  mipmap-*/ic_launcher*.png           — legacy launcher PNGs (see scripts/regen-icons.md)
  mipmap-anydpi-v26/ic_launcher*.xml  — adaptive icon wrappers
```

## Wordmark

`TOGETHER CAR WORKS` — bold sans-serif, all caps, tight tracking (Inter Tight / Inter in SVG; system sans-serif fallback).

## Regenerating raster icons

See `scripts/regen-icons.md` for ImageMagick/Inkscape commands and the PowerShell `System.Drawing` fallback script.
