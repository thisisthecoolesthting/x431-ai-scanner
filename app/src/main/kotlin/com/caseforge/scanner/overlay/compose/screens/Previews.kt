package com.caseforge.scanner.overlay.compose.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.caseforge.scanner.engine.ScrapedDtc
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.ScreenKind
import com.caseforge.scanner.ui.theme.TogetherCarWorksTheme

/**
 * Shared preview fixtures and theme wrappers for @Preview annotations.
 *
 * Usage in a screen's @Preview composable:
 *   PreviewContainer {
 *       ModuleListScreen(state = ..., onAction = {})
 *   }
 */

@Composable
fun PreviewContainer(content: @Composable () -> Unit) {
    TogetherCarWorksTheme(mode = "dark") {
        Surface(color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}

// Sample EngineState fixtures for common scenarios

val SAMPLE_ENGINE_NO_VEHICLE = EngineState(
    screen = ScreenKind.NoEngine,
    vehicleVin = null,
    vehicleSummary = null,
    currentMenuPath = emptyList(),
    busy = false,
)

val SAMPLE_ENGINE_HOME_MENU = EngineState(
    screen = ScreenKind.HomeMenu,
    vehicleVin = "5FNRL6H73LB123456",
    vehicleSummary = "2020 Honda Odyssey",
    currentMenuPath = listOf("Main Menu"),
    busy = false,
)

val SAMPLE_ENGINE_SCANNING = EngineState(
    screen = ScreenKind.FullScanProgress,
    vehicleVin = "5FNRL6H73LB123456",
    vehicleSummary = "2020 Honda Odyssey",
    currentMenuPath = listOf("Engine", "DTC", "Reading codes"),
    busy = true,
)

val SAMPLE_ENGINE_CLEAN_SCAN = EngineState(
    screen = ScreenKind.FullScanResults,
    vehicleVin = "5FNRL6H73LB123456",
    vehicleSummary = "2020 Honda Odyssey",
    dtcs = emptyList(),
    busy = false,
)

val SAMPLE_ENGINE_WITH_DTCS = EngineState(
    screen = ScreenKind.FullScanResults,
    vehicleVin = "5FNRL6H73LB123456",
    vehicleSummary = "2020 Honda Odyssey",
    dtcs = listOf(
        ScrapedDtc("P0101", description = "Mass or Volume Air Flow Sensor A Range/Performance", module = "Engine"),
        ScrapedDtc("P0405", description = "EGR Sensor A Circuit Low", module = "Engine"),
        ScrapedDtc("B1234", description = "Driver Seat Track Position Memory not stored", module = "Seat Memory"),
    ),
    busy = false,
)

val SAMPLE_ENGINE_LIVE_DATA = EngineState(
    screen = ScreenKind.LiveDataView,
    vehicleVin = "5FNRL6H73LB123456",
    vehicleSummary = "2020 Honda Odyssey",
    liveData = mapOf(
        "Engine RPM" to 1250.5,
        "Vehicle Speed" to 35.2,
        "Coolant Temp (°C)" to 85.0,
        "Intake Air Temp (°C)" to 22.5,
        "Throttle (%)" to 12.5,
        "MAP (kPa)" to 45.3,
    ),
    busy = false,
)

val SAMPLE_ENGINE_ACTUATION = EngineState(
    screen = ScreenKind.ActuationTest,
    vehicleVin = "5FNRL6H73LB123456",
    vehicleSummary = "2020 Honda Odyssey",
    busy = true,
)

val SAMPLE_ENGINE_DIALOG = EngineState(
    screen = ScreenKind.Dialog("Are you sure you want to clear all codes?"),
    vehicleVin = "5FNRL6H73LB123456",
    vehicleSummary = "2020 Honda Odyssey",
)

val SAMPLE_ENGINE_UNKNOWN = EngineState(
    screen = ScreenKind.Unknown("Some undocumented OEM diagnostic app screen type"),
    vehicleVin = "5FNRL6H73LB123456",
    vehicleSummary = "2020 Honda Odyssey",
)
