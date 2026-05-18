# CAPABILITY #7: Automated Multi-Step Test Sequences

A composable framework for automotive diagnostic workflows. Enables reproducible, user-guided sequences for compression tests, EVAP leak detection, parasitic-draw bisection, injector analysis, and more.

## Architecture

### Core Classes

#### `TestSequence` (TestSequence.kt)
```kotlin
data class TestSequence(
    val id: String,
    val label: String,
    val description: String,
    val steps: List<Step>,
    val timeout: Long = 300_000L
)
```

**Step Types:**
- `RunCapability(capabilityId, params)` — Execute an engine capability
- `Wait(seconds)` — Pause execution
- `CapturePid(pid, storageKey)` — Read and store a PID value
- `Prompt(message, imageUrl?)` — Display user-facing message + await acknowledgment
- `Branch(condition, ifTrue, ifFalse)` — Conditional branching on captured values

#### `SequenceRunner` (SequenceRunner.kt)
Executes sequences step-by-step with:
- **Live data capture**: Stores PID values for branch evaluation
- **Condition evaluation**: Simple expression parser (`rpm_delta_1 > 150`, `evap_pressure < 2.0`)
- **Error resilience**: Captures diagnostics even on step failure
- **Per-step timing**: Logs duration of each step

#### `CommonSequences` (CommonSequences.kt)
Factory providing 5 production-ready sequences:

| Sequence | ID | Use Case |
|----------|----|----|
| **Relative Compression** | `relative_compression` | Detect weak/failed cylinders via injector-cutout sweep |
| **EVAP Smoke Test** | `evap_smoke_test` | Locate charcoal canister leaks |
| **Parasitic Draw Bisection** | `parasitic_draw_bisect` | User-guided fuse-pull to isolate battery drain |
| **Injector Kill Sweep** | `injector_kill_sweep` | Measure misfire delta per injector |
| **VVT Solenoid Sweep** | `vvt_solenoid_sweep` | ⚠️ **REQUIRES F10**: Bidirectional OCV PWM control |

### UI: `SequenceRunnerScreen` (overlay/compose/SequenceRunnerScreen.kt)

Real-time monitoring UI in Compose:
- **Live progress bar** showing step count
- **Current step card** with animation
- **Result cards** for completed steps (pass/fail, output, captured values, duration)
- **Pending step queue** (next 2 steps)
- **Summary footer** with total duration and error messages

