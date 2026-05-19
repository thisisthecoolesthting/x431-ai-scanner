# UI polish brief (final pass — Opus)

Use this **after** transfer + setup flows are stable. Scope: visual polish only — no architecture changes.

## Product

Together Car Works — shop-grade Android diagnostic tablet app (Compose Material 3).

## Current home layout

- `TcwCommercialHero` — gradient hero, VCI/VIN chips
- `TcwSetupStrip` — Connect / Export / Settings / Updates
- `DataTransferCard` — mode chips (Share | Your server | LAN PC), step dots, working bar
- Action tile grid (Scan, Live Data, Service, …)
- `TcwBusyOverlay` during `engineBusy`

## Polish goals

1. **Commercial shop aesthetic** — confident typography, restrained color (no playful gradients beyond hero), clear primary CTA per screen.
2. **Motion** — every async action shows progress (pulse, linear bar, or overlay). No static waits >300ms without feedback.
3. **Loading** — first launch / heavy export: full-screen branded splash optional; keep overlay copy short ("Exporting vehicle data…").
4. **Transfer card** — simplify copy for non-technical techs; iconography for Share vs Server vs LAN.
5. **Settings** — group export + advanced LAN under one "Data & cloud" section with helper text.
6. **Accessibility** — 48dp touch targets, contrast on error containers.

## Do not

- Add paid third-party upload APIs.
- Re-enable "find my PC on LAN" as the default path.
- Change VCI/protocol code in a UI-only pass.

## Files to touch

- `ui/components/TcwMotionUi.kt`
- `ui/main/MainScreen.kt`
- `ui/transfer/OneTapSendCard.kt`
- `ui/settings/SettingsScreen.kt`
- `res/values/strings.xml`
- Theme tokens if centralized

## Acceptance

- Operator can explain export in one sentence: "Tap Export & share, pick Drive or email."
- No screen feels "dev tool" gray — reads like a shipped shop product.
