# LOCAL-LLM.md — On-Device Fallback for Together Scanners AI

**Status:** Research only — no code changes. Primes a future implementation ticket.
**App:** Together Scanners AI (formerly OEM AI)
**Relevant source:** `app/src/main/kotlin/com/Together Car Works/scanner/ai/ClaudeClient.kt`

---

## 1. Target Hardware Profile

The OEM OEM diagnostic tablet PRO / V+ tablet is the sole target device. Constraints are tight and non-negotiable for the foreseeable future.

| Dimension | Typical spec |
|---|---|
| SoC | Octa-core ARM Cortex-A53 or A55 (ARMv8-A), ~1.8–2.0 GHz |
| RAM | 2–4 GB LPDDR4 (effective free budget: ~1.2–1.8 GB after OS + OEM diagnostic tablet app) |
| GPU | Mali-G51 or equivalent entry-level; OpenCL support present but not reliable across firmware versions |
| Storage | 64 GB eMMC; ~30–40 GB typically free after system + OEM diagnostic tablet software |
| Android version | 10–12 |
| CPU features | ARMv8-A `sdot`/`smmla` absent on A53/A55 — the fast-path kernels in llama.cpp and KleidiAI require ARMv8.2-A or later |

**Key implication:** This hardware sits in the slowest viable tier for on-device LLM. ARMv8-A without `sdot`/`smmla` limits decode throughput to roughly 3–8 tok/s for a 1–2 GB Q4 model. That is tolerable for a fallback where the user expects degraded performance, but models above ~2 GB RAM peak are a hard no.

The GPU cannot be relied on as a performance lever. Mali-G51 OpenCL performance for LLM matrix operations is inconsistent across firmware versions, and MLC LLM benchmarks show Mali-G78 (a far more powerful GPU) still exhibits poor prefill performance. Budget the GPU as absent.

---

## 2. Model Survey

All sizes are Q4_K_M GGUF (the standard balanced quantization). RAM peak includes the KV cache at a 512-token context; 2048-token context adds roughly 200–400 MB depending on model architecture. Tok/s estimates are decode-phase throughput on a 4-core ARMv8-A CPU running llama.cpp with no GPU; figures come from community benchmarks and published ARM/ExecuTorch measurements.

### 2.1 Candidates

| Model | GGUF size | RAM peak (512-ctx) | Tok/s (A53/A55, 4T) | Notes |
|---|---|---|---|---|
| **SmolLM2-360M Q4_K_M** | ~240 MB | ~400 MB | 25–40 | Minimal reasoning. Suitable only for intent routing or very short Q&A. |
| **Qwen 2.5-1.5B Q4_K_M** | ~1.0 GB | ~1.2 GB | 8–15 | Strong instruction following for size; multilingual. Best throughput in the viable range. |
| **Llama 3.2-1B Q4_K_M** | ~0.8 GB | ~1.1 GB | 10–18 | Solid for plain Q&A; no native tool-call schema. Lighter than Qwen 2.5-1.5B. |
| **Gemma 2B Q4_K_M** | ~1.5 GB | ~1.8 GB | 5–10 | Marginal fit on 2 GB RAM devices; comfortable on 4 GB. Better reasoning than 1B models. |
| **Llama 3.2-3B Q4_K_M** | ~2.0 GB | ~2.3 GB | 3–7 | Confirmed 2.1 GB RAM / 11.2 tok/s on Pixel 8 Pro; exceeds safe budget on 2 GB devices. |
| **Phi-3-mini-4k Q4_K_M** | ~2.2 GB | ~2.6 GB | 2–5 | Excellent reasoning per byte, but RAM peak exceeds the 2 GB budget — 4 GB device only. |
| **Qwen 2.5-0.5B Q4_K_M** | ~400 MB | ~600 MB | 5–10 | Published result: 5.1 tok/s on Cortex-A53. Near-floor quality for diagnostic reasoning. |

### 2.2 Recommendation

**Primary candidate: Qwen 2.5-1.5B Q4_K_M** (~1.0 GB on disk, ~1.2 GB RAM peak).
- Fits all devices (2 GB and 4 GB).
- Best instruction-following quality-to-size ratio in this tier as of 2025–2026.
- Multilingual; handles structured prompts well.
- Achieves 8–15 tok/s on the target CPU class — marginal but usable for fallback.

**Contingency on 4 GB devices: Gemma 2B Q4_K_M** as a quality step-up (~1.8 GB RAM peak). Detect available RAM at runtime and select accordingly.

