# Phase 3 Hardware-Max Consultation (synthesized)

OpenRouter key was unavailable in this environment; ranking follows CURSOR-FULL-BUILDOUT.md priorities and workshop impact.

## Top 18 (ranked)

1. **VIN Camera OCR** — Snap the windshield/barcode label; auto-fill vehicleVin without typing. Workshop: saves 30s every RO. Hardware: rear camera. Complexity: S. Builds on: evidence capture, overlay scraper.
2. **On-device offline LLM (Llama 3.2 1B / Phi-3-mini)** — Fallback when shop has no WAN. ~8–15 tok/s on Snapdragon 6xx with NNAPI. Complexity: L. Builds on: DtcCorrelator + repair_info patterns.
3. **Engine-bay photo triage** — Photo → cloud vision identifies obvious leaks/worn belts. Complexity: M. Builds on: EvidenceCapture + Claude vision.
4. **Microphone misfire / knock surrogate** — FFT on idle RPM for cylinder imbalance hint. Complexity: M. Builds on: VoiceMode audio path.
5. **GPS-tagged scan history** — Stamp lat/lon on SessionEntity for fleet shops. Complexity: S. Builds on: Room sessions.
6. **Recall push when NHTSA adds TSB** — Background WorkManager poll per saved VIN. Complexity: M. Builds on: RecallFlagger.
7. **QR/barcode parts lookup** — Scan box → parts catalog deep link. Complexity: S. Builds on: camera stack.
8. **AR part highlight (ARCore)** — Overlay arrow on live camera for "replace this coil". Complexity: L. Builds on: RootCauseCard hints.
9. **Scope ADC integration (PRO5+)** — Embed waveform in LiveDataScreen. Complexity: L. Hardware: 2-ch ADC models only.
10. **Multi-modal voice+vision** — "Show me where you hear the noise" single flow. Complexity: L.
11. **Fleet multi-vehicle dashboard** — Shop view of open ROs. Complexity: M. Builds on: RepairOrderEntity.
12. **Background capability prefetch** — Nightly OEM JSON delta. Complexity: S.
13. **Idle DTC pattern learning** — On-device stats across tickets. Complexity: L.
14. **Acoustic exhaust leak detector** — High-frequency hiss classifier. Complexity: M.
15. **Cellular OTA model update** — Ship quantized GGUF deltas. Complexity: L.
16. **Customer PDF auto-email** — Post-scan SMTP/share sheet. Complexity: S. Builds on: PdfReportBuilder.
17. **Night-shift fine-tune** — Impractical on tablet; defer to cloud. Complexity: XL.
18. **Full local multimodal 7B** — Too heavy for 4GB RAM tablets; speculative.

## Top 3 selected for implementation (tasks 100–102)

| # | Idea | Branch |
|---|------|--------|
| 100 | VIN Camera OCR | feat/vin-ocr |
| 101 | Offline rules diagnostic fallback | feat/offline-diag |
| 102 | GPS-tagged session history | feat/gps-sessions |
