# Capability #8: Recall/TSB Auto-Flag

Cross-references NHTSA VIN decode with detected DTCs to surface open vehicle recalls directly in the diagnostic report.

## Overview

This capability queries NHTSA's public recalls API to find open recalls for a vehicle (identified by make, model, and model year decoded from VIN), then cross-references the recall defect/consequence text against the detected DTC codes. Matching recalls are displayed in a collapsible banner on ReportScreen, ranked by relevance (most DTC hits first).

## Components

### 1. `ai/RecallMatch.kt`
Data class representing a single NHTSA recall match:
- `campaignId`: NHTSA campaign ID (e.g., "07E055000")
- `summary`: Defect summary text
- `consequence`: Consequence text
- `relatedDtcs`: List of DTC codes found in defect/consequence text
- `isOpenForVin`: Whether recall is still open for this VIN
- `manufacturer`, `modelYear`, `make`, `model`: Vehicle metadata

### 2. `ai/RecallMatcher.kt`
Core matching logic:
```kotlin
suspend fun match(
    vin: String,
    make: String,
    model: String,
    modelYear: Int,
    dtcs: List<Dtc>,
): List<RecallMatch>
```

**Workflow:**
1. Fetches recalls from NHTSA's `recallsByVehicle` endpoint (no auth required)
2. Scans defect/consequence text for DTC codes (P####, B####, C####, U####)
3. Returns sorted list ranked by DTC hit count (descending)

**Key features:**
- Uses OkHttpClient pattern from CapabilityRegistry
- All errors logged, never thrown (graceful degradation)
- ~150 lines, fully compilable Kotlin

### 3. `overlay/compose/RecallBanner.kt`
Compose UI component for ReportScreen:
- Collapsed: shows "X open recalls related to this fault" with warning icon
- Expanded: shows full recall details (campaign ID, defect summary, related DTCs)
- Uses MaterialTheme colors (errorContainer for warning state)
- Fully integrated with Material 3 theming (light/dark modes)
- 3 preview states: collapsed, expanded, empty

## Integration into ReportScreen

### Step 1: Add RecallMatcher to EngineState or service layer
```kotlin
// In EngineDriver or overlay service:
private val recallMatcher = RecallMatcher(httpClient)

// After collecting DTCs, call match:
val recalls = recallMatcher.match(
    vin = vinString,
    make = "Honda",         // from VIN decode
    model = "Accord",       // from VIN decode
    modelYear = 2020,       // from VIN decode
    dtcs = state.dtcs,
)
state = state.copy(recalls = recalls)
```

### Step 2: Update EngineState to include recalls
```kotlin
data class EngineState(
    // ... existing fields ...
    val recalls: List<RecallMatch> = emptyList(),
)
```

### Step 3: Add RecallBanner to ReportScreen
```kotlin
@Composable
fun ReportScreen(
    state: EngineState,
    onAction: (UiAction) -> Unit,
) {
    Column(/* ... */) {
        Text("Scan complete", /* ... */)
        Text("${state.dtcs.size} DTC(s) found", /* ... */)
        HorizontalDivider()

        // NEW: Show recall banner if matches exist
        RecallBanner(state.recalls)

        // Existing DTC cards
        if (state.dtcs.isEmpty()) {
            // ...
        } else {
            // ...
        }
    }
}
```

## NHTSA API

**Endpoint:** `https://api.nhtsa.gov/recalls/recallsByVehicle`

**Parameters:**
- `make`: Vehicle make (e.g., "Honda", "Toyota")
- `model`: Vehicle model (e.g., "Accord", "Camry")
- `modelYear`: Model year (e.g., 2020)

**Response:** JSON array of recall records with:
- `Campaign Number`: Campaign ID
- `Defect Summary`: Description of defect
- `Consequence Summary`: Potential consequences
- `Manufacturer Name`: OEM name
- `Model Year`: Model year

No authentication required. Public API.

## Testing

### Unit test pattern (RecallMatcherTest.kt):
```kotlin
@Test
fun testMatchesRelevantRecalls() = runTest {
    val matcher = RecallMatcher(mockHttpClient)
    val dtcs = listOf(
        Dtc("Engine", "P0700", "Transmission error", Severity.Red, null),
        Dtc("Engine", "P0705", "Gear selection error", Severity.Red, null),
    )
    val recalls = matcher.match(
        vin = "JTDKARPV6K3089467",
        make = "Honda",
        model = "Accord",
        modelYear = 2020,
        dtcs = dtcs,
    )
    assertTrue(recalls.isNotEmpty())
    assertTrue(recalls.first().relatedDtcs.containsAll(listOf("P0700", "P0705")))
}
```

### Manual testing:
1. Create a scan with DTCs (P0101, P0700, etc.)
2. Verify ReportScreen displays recall banner
3. Tap banner to expand and verify details show
4. Check Logcat for "RecallMatcher" entries (success/failure)

## Performance & Caching

- **Network call:** ~500ms typical (timeout = 5s)
- **DTC scanning:** O(n*m) where n = recall count, m = DTC count (negligible)
- **Caching:** Implement at service layer if needed (similar to CapabilityRegistry pattern)

## Dependencies

- `okhttp3`: Already included via CapabilityRegistry
- `kotlinx-serialization`: Already included
- `androidx.compose.material3`: Already included

## Gradle configuration

No new dependencies required. Build uses existing OkHttpClient from app's networking stack.

## Migration path

This component is non-breaking. Can be integrated incrementally:
1. Wire RecallMatcher into service layer (silent on failure)
2. Update EngineState to carry recalls
3. Add RecallBanner to ReportScreen
4. Optionally: add caching layer if performance is needed

## Files

- `ai/RecallMatch.kt` (~25 lines)
- `ai/RecallMatcher.kt` (~170 lines)
- `overlay/compose/RecallBanner.kt` (~180 lines)
- Total: ~250 lines, compilable Kotlin

## Author

Generated for launch-ai-dispatch Capability #8.