**Do not ship Phi-3-mini or Llama 3.2-3B as the default.** Their RAM peaks exceed 2 GB free headroom and will cause OOM kills on the lower-spec tablets.

### 2.3 Quality on Diagnostic Reasoning

None of these models approach Claude 3.5/3.7 quality on multi-step DTC diagnosis. Expect:
- Correct DTC code definitions: reliable across all candidates.
- Likely cause ranking: plausible but narrower than cloud Claude; no access to RAG or repair info lookup.
- Step-by-step test procedures: basic sequences achievable; nuanced wiring diagrams and ECU-specific guidance will be incomplete.
- Tool use / function calling: not supported natively — see Section 4.

The fallback mode should be framed to the user as "basic offline Q&A" rather than "full agent."

---

## 3. Inference Library Options on Android

### 3.1 llama.cpp (Android NDK / JNI)

**Recommended for this project.**

- Build path: cross-compile via CMake + Android NDK into `arm64-v8a` `.so`; wire via JNI from Kotlin. The official `examples/llama.android` directory demonstrates the full setup.
- CPU-only, no GPU dependency — robust on all OEM diagnostic tablet firmware variants.
- GGUF format is the de facto standard; every model in the survey ships as GGUF on Hugging Face.
- Active community, frequent updates, deterministic behavior.
- JNI surface is minimal: `llamaInit()`, `llamaTokenize()`, `llamaEval()`, `llamaFree()`.
- Cons: no GPU acceleration (relevant only for higher-end devices); NDK build adds ~3–5 MB to APK for the `.so`; OpenMP is not supported in the Android NDK build, limiting some parallelism paths.
- ARMv8.7-A march flag should be set in CMake (`-march=armv8.7a`) but the binary will still run on A53/A55 via fallback scalar kernels.

### 3.2 MLC LLM (TVM-based)

- Pros: genuine GPU acceleration via OpenCL/Vulkan if the device supports it; ahead-of-time kernel compilation maximizes throughput on supported GPUs (Adreno > Mali for LLM workloads).
- Cons for this project: Mali-G51 (OEM diagnostic tablet GPU class) shows poor LLM prefill performance even on more powerful Mali-G78; kernel compilation requires a model-specific compile step per device target, adding build complexity; physical device required (no emulator); integration overhead via TVM4J is higher than llama.cpp JNI.
- Verdict: **Not recommended** unless a future OEM diagnostic tablet device ships Adreno GPU. Revisit when hardware profile changes.

### 3.3 ONNX Runtime Mobile (Microsoft GenAI)

- Pros: cross-platform, backed by Microsoft; NNAPI execution provider can offload ops on newer Android devices; pre-quantized ONNX models exist on Hugging Face for Phi-3-mini and Gemma.
- Cons: GGUF is not native — requires format conversion (ONNX export); GenAI wrapper layer adds memory overhead; NNAPI delegation is per-op and falls back to CPU for unsupported ops, making throughput unpredictable; integration overhead for Android is higher than llama.cpp; LLM GenAI path is still maturing.
- Verdict: Viable if the team already standardizes on ONNX export for other reasons. No advantage over llama.cpp for this use case.

### 3.4 Google AI Edge / MediaPipe LLM Inference API

- Note: The MediaPipe Android and iOS LLM Inference APIs were deprecated in 2025; Google recommends migration to LiteRT-LM.
- LiteRT-LM is optimized for high-end devices (Pixel 8+, Samsung S23+); does not reliably support low-end ARM tablets.
- Supported model zoo is narrow (Gemma-3n variants, Phi-2, Falcon-RW-1B).
- Verdict: **Not recommended.** Deprecated path, wrong device tier, narrow model support.

---

## 4. Integration Plan

### 4.1 Drop-in Interface Against ClaudeClient.kt

`ClaudeClient.kt` is on the "never touch" list (SHARED-RULES.md). The correct approach is to introduce a parallel interface and route behind it — callers never change.

Proposed structure:

```
ai/
  LlmClient.kt          <- new interface (suspend fun complete(prompt: String): String)
  ClaudeClient.kt       <- existing; implements LlmClient (no changes)
  LocalLlmClient.kt     <- new; implements LlmClient; wraps llama.cpp JNI
  LlmClientFactory.kt   <- new; returns the correct impl based on mode + connectivity
```

`LlmClientFactory` selects the implementation:
1. If `localLlmMode == FORCED` (user toggle ON) → `LocalLlmClient`
2. If `localLlmMode == AUTO` and network is unavailable → `LocalLlmClient`
3. Otherwise → `ClaudeClient`

