# F2 — Cross-Module Fault Correlation

Capability #2 of the Together Scanners AI overlay: groups DTCs from every
scanned ECU module into a ranked root-cause story using Claude.

---

## Files in this draft

| File | Purpose |
|---|---|
| `ai/CorrelationReport.kt` | Data model: `CorrelationReport`, `RootCauseGroup`, `ConfidenceTier` |
| `ai/DtcCorrelator.kt` | Claude API client + `VinDecode` carrier class |
| `overlay/compose/CorrelationView.kt` | Compose section rendered inside FullScanResults |
| `README.md` | This file |

---

## Wire-up

### 1. Copy files into the working module

```
app/src/main/kotlin/com/caseforge/scanner/ai/CorrelationReport.kt
app/src/main/kotlin/com/caseforge/scanner/ai/DtcCorrelator.kt
app/src/main/kotlin/com/caseforge/scanner/overlay/compose/CorrelationView.kt
```

### 2. Add dependencies to `app/build.gradle.kts`

```kotlin
// OkHttp — HTTP client for Anthropic API
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// kotlinx.serialization — JSON parsing
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
// The Kotlin serialization plugin must also be applied in build.gradle.kts:
// plugins { id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23" }
```

### 3. Add your Anthropic API key to `local.properties`

```
ANTHROPIC_API_KEY=sk-ant-...
```

Then expose it via `BuildConfig` in `app/build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        buildConfigField("String", "ANTHROPIC_API_KEY",
            "\"${properties["ANTHROPIC_API_KEY"]}\"")
    }
}
```

### 4. Instantiate `DtcCorrelator` in your ViewModel or Service

```kotlin
// Typically a singleton / Hilt @Singleton
val correlator = DtcCorrelator(apiKey = BuildConfig.ANTHROPIC_API_KEY)
```

If your app already has a shared `OkHttpClient`, pass it in:

```kotlin
val correlator = DtcCorrelator(apiKey = BuildConfig.ANTHROPIC_API_KEY, http = appOkHttpClient)
```

### 5. Call `correlate` after `EngineDriver.fullScan()`

```kotlin
// In your ViewModel or FullScreenOverlayService coroutine scope:
val scanResult: FullScanResult = engineDriver.fullScan().getOrThrow()

// Build the map expected by DtcCorrelator
val dtcsByModule: Map<String, List<Dtc>> =
    scanResult.modules.associate { it.name to it.dtcs }

// Optional: decode the VIN first
val vinDecode: VinDecode? = scanResult.modules
    .firstNotNullOfOrNull { it.dtcs.firstOrNull()?.module }
    ?.let { /* your VIN decode logic */ null }

// Call Claude
_correlationLoading.value = true
val report = correlator.correlate(dtcsByModule, vinDecode)
_correlationReport.value = report
_correlationLoading.value = false
```

### 6. Render `CorrelationView` inside `ReportScreen`

In `overlay/compose/screens/ReportScreen.kt`, add below the existing DTC list:

```kotlin
@Composable
fun ReportScreen(
    state: EngineState,
    correlationReport: CorrelationReport?,   // add parameter
    correlationLoading: Boolean,             // add parameter
    onAction: (UiAction) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ... existing DTC list ...

        // F2: Correlation section
        Spacer(Modifier.height(8.dp))
        CorrelationView(
            report = correlationReport,
            loading = correlationLoading,
            onRunCapability = { capId -> onAction(UiAction.TapCapability(capId)) },
        )
    }
}
```

Pass `correlationReport` and `correlationLoading` from state held in
`FullScreenOverlayService` or a ViewModel down through `OverlayRoot` →
`ScreenRouter` → `ReportScreen`.

### 7. `ScreenRouter` update in `OverlayRoot.kt`

```kotlin
is ScreenKind.FullScanResults -> ReportScreen(
    state = state,
    correlationReport = correlationReport,  // new
    correlationLoading = correlationLoading, // new
    onAction = onAction,
)
```

---

## Prompt design notes

The Claude prompt in `DtcCorrelator.buildPrompt()`:

- Explicitly instructs Claude to look for **U-code CAN/network faults** that
  mask real sensor failures before attributing the fault to the sensor.
- Provides **three few-shot examples** covering the most common misattribution
  patterns:
  1. `U0100 + P0128` → thermostat is the root, CAN dropout is downstream.
  2. `P0562 + BCM B-codes` → weak battery spawns ghost BCM faults.
  3. `P0102 + P0299 + TCM U0401` → failed MAF propagates into TCM.
- Requests **structured JSON output** (no markdown fence) so parsing is
  deterministic and does not depend on prose formatting.
- Asks Claude to rate its own confidence per group in `[0.0, 1.0]` so the UI
  can render `High / Medium / Low` tiers with appropriate visual weight.
- Asks for an optional `capabilityHint` so the overlay can offer a one-tap
  shortcut to the next diagnostic step.

---

## Adding more few-shot examples

Extend the `## Few-Shot Examples` section inside `DtcCorrelator.buildPrompt()`.
Good candidates: ABS/wheel-speed sensor masking chassis codes, NOx sensor vs
SCR catalyst, TPMS communication faults masking tire pressure issues.

---

## Security note

`ANTHROPIC_API_KEY` must **never** be committed to source control. Use
`local.properties` + `BuildConfig` as shown above, or inject via a runtime
secrets manager (e.g. Android Keystore backed by a server-side key exchange).