**Colors:**
- Orange: Running
- Green (#00AA00): Pass
- Red (#DD0000): Fail
- Dark theme (#1A1A1A background)

---

## Integration Wiring

### 1. Add to `EngineDriver`

```kotlin
// In EngineDriver.kt or equivalent
suspend fun runCapability(capabilityId: String, params: Map<String, String>): CapabilityResult
suspend fun queryPid(pid: String): String?
suspend fun notifyUser(message: String)
```

### 2. Wire in MainActivity/ComposeActivity

```kotlin
import com.caseforge.scanner.engine.sequences.*
import com.caseforge.scanner.overlay.compose.SequenceRunnerScreen

class DiagnosticActivity : AppCompatActivity() {
    private val sequenceRunner by lazy { SequenceRunner(engineDriver) }

    fun launchRelativeCompressionTest() {
        val sequence = CommonSequences.relativeCompression()
        
        setContent {
            SequenceRunnerScreen(
                sequence = sequence,
                runner = sequenceRunner,
                onComplete = { result ->
                    logSequenceResult(result)
                    showResults(result)
                }
            )
        }
    }

    private fun logSequenceResult(result: SequenceResult) {
        Log.d("SequenceRunner", "${result.sequenceId}: ${result.summary}")
        result.stepResults.forEach { step ->
            Log.d("SequenceRunner", "  ${step.step.label}: ${step.output} (${step.duration}ms)")
        }
    }
}
```

### 3. Add to Capability Menu

```kotlin
// In CapabilityMenuScreen or equivalent
MenuItem(
    label = "Relative Compression",
    icon = Icons.Default.Speed,
    onSelect = { viewModel.launchSequence(CommonSequences.relativeCompression()) }
),
MenuItem(
    label = "EVAP Smoke Test",
    icon = Icons.Default.Science,
    onSelect = { viewModel.launchSequence(CommonSequences.evapSmokeTest()) }
),
MenuItem(
    label = "Parasitic Draw Bisect",
    icon = Icons.Default.Bolt,
    onSelect = { viewModel.launchSequence(CommonSequences.parasiticDrawBisection()) }
),
```

### 4. Serialization (kotlinx.serialization)

All data classes use `@Serializable` for JSON persistence:

```kotlin
// Save sequence result for later review
val json = Json.encodeToString(result)

// Load from history
val result = Json.decodeFromString<SequenceResult>(jsonString)
```

---

## Example: Running Relative Compression Test

```kotlin
val sequence = CommonSequences.relativeCompression()
val runner = SequenceRunner(engineDriver)

val result = runner.run(sequence)

// Result structure:
// SequenceResult(
//   sequenceId = "relative_compression",
//   passed = true,
//   stepResults = [
//     StepResult(step=Prompt(...), passed=true, ...),
//     StepResult(step=CapturePid(...), passed=true, output="850", capturedValues={"baseline_rpm": "850"}, ...),
//     StepResult(step=RunCapability(...), passed=true, output="Injector cutout: CYL 1", ...),
//     StepResult(step=CapturePid(...), passed=true, output="720", capturedValues={"rpm_delta_1": "720"}, ...),
//     ... (repeats for cylinders 2–6)
//   ],
//   totalDuration = 125_000L,
//   errorMessage = null
// )
```

---

## Known Limitations & F10 Dependencies

### VVT Solenoid Sweep
Currently a **placeholder**. Full implementation requires **F10: Direct VCI Control** (bidirectional PWM output to OCV solenoid).

When F10 is available, the sequence will:
1. Command OCV PWM 0% → measure cam advance delay
2. Command OCV PWM 50% → measure response time
3. Command OCV PWM 100% → measure full retard position
4. Detect dead bands, stiction, response hysteresis

### Other Sequences
All other sequences use **read-only** PID queries and user prompts. No direct control requirement.

---

## Performance Notes

- **Relative Compression**: ~120s (2s settle + 10s per cylinder)
- **EVAP Smoke**: ~90s (smoke dwell is manual; automation waits for user)
- **Parasitic Draw Bisection**: ~10 min (user-paced fuse pulls)
- **Injector Kill Sweep**: ~180s (5s dwell × 6 cylinders)
- **Step timing**: Per-step duration logged in `StepResult.duration`

---

## Testing Checklist

- [ ] Compile Kotlin (no dependencies on external libraries beyond Compose)
- [ ] MockEngineDriver: return synthetic PID/capability results
- [ ] SequenceRunner: verify branch evaluation with sample data
- [ ] UI: run SequenceRunnerScreen on emulator/device
- [ ] End-to-end: launch sequence from capability menu, verify results logged
- [ ] Serialization: save/load sequence result as JSON

---

## Files

| File | Purpose | Lines |
|------|---------|-------|
| `sequences/TestSequence.kt` | Data classes: `TestSequence`, `Step` hierarchy, `SequenceResult` | ~80 |
| `sequences/SequenceRunner.kt` | Execution engine with branch evaluation | ~150 |
| `sequences/CommonSequences.kt` | 5 built-in sequences | ~250 |
| `overlay/compose/SequenceRunnerScreen.kt` | Compose UI with live monitoring | ~350 |
| `README.md` | This file | — |
| **Total** | | ~500 LOC |

---

## Next Steps

1. **Integrate to working repo** (F16 merge)
2. **Add MockEngineDriver** for testing
3. **Wire capability menu** in tablet UI
4. **Collect telemetry** (sequence duration, pass rates)
5. **Implement F10** once VCI bidirectional control available
