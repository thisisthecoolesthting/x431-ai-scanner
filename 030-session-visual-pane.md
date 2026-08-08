# 030 — Session visual pane

The session chat hosts a **visual strip** above the transcript ([SessionVisualStrip]), composed by [SessionVisualComposer].

## Priority (top → bottom)

1. **Photo insight card** — Claude vision bullets from `photoDiagnosticJson` (not blank placeholders)
2. **DIA / OBD live** — link status, DTC count; live PIDs use collapsible **[SessionGaugeTile]** boxes (RPM arc, coolant/voltage numeric + mini bar)
3. **Component hint** — parsed from last assistant message (battery, fuse, SKREEM, etc.)
4. **Marque wedge** — WMI + model-year card from [MarqueWedgeConfig]
5. **Photo thumbnails** — fallback when no insights yet; secondary row when OBD is primary

## Photo insight card

`StripItem.PhotoInsightCard` shows:

- Up to 5 bullets from `perPhoto` or `findings`
- Up to 3 `suggestedNextSteps`
- Footer: *Visual estimate — verify on vehicle.*

Transcript mirrors the same content via [VisualAttachment] (`kind=visual_card`, `bullets=[…]`).

## Ongoing updates

[SessionLiveObdPoller] refreshes `BackgroundObdSnapshot` every ~3s while Tier 0 / native OBD is enabled; pane recomposes without clearing photo insights. RPM uses PID `0C`; the last ~60 poll samples populate `rpmHistory` for sparkline smoothing and tach needle transitions.

### Collapsible gauge tiles

**[SessionGaugePane]** lays out **[SessionGaugeTile]** boxes above the strip cards when gauges are *called*:

| Invoke | Tiles shown |
|--------|-------------|
| OBD connected | RPM + coolant + voltage (dismiss/expand per session via [SessionGaugeUiState]) |
| Agent text | Keyword match (`rpm`, `coolant`, `voltage`, `live data`, …) via [SessionVisualComposer.resolveCalledGauges] |
| Empty chat | Large center **[SessionRpmTachLarge]** in the transcript (not the tile row) |

Each tile: **close** (hide for session), **collapse/expand** (compact value vs arc/bar body). [SessionVisualComposer] no longer embeds live PIDs in a fixed **Live data** strip row — gauges replace that layout when OBD is live.

## Logging

Each compose logs `visual_pane` to `session_events.jsonl` with `primary` / `secondary` strip item class names.

## AI cost debug (Settings)

[SessionTokenAccounting] records input/output tokens, vision calls, and chat turns per session; Settings shows **Estimated AI cost (last session)** using Sonnet 4.6 list rates ($3/$15 per MTok, advisory only). Totals persist to [SettingsRepo] and `CustomerSessionEntity.lastAiUsageJson` on session end.
