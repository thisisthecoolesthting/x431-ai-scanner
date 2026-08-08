package com.caseforge.scanner.engine

import com.caseforge.scanner.agent.AgentActionLog
import com.caseforge.scanner.agent.ScannerAccessibilityService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class EngineDriverDirectVciTest {

    private class FakeVciPort : VciDiagnosticPort {
        var readDtcsCalls = 0
        var lastModule: String? = null

        override suspend fun runCapability(id: String): Result<JsonObject> =
            Result.success(JsonObject(emptyMap()))

        override suspend fun fullScan(): Result<FullScanResult> =
            Result.success(FullScanResult(modules = emptyList(), durationMs = 0))

        override suspend fun readDtcs(module: String?): Result<List<Dtc>> {
            readDtcsCalls++
            lastModule = module
            return Result.success(
                listOf(
                    Dtc(
                        module = "OBD-II",
                        code = "P0300",
                        description = "P0300",
                        severity = Severity.Amber,
                        freezeFrame = null,
                    ),
                ),
            )
        }

        override suspend fun clearCodes(): Result<Unit> = Result.success(Unit)

        override fun liveData(pids: List<String>) = emptyFlow<LiveSample>()

        override suspend fun actuate(testId: String): Result<ActuationResult> =
            Result.success(ActuationResult(testId = testId, success = true, log = emptyList()))
    }

    private lateinit var a11y: ScannerAccessibilityService
    private lateinit var actionLog: AgentActionLog
    private lateinit var fakePort: FakeVciPort

    @Before
    fun setUp() {
        a11y = mock()
        actionLog = mock()
        fakePort = FakeVciPort()
    }

    private fun directVciDriver(port: VciDiagnosticPort? = fakePort): EngineDriver =
        EngineDriver(
            a11y = a11y,
            capabilities = object : CapabilityRegistry {
                override fun find(id: String): CapabilityMap.Capability? = null
            },
            scraper = EngineScraper,
            state = MutableStateFlow(EngineState.EMPTY),
            actionLog = actionLog,
            dataRoute = EngineDataRoute.DIRECT_VCI,
            vciPort = port,
        )

    @Test
    fun `readDtcs uses VciDiagnosticPort and never taps accessibility tree`() = runTest {
        val driver = directVciDriver()

        val result = driver.readDtcs()

        assertTrue(result.isSuccess)
        assertEquals(1, fakePort.readDtcsCalls)
        assertEquals("P0300", result.getOrThrow().single().code)
        verify(a11y, never()).tapByText(any(), any())
    }

    @Test
    fun `readDtcs fails when port missing without accessibility navigation`() = runTest {
        val driver = directVciDriver(port = null)

        val result = driver.readDtcs()

        assertTrue(result.isFailure)
        verify(a11y, never()).tapByText(any(), any())
    }
}
