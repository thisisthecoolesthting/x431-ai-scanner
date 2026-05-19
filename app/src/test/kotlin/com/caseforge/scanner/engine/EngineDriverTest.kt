package com.caseforge.scanner.engine

import com.caseforge.scanner.agent.AgentActionLog
import com.caseforge.scanner.agent.ScreenSnapshot
import com.caseforge.scanner.agent.ScannerAccessibilityService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [EngineDriver].
 *
 * [ScannerAccessibilityService] and [AgentActionLog] are Mockito mocks so no Android
 * framework or AccessibilityService lifecycle is needed in the JVM test runner.
 * [CapabilityRegistry] is a hand-written JVM fake.
 *
 * Each public method has at least one happy-path test and one timeout/failure test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineDriverTest {

    // ------------------------------------------------------------------
    // Fake CapabilityRegistry
    // ------------------------------------------------------------------

    private class FakeRegistry(
        private val map: Map<String, CapabilityMap.Capability> = emptyMap(),
    ) : CapabilityRegistry {
        override fun find(id: String): CapabilityMap.Capability? = map[id]
    }

    // ------------------------------------------------------------------
    // Fixture caps
    // ------------------------------------------------------------------

    private val shortCap = CapabilityMap.Capability(
        id = "test_cap",
        label = "Test",
        category = CapabilityMap.Category.Scan,
        path = listOf("diagnose", "test action"),
        doneWhen = "done marker",
        timeoutSec = 5,
    )

    private val fullScanCap = CapabilityMap.Capability(
        id = "full_scan",
        label = "Full system scan",
        category = CapabilityMap.Category.Scan,
        path = listOf("diagnose", "auto scan"),
        doneWhen = "scan complete",
        timeoutSec = 10,
    )

    private val readDtcsCap = CapabilityMap.Capability(
        id = "read_dtcs",
        label = "Read DTCs",
        category = CapabilityMap.Category.Codes,
        path = listOf("diagnose", "read fault code"),
        doneWhen = "fault code",
        timeoutSec = 5,
    )

    private val clearDtcsCap = CapabilityMap.Capability(
        id = "clear_dtcs",
        label = "Clear DTCs",
        category = CapabilityMap.Category.Codes,
        path = listOf("diagnose", "clear fault"),
        doneWhen = "clear successfully",
        timeoutSec = 5,
    )

    private val liveDataCap = CapabilityMap.Capability(
        id = "live_data",
        label = "Live data",
        category = CapabilityMap.Category.LiveData,
        path = listOf("diagnose", "read data stream"),
        timeoutSec = 60,
    )

    private val actuationCap = CapabilityMap.Capability(
        id = "actuation",
        label = "Actuation test",
        category = CapabilityMap.Category.Bidirectional,
        path = listOf("diagnose", "actuation test"),
        timeoutSec = 10,
    )

    // ------------------------------------------------------------------
    // Test infrastructure
    // ------------------------------------------------------------------

    /**
     * Mockito mock of [ScannerAccessibilityService]. Mockito subclasses the class
     * and stubs every method without calling the Android [AccessibilityService] super
     * constructor — safe in a pure JVM test runner.
     */
    private lateinit var a11y: ScannerAccessibilityService

    /**
     * Mockito mock of [AgentActionLog]. AgentActionLog requires an Android [Context]
     * constructor argument, so mocking is the only viable strategy in a JVM test.
     */
    private lateinit var actionLog: AgentActionLog
    private lateinit var stateFlow: MutableStateFlow<EngineState>

    @Before
    fun setUp() {
        a11y = mock()
        actionLog = mock()
        stateFlow = MutableStateFlow(EngineState.EMPTY)

        // Default stub: every tap succeeds; screen shows nothing special.
        whenever(a11y.tapByText(any(), any())).thenReturn(true)
        whenever(a11y.readScreen()).thenReturn(
            ScreenSnapshot(pkg = "com.cnlaunch.x431padv", activity = null, nodes = emptyList(), text = "")
        )
    }

    // ------------------------------------------------------------------
    // Helper: build a driver with the given caps and an optional delay.
    // ------------------------------------------------------------------

    private fun driver(
        vararg caps: CapabilityMap.Capability,
        delayMs: Long = 10L,
    ): EngineDriver = EngineDriver(
        a11y = a11y,
        capabilities = FakeRegistry(caps.associateBy { it.id }),
        scraper = EngineScraper,
        state = stateFlow,
        actionLog = actionLog,
        interStepDelayMs = delayMs,
    )

    // ------------------------------------------------------------------
    // Helper: configure a11y to return text that satisfies a doneWhen marker.
    // ------------------------------------------------------------------

    private fun stubScreenText(text: String) {
        whenever(a11y.readScreen()).thenReturn(
            ScreenSnapshot(pkg = "com.cnlaunch.x431padv", activity = null, nodes = emptyList(), text = text)
        )
    }

    // ==================================================================
    // runCapability
    // ==================================================================

    @Test
    fun `runCapability happy path - taps all steps, logs events, returns JsonObject`() = runTest {
        stubScreenText("done marker")
        val d = driver(shortCap)

        val result = d.runCapability("test_cap")

        assertTrue("Expected success, got ${result.exceptionOrNull()}", result.isSuccess)
        assertNotNull(result.getOrThrow())
        // walkPath calls actionLog.event twice per step (one "step" + one "step_timing")
        verify(actionLog, times(shortCap.path.size * 2)).event(any(), any())
    }

    @Test
    fun `runCapability returns CapabilityNotFound for unknown id`() = runTest {
        val d = driver() // empty registry

        val result = d.runCapability("nonexistent")

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Expected CapabilityNotFound, got $ex", ex is EngineException.CapabilityNotFound)
        assertEquals("nonexistent", (ex as EngineException.CapabilityNotFound).capId)
    }

    @Test
    fun `runCapability returns Timeout when doneWhen marker never appears`() = runTest {
        stubScreenText("something unrelated")
        val timedCap = shortCap.copy(timeoutSec = 1)
        val d = driver(timedCap, delayMs = 10L)

        val result = d.runCapability("test_cap")

        assertTrue(result.isFailure)
        assertTrue(
            "Expected Timeout, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is EngineException.Timeout,
        )
    }

    @Test
    fun `runCapability returns StepFailed when tapByText returns false`() = runTest {
        whenever(a11y.tapByText(any(), any())).thenReturn(false)
        val d = driver(shortCap)

        val result = d.runCapability("test_cap")

        assertTrue(result.isFailure)
        assertTrue(
            "Expected StepFailed, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is EngineException.StepFailed,
        )
    }

    // ==================================================================
    // fullScan
    // ==================================================================

    @Test
    fun `fullScan happy path - returns FullScanResult with non-negative duration`() = runTest {
        stubScreenText("auto scan complete scan complete")
        val d = driver(fullScanCap)

        val result = d.fullScan()

        assertTrue("Expected success, got ${result.exceptionOrNull()}", result.isSuccess)
        val fsr = result.getOrThrow()
        assertNotNull(fsr)
        assertTrue("durationMs should be >= 0", fsr.durationMs >= 0)
    }

    @Test
    fun `fullScan returns CapabilityNotFound when full_scan not registered`() = runTest {
        val d = driver()

        val result = d.fullScan()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is EngineException.CapabilityNotFound)
        assertEquals("full_scan", (result.exceptionOrNull() as EngineException.CapabilityNotFound).capId)
    }

    @Test
    fun `fullScan returns Timeout when scan never completes`() = runTest {
        stubScreenText("scanning in progress…")
        val timedCap = fullScanCap.copy(timeoutSec = 1)
        val d = driver(timedCap, delayMs = 10L)

        val result = d.fullScan()

        assertTrue(result.isFailure)
        assertTrue(
            "Expected Timeout, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is EngineException.Timeout,
        )
    }

    // ==================================================================
    // readDtcs
    // ==================================================================

    @Test
    fun `readDtcs happy path - scraper extracts DTCs from screen text`() = runTest {
        // EngineScraper uses DTC_REGEX (\b[PCBU][0-9A-F]{4}\b) on snapshot text.
        stubScreenText("fault code P0300 P0420")
        val d = driver(readDtcsCap)

        val result = d.readDtcs()

        assertTrue("Expected success, got ${result.exceptionOrNull()}", result.isSuccess)
        val dtcs = result.getOrThrow()
        assertTrue("Expected at least one DTC", dtcs.isNotEmpty())
    }

    @Test
    fun `readDtcs with module filter excludes non-matching entries`() = runTest {
        stubScreenText("fault code P0300")
        val d = driver(readDtcsCap)

        // Scraper sets module = null on raw DTCs; filter "ABS" matches nothing.
        val result = d.readDtcs(module = "ABS")

        assertTrue(result.isSuccess)
        assertTrue("Expected empty list for unmatched module", result.getOrThrow().isEmpty())
    }

    @Test
    fun `readDtcs returns Timeout when doneWhen marker never appears`() = runTest {
        stubScreenText("loading…")
        val timedCap = readDtcsCap.copy(timeoutSec = 1)
        val d = driver(timedCap, delayMs = 10L)

        val result = d.readDtcs()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is EngineException.Timeout)
    }

    // ==================================================================
    // clearCodes
    // ==================================================================

    @Test
    fun `clearCodes happy path - returns Unit on success`() = runTest {
        stubScreenText("clear successfully done")
        val d = driver(clearDtcsCap)

        val result = d.clearCodes()

        assertTrue("Expected success, got ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(Unit, result.getOrThrow())
    }

    @Test
    fun `clearCodes returns CapabilityNotFound when clear_dtcs not registered`() = runTest {
        val d = driver()

        val result = d.clearCodes()

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is EngineException.CapabilityNotFound)
        assertEquals("clear_dtcs", (ex as EngineException.CapabilityNotFound).capId)
    }

    @Test
    fun `clearCodes returns Timeout when OEM diagnostic app never confirms clear`() = runTest {
        stubScreenText("clearing codes…")
        val timedCap = clearDtcsCap.copy(timeoutSec = 1)
        val d = driver(timedCap, delayMs = 10L)

        val result = d.clearCodes()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is EngineException.Timeout)
    }

    // ==================================================================
    // liveData
    // ==================================================================

    @Test
    fun `liveData emits LiveSample for a known PID`() = runTest {
        stateFlow.value = EngineState(
            screen = ScreenKind.LiveDataView,
            liveData = mapOf("rpm" to 800.0),
        )
        val d = driver(liveDataCap)

        val samples = mutableListOf<LiveSample>()
        val job = launch {
            d.liveData(listOf("rpm")).collect { samples.add(it) }
        }

        advanceTimeBy(500L)
        job.cancel()

        assertTrue("Expected at least one sample", samples.isNotEmpty())
        assertEquals("rpm", samples.first().pid)
        assertEquals(800.0, samples.first().value, 0.001)
    }

    @Test
    fun `liveData emits nothing for an unknown PID`() = runTest {
        stateFlow.value = EngineState(
            screen = ScreenKind.LiveDataView,
            liveData = mapOf("rpm" to 800.0),
        )
        val d = driver(liveDataCap)

        val samples = mutableListOf<LiveSample>()
        val job = launch {
            d.liveData(listOf("unknown_pid")).collect { samples.add(it) }
        }

        advanceTimeBy(600L)
        job.cancel()

        assertTrue("Expected no samples for unknown pid", samples.isEmpty())
    }

    @Test
    fun `liveData stops emitting after collection is cancelled`() = runTest {
        stateFlow.value = EngineState(
            screen = ScreenKind.LiveDataView,
            liveData = mapOf("coolant_temp" to 90.0),
        )
        val d = driver(liveDataCap)

        val samples = mutableListOf<LiveSample>()
        val job = launch {
            d.liveData(listOf("coolant_temp")).collect { samples.add(it) }
        }

        advanceTimeBy(300L)
        job.cancel()
        val countAfterCancel = samples.size

        advanceTimeBy(1_000L)
        assertEquals("No new samples after cancellation", countAfterCancel, samples.size)
    }

    // ==================================================================
    // actuate
    // ==================================================================

    @Test
    fun `actuate happy path - returns ActuationResult with testId and log`() = runTest {
        stubScreenText("actuation test active test")
        val d = driver(actuationCap)

        val result = d.actuate("injector_1")

        assertTrue("Expected success, got ${result.exceptionOrNull()}", result.isSuccess)
        val ar = result.getOrThrow()
        assertEquals("injector_1", ar.testId)
        assertTrue("Expected non-empty log", ar.log.isNotEmpty())
    }

    @Test
    fun `actuate returns CapabilityNotFound when actuation not registered`() = runTest {
        val d = driver()

        val result = d.actuate("injector_1")

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is EngineException.CapabilityNotFound)
        assertEquals("actuation", (ex as EngineException.CapabilityNotFound).capId)
    }

    @Test
    fun `actuate returns failure when actuation screen never reached within timeout`() = runTest {
        stubScreenText("communicating please wait")
        val timedCap = actuationCap.copy(timeoutSec = 1, doneWhen = "actuation complete")
        val d = driver(timedCap, delayMs = 10L)

        val result = d.actuate("injector_1")

        assertTrue("Expected failure, got success", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(
            "Expected Timeout or StepFailed, got $ex",
            ex is EngineException.Timeout || ex is EngineException.StepFailed,
        )
    }

    // ==================================================================
    // Cross-cutting: actionLog is called for every step taken
    // ==================================================================

    @Test
    fun `actionLog receives two events per path step (step + step_timing)`() = runTest {
        stubScreenText("done marker")
        val d = driver(shortCap)

        d.runCapability("test_cap")

        // shortCap.path has 2 elements → 2 × 2 = 4 log events
        verify(actionLog, times(shortCap.path.size * 2)).event(any(), any())
    }
}