`AgentRunner` (also never-touch) currently holds a `ClaudeClient` reference. The migration ticket must substitute it with `LlmClient` — that single line change is the only required edit to protected files and should be pre-approved by the orchestrator.

### 4.2 Fallback Trigger: Hybrid Mode (Recommended)

Two controls in `SettingsRepo`:

| Key | Type | Behavior |
|---|---|---|
| `local_llm_mode` | enum: `OFF / AUTO / FORCED` | `OFF` = cloud only. `AUTO` = switch on connectivity loss. `FORCED` = always local. |
| `local_llm_model_path` | String | Absolute path to the GGUF file, set after successful download. |

**Recommended default: `AUTO`.**
- Requires no user decision for the common case (no internet in a shop bay).
- User can override to `FORCED` for privacy reasons or to test the model.
- `OFF` is available for users who never want local inference.

Connectivity check: use `ConnectivityManager.getNetworkCapabilities()` with `NET_CAPABILITY_INTERNET` + `NET_CAPABILITY_VALIDATED`. Check before each `AgentRunner` session start; mid-session switch is out of scope for v1.

### 4.3 Tool-Use Degradation

Cloud Claude supports function calling via the Anthropic tool-use API. All local models in the survey lack native function-calling schemas. The agent loop in `AgentRunner` expects structured tool calls; it will fail if the model returns plain text.

**Proposed: "text-only fallback mode"**

When `LocalLlmClient` is active:
- Disable the tool-use loop entirely. Do not pass tool schemas to the model.
- Convert the system prompt to a plain instruction: "You are an automotive diagnostic assistant. Answer questions about DTCs, likely causes, and test procedures in plain text. You cannot read the screen or interact with the OEM diagnostic tablet app."
- Surface a persistent banner in the overlay: "Offline mode — agent cannot control the OEM diagnostic tablet app. Ask questions only."
- `read_screen`, `tap`, `scroll`, and all actuation tools return a graceful no-op with an explanatory string rather than throwing.
- `repair_info_lookup` can still function if it is redesigned to call `LocalLlmClient` rather than making a network call — defer to the implementation ticket.

This degrades gracefully: the technician gets a useful Q&A assistant instead of a broken agent.

---

## 5. Storage and UX

### 5.1 Model Location on Disk

```
/data/data/com.Together Car Works.scanner/files/models/
    qwen2.5-1.5b-instruct-q4_k_m.gguf     (~1.0 GB)
    [optional] gemma-2b-instruct-q4_k_m.gguf  (~1.5 GB)
```

Use `Context.getFilesDir()` + `"models/"` subdirectory. This path is:
- Private to the app (no READ_EXTERNAL_STORAGE permission required).
- Excluded from automatic backup by default (add `android:allowBackup="false"` for the models dir or use `backup_rules.xml` to exclude it — model files should not be synced to Google Drive).
- Not visible to other apps without root.
- Persists across app updates; survives uninstall only if using external storage (not desired here).

Do not use external storage (`getExternalFilesDir()`). The OEM diagnostic tablet tablet's external storage availability varies by firmware.

### 5.2 Download UX

Trigger: Settings screen → "Offline AI Model" section → "Download model" button.

Flow:
1. Show estimated size (~1.0 GB for Qwen 2.5-1.5B) and a "Wi-Fi recommended" warning before starting.
2. Start download via `WorkManager` + `DownloadManager` (survives app backgrounding).
3. Display progress in a persistent notification and in the Settings screen.
4. On completion, verify SHA-256 checksum against a hardcoded expected value baked into the APK. If mismatch: delete the file, show an error, allow retry.
5. Store the verified file path in `SettingsRepo.local_llm_model_path`.
6. Show a "Model ready — offline mode available" confirmation.

The download URL should point to a stable release artifact (Hugging Face direct link or a CDN mirror controlled by the team) — not a mutable `main` branch pointer.

### 5.3 Disk Usage Warning

Display a warning dialog before download begins if free disk space is below 2.5 GB (1.0 GB model + 1.0 GB extraction headroom + 500 MB buffer). Use `StatFs` on `getFilesDir()`.

Disk usage summary to show in Settings after download:
- Model file: ~1.0 GB (Qwen 2.5-1.5B) or ~1.5 GB (Gemma 2B)
- Runtime working memory: ~1.2–1.8 GB RAM (not disk)
- No secondary extraction needed for GGUF — llama.cpp memory-maps directly.

### 5.4 User Opt-In Flow

Local inference is **opt-in by default** (`local_llm_mode = OFF` until user changes it). The onboarding wizard (ticket C2) or the Settings screen presents the option.

