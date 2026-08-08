You are reviewing Together Scanners AI after Phase 1 + Phase 2 have shipped. Phase 1 = full AI overlay over Launch X431 with predictive next-test, cross-module DTC correlation, evidence-capture + repair-story PDF, hands-free voice mode, multi-step diagnostic sequences (compression / EVAP / injector kill / VVT / parasitic draw), NHTSA recall + TSB flagging, 240+ OEM capability entries across Ford/GM/Stellantis/Toyota/Honda/VAG/BMW/Mercedes. Phase 2 = Direct VCI Bluetooth standalone mode (bypasses X431 entirely) gated behind a settings toggle; Mode 01/03/09 working.

Hardware available on the X431 PRO / PROS / V+ tablet: Android 10-12, Snapdragon 6xx-class SoC, 4-8 GB RAM, 64-128 GB storage, 10-inch IPS display, rear camera, microphone, Bluetooth 5, Wi-Fi, optional cellular, GPS, on some models a 2-channel oscilloscope ADC.

Question for you: what's a ranked list of NEW capabilities that maximize this hardware that we have NOT already built? Focus on:
- On-device local LLM fallback for offline diagnosis (Phi-3-mini / Gemma 2B / Llama 3.2 1B — which fits best, what tok/sec on this SoC class)
- Camera as a diagnostic input: VIN OCR (no manual entry), QR/barcode scan for parts, photograph engine bay → AI identifies leaking component
- AR overlay on engine bay (ARCore + tablet camera): highlight the part the AI is talking about
- Microphone as a sensor: listen to engine for misfire detection, exhaust leak detection, knock sensor surrogate
- GPS + cellular: fleet mode (multi-vehicle workflow), location-tagged scan history, recall push-notifications when a customer's VIN gets a new TSB
- Oscilloscope ADC (on PRO5+ models): replace the dedicated scope app entirely; tied into our scan UI
- Multi-modal local AI: vision + voice + text in one flow ("show me where you hear the noise")
- Background daemons: pre-fetch vehicle DB updates, idle-time DTC pattern learning, overnight model fine-tuning

For each idea: name, what-it-does in one paragraph, why-it-matters in the workshop, what hardware it composes with, build complexity (S/M/L), and what existing Together piece it builds on top of. Aim 12-18 ideas ranked best to most speculative. No fluff.
