package com.caseforge.scanner.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastWorkflowStateTest {

    @Test
    fun emptyStateHasNoMemory() {
        assertFalse(FastWorkflowState().hasAnyMemory)
    }

    @Test
    fun vinOnlyCountsAsMemory() {
        assertTrue(FastWorkflowState(lastVin = "1HGCM82633A004352").hasAnyMemory)
    }

    @Test
    fun lastGoodTransportCountsAsMemory() {
        assertTrue(FastWorkflowState(lastGoodTransport = "oem_usb").hasAnyMemory)
    }

    @Test
    fun successfulScanTimestampCountsAsMemory() {
        assertTrue(FastWorkflowState(lastSuccessfulScanAt = 1_700_000_000_000L).hasAnyMemory)
    }
}
