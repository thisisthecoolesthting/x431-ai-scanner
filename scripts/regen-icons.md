# Regenerating TCW launcher PNGs

Source SVG: `assets/brand/tcw-mark.svg` (viewBox `0 0 108 108`).

Target outputs (normal + round use identical art):

| Density | Folder | Size (px) |
|---|---|---|
| mdpi | `app/src/main/res/mipmap-mdpi/` | 48 |
| hdpi | `app/src/main/res/mipmap-hdpi/` | 72 |
| xhdpi | `app/src/main/res/mipmap-xhdpi/` | 96 |
| xxhdpi | `app/src/main/res/mipmap-xxhdpi/` | 144 |
| xxxhdpi | `app/src/main/res/mipmap-xxxhdpi/` | 192 |

Each folder needs `ic_launcher.png` and `ic_launcher_round.png`.

---

## Option A — ImageMagick (preferred)

Requires ImageMagick 7 (`magick`) or legacy `convert`. Run from repo root.

```bash
# ImageMagick 7
for size in 48 72 96 144 192; do
  case $size in
    48)  dir=mdpi ;;
    72)  dir=hdpi ;;
    96)  dir=xhdpi ;;
    144) dir=xxhdpi ;;
    192) dir=xxxhdpi ;;
  esac
  magick -background "#0B5FFF" -resize "${size}x${size}" assets/brand/tcw-mark.svg \
    "app/src/main/res/mipmap-${dir}/ic_launcher.png"
  cp "app/src/main/res/mipmap-${dir}/ic_launcher.png" \
    "app/src/main/res/mipmap-${dir}/ic_launcher_round.png"
done
```

Windows PowerShell equivalent:

```powershell
$map = @{48='mdpi';72='hdpi';96='xhdpi';144='xxhdpi';192='xxxhdpi'}
foreach ($size in $map.Keys) {
  $dir = "app/src/main/res/mipmap-$($map[$size])"
  magick -background "#0B5FFF" -resize "${size}x${size}" assets/brand/tcw-mark.svg "$dir/ic_launcher.png"
  Copy-Item "$dir/ic_launcher.png" "$dir/ic_launcher_round.png" -Force
}
```

## Option A-alt — Inkscape

```bash
inkscape assets/brand/tcw-mark.svg -w 192 -h 192 -b "#0B5FFF" \
  -o app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
# Repeat for each density size, then copy to ic_launcher_round.png
```

---

## Option B — PowerShell System.Drawing (no external tools)

Used for the first commercial cut on this machine (ImageMagick not installed). Copy-paste entire block from repo root:

```powershell
Add-Type -AssemblyName System.Drawing

function Draw-RoundedRect($g, $brush, $pen, $x, $y, $w, $h, $r) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddArc([float]$x, [float]$y, [float](2*$r), [float](2*$r), 180, 90)
    $path.AddArc([float]($x+$w-2*$r), [float]$y, [float](2*$r), [float](2*$r), 270, 90)
    $path.AddArc([float]($x+$w-2*$r), [float]($y+$h-2*$r), [float](2*$r), [float](2*$r), 0, 90)
    $path.AddArc([float]$x, [float]($y+$h-2*$r), [float](2*$r), [float](2*$r), 90, 90)
    $path.CloseFigure()
    $g.FillPath($brush, $path)
    if ($pen) { $g.DrawPath($pen, $path) }
    $path.Dispose()
}

function Draw-TMark($g, $size) {
    $scale = $size / 108.0
    $blue = [System.Drawing.Color]::FromArgb(0x0B, 0x5F, 0xFF)
    $amber = [System.Drawing.Color]::FromArgb(0xFF, 0x7A, 0x1A)
    $ink = [System.Drawing.Color]::FromArgb(0x0F, 0x16, 0x20)
    $blueBrush = New-Object System.Drawing.SolidBrush($blue)
    $amberBrush = New-Object System.Drawing.SolidBrush($amber)
    $pen = New-Object System.Drawing.Pen($ink, [Math]::Max(1, $scale * 1.5))
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $r = [Math]::Max(1, 3 * $scale)
    Draw-RoundedRect $g $blueBrush $pen (28*$scale) (20*$scale) (52*$scale) (16*$scale) $r
    Draw-RoundedRect $g $amberBrush $pen (46*$scale) (20*$scale) (16*$scale) (60*$scale) $r
    $br = [Math]::Max(1.5, 3 * $scale)
    $g.FillEllipse($blueBrush, [float](31*$scale-$br), [float](28*$scale-$br), [float](2*$br), [float](2*$br))
    $g.DrawEllipse($pen, [float](31*$scale-$br), [float](28*$scale-$br), [float](2*$br), [float](2*$br))
    $g.FillEllipse($blueBrush, [float](77*$scale-$br), [float](28*$scale-$br), [float](2*$br), [float](2*$br))
    $g.DrawEllipse($pen, [float](77*$scale-$br), [float](28*$scale-$br), [float](2*$br), [float](2*$br))
    $g.FillEllipse($amberBrush, [float](54*$scale-$br), [float](77*$scale-$br), [float](2*$br), [float](2*$br))
    $g.DrawEllipse($pen, [float](54*$scale-$br), [float](77*$scale-$br), [float](2*$br), [float](2*$br))
    $blueBrush.Dispose(); $amberBrush.Dispose(); $pen.Dispose()
}

$sizes = @{ 'mdpi'=48; 'hdpi'=72; 'xhdpi'=96; 'xxhdpi'=144; 'xxxhdpi'=192 }
$bgColor = [System.Drawing.Color]::FromArgb(0x0B, 0x5F, 0xFF)

foreach ($k in $sizes.Keys) {
    $s = $sizes[$k]
    $dir = "app/src/main/res/mipmap-$k"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    foreach ($name in @('ic_launcher.png','ic_launcher_round.png')) {
        $bmp = New-Object System.Drawing.Bitmap($s, $s)
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $g.Clear($bgColor)
        Draw-TMark $g $s
        $bmp.Save("$dir/$name", [System.Drawing.Imaging.ImageFormat]::Png)
        $g.Dispose(); $bmp.Dispose()
    }
}
Write-Host "Launcher PNGs regenerated."
```

---

## Adaptive icon vectors

PNG regeneration does **not** update adaptive icons. After editing `assets/brand/tcw-mark.svg`, also sync path data into:

- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/drawable/ic_notification.xml` (white silhouette variant)
- `app/src/main/res/drawable/splash_logo.xml` (scaled mark)

Background stays solid `#0B5FFF` in `ic_launcher_background.xml`.

## Validation

Each PNG should be > 200 bytes. Quick check:

```powershell
Get-ChildItem app/src/main/res/mipmap-*/ic_launcher*.png | Select-Object FullName, Length
```