Suggested Settings entry point:

```
Offline AI Model
  [ ] Enable offline mode (no internet required)
      Uses a local AI model on this device.
      Quality is lower than cloud Claude.
  [Download model — 1.0 GB]  /  [Delete model]
  Mode: Auto / Always offline / Off
```

No model file → mode selector is disabled → download button is active.
Model downloaded → mode selector is enabled.

---

## 6. Open Questions for the Implementation Ticket

These require empirical measurement on physical OEM diagnostic tablet hardware and are out of scope for this research pass.

**6.1 Battery impact during sustained inference**
llama.cpp running a 1.5B Q4 model at 8–15 tok/s will saturate 2–4 CPU cores continuously. A typical 30-minute diagnostic session generating ~2,000 tokens could draw 500–800 mA extra, translating to 15–25% additional battery drain versus cloud mode on a 5000 mAh battery. This is an estimate; measure with `BatteryManager` telemetry on real hardware.

**6.2 Thermal throttling on long sessions**
ARM Cortex-A53/A55 cores in a tablet form factor with no active cooling will hit thermal limits under sustained 100% CPU load. Expect the SoC to throttle clock speed after 5–15 minutes depending on ambient temperature and case design. Throttling reduces tok/s — a session that starts at 10 tok/s may drop to 4–6 tok/s after 10 minutes. The implementation ticket should include a thermal monitor hook (`getThermalStatus()` on Android 10+) and a UI warning if throttling is detected.

**6.3 Bundle vs. download-on-first-use**
Bundling the model in the APK adds ~1.0 GB to the binary — Play Store and enterprise MDM (Jamf/AirWatch) have APK size limits, and download times for the APK itself become problematic over cellular. **Recommendation: download on first use** from a CDN. This keeps the base APK small, allows model updates independently of app releases, and lets users who never need offline mode avoid the storage cost. The APK bundles only the llama.cpp `.so` runtime (~3–5 MB).

**6.4 Quality regression on diagnostic prompts vs. cloud Claude**
Qualitative testing on a suite of real DTC prompts (P0300, U0100, B1234 class codes across GM/Ford/Stellantis targets) is needed before shipping. Expect meaningful regression on multi-system correlation ("P0420 + P0171 + rough idle — what is the likely root cause on a 2019 5.3L?"). Recommend a red-team pass: run 20 representative prompts through both cloud Claude and the local model; document gaps. This informs the UX copy and sets expectations correctly.

---

## Top Recommendations

1. **Use Qwen 2.5-1.5B Q4_K_M as the sole shipped model.** It fits all device RAM tiers (2 GB and 4 GB), delivers the best instruction-following quality in the <1 GB disk footprint class, and achieves acceptable tok/s on Cortex-A53/A55. Conditionally offer Gemma 2B on 4 GB devices as a runtime upgrade.
2. **Integrate via llama.cpp Android NDK with a thin `LlmClient` interface.** This keeps `ClaudeClient.kt` untouched, runs CPU-only (no Mali GPU dependency risk), and has the strongest community support for GGUF models.
3. **Default to AUTO hybrid mode** (cloud when reachable, local on network failure). Give the user an explicit toggle in Settings but do not require a decision at first OEM — automatic fallback is the highest-value behavior for shop environments with unreliable connectivity.
4. **Implement text-only fallback mode in the agent loop.** Tool-use is unavailable locally; disable the tool schema, replace the system prompt with a plain Q&A framing, and show a persistent "offline mode" banner. This is a clean degradation path rather than a broken one.
5. **Treat battery and thermal behavior as blockers before GA.** Measure on a physical OEM diagnostic tablet PRO/V+ tablet under a 20-minute inference session. If thermal throttling degrades tok/s below ~3 tok/s, add a cooldown gate (pause inference after 10 minutes, resume after 2 minutes) — the UX cost of a pause is lower than the UX cost of unreadably slow generation.

---

*Sources consulted: Hugging Face model cards (Phi-3-mini-4k-instruct-gguf, Qwen/Qwen2.5-1.5B-Instruct-GGUF, SmolLM2-GGUF variants), llama.cpp official Android docs (`docs/android.md`), MLC LLM Android SDK docs, Google AI Edge MediaPipe LLM Inference deprecation notice, ARM Newsroom / PyTorch blog on KleidiAI + ExecuTorch Llama 3.2 benchmarks, arxiv:2410.03613 (LLM performance study on COTS mobile devices), DEV Community on GGUF Android real-world tradeoffs, MVP Factory blog on KMP + llama.cpp memory-mapped loading and thermal budget patterns.*
