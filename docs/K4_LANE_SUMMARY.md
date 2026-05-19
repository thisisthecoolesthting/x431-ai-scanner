# K4 Lane Summary — Launcher icons + empty-state assets

**Branch:** `feat/tcw-launcher-icons-and-empty-states`  
**Worktree:** `_x431-worktrees/K4`  
**Rasterization path:** Option B — PowerShell `System.Drawing` (ImageMagick / `magick` not installed on build machine)

## Assets created

### Brand sources (`assets/brand/`)

| File | Description |
|---|---|
| `tcw-mark.svg` | 256×256 two-wrench T mark (viewBox 108×108) |
| `tcw-wordmark.svg` | 800×200 mark + TOGETHER CAR WORKS text |
| `README.md` | Palette, brand law, file structure |

### Adaptive launcher

| File | Description |
|---|---|
| `drawable/ic_launcher_foreground.xml` | Two-wrench T (blue horizontal + amber vertical, bolt heads, ink outline) |
| `drawable/ic_launcher_background.xml` | Solid `#0B5FFF` fill |
| `mipmap-anydpi-v26/ic_launcher.xml` | Adaptive icon wrapper |
| `mipmap-anydpi-v26/ic_launcher_round.xml` | Round adaptive icon wrapper |

### Legacy PNG mipmaps (10 files)

| Density | Files | Size (px) | PNG bytes (approx) |
|---|---|---|---|
| mdpi | `ic_launcher.png`, `ic_launcher_round.png` | 48 | ~598 |
| hdpi | both | 72 | ~864 |
| xhdpi | both | 96 | ~1064 |
| xxhdpi | both | 144 | ~1719 |
| xxxhdpi | both | 192 | ~2309 |

### Notification + splash

| File | Viewport | Description |
|---|---|---|
| `drawable/ic_notification.xml` | 24×24 | White silhouette T mark (Android tints) |
| `drawable/splash_logo.xml` | 320×120 | Mark only; Compose renders wordmark text |

### Empty-state vectors (240×160)

| File | Scene |
|---|---|
| `empty_no_vci.xml` | OBD-II port + unplugged amber cable |
| `empty_no_db.xml` | Empty folder + downward arrow |
| `empty_receiver_offline.xml` | PC monitor + broken Wi-Fi glyph |
| `empty_no_dtcs.xml` | Clipboard + green check (no codes) |
| `empty_update_available.xml` | App tile + down arrow + sparkles |
| `empty_update_success.xml` | Green check in rounded square + amber sparkles |

### Documentation

| File | Description |
|---|---|
| `scripts/regen-icons.md` | ImageMagick/Inkscape commands + PowerShell fallback script |

## Commits (7)

```
feat: brand mark + wordmark SVG sources
feat: adaptive icon foreground + background vector drawables
feat: mipmap-* launcher icons (mdpi-xxxhdpi, normal + round)
feat: monochrome notification icon
feat: splash logo vector
feat: empty-state vector drawables
docs: regen-icons + brand README
```

## Quality caveats

- **First-cut launcher PNGs** use pure rounded-rectangle shapes drawn via `System.Drawing`, not SVG rasterization. They are clean and on-brand at all densities but lack sub-pixel fidelity from a vector renderer. Re-run `scripts/regen-icons.md` Option A once ImageMagick or Inkscape is available.
- **Round launcher PNGs** are identical to square PNGs (no circular mask baked in); Android applies the round mask at runtime via adaptive icons on API 26+.
- **Splash wordmark** is intentionally omitted from `splash_logo.xml`; the Compose splash screen should render "TOGETHER CAR WORKS" as text for crisp typography.
- **Empty states** are minimal two-color flat illustrations (3–8 paths each), suitable for operator-approved first commercial cut; can be replaced with higher-fidelity art before 1.0 if desired.

## Manifest

No manifest edits in this lane. Existing `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round` references slot in automatically.
